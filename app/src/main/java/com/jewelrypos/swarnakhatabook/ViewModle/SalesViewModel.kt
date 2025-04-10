package com.jewelrypos.swarnakhatabook.ViewModle

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.ExtraCharge
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.DataClasses.SelectedItemWithPrice
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import kotlinx.coroutines.launch

class SalesViewModel(
    private val repository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    // Selected customer for new order/invoice
    private val _selectedCustomer = MutableLiveData<Customer>()
    val selectedCustomer: LiveData<Customer> = _selectedCustomer

    // Selected items for new order/invoice
    private val _selectedItems = MutableLiveData<List<SelectedItemWithPrice>>()
    val selectedItems: LiveData<List<SelectedItemWithPrice>> = _selectedItems

    // Payments for new invoice
    private val _payments = MutableLiveData<List<Payment>>(emptyList())
    val payments: LiveData<List<Payment>> = _payments

    // All invoices before filtering
    private val _allInvoices = mutableListOf<Invoice>()

    // Filtered invoices list that's exposed to the UI
    private val _invoices = MutableLiveData<List<Invoice>>()
    val invoices: LiveData<List<Invoice>> = _invoices

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error messages
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _customerInvoices = MutableLiveData<List<Invoice>>()
    val customerInvoices: LiveData<List<Invoice>> = _customerInvoices

    // Current search query
    private var currentSearchQuery = ""

    init {
        loadFirstPage()
    }


    // Check if device is online
    private fun isOnline(): Boolean {
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun loadFirstPage() {
        _isLoading.value = true
        _allInvoices.clear()
        viewModelScope.launch {
            // Choose source based on connectivity
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            repository.fetchInvoicesPaginated(false, source).fold(
                onSuccess = {
                    _allInvoices.addAll(it)
                    // Apply current search filter if any
                    applySearchFilter()
                    _isLoading.value = false
                    Log.d("SalesViewModel", "First page loaded: ${it.size} invoices")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("SalesViewModel", "Error loading first page: ${it.message}")
                }
            )
        }
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return

        _isLoading.value = true
        viewModelScope.launch {
            // Choose source based on connectivity
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            repository.fetchInvoicesPaginated(true, source).fold(
                onSuccess = { newInvoices ->
                    _allInvoices.addAll(newInvoices)
                    // Apply search filter to the updated list
                    applySearchFilter()
                    _isLoading.value = false
                    Log.d("SalesViewModel", "Next page loaded: ${newInvoices.size} invoices")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("SalesViewModel", "Error loading next page: ${it.message}")
                }
            )
        }
    }

    fun refreshInvoices() {
        _isLoading.value = true
        viewModelScope.launch {
            loadFirstPage()
        }
    }

    // New method to search invoices
    fun searchInvoices(query: String) {
        currentSearchQuery = query.trim().lowercase()
        applySearchFilter()
    }

    // Helper method to apply the search filter to all invoices
    private fun applySearchFilter() {
        if (currentSearchQuery.isEmpty()) {
            // No search filter, show all invoices
            _invoices.value = _allInvoices.toList()
            return
        }

        // Apply search filter
        val filteredList = _allInvoices.filter { invoice ->
            // Search in invoice number
            invoice.invoiceNumber.lowercase().contains(currentSearchQuery) ||
                    // Search in customer name
                    invoice.customerName.lowercase().contains(currentSearchQuery) ||
                    // Search in customer phone
                    invoice.customerPhone.lowercase().contains(currentSearchQuery) ||
                    // Search in notes
                    invoice.notes.lowercase().contains(currentSearchQuery) ||
                    // Search in invoice items
                    invoice.items.any {
                        it.itemDetails.displayName.lowercase().contains(currentSearchQuery) ||
                                it.itemDetails.jewelryCode.lowercase().contains(currentSearchQuery)
                    }
        }

        _invoices.value = filteredList
        Log.d("SalesViewModel", "Search applied. Results: ${filteredList.size}")
    }

    // Add a selected item
    fun addSelectedItem(item: JewelleryItem, price: Double) {
        val currentItems = _selectedItems.value?.toMutableList() ?: mutableListOf()

        // Check if item already exists in the list
        val existingItemIndex = currentItems.indexOfFirst { it.item.id == item.id }

        if (existingItemIndex >= 0) {
            // Update quantity of existing item
            val existingItem = currentItems[existingItemIndex]
            currentItems[existingItemIndex] = SelectedItemWithPrice(
                item = existingItem.item,
                quantity = existingItem.quantity + 1,
                price = price
            )
        } else {
            // Add new item with quantity 1
            currentItems.add(
                SelectedItemWithPrice(
                    item = item,
                    quantity = 1,
                    price = price
                )
            )
        }

        _selectedItems.value = currentItems
    }

    // Remove a selected item
    fun removeSelectedItem(item: SelectedItemWithPrice) {
        val currentItems = _selectedItems.value?.toMutableList() ?: return
        currentItems.remove(item)
        _selectedItems.value = currentItems
    }

    // Update item quantity
    fun updateItemQuantity(itemEdit: SelectedItemWithPrice, quantity: Int) {
        // If quantity is 0 or negative, remove the item
        if (quantity <= 0) {
            removeSelectedItem(itemEdit)
            return
        }

        val currentItems = _selectedItems.value?.toMutableList() ?: return
        val itemIndex = currentItems.indexOfFirst { it.item.id == itemEdit.item.id }

        if (itemIndex >= 0) {
            val item = currentItems[itemIndex]
            currentItems[itemIndex] = SelectedItemWithPrice(
                item = item.item,
                quantity = quantity,
                price = item.price
            )
            _selectedItems.value = currentItems
        }
    }

    // Update selected item
    fun updateSelectedItem(updatedItem: JewelleryItem, newPrice: Double): Boolean {
        val currentItems = _selectedItems.value?.toMutableList() ?: return false
        val itemIndex = currentItems.indexOfFirst { it.item.id == updatedItem.id }

        return if (itemIndex >= 0) {
            // Preserve the quantity from the existing item
            val existingQuantity = currentItems[itemIndex].quantity

            // Replace with updated item but keep same quantity
            currentItems[itemIndex] = SelectedItemWithPrice(
                item = updatedItem,
                quantity = existingQuantity,
                price = newPrice
            )

            // This is a critical line - needs to update the LiveData object
            _selectedItems.value = currentItems

            // Log successful update
            Log.d("SalesViewModel", "Successfully updated item: ${updatedItem.id}")
            true
        } else {
            // Item not found in the list
            Log.w(
                "SalesViewModel",
                "Attempted to update non-existent item with ID: ${updatedItem.id}"
            )
            false
        }
    }

    // Payments methods
    fun addPayment(payment: Payment) {
        val currentPayments = _payments.value?.toMutableList() ?: mutableListOf()
        currentPayments.add(payment)
        _payments.value = currentPayments
    }

    fun removePayment(payment: Payment) {
        val currentPayments = _payments.value?.toMutableList() ?: return
        currentPayments.remove(payment)
        _payments.value = currentPayments
    }

    // Calculate methods
    fun calculateSubtotal(): Double {
        return _selectedItems.value?.sumOf { it.price * it.quantity } ?: 0.0
    }

    // Get all extra charges from all items
    fun getAllExtraCharges(): List<Pair<String, Double>> {
        val charges = mutableListOf<Pair<String, Double>>()

        _selectedItems.value?.forEach { selectedItem ->
            // Multiply each extra charge by the item quantity
            selectedItem.item.listOfExtraCharges.forEach { extraCharge ->
                charges.add(Pair(extraCharge.name, extraCharge.amount * selectedItem.quantity))
            }
        }

        return charges
    }
    fun updateItems(newItems: List<SelectedItemWithPrice>) {
        // Clear current items
        _selectedItems.value?.forEach { item ->
            removeSelectedItem(item)
        }

        // Add all new items
        newItems.forEach { item ->
            _selectedItems.value = (_selectedItems.value ?: mutableListOf()) + item
        }
    }


    // Calculate total extra charges
    fun calculateExtraCharges(): Double {
        return getAllExtraCharges().sumOf { it.second }
    }

    // Calculate tax based on each item's tax rate
    fun calculateTax(): Double {
        var totalTax = 0.0

        _selectedItems.value?.forEach { selectedItem ->
            // Use the totalTax field directly from the item
            totalTax += selectedItem.item.totalTax * selectedItem.quantity
        }

        return totalTax
    }

    // Calculate total (subtotal + extra charges + tax)
    fun calculateTotal(): Double {
        return calculateSubtotal() + calculateExtraCharges() + calculateTax()
    }

    // Calculate total paid amount
    fun calculateTotalPaid(): Double {
        return _payments.value?.sumOf { it.amount } ?: 0.0
    }

    // Set the selected customer
    fun setSelectedCustomer(customer: Customer) {
        _selectedCustomer.value = customer
    }

    // Save a new invoice
    // Save a new invoice with improved error handling
    fun saveInvoice(invoice: Invoice, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Log the saving attempt for debugging
                Log.d("SalesViewModel", "Saving invoice: ${invoice.invoiceNumber}, " +
                        "items: ${invoice.items.size}, payments: ${invoice.payments.size}")

                // Try to save the invoice
                repository.saveInvoice(invoice).fold(
                    onSuccess = {
                        // Refresh invoices list after saving
                        refreshInvoices()
                        // Clear current invoice data
                        clearCurrentInvoice()
                        _isLoading.value = false
                        callback(true)
                        Log.d("SalesViewModel", "Invoice saved successfully: ${invoice.invoiceNumber}")
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to save invoice: ${error.message}"
                        _isLoading.value = false
                        callback(false)
                        Log.e("SalesViewModel", "Failed to save invoice: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Unexpected error: ${e.message}"
                _isLoading.value = false
                callback(false)
                Log.e("SalesViewModel", "Unexpected error saving invoice", e)
            }
        }
    }

    // Clear current invoice data after saving
    private fun clearCurrentInvoice() {
        _selectedCustomer.value = Customer()
        _selectedItems.value = emptyList()
        _payments.value = emptyList()
    }

    // Method to load invoices for a specific customer
    fun loadCustomerInvoices(customerId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Fetch all invoices (you might want to optimize this by adding a repository method
                // that fetches only invoices for a specific customer)
                repository.fetchInvoicesPaginated(false).fold(
                    onSuccess = { allInvoices ->
                        // Filter invoices for the specified customer
                        val filteredInvoices = allInvoices.filter { it.customerId == customerId }
                        _customerInvoices.value = filteredInvoices
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
}