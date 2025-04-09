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

    private val _filteredPayments = MutableLiveData<List<PaymentWithContext>>()
    val filteredPayments: LiveData<List<PaymentWithContext>> = _filteredPayments

    private val _totalCollected = MutableLiveData<Double>()
    val totalCollected: LiveData<Double> = _totalCollected

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    init {
        loadPayments()
    }

    private fun isOnline(): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun loadPayments() {
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

                        // Default filter to this month
                        filterPaymentsByPeriod("This Month", paymentsWithContext)

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

    fun filterPaymentsByPeriod(
        period: String,
        allPayments: List<PaymentWithContext>? = null
    ) {
        val paymentsToFilter = allPayments ?: _filteredPayments.value ?: emptyList()
        val now = Calendar.getInstance()

        val filteredList = paymentsToFilter.filter { paymentContext ->
            val paymentDate = Calendar.getInstance().apply {
                timeInMillis = paymentContext.payment.date
            }

            when (period) {
                "Today" -> isSameDay(now, paymentDate)
                "This Week" -> isSameWeek(now, paymentDate)
                "This Month" -> isSameMonth(now, paymentDate)
                "This Year" -> isSameYear(now, paymentDate)
                else -> true // "All Time"
            }
        }

        _filteredPayments.value = filteredList
        _totalCollected.value = filteredList.sumOf { it.payment.amount }
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