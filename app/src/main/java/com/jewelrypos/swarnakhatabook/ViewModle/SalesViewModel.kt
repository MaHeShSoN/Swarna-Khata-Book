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
import com.jewelrypos.swarnakhatabook.DataClasses.ExtraCharge // Assuming needed
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem // Assuming needed
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.DataClasses.SelectedItemWithPrice
import com.jewelrypos.swarnakhatabook.Enums.DateFilterType
import com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job // Import Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SalesViewModel(
    private val repository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    // --- LiveData for UI State ---
    // Selected customer for new order/invoice
    private val _selectedCustomer = MutableLiveData<Customer?>() // Allow null initially
    val selectedCustomer: LiveData<Customer?> = _selectedCustomer

    // Selected items for new order/invoice
    private val _selectedItems = MutableLiveData<List<SelectedItemWithPrice>>(emptyList())
    val selectedItems: LiveData<List<SelectedItemWithPrice>> = _selectedItems

    // Payments for new invoice
    private val _payments = MutableLiveData<List<Payment>>(emptyList())
    val payments: LiveData<List<Payment>> = _payments

    // Filtered Invoices list for display
    private val _invoices = MutableLiveData<List<Invoice>>()
    val invoices: LiveData<List<Invoice>> = _invoices

    // Loading state (covers both fetching and filtering)
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error messages
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // Optional: LiveData specifically for customer's invoices if needed separately
    private val _customerInvoices = MutableLiveData<List<Invoice>>()
    val customerInvoices: LiveData<List<Invoice>> = _customerInvoices

    // --- Internal State ---
    // Master list holding ALL fetched invoices (source for filtering)
    private val _allInvoices = mutableListOf<Invoice>()

    // Current filter states
    private var currentSearchQuery = ""
    private var currentDateFilter = DateFilterType.ALL_TIME
    private var currentStatusFilter = PaymentStatusFilter.ALL

    // Coroutine Job for managing the asynchronous filter operation
    private var filterJob: Job? = null

    init {
        // Load the initial data when the ViewModel is created
        loadFirstPage()
    }

    // --- Getters for current filter state (used by UI to sync) ---
    fun getCurrentDateFilter(): DateFilterType = currentDateFilter
    fun getCurrentStatusFilter(): PaymentStatusFilter = currentStatusFilter

    // --- Filtering Logic (Now Asynchronous) ---

    /**
     * Applies all current filters (date, status, search) asynchronously
     * to the master list (_allInvoices) and updates the public _invoices LiveData.
     * Cancels any previous filter job to ensure only the latest filter runs.
     */
    private fun applyFilters() {
        // Cancel any previously running filter job
        filterJob?.cancel()

        // Launch the filtering operation in a coroutine within the ViewModel's scope
        filterJob = viewModelScope.launch {
            // Indicate that filtering is in progress (updates loading indicator)
            _isLoading.value = true

            // Perform the potentially long-running filter operation on a background thread
            val filteredList =
                withContext(Dispatchers.Default) { // Use Default dispatcher for CPU-bound work
                    // Take immutable copies of state used for filtering inside the background context
                    val allInvoicesCopy = _allInvoices.toList()
                    val dateFilter = currentDateFilter
                    val statusFilter = currentStatusFilter
                    val query = currentSearchQuery

                    Log.d(
                        "SalesViewModel",
                        "Starting background filter: items=${allInvoicesCopy.size}, date=$dateFilter, status=$statusFilter, search='$query'"
                    )

                    // Apply filters sequentially using the copied state
                    allInvoicesCopy
                        .filter { invoice -> matchesDateFilter(invoice, dateFilter) }
                        .filter { invoice -> matchesStatusFilter(invoice, statusFilter) }
                        .filter { invoice -> matchesSearchQuery(invoice, query) }
                    // The result of the filter chain is the return value of withContext
                } // Automatically switches back to the main thread after this block

            // Update the LiveData with the filtered results (happens safely on the main thread)
            _invoices.value = filteredList
            _isLoading.value = false // Hide loading indicator now that filtering is complete
            Log.d("SalesViewModel", "Filters applied, result count: ${filteredList.size}")
        }
    }

    /**
     * Checks if an invoice matches the given date filter type.
     * This function is now pure and takes the filter type as a parameter.
     * NOTE: Consider using java.time API (if API level/desugaring allows) for cleaner date logic.
     */
    private fun matchesDateFilter(invoice: Invoice, filterType: DateFilterType): Boolean {
        val invoiceDate = invoice.invoiceDate
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // Use the 'filterType' parameter instead of the class property 'currentDateFilter'
        return when (filterType) {
            DateFilterType.ALL_TIME -> true
            DateFilterType.TODAY -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(
                    Calendar.MINUTE,
                    0
                ); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(
                    Calendar.MINUTE,
                    59
                ); calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
                val endOfDay = calendar.timeInMillis
                invoiceDate in startOfDay..endOfDay
            }

            DateFilterType.YESTERDAY -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(
                    Calendar.MINUTE,
                    0
                ); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                val startOfYesterday = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(
                    Calendar.MINUTE,
                    59
                ); calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
                val endOfYesterday = calendar.timeInMillis
                invoiceDate in startOfYesterday..endOfYesterday
            }

            DateFilterType.THIS_WEEK -> {
                calendar.timeInMillis = now
                // Adjust to the start of the week based on locale
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(
                    Calendar.MINUTE,
                    0
                ); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                val startOfWeek = calendar.timeInMillis
                // Go to the start of the next week and subtract one millisecond
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfWeek = calendar.timeInMillis
                invoiceDate in startOfWeek..endOfWeek
            }

            DateFilterType.THIS_MONTH -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(
                    Calendar.MINUTE,
                    0
                ); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis
                // Go to the start of the next month and subtract one millisecond
                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfMonth = calendar.timeInMillis
                invoiceDate in startOfMonth..endOfMonth
            }

            DateFilterType.LAST_MONTH -> {
                calendar.timeInMillis = now
                // Go to the start of the current month
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(
                    Calendar.MINUTE,
                    0
                ); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                // End of last month is one millisecond before start of current month
                val endOfLastMonth = calendar.timeInMillis - 1
                // Go back one month to get the start of last month
                calendar.add(Calendar.MONTH, -1)
                val startOfLastMonth = calendar.timeInMillis
                invoiceDate in startOfLastMonth..endOfLastMonth
            }

            DateFilterType.THIS_QUARTER -> {
                calendar.timeInMillis = now
                val currentMonth = calendar.get(Calendar.MONTH) // 0-11
                val firstMonthOfQuarter = (currentMonth / 3) * 3
                calendar.set(Calendar.MONTH, firstMonthOfQuarter)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(
                    Calendar.MINUTE,
                    0
                ); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                val startOfQuarter = calendar.timeInMillis
                // Go to the start of the next quarter and subtract one millisecond
                calendar.add(Calendar.MONTH, 3)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfQuarter = calendar.timeInMillis
                invoiceDate in startOfQuarter..endOfQuarter
            }

            DateFilterType.THIS_YEAR -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(
                    Calendar.MINUTE,
                    0
                ); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
                val startOfYear = calendar.timeInMillis
                // Go to the start of the next year and subtract one millisecond
                calendar.add(Calendar.YEAR, 1)
                calendar.add(Calendar.MILLISECOND, -1)
                val endOfYear = calendar.timeInMillis
                invoiceDate in startOfYear..endOfYear
            }
        }
    }

    /**
     * Checks if an invoice matches the given payment status filter type.
     * This function is now pure and takes the filter type as a parameter.
     */
    private fun matchesStatusFilter(invoice: Invoice, filterType: PaymentStatusFilter): Boolean {
        // Use the 'filterType' parameter instead of 'currentStatusFilter'
        if (filterType == PaymentStatusFilter.ALL) return true

        val balanceDue = invoice.totalAmount - invoice.paidAmount
        return when (filterType) {
            PaymentStatusFilter.PAID -> balanceDue <= 0.001 // Use tolerance for float comparison
            PaymentStatusFilter.PARTIAL -> balanceDue > 0.001 && invoice.paidAmount > 0.001
            PaymentStatusFilter.UNPAID -> invoice.paidAmount <= 0.001 && invoice.totalAmount > 0.001 // Check total > 0
            else -> true // Should not be reached if ALL is handled
        }
    }

    /**
     * Checks if an invoice matches the given search query string (case-insensitive).
     * This function is pure and takes the query as a parameter.
     */
    private fun matchesSearchQuery(invoice: Invoice, query: String): Boolean {
        if (query.isEmpty()) return true
        // query should already be lowercase from searchInvoices()

        // Check various fields for the query substring
        return invoice.invoiceNumber.lowercase().contains(query) ||
                invoice.customerName.lowercase().contains(query) ||
                invoice.customerPhone.lowercase().contains(query) ||
                invoice.notes.lowercase().contains(query) ||
                // Also check the formatted date string
                SimpleDateFormat("dd MMM yy", Locale.getDefault()) // Consistent format
                    .format(Date(invoice.invoiceDate))
                    .lowercase()
                    .contains(query)
    }

    // --- Filter Setters (Trigger Async Filtering) ---

    /**
     * Sets the current payment status filter and triggers asynchronous re-filtering.
     */
    fun setPaymentStatusFilter(filterType: PaymentStatusFilter) {
        if (currentStatusFilter != filterType) {
            currentStatusFilter = filterType
            Log.d("SalesViewModel", "Payment status filter set to: $filterType")
            applyFilters() // Calls the async applyFilters method
        }
    }

    /**
     * Sets the current date filter and triggers asynchronous re-filtering.
     */
    fun setDateFilter(filterType: DateFilterType) {
        if (currentDateFilter != filterType) {
            currentDateFilter = filterType
            Log.d("SalesViewModel", "Date filter set to: $filterType")
            applyFilters() // Calls the async applyFilters method
        }
    }

    /**
     * Sets the current search query and triggers asynchronous re-filtering.
     * It's recommended to DEBOUNCE calls to this method from the UI (Fragment/Activity)
     * to avoid excessive filtering during typing.
     */
    fun searchInvoices(query: String) {
        val newQuery = query.trim().lowercase()
        // Only trigger filtering if the actual query text has changed
        if (currentSearchQuery != newQuery) {
            currentSearchQuery = newQuery
            Log.d("SalesViewModel", "Search query set to: '$newQuery'")
            applyFilters() // Calls the async applyFilters method
        }
    }

    // --- Pagination Logic (Interacts with Async Filtering) ---

    /**
     * Loads the first page of invoices from the repository.
     * Clears existing data and applies current filters asynchronously after fetch.
     */
    private fun loadFirstPage() {
        // Cancel any pending filter job before starting a network load
        filterJob?.cancel()
        _isLoading.value = true // Show loading indicator for network fetch
        viewModelScope.launch {
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE
            repository.fetchInvoicesPaginated(loadNextPage = false, source = source).fold(
                onSuccess = { fetchedInvoices ->
                    _allInvoices.clear()
                    _allInvoices.addAll(fetchedInvoices)
                    Log.d("SalesViewModel", "First page loaded: ${fetchedInvoices.size} invoices")
                    // Apply current filters asynchronously. applyFilters will handle isLoading state.
                    applyFilters()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Error loading first page"
                    _isLoading.value = false // Ensure loading stops on failure
                    Log.e("SalesViewModel", "Error loading first page: ${error.message}", error)
                }
            )
        }
    }

    /**
     * Loads the next page of invoices from the repository.
     * Appends new data and applies current filters asynchronously after fetch.
     */
    fun loadNextPage() {
        // Prevent multiple simultaneous loads and loading if already filtering
        if (_isLoading.value == true || filterJob?.isActive == true) {
            Log.d("SalesViewModel", "Load next page skipped: Already loading or filtering.")
            return
        }
        // Cancel any pending filter job before starting a network load
        filterJob?.cancel()

        _isLoading.value = true // Show loading indicator for network fetch
        viewModelScope.launch {
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE
            repository.fetchInvoicesPaginated(loadNextPage = true, source = source).fold(
                onSuccess = { newInvoices ->
                    if (newInvoices.isNotEmpty()) {
                        _allInvoices.addAll(newInvoices)
                        Log.d(
                            "SalesViewModel",
                            "Next page loaded: ${newInvoices.size} invoices. Total now: ${_allInvoices.size}"
                        )
                        // Apply current filters asynchronously. applyFilters will handle isLoading state.
                        applyFilters()
                    } else {
                        // No more items were loaded, stop the loading indicator
                        _isLoading.value = false
                        Log.d("SalesViewModel", "No more invoices to load (last page reached).")
                        // Optionally set a flag like 'isLastPageReached = true' if needed by UI
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Error loading next page"
                    _isLoading.value = false // Ensure loading stops on failure
                    Log.e("SalesViewModel", "Error loading next page: ${error.message}", error)
                }
            )
        }
    }

    /**
     * Refreshes the invoice list by reloading the first page.
     * Optionally resets all filters before reloading.
     */
    fun refreshInvoices(resetFilters: Boolean = false) {
        if (resetFilters) {
            currentDateFilter = DateFilterType.ALL_TIME
            currentStatusFilter = PaymentStatusFilter.ALL
            currentSearchQuery = ""
            Log.d("SalesViewModel", "Filters reset during refresh.")
            // No need to call applyFilters here, loadFirstPage will do it after fetch
        }
        // Trigger loading the first page. It will cancel existing jobs and apply filters after fetch.
        loadFirstPage()
    }

    fun getCurrentSearchQuery(): String = currentSearchQuery

    // --- Invoice Creation/Editing Methods ---

    // Set the selected customer
    fun setSelectedCustomer(customer: Customer?) { // Allow null to clear
        _selectedCustomer.value = customer
    }

    // Add a selected item (handles existing items by incrementing quantity)
    fun addSelectedItem(item: JewelleryItem, price: Double) {
        val currentItems = _selectedItems.value?.toMutableList() ?: mutableListOf()
        val existingItemIndex = currentItems.indexOfFirst { it.item.id == item.id }

        if (existingItemIndex >= 0) {
            // Item exists, update quantity
            val existingItem = currentItems[existingItemIndex]
            // Ensure quantity increases correctly
            currentItems[existingItemIndex] = existingItem.copy(
                quantity = existingItem.quantity + 1,
                price = price
            ) // Update price too if needed
            Log.d("SalesViewModel", "Incremented quantity for item: ${item.id}")
        } else {
            // Add new item
            currentItems.add(SelectedItemWithPrice(item = item, quantity = 1, price = price))
            Log.d("SalesViewModel", "Added new selected item: ${item.id}")
        }
        _selectedItems.value = currentItems
    }

    // Remove a selected item completely
    fun removeSelectedItem(itemToRemove: SelectedItemWithPrice) {
        val currentItems = _selectedItems.value?.toMutableList() ?: return
        if (currentItems.remove(itemToRemove)) {
            _selectedItems.value = currentItems
            Log.d("SalesViewModel", "Removed selected item: ${itemToRemove.item.id}")
        }
    }

    // Update quantity of a specific selected item
    fun updateItemQuantity(itemToUpdate: SelectedItemWithPrice, newQuantity: Int) {
        if (newQuantity <= 0) {
            // Remove item if quantity becomes zero or less
            removeSelectedItem(itemToUpdate)
            return
        }

        val currentItems = _selectedItems.value?.toMutableList() ?: return
        val itemIndex = currentItems.indexOfFirst { it.item.id == itemToUpdate.item.id }

        if (itemIndex >= 0) {
            // Update the item at the found index with the new quantity
            currentItems[itemIndex] = currentItems[itemIndex].copy(quantity = newQuantity)
            _selectedItems.value = currentItems
            Log.d(
                "SalesViewModel",
                "Updated quantity for item ${itemToUpdate.item.id} to $newQuantity"
            )
        }
    }

    /**
     * Updates an item within the selected items list, typically after editing details.
     * Preserves the existing quantity. Returns true if successful, false otherwise.
     */
    fun updateSelectedItem(updatedJewelleryItem: JewelleryItem, newPrice: Double): Boolean {
        val currentItems = _selectedItems.value?.toMutableList() ?: return false
        val itemIndex = currentItems.indexOfFirst { it.item.id == updatedJewelleryItem.id }

        return if (itemIndex >= 0) {
            val existingQuantity = currentItems[itemIndex].quantity // Keep the quantity
            // Replace item with updated details, keeping quantity
            currentItems[itemIndex] = SelectedItemWithPrice(
                item = updatedJewelleryItem,
                quantity = existingQuantity,
                price = newPrice
            )
            _selectedItems.value = currentItems // Update LiveData
            Log.d("SalesViewModel", "Updated selected item details for: ${updatedJewelleryItem.id}")
            true
        } else {
            Log.w(
                "SalesViewModel",
                "Attempted to update non-existent selected item: ${updatedJewelleryItem.id}"
            )
            false
        }
    }

    /**
     * Updates the entire list of selected items, replacing the current list.
     * Useful when loading items from an existing invoice for editing.
     */
    fun updateItems(newItems: List<SelectedItemWithPrice>) {
        _selectedItems.value = newItems
        Log.d("SalesViewModel", "Replaced selected items list with ${newItems.size} items.")
    }


    // --- Payment Methods ---
    fun addPayment(payment: Payment) {
        val currentPayments = _payments.value?.toMutableList() ?: mutableListOf()
        currentPayments.add(payment)
        _payments.value = currentPayments
        Log.d("SalesViewModel", "Added payment: ${payment.amount} via ${payment.method}")
    }

    fun removePayment(paymentToRemove: Payment) {
        val currentPayments = _payments.value?.toMutableList() ?: return
        if (currentPayments.remove(paymentToRemove)) {
            _payments.value = currentPayments
            Log.d(
                "SalesViewModel",
                "Removed payment: ${paymentToRemove.amount} via ${paymentToRemove.method}"
            )
        }
    }

    // --- Calculation Methods ---
    fun calculateSubtotal(): Double {
        // Ensure quantity is considered
        return _selectedItems.value?.sumOf { (it.price * it.quantity) } ?: 0.0
    }

    // Get all extra charges, applying quantity
    fun getAllExtraCharges(): List<Pair<String, Double>> {
        return _selectedItems.value?.flatMap { selectedItem ->
            selectedItem.item.listOfExtraCharges.map { extraCharge ->
                // Multiply each charge by the quantity of the item it belongs to
                Pair(extraCharge.name, extraCharge.amount * selectedItem.quantity)
            }
        } ?: emptyList()
    }

    // Calculate total of all extra charges
    fun calculateExtraCharges(): Double {
        // Sum the amounts from the list generated by getAllExtraCharges
        return getAllExtraCharges().sumOf { it.second }
    }

    // Calculate total tax based on each item's tax and quantity
    fun calculateTax(): Double {
        return _selectedItems.value?.sumOf { selectedItem ->
            // Assuming item.totalTax is the tax *per unit* of the item
            selectedItem.item.totalTax * selectedItem.quantity
        } ?: 0.0
    }

    // Calculate grand total
    fun calculateTotal(): Double {
        return calculateSubtotal() + calculateExtraCharges() + calculateTax()
    }

    // Calculate total amount paid
    fun calculateTotalPaid(): Double {
        return _payments.value?.sumOf { it.amount } ?: 0.0
    }

    // Calculate balance due
    fun calculateBalanceDue(): Double {
        return calculateTotal() - calculateTotalPaid()
    }

    // --- Save Invoice ---
    /**
     * Saves the currently constructed invoice data to the repository.
     * Invokes the callback with true on success, false on failure.
     */
    fun saveInvoice(invoice: Invoice, callback: (Boolean) -> Unit) {
        // Ensure save operations don't conflict with filtering/loading
        filterJob?.cancel()
        _isLoading.value = true // Show loading during save
        viewModelScope.launch {
            try {
                Log.d(TAG, "Attempting to save invoice: ${invoice.invoiceNumber}")
                repository.saveInvoice(invoice).fold(
                    onSuccess = {
                        Log.d(TAG, "Invoice saved successfully: ${invoice.invoiceNumber}")
                        // Refresh the main invoice list (will apply filters)
                        refreshInvoices() // Reloads first page and applies filters
                        clearCurrentInvoiceData() // Clear data used for creation form
                        _isLoading.value = false // Stop loading indicator
                        callback(true) // Notify success
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Save failed: ${error.message}"
                        _isLoading.value = false // Stop loading indicator
                        Log.e(TAG, "Failed to save invoice: ${error.message}", error)
                        callback(false) // Notify failure
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Unexpected error during save: ${e.message}"
                _isLoading.value = false // Stop loading indicator
                Log.e(TAG, "Unexpected error saving invoice", e)
                callback(false) // Notify failure
            }
        }
    }

    /**
     * Clears the state related to the invoice currently being created/edited.
     */
    fun clearCurrentInvoiceData() {
        _selectedCustomer.value = null // Use null to represent no selection
        _selectedItems.value = emptyList()
        _payments.value = emptyList()
        Log.d("SalesViewModel", "Cleared current invoice creation data.")
    }

    // --- Customer Specific Invoices ---
    /**
     * Loads invoices specifically for a given customer ID.
     * NOTE: This currently filters client-side. For better performance,
     * implement server-side filtering in the InvoiceRepository.
     */
    fun loadCustomerInvoices(customerId: String) {
        if (customerId.isBlank()) {
            _customerInvoices.value = emptyList() // Clear if ID is blank
            return
        }
        // Consider showing loading specific to this operation if needed
        // _isCustomerInvoiceLoading.value = true
        viewModelScope.launch {
            // **Inefficient Approach:** Fetches all and filters client-side.
            // **TODO:** Replace with a repository call like:
            // repository.fetchInvoicesForCustomerPaginated(customerId, false).fold(...)
            val allInvoicesCopy = _allInvoices.toList() // Use already fetched data if available
            val filtered = withContext(Dispatchers.Default) {
                allInvoicesCopy.filter { it.customerId == customerId }
            }
            _customerInvoices.value = filtered
//             _isCustomerInvoiceLoading.value = false
            Log.d(
                "SalesViewModel",
                "Loaded ${filtered.size} invoices for customer $customerId (client-side filter)"
            )

            // --- OR --- (If you must fetch specifically, but inefficiently)
            _isLoading.value = true // Use main loading indicator for now
            repository.fetchInvoicesPaginated(loadNextPage = false).fold( // Fetches ALL again
                onSuccess = { allInvoices ->
                    val filtered = withContext(Dispatchers.Default) {
                        allInvoices.filter { it.customerId == customerId }
                    }
                    _customerInvoices.value = filtered
                    _isLoading.value = false
                    Log.d(
                        "SalesViewModel",
                        "Fetched and filtered ${filtered.size} invoices for customer $customerId"
                    )
                },
                onFailure = { error ->
                    _errorMessage.value = error.message ?: "Failed to load customer invoices"
                    _isLoading.value = false
                    Log.e(
                        "SalesViewModel",
                        "Error loading customer invoices for $customerId",
                        error
                    )
                }
            )
        }
    }


    // --- Helper Functions ---
    /**
     * Checks if the device has an active internet connection.
     */
    private fun isOnline(): Boolean {
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }

    // Companion Object (Optional: for constants like TAG)
    companion object {
        private const val TAG = "SalesViewModel"
    }
}
