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
import com.jewelrypos.swarnakhatabook.Enums.DateFilterType
import com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    // Invoices list
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

    private val _allInvoices = mutableListOf<Invoice>()

    // Current search query
    private var currentSearchQuery = ""

    private var currentDateFilter = DateFilterType.ALL_TIME

    private var currentStatusFilter = PaymentStatusFilter.ALL


    init {
        loadFirstPage()
    }

    fun getCurrentDateFilter(): DateFilterType = currentDateFilter
    fun getCurrentStatusFilter(): PaymentStatusFilter = currentStatusFilter


    private fun applyFilters() {
        val filtered = _allInvoices
            .filter { invoice -> matchesDateFilter(invoice) }
            .filter { invoice -> matchesStatusFilter(invoice) }
            .filter { invoice -> matchesSearchQuery(invoice, currentSearchQuery) }

        _invoices.value = filtered
    }

    private fun matchesStatusFilter(invoice: Invoice): Boolean {
        if (currentStatusFilter == PaymentStatusFilter.ALL) return true

        val balanceDue = invoice.totalAmount - invoice.paidAmount
        return when (currentStatusFilter) {
            PaymentStatusFilter.PAID -> balanceDue <= 0
            PaymentStatusFilter.PARTIAL -> balanceDue > 0 && invoice.paidAmount > 0
            PaymentStatusFilter.UNPAID -> invoice.paidAmount <= 0 && invoice.totalAmount > 0
            else -> true
        }
    }

    fun setPaymentStatusFilter(filterType: PaymentStatusFilter) {
        if (currentStatusFilter != filterType) {
            currentStatusFilter = filterType
            Log.d("SalesViewModel", "Payment status filter changed to: $filterType")

            // Apply all filters
            applyFilters()
        }
    }

    private fun matchesDateFilter(invoice: Invoice): Boolean {
        val invoiceDate = invoice.invoiceDate
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        return when (currentDateFilter) {
            DateFilterType.ALL_TIME -> true
            DateFilterType.TODAY -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfDay = calendar.timeInMillis

                invoiceDate in startOfDay..endOfDay
            }

            DateFilterType.YESTERDAY -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfYesterday = calendar.timeInMillis

                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfYesterday = calendar.timeInMillis

                invoiceDate in startOfYesterday..endOfYesterday
            }

            DateFilterType.THIS_WEEK -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfWeek = calendar.timeInMillis

                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfWeek = calendar.timeInMillis

                invoiceDate in startOfWeek..endOfWeek
            }

            DateFilterType.THIS_MONTH -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfMonth = calendar.timeInMillis

                invoiceDate in startOfMonth..endOfMonth
            }

            DateFilterType.LAST_MONTH -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.add(Calendar.MONTH, -1)
                val startOfLastMonth = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfLastMonth = calendar.timeInMillis

                invoiceDate in startOfLastMonth..endOfLastMonth
            }

            DateFilterType.THIS_QUARTER -> {
                calendar.timeInMillis = now
                val month = calendar.get(Calendar.MONTH)
                val currentQuarter = month / 3

                calendar.set(Calendar.MONTH, currentQuarter * 3)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfQuarter = calendar.timeInMillis

                calendar.add(Calendar.MONTH, 3)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfQuarter = calendar.timeInMillis

                invoiceDate in startOfQuarter..endOfQuarter
            }

            DateFilterType.THIS_YEAR -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfYear = calendar.timeInMillis

                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfYear = calendar.timeInMillis

                invoiceDate in startOfYear..endOfYear
            }
        }
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
        viewModelScope.launch {
            // Choose source based on connectivity
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            repository.fetchInvoicesPaginated(false, source).fold(
                onSuccess = { invoices ->
                    _allInvoices.clear()
                    _allInvoices.addAll(invoices)

                    // Apply any current search filter
                    if (currentSearchQuery.isNotEmpty()) {
                        _invoices.value = filterInvoices(currentSearchQuery)
                    } else {
                        _invoices.value = invoices
                    }

                    _isLoading.value = false
                    Log.d("SalesViewModel", "First page loaded: ${invoices.size} invoices")
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
                    // Add to all invoices list
                    _allInvoices.addAll(newInvoices)

                    // Apply current search filter
                    if (currentSearchQuery.isNotEmpty()) {
                        _invoices.value = filterInvoices(currentSearchQuery)
                    } else {
                        val currentList = _invoices.value ?: emptyList()
                        _invoices.value = currentList + newInvoices
                    }

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

    fun searchInvoices(query: String) {
        currentSearchQuery = query.trim().lowercase()
        applyFilters()
    }


    private fun filterInvoices(query: String): List<Invoice> {
        currentSearchQuery = query.trim().lowercase()
        return _allInvoices
            .filter { invoice -> matchesDateFilter(invoice) }
            .filter { invoice -> matchesSearchQuery(invoice, query) }
    }

    private fun matchesSearchQuery(invoice: Invoice, query: String): Boolean {
        if (query.isEmpty()) return true

        return invoice.invoiceNumber.lowercase().contains(query) ||
                invoice.customerName.lowercase().contains(query) ||
                invoice.customerPhone.lowercase().contains(query) ||
                invoice.notes.lowercase().contains(query) ||
                // Convert date to string and search in it
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(Date(invoice.invoiceDate))
                    .lowercase()
                    .contains(query)
    }

    // Add new method to set date filter
    fun setDateFilter(filterType: DateFilterType) {
        if (currentDateFilter != filterType) {
            currentDateFilter = filterType
            Log.d("SalesViewModel", "Date filter changed to: $filterType")

            // Apply both date and search filters
            applyFilters()
        }
    }

    fun refreshInvoices(resetFilters: Boolean = false) {
        if (resetFilters) {
            currentDateFilter = DateFilterType.ALL_TIME
            currentStatusFilter = PaymentStatusFilter.ALL
            currentSearchQuery = ""
        }

        _isLoading.value = true
        viewModelScope.launch {
            loadFirstPage()
        }
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
        val customer = _selectedCustomer.value
        val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)


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
                Log.d(
                    "SalesViewModel", "Saving invoice: ${invoice.invoiceNumber}, " +
                            "items: ${invoice.items.size}, payments: ${invoice.payments.size}"
                )

                // Try to save the invoice
                repository.saveInvoice(invoice).fold(
                    onSuccess = {
                        // Refresh invoices list after saving
                        refreshInvoices()
                        // Clear current invoice data
                        clearCurrentInvoice()
                        _isLoading.value = false
                        callback(true)
                        Log.d(
                            "SalesViewModel",
                            "Invoice saved successfully: ${invoice.invoiceNumber}"
                        )
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