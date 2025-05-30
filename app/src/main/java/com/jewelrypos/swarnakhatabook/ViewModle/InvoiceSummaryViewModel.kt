package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvoiceSummaryViewModel(application: Application) : AndroidViewModel(application) {

    private val _invoice = MutableLiveData<Invoice>()
    val invoice: LiveData<Invoice> = _invoice

    private val _customer = MutableLiveData<Customer?>()
    val customer: LiveData<Customer?> = _customer

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val invoiceRepository: InvoiceRepository
    private val customerRepository: CustomerRepository
    private val pdfSettingsManager: PdfSettingsManager
    private val shopManager: ShopManager

    init {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()

        invoiceRepository = InvoiceRepository(firestore, auth, application.applicationContext)
        customerRepository = CustomerRepository(firestore, auth, application.applicationContext)
        pdfSettingsManager = PdfSettingsManager(application.applicationContext)
        shopManager = ShopManager
        shopManager.initialize(application.applicationContext) // Ensure ShopManager is initialized
    }

    fun loadInvoice(invoiceId: String) {
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                coroutineScope {
                    val invoiceResult = invoiceRepository.getInvoiceByNumber(invoiceId)

                    invoiceResult.fold(
                        onSuccess = { invoiceResponse ->
                            val invoice =
                                invoiceResponse ?: throw IllegalStateException("Invoice not found")

                            _invoice.value = invoice

                            invoice.customerId.takeIf { it.isNotEmpty() }?.let { customerId ->
                                val customerDeferred = async {
                                    customerRepository.getCustomerById(customerId)
                                }
                                val customerResult = customerDeferred.await()

                                customerResult.fold(
                                    onSuccess = { customer ->
                                        _customer.value = customer
                                    },
                                    onFailure = { customerError ->
                                        Log.w(
                                            "InvoiceSummaryViewModel",
                                            "Failed to load customer: ${customerError.message}"
                                        )
                                    }
                                )
                            }
                        },
                        onFailure = { error ->
                            _errorMessage.value = "Failed to load invoice: ${error.message}"
                            Log.e("InvoiceSummaryViewModel", "Invoice load error", error)
                        }
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error loading invoice: ${e.localizedMessage}"
                Log.e("InvoiceSummaryViewModel", "Unexpected error loading invoice", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markInvoiceAsPaid(balanceDue: Double) {
        val currentInvoice = _invoice.value ?: return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Add full payment for remaining balance
                val newPayment = Payment(
                    amount = balanceDue,
                    method = "Manual Adjustment",
                    date = System.currentTimeMillis(),
                    reference = "Marked as Paid",
                    notes = "Invoice Manually Marked"
                )
                val updatedPayments = currentInvoice.payments + newPayment
                val newPaidAmount = updatedPayments.sumOf { it.amount }

                val updatedInvoice = currentInvoice.copy(
                    payments = updatedPayments,
                    paidAmount = newPaidAmount
                )

                val result = invoiceRepository.saveInvoice(updatedInvoice)
                result.fold(
                    onSuccess = {
                        _invoice.value = updatedInvoice
                        _errorMessage.value = "Invoice marked as paid"
                    },
                    onFailure = {
                        _errorMessage.value = "Failed to mark invoice as paid: ${it.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error marking invoice as paid: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun duplicateInvoice() {
        val currentInvoice = _invoice.value ?: return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val dateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
                val datePart = dateFormat.format(Date())
                val random = (1000..9999).random()
                val newInvoiceNumber = "INV-$datePart-$random"

                val newInvoice = currentInvoice.copy(
                    id = newInvoiceNumber, // New ID
                    invoiceNumber = newInvoiceNumber,
                    invoiceDate = System.currentTimeMillis(),
                    payments = emptyList(), // Reset payments for duplicated invoice
                    paidAmount = 0.0,
                    notes = "${currentInvoice.notes}\n(Duplicated from ${currentInvoice.invoiceNumber})"
                )

                val result = invoiceRepository.saveInvoice(newInvoice)
                result.fold(
                    onSuccess = {
                        _errorMessage.value = "Invoice duplicated successfully (New Invoice: $newInvoiceNumber)"
                    },
                    onFailure = {
                        _errorMessage.value = "Failed to duplicate invoice: ${it.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error duplicating invoice: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteInvoice(onComplete: (Boolean) -> Unit) {
        val currentInvoice = _invoice.value ?: return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val result = invoiceRepository.moveInvoiceToRecycleBin(currentInvoice.invoiceNumber)
                result.fold(
                    onSuccess = {
                        _errorMessage.value = "Invoice moved to recycling bin"
                        onComplete(true)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to delete invoice: ${error.message}"
                        Log.e("InvoiceSummaryViewModel", "Failed to delete invoice", error)
                        onComplete(false)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error deleting invoice: ${e.message}"
                Log.e("InvoiceSummaryViewModel", "Error deleting invoice", e)
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Helper to get PdfSettings
    fun getPdfSettings() = pdfSettingsManager.loadSettings()

    // Helper to get ShopInvoiceDetails
    fun getShopDetails() = shopManager.getShopDetails(getApplication<Application>().applicationContext)

    // Helper to get customer phone for calling/messaging
    fun getCustomerPhone(): String = _customer.value?.phoneNumber ?: _invoice.value?.customerPhone ?: ""

    // Helper to get customer type
    fun getCustomerType(): String? = _customer.value?.customerType
}