package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class InvoiceDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _invoice = MutableLiveData<Invoice>()
    val invoice: LiveData<Invoice> = _invoice

    private val _customer = MutableLiveData<Customer?>()
    val customer: LiveData<Customer?> = _customer

    private val _errorMessage = MutableLiveData("")
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData(false)
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
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Perform concurrent loading of invoice and customer
                coroutineScope {
                    val invoiceResult = invoiceRepository.getInvoiceByNumber(invoiceId)

                    invoiceResult.fold(
                        onSuccess = { invoiceResponse ->
                            // Ensure invoice is not null
                            val invoice =
                                invoiceResponse ?: throw IllegalStateException("Invoice not found")

                            _invoice.value = invoice

                            // Only load customer if customerId exists
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
                                            "InvoiceDetailViewModel",
                                            "Failed to load customer: ${customerError.message}"
                                        )
                                    }
                                )
                            }
                        },
                        onFailure = { error ->
                            _errorMessage.value = "Failed to load invoice: ${error.message}"
                            Log.e("InvoiceDetailViewModel", "Invoice load error", error)
                        }
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error loading invoice: ${e.localizedMessage}"
                Log.e("InvoiceDetailViewModel", "Unexpected error loading invoice", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateInvoice(
        updateBlock: (Invoice) -> Invoice,
        successMessage: String? = null,
        errorMessage: String = "Failed to update invoice"
    ) {
        val currentInvoice = _invoice.value ?: return

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Store original invoice for comparison
                val originalInvoice = currentInvoice.copy()

                // Create updated invoice
                val updatedInvoice = updateBlock(currentInvoice)

                // Save invoice first
                val result = invoiceRepository.saveInvoice(updatedInvoice)
                result.fold(
                    onSuccess = {
                        // Update customer balance after successful invoice save
                        try {
                            updateCustomerBalance(originalInvoice, updatedInvoice)
                        } catch (e: Exception) {
                            // Log error but don't fail the whole operation
                            Log.e("InvoiceDetailViewModel", "Error updating customer balance", e)
                        }

                        _invoice.value = updatedInvoice
                        successMessage?.let {
                            _errorMessage.value = it
                        }
                    },
                    onFailure = {
                        _errorMessage.value = errorMessage
                        Log.e("InvoiceDetailViewModel", errorMessage, it)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = errorMessage
                Log.e("InvoiceDetailViewModel", errorMessage, e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addInvoiceItem(newItem: InvoiceItem) {
        updateInvoice(
            updateBlock = { invoice ->
                val updatedItems = invoice.items.toMutableList().apply {
                    // Check if an identical item already exists
                    val existingItemIndex = indexOfFirst { it.isSameItem(newItem) }
                    if (existingItemIndex != -1) {
                        // Update quantity of existing item
                        val existingItem = this[existingItemIndex]
                        this[existingItemIndex] = existingItem.copy(
                            quantity = existingItem.quantity + newItem.quantity
                        )
                    } else {
                        // Add new item
                        add(newItem)
                    }
                }

                val newTotalAmount = calculateTotalAmount(updatedItems)

                invoice.copy(
                    items = updatedItems,
                    totalAmount = newTotalAmount
                )
            },
            successMessage = "Item added successfully"
        )
    }


    fun updateItemQuantity(item: InvoiceItem, newQuantity: Int) {
        updateInvoice(
            updateBlock = { invoice ->
                val updatedItems = invoice.items.map { existingItem ->
                    if (existingItem.id == item.id) {
                        existingItem.copy(quantity = newQuantity)
                    } else {
                        existingItem
                    }
                }

                val newTotalAmount = calculateTotalAmount(updatedItems)

                invoice.copy(
                    items = updatedItems,
                    totalAmount = newTotalAmount,
                    paidAmount = invoice.paidAmount.coerceAtMost(newTotalAmount)
                )
            },
            successMessage = "Quantity updated successfully"
        )
    }


    fun updateInvoiceItem(updatedItem: InvoiceItem) {
        Log.d(
            "InvoiceDetailViewModel",
            "Updating item with ${updatedItem.itemDetails.listOfExtraCharges.size} extra charges"
        )

        updateInvoice(
            updateBlock = { invoice ->
                val updatedItems = invoice.items.map { existingItem ->
                    if (existingItem.id == updatedItem.id) {
                        // Replace with updated item
                        updatedItem
                    } else {
                        existingItem
                    }
                }

                // Calculate new total amount considering all charges
                val newTotalAmount = calculateTotalAmount(updatedItems)
                Log.d("InvoiceDetailViewModel", "New total amount: $newTotalAmount")

                invoice.copy(
                    items = updatedItems,
                    totalAmount = newTotalAmount,
                    paidAmount = invoice.paidAmount.coerceAtMost(newTotalAmount)
                )
            },
            successMessage = "Item updated successfully"
        )
    }

    fun removeInvoiceItem(itemToRemove: InvoiceItem) {
        updateInvoice(
            updateBlock = { invoice ->
                val updatedItems = invoice.items.filter { it.id != itemToRemove.id }

                // Prevent removing the last item
                if (updatedItems.isEmpty()) {
                    throw IllegalStateException("Cannot remove the last item from an invoice")
                }

                val newTotalAmount = calculateTotalAmount(updatedItems)

                invoice.copy(
                    items = updatedItems,
                    totalAmount = newTotalAmount,
                    paidAmount = invoice.paidAmount.coerceAtMost(newTotalAmount)
                )
            },
            successMessage = "Item removed successfully"
        )
    }

    fun updateInvoiceNotes(notes: String) {
        updateInvoice(
            updateBlock = { invoice ->
                invoice.copy(notes = notes)
            },
            successMessage = "Notes updated successfully"
        )
    }

    fun addPaymentToInvoice(payment: Payment) {
        updateInvoice(
            updateBlock = { invoice ->
                // Add the new payment with its unique ID
                val updatedPayments = invoice.payments + payment

                // Recalculate total paid amount
                val totalPaid = updatedPayments.sumOf { it.amount }

                invoice.copy(
                    payments = updatedPayments,
                    paidAmount = totalPaid
                )
            },
            successMessage = "Payment added successfully"
        )
    }

    fun removePayment(paymentToRemove: Payment) {
        updateInvoice(
            updateBlock = { invoice ->
                // Remove only the specific payment using its unique ID
                val updatedPayments = invoice.payments.filter { it.id != paymentToRemove.id }

                // Recalculate total paid amount
                val totalPaid = updatedPayments.sumOf { it.amount }

                invoice.copy(
                    payments = updatedPayments,
                    paidAmount = totalPaid
                )
            },
            successMessage = "Payment removed successfully"
        )
    }


    fun duplicateInvoice() {
        val currentInvoice = _invoice.value ?: return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Generate a new invoice number
                val dateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
                val datePart = dateFormat.format(Date())
                val random = (1000..9999).random()
                val newInvoiceNumber = "INV-$datePart-$random"

                // Create new invoice with same details but different ID and payments reset
                val newInvoice = currentInvoice.copy(
                    id = "",
                    invoiceNumber = newInvoiceNumber,
                    invoiceDate = System.currentTimeMillis(),
                    payments = emptyList(),
                    paidAmount = 0.0,
                    notes = "${currentInvoice.notes}\n(Duplicated from ${currentInvoice.invoiceNumber})"
                )

                val result = invoiceRepository.saveInvoice(newInvoice)
                result.fold(
                    onSuccess = {
                        _errorMessage.value = "Invoice duplicated successfully"
                    },
                    onFailure = {
                        _errorMessage.value = "Failed to duplicate invoice"
                        Log.e("InvoiceDetailViewModel", "Failed to duplicate invoice", it)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error duplicating invoice"
                Log.e("InvoiceDetailViewModel", "Error duplicating invoice", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteInvoice(onComplete: (Boolean) -> Unit = {}) {
        val currentInvoice = _invoice.value ?: return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Calculate the balance adjustment
                val unpaidAmount = currentInvoice.totalAmount - currentInvoice.paidAmount

                // Delete invoice first
                val result = invoiceRepository.deleteInvoice(currentInvoice.invoiceNumber)
                result.fold(
                    onSuccess = {
                        // Now update customer balance if needed
                        if (unpaidAmount > 0 && currentInvoice.customerId.isNotEmpty()) {
                            try {
                                val customerResult = customerRepository.getCustomerById(currentInvoice.customerId)
                                customerResult.fold(
                                    onSuccess = { customer ->
                                        // Reduce balance by the unpaid amount
                                        val newBalance = customer.currentBalance - unpaidAmount
                                        val updatedCustomer = customer.copy(currentBalance = newBalance)
                                        customerRepository.updateCustomer(updatedCustomer)

                                        Log.d("InvoiceDetailViewModel",
                                            "Customer balance updated after invoice deletion: ${customer.currentBalance} -> $newBalance")
                                    },
                                    onFailure = { error ->
                                        Log.e("InvoiceDetailViewModel", "Failed to update customer balance", error)
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("InvoiceDetailViewModel", "Error updating customer balance after invoice deletion", e)
                            }
                        }

                        _errorMessage.value = "Invoice deleted successfully"
                        onComplete(true) // Signal successful completion
                    },
                    onFailure = {
                        _errorMessage.value = "Failed to delete invoice"
                        Log.e("InvoiceDetailViewModel", "Failed to delete invoice", it)
                        onComplete(false) // Signal failed completion
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error deleting invoice"
                Log.e("InvoiceDetailViewModel", "Error deleting invoice", e)
                onComplete(false) // Signal failed completion
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun updateCustomerBalance(oldInvoice: Invoice, newInvoice: Invoice) {
        // Only proceed if customer IDs match and we're working with the same customer
        if (oldInvoice.customerId != newInvoice.customerId || oldInvoice.customerId.isEmpty()) {
            return
        }

        try {
            // Get the current customer
            val customerResult = customerRepository.getCustomerById(oldInvoice.customerId)

            customerResult.fold(
                onSuccess = { customer ->
                    // Calculate the balance change
                    val oldUnpaidAmount = oldInvoice.totalAmount - oldInvoice.paidAmount
                    val newUnpaidAmount = newInvoice.totalAmount - newInvoice.paidAmount
                    val balanceChange = newUnpaidAmount - oldUnpaidAmount

                    // Only update if there's an actual change
                    if (balanceChange != 0.0) {
                        // Calculate new balance
                        val newBalance = customer.currentBalance + balanceChange

                        // Update customer with new balance
                        val updatedCustomer = customer.copy(currentBalance = newBalance)
                        customerRepository.updateCustomer(updatedCustomer)

                        Log.d(
                            "InvoiceDetailViewModel", "Customer balance updated: " +
                                    "Old: ${customer.currentBalance}, New: $newBalance, Change: $balanceChange"
                        )
                    }
                },
                onFailure = { error ->
                    Log.e("InvoiceDetailViewModel", "Failed to update customer balance", error)
                    throw error
                }
            )
        } catch (e: Exception) {
            Log.e("InvoiceDetailViewModel", "Error updating customer balance", e)
            throw e
        }
    }


    private fun calculateTotalAmount(items: List<InvoiceItem>): Double {
        // Calculate subtotal (base price * quantity for each item)
        val subtotal = items.sumOf { it.price * it.quantity }

        // Calculate tax amount
        val taxAmount = items.sumOf { item ->
            val itemTotal = item.price * item.quantity
            itemTotal * (item.itemDetails.taxRate / 100.0)
        }

        // Calculate extra charges - ensure we're counting all charges
        val extraChargesTotal = items.sumOf { item ->
            Log.d(
                "InvoiceDetailViewModel",
                "Item ${item.itemDetails.displayName} has ${item.itemDetails.listOfExtraCharges.size} extra charges"
            )

            item.itemDetails.listOfExtraCharges.sumOf { charge ->
                val chargeAmount = charge.amount * item.quantity
                Log.d("InvoiceDetailViewModel", "Extra charge: ${charge.name} = $chargeAmount")
                chargeAmount
            }
        }

        Log.d(
            "InvoiceDetailViewModel",
            "Subtotal: $subtotal, Tax: $taxAmount, Extra Charges: $extraChargesTotal"
        )

        return subtotal + taxAmount + extraChargesTotal
    }

    // Helper method to calculate total amount
//    private fun calculateTotalAmount(items: List<InvoiceItem>): Double {
//        // Calculate subtotal
//        val subtotal = items.sumOf { it.price * it.quantity }
//
//        // Calculate tax amount
//        val taxAmount = items.sumOf { item ->
//            val itemTotal = item.price * item.quantity
//            itemTotal * (item.itemDetails.taxRate / 100.0)
//        }
//
//        // Calculate extra charges
//        val extraChargesTotal = items.sumOf { item ->
//            item.itemDetails.listOfExtraCharges.sumOf { charge ->
//                charge.amount * item.quantity
//            }
//        }
//
//        return subtotal + taxAmount + extraChargesTotal
//    }

    // Getter methods with null checks
    fun getCustomerPhone(): String = _customer.value?.phoneNumber ?: ""

    fun getCustomerAddress(): String {
        val customer = _customer.value
        return when {
            customer != null -> {
                val parts = mutableListOf<String>()
                if (customer.streetAddress.isNotEmpty()) parts.add(customer.streetAddress)
                if (customer.city.isNotEmpty()) parts.add(customer.city)
                if (customer.state.isNotEmpty()) parts.add(customer.state)
                parts.joinToString(", ").takeIf { it.isNotEmpty() } ?: "Address not available"
            }

            else -> _invoice.value?.customerAddress ?: "Address not available"
        }
    }
}