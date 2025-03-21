package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.net.ConnectivityManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InvoiceDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _invoice = MutableLiveData<Invoice>()
    val invoice: LiveData<Invoice> = _invoice

    private val _customer = MutableLiveData<Customer?>()
    val customer: LiveData<Customer?> = _customer

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val invoiceRepository: InvoiceRepository
    private val customerRepository: CustomerRepository

    init {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()

        invoiceRepository = InvoiceRepository(firestore, auth)
        customerRepository = CustomerRepository(firestore, auth)
    }

    fun loadInvoice(invoiceId: String) {
        _isLoading.value = true

        viewModelScope.launch {
            invoiceRepository.getInvoiceByNumber(invoiceId).fold(
                onSuccess = { invoice ->
                    _invoice.value = invoice
                    loadCustomerDetails(invoice.customerId)
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to load invoice: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    private fun loadCustomerDetails(customerId: String) {
        if (customerId.isEmpty()) return

        viewModelScope.launch {
            customerRepository.getCustomerById(customerId).fold(
                onSuccess = { customer ->
                    _customer.value = customer
                },
                onFailure = { error ->
                    Log.e("InvoiceDetailViewModel", "Error loading customer: ${error.message}")
                    // Not showing this error to the user as it's not critical
                }
            )
        }
    }

    fun getCustomerPhone(): String {
        return _customer.value?.phoneNumber ?: ""
    }

    fun getCustomerAddress(): String {
        val customer = _customer.value

        if (customer == null) {
            // If customer is null, try to provide a fallback from the invoice
            val invoice = _invoice.value
            return invoice?.customerAddress ?: "Address not available"
        }

        // If we have customer data, construct the address
        val parts = mutableListOf<String>()

        if (customer.streetAddress.isNotEmpty()) {
            parts.add(customer.streetAddress)
        }

        if (customer.city.isNotEmpty()) {
            parts.add(customer.city)
        }

        if (customer.state.isNotEmpty()) {
            parts.add(customer.state)
        }

        return if (parts.isNotEmpty()) parts.joinToString(", ") else "Address not available"
    }

    fun addPaymentToInvoice(payment: Payment) {
        val currentInvoice = _invoice.value ?: return

        val paymentWithId = payment.copy(
            id = UUID.randomUUID().toString()
        )

        val updatedPayments = currentInvoice.payments + paymentWithId
        val totalPaid = updatedPayments.sumOf { it.amount }

        val updatedInvoice = currentInvoice.copy(
            payments = updatedPayments,
            paidAmount = totalPaid
        )

        viewModelScope.launch {
            _isLoading.value = true

            invoiceRepository.saveInvoice(updatedInvoice).fold(
                onSuccess = {
                    _invoice.value = updatedInvoice
                    _isLoading.value = false
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to add payment: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun duplicateInvoice(originalInvoice: Invoice, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true

            // Generate a new invoice number
            val dateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
            val datePart = dateFormat.format(Date())
            val random = (1000..9999).random()
            val newInvoiceNumber = "INV-$datePart-$random"

            // Create new invoice with same details but different ID and payments reset
            val newInvoice = originalInvoice.copy(
                id = "",
                invoiceNumber = newInvoiceNumber,
                invoiceDate = System.currentTimeMillis(),
                payments = emptyList(),
                paidAmount = 0.0,
                notes = originalInvoice.notes + "\n(Duplicated from ${originalInvoice.invoiceNumber})"
            )

            invoiceRepository.saveInvoice(newInvoice).fold(
                onSuccess = {
                    _isLoading.value = false
                    callback(true)
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to duplicate invoice: ${error.message}"
                    _isLoading.value = false
                    callback(false)
                }
            )
        }
    }

    fun deleteInvoice(invoice: Invoice, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true

            invoiceRepository.deleteInvoice(invoice.invoiceNumber).fold(
                onSuccess = {
                    _isLoading.value = false
                    callback(true)
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to delete invoice: ${error.message}"
                    _isLoading.value = false
                    callback(false)
                }
            )
        }
    }
}