package com.jewelrypos.swarnakhatabook.ViewModle

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.PaymentsRepository
import kotlinx.coroutines.launch
import java.util.Calendar


class PaymentsViewModel(
    private val repository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    private val _allInvoices = MutableLiveData<List<Invoice>>()

    // Original unfiltered payments
    private val _allPayments = MutableLiveData<List<PaymentWithContext>>()

    // Filtered payments (by period and search)
    private val _filteredPayments = MutableLiveData<List<PaymentWithContext>>()
    val filteredPayments: LiveData<List<PaymentWithContext>> = _filteredPayments

    private val _totalCollected = MutableLiveData<Double>()
    val totalCollected: LiveData<Double> = _totalCollected

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // Current search query
    private var currentSearchQuery = ""

    // Current time period filter
    private var currentPeriodFilter = "This Month"

    init {
        loadPayments()
    }

    private fun isOnline(): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    fun loadPayments() {
        _isLoading.value = true
        viewModelScope.launch {
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            try {
                // Use the repository method directly
                val result = repository.fetchInvoicesPaginated(
                    loadNextPage = false,
                    source = source
                )

                result.fold(
                    onSuccess = { invoices ->
                        _allInvoices.value = invoices

                        // Extract payments with context
                        val paymentsWithContext = extractPaymentsWithContext(invoices)
                        _allPayments.value = paymentsWithContext

                        // Apply filters based on current period and search query
                        applyAllFilters()

                        _isLoading.value = false
                    },
                    onFailure = { error ->
                        _errorMessage.value = error.message
                        _isLoading.value = false
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.message
                _isLoading.value = false
            }
        }
    }

    private fun extractPaymentsWithContext(invoices: List<Invoice>): List<PaymentWithContext> {
        return invoices.flatMap { invoice ->
            invoice.payments.map { payment ->
                PaymentWithContext(
                    payment = payment,
                    invoiceNumber = invoice.invoiceNumber,
                    customerName = invoice.customerName,
                    customerId = invoice.customerId
                )
            }
        }
    }

    // Set the search query and trigger filtering
    fun setSearchQuery(query: String) {
        currentSearchQuery = query.trim().lowercase()
        applyAllFilters()
    }

    // Set the period filter and trigger filtering
    fun setPeriodFilter(period: String) {
        currentPeriodFilter = period
        applyAllFilters()
    }

    // Apply both period and search filters
    private fun applyAllFilters() {
        val allPayments = _allPayments.value ?: emptyList()

        // First filter by period
        val periodFiltered = filterByPeriod(allPayments, currentPeriodFilter)

        // Then filter by search query
        val searchFiltered = if (currentSearchQuery.isEmpty()) {
            periodFiltered
        } else {
            filterBySearchQuery(periodFiltered, currentSearchQuery)
        }

        // Update filtered payments
        _filteredPayments.value = searchFiltered

        // Update total collected
        _totalCollected.value = searchFiltered.sumOf { it.payment.amount }
    }

    // Filter payments by period
    private fun filterByPeriod(payments: List<PaymentWithContext>, period: String): List<PaymentWithContext> {
        if (period == "All Time") {
            return payments
        }

        val now = Calendar.getInstance()
        return payments.filter { paymentContext ->
            val paymentDate = Calendar.getInstance().apply {
                timeInMillis = paymentContext.payment.date
            }

            when (period) {
                "Today" -> isSameDay(now, paymentDate)
                "This Week" -> isSameWeek(now, paymentDate)
                "This Month" -> isSameMonth(now, paymentDate)
                "This Year" -> isSameYear(now, paymentDate)
                else -> true
            }
        }
    }

    // Filter payments by search query
    private fun filterBySearchQuery(payments: List<PaymentWithContext>, query: String): List<PaymentWithContext> {
        return payments.filter { payment ->
            // Search by multiple criteria
            val matchesCustomerName = payment.customerName?.lowercase()?.contains(query) == true
            val matchesInvoiceNumber = payment.invoiceNumber?.lowercase()?.contains(query) == true
            val matchesMethod = payment.payment.method.lowercase().contains(query)
            val matchesAmount = payment.payment.amount.toString().contains(query)
            val matchesReference = payment.payment.reference.lowercase().contains(query)

            // Match against payment details
            var matchesDetails = false
            for ((_, value) in payment.payment.details) {
                if (value.toString().lowercase().contains(query)) {
                    matchesDetails = true
                    break
                }
            }

            matchesCustomerName || matchesInvoiceNumber || matchesMethod ||
                    matchesAmount || matchesReference || matchesDetails
        }
    }

    // Date comparison helper methods
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameWeek(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR)
    }

    private fun isSameMonth(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    private fun isSameYear(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }

    // Wrapper class to include additional context
    data class PaymentWithContext(
        val payment: Payment,
        val invoiceNumber: String,
        val customerName: String,
        val customerId: String
    )
}