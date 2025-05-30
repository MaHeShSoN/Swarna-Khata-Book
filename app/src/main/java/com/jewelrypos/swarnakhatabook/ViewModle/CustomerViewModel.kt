package com.jewelrypos.swarnakhatabook.ViewModle

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.asFlow
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart

class CustomerViewModel(
    private val repository: CustomerRepository,
    private val connectivityManager: ConnectivityManager,
    private val context: Context
) : ViewModel() {

    // Add this constant at the top
    private val SEARCH_PHONE_NUMBER_MIN_LENGTH = 7

    // --- Constants for Filters ---
    companion object {
        const val FILTER_CONSUMER = "Consumer"
        const val FILTER_WHOLESALER = "Wholesaler"
        const val PAGE_SIZE = 20
    }

    // Original list of all customers
//    private val _allCustomers = mutableListOf<Customer>()

    // Filtered list based on search query and filters
//    private val _customers = MutableLiveData<List<Customer>>()
//    val customers: LiveData<List<Customer>> = _customers

    private val _errorMessage = MutableLiveData<String?>() // Use nullable String
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isActionLoading = MutableLiveData<Boolean>(false)
    val isActionLoading: LiveData<Boolean> = _isActionLoading

    private val _searchQuery = MutableStateFlow("")

    // --- Filter States ---
    private val _activeCustomerType = MutableLiveData<String?>()
    val activeCustomerType: LiveData<String?> = _activeCustomerType


    // Combined indicator for UI
    private val _isFilterActive = MutableLiveData<Boolean>(false)
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow() // <--- Expose publicly


    // --- Corrected pagedCustomers Flow using simple combine and onStart for LiveData flow ---
    val pagedCustomers: Flow<PagingData<Customer>> =
        combine(
            SessionManager.activeShopIdLiveData.asFlow().onEach { shopId -> Log.d("CustomerViewModel", "ShopID Flow emitted: $shopId") }, // Source 1: Shop ID
            _searchQuery.onEach { query -> Log.d("CustomerViewModel", "SearchQuery Flow emitted: '$query'") }, // Source 2: Search Query (StateFlow emits initial)
            _activeCustomerType.asFlow().onStart { emit(null) }.onEach { type -> Log.d("CustomerViewModel", "CustomerType Flow emitted: $type (after onStart)") } // Source 3: Customer Type Filter (LiveData.asFlow with onStart(null) to ensure initial emission)
        ) { shopId, query, type ->
            // This block triggers only when all three sources have emitted at least once,
            // and subsequently whenever any of them emits a new value.
             Log.d("CustomerViewModel", "Combined Flow triggered: ShopID=$shopId, Query='$query', Type=$type")
            Triple(shopId, query, type) // Emit the combined values
        }
        .onEach { (shopId, query, type) -> // <--- Add logging here
             Log.d("CustomerViewModel", "Final Combined Flow emitted: ShopID=$shopId, Query='$query', Type=$type")
        }
        .flatMapLatest { (shopId, query, type) ->
            Log.d("CustomerViewModel", "flatMapLatest triggered with: ShopID=$shopId, Query='$query', Type=$type") // Log flatMapLatest trigger
            if (shopId != null) {
                Log.d("CustomerViewModel", "Shop ID is NOT null, getting paginated customers flow from repository.")
                repository.getPaginatedCustomers(shopId = shopId, searchQuery = query, customerType = type)
            } else {
                Log.d("CustomerViewModel", "Shop ID IS null, returning empty PagingData flow.")
                // Return an empty flow if shopId is null
                flowOf(PagingData.empty())
            }
        }
        .cachedIn(viewModelScope)


    // Search query
    private var currentSearchQuery = ""

    // --- RecyclerView state preservation ---
    var layoutManagerState: Parcelable? = null

    // Paged data for customers
    val allCustomers: Flow<PagingData<Customer>> = SessionManager.activeShopIdLiveData
        .asFlow()
        .flatMapLatest { shopId ->
            if (shopId != null) {
                repository.getAllCustomersForShop()
            } else {
                flowOf(PagingData.empty())
            }
        }
        .cachedIn(viewModelScope)

    // For dashboard calculations
    private val _totalCustomers = MutableStateFlow(0)
    val totalCustomers: StateFlow<Int> = _totalCustomers.asStateFlow()

//    init {
//        loadFirstPage()
//        // Observe filter changes to update the combined active indicator
//        _activeCustomerType.observeForever { updateFilterActiveState() }
//        refreshData()
//    }

    private fun updateFilterActiveState() {
        val isActive = _activeCustomerType.value != null
        // Consider non-default sort as active filter
        _isFilterActive.value = isActive
    }

    /**
     * Applies a set of filters and triggers the background filtering process.
     * This function is now the main entry point for applying any filter change.
     */
    fun applyFilters(
        customerType: String? = _activeCustomerType.value, // Default to current value
    ) {
        var changed = false
        if (_activeCustomerType.value != customerType) {
            _activeCustomerType.value = customerType
            changed = true
        }

        if (changed) {
            updateFilterActiveState() // Update combined state
        }
    }

    /**
     * Clears all applied filters and resets sorting to default (ASC).
     */
    fun clearAllFilters() {
        var changed = false
        if (_activeCustomerType.value != null) {
            _activeCustomerType.value = null
            changed = true
        }
        if (_searchQuery.value.isNotEmpty()) {
            _searchQuery.value = ""
            changed = true
        }
        if (changed) {
            Log.d("CustomerViewModel", "All filters cleared via ViewModel")
        }
    }

    fun applyCustomerTypeFilter(customerType: String?) {
        if (_activeCustomerType.value != customerType) {
            Log.d("CustomerViewModel", "Applying customer type filter: $customerType")
            _activeCustomerType.value = customerType
        }
    }

    /**
     * Applies active filters and search query asynchronously.
     */
//    private fun applyFiltersAndSearch() {
//        viewModelScope.launch {
//            Log.d("CustomerViewModel", "applyFiltersAndSearch START")
//            try {
//                val filteredList = withContext(Dispatchers.Default) {
//                    var tempList = _allCustomers.toList()
//                    Log.d("CustomerViewModel", "applyFiltersAndSearch: Starting with ${tempList.size} customers")
//
//                    // Apply Customer Type Filter
//                    _activeCustomerType.value?.let { typeFilter ->
//                        tempList = tempList.filter { customer ->
//                            customer.customerType.equals(typeFilter, ignoreCase = true)
//                        }
//                        Log.d("CustomerViewModel", "applyFiltersAndSearch: After type filter: ${tempList.size} customers")
//                    }
//
//                    // Apply Search Query
//                    if (currentSearchQuery.isNotEmpty()) {
//                        tempList = tempList.filter { customer ->
//                            customer.firstName.contains(currentSearchQuery, ignoreCase = true) ||
//                                    customer.lastName.contains(currentSearchQuery, ignoreCase = true) ||
//                                    customer.phoneNumber.contains(currentSearchQuery, ignoreCase = true) ||
//                                    (customer.email?.contains(currentSearchQuery, ignoreCase = true) == true) ||
//                                    (customer.businessName?.contains(currentSearchQuery, ignoreCase = true) == true) ||
//                                    "${customer.firstName} ${customer.lastName}".contains(currentSearchQuery, ignoreCase = true)
//                        }
//                        Log.d("CustomerViewModel", "applyFiltersAndSearch: After search filter: ${tempList.size} customers")
//                    }
//
//                    tempList
//                }
//
//                Log.d("CustomerViewModel", "applyFiltersAndSearch: Updating customers LiveData with ${filteredList.size} customers")
//                _customers.value = filteredList
//            } catch (e: Exception) {
//                Log.e("CustomerViewModel", "applyFiltersAndSearch: Error applying filters", e)
//                _errorMessage.value = e.message
//            }
//        }
//    }

    fun searchCustomers(query: String) {
        val newQuery =
            query.trim() // No need for lowercase here if PagingSource handles it or Firestore is case-sensitive
        if (_searchQuery.value != newQuery) {
            Log.d("CustomerViewModel", "New search query: $newQuery")
            _searchQuery.value = newQuery
        }
    }

//    fun refreshData() {
//        Log.d("CustomerViewModel", "refreshData: Starting data refresh")
//        viewModelScope.launch {
//            _isLoading.value = true
//            try {
//                Log.d("CustomerViewModel", "refreshData: Fetching customers list")
//                val customers = repository.getAllCustomersListForShop()
//                Log.d("CustomerViewModel", "refreshData: Received ${customers.size} customers")
//                _totalCustomers.value = customers.size
//                _allCustomers.clear()
//                _allCustomers.addAll(customers)
//                applyFiltersAndSearch()
//                Log.d("CustomerViewModel", "refreshData: Data refresh completed")
//            } catch (e: Exception) {
//                Log.e("CustomerViewModel", "refreshData: Error refreshing data", e)
//                _errorMessage.value = e.message
//            } finally {
//                Log.d("CustomerViewModel", "refreshData: Setting isLoading = false")
//                _isLoading.value = false
//            }
//        }
//    }

    private fun isOnline(): Boolean {
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

//    private fun loadFirstPage() {
//        Log.d("CustomerViewModel", "loadFirstPage START -> Setting isLoading = true") // ADD LOG
//        _isLoading.value = true
//        viewModelScope.launch {
//            val source = if (isOnline()) Source.DEFAULT else Source.CACHE
//            repository.fetchCustomersPaginated(loadNextPage = false, source = source).fold(
//                onSuccess = { fetchedCustomers ->
//                    _allCustomers.clear()
//                    _allCustomers.addAll(fetchedCustomers)
//                    applyFiltersAndSearch() // Apply filters after loading
//                    // isLoading set to false inside applyFiltersAndSearch
//                },
//                onFailure = { error ->
//                    _errorMessage.value = error.message
//                    _isLoading.value = false
//                    Log.d("CustomerViewModel", "Error loading first page: ${error.message}")
//                }
//            )
//        }
//    }

//    fun loadNextPage() {
//        if (_isLoading.value == true) return
//        Log.d("CustomerViewModel", "loadNextPage START -> Setting isLoading = true") // ADD LOG
//        _isLoading.value = true
//        viewModelScope.launch {
//            val source = if (isOnline()) Source.DEFAULT else Source.CACHE
//            repository.fetchCustomersPaginated(loadNextPage = true, source = source).fold(
//                onSuccess = { newCustomers ->
//                    if (newCustomers.isNotEmpty()) {
//                        _allCustomers.addAll(newCustomers)
//                        applyFiltersAndSearch() // Apply filters after loading more
//                    } else {
//                        _isLoading.value = false // No more data, stop loading
//                    }
//                    // isLoading set to false inside applyFiltersAndSearch
//                },
//                onFailure = { error ->
//                    _errorMessage.value = error.message
//                    _isLoading.value = false
//                    Log.d("CustomerViewModel", "Error loading next page: ${error.message}")
//                }
//            )
//        }
//    }

    fun moveCustomerToRecycleBin(customerId: String): LiveData<Result<Unit>> {
        val resultLiveData = MutableLiveData<Result<Unit>>()
        _isLoading.value = true
        viewModelScope.launch {
            repository.moveCustomerToRecycleBin(customerId).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                }
            )
        }
        return resultLiveData
    }


    fun addCustomer(customer: Customer): LiveData<Result<Customer>> {
        val resultLiveData = MutableLiveData<Result<Customer>>()
        viewModelScope.launch {
            _isActionLoading.value = true
            repository.addCustomer(customer).fold(
                onSuccess = { newCustomer ->
                    resultLiveData.value = Result.success(newCustomer)
                    // No local list update here, rely on adapter.refresh() in Fragment
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                }
            )
            _isActionLoading.value = false
        }
        return resultLiveData
    }
//    fun addCustomer(customer: Customer): LiveData<Result<Customer>> {
//        val resultLiveData = MutableLiveData<Result<Customer>>()
//        viewModelScope.launch {
//            Log.d("CustomerViewModel", "addCustomer START -> Setting isLoading = true")
//            _isLoading.value = true
//            try {
//                repository.addCustomer(customer).fold(
//                    onSuccess = { newCustomer ->
//                        Log.d("CustomerViewModel", "addCustomer: Customer added successfully, refreshing data")
//                        resultLiveData.value = Result.success(newCustomer)
//                        // Add the new customer to the list immediately
//                        _allCustomers.add(0, newCustomer)
//                        // Update the filtered list
//                        applyFiltersAndSearch()
//                    },
//                    onFailure = { error ->
//                        Log.e("CustomerViewModel", "addCustomer: Error adding customer", error)
//                        resultLiveData.value = Result.failure(error)
//                        _errorMessage.value = error.message
//                    }
//                )
//            } finally {
//                Log.d("CustomerViewModel", "addCustomer: Setting isLoading = false")
//                _isLoading.value = false
//            }
//        }
//        return resultLiveData
//    }


    fun updateCustomer(customer: Customer): LiveData<Result<Unit>> { // Changed from your previous version
        val resultLiveData = MutableLiveData<Result<Unit>>()
        viewModelScope.launch {
            _isActionLoading.value = true
            repository.updateCustomer(customer).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                    // No refreshData() here, rely on adapter.refresh() in Fragment
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                }
            )
            _isActionLoading.value = false
        }
        return resultLiveData
    }

    //    fun updateCustomer(customer: Customer): LiveData<Result<Unit>> {
//        val resultLiveData = MutableLiveData<Result<Unit>>()
//        viewModelScope.launch {
//            Log.d("CustomerViewModel", "updateCustomer START -> Setting isLoading = true")
//            _isLoading.value = true
//            try {
//                repository.updateCustomer(customer).fold(
//                    onSuccess = {
//                        Log.d("CustomerViewModel", "updateCustomer: Customer updated successfully in repository.")
//                        // DO NOT call refreshData() here directly.
//                        // Instead, update local state if necessary, or rely on EventBus refresh.
//                        // For consistency with the add flow and to ensure a fresh state,
//                        // we'll rely on the EventBus triggered refresh in the Fragment.
//                        // However, you might want to update the specific customer in _allCustomers
//                        // if immediate local reflection before EventBus refresh is desired for other reasons.
//                        // For now, let's keep it simple and rely on the EventBus refresh.
//
//                        // Example of updating the customer in the local list _allCustomers:
//                        val index = _allCustomers.indexOfFirst { it.id == customer.id }
//                        if (index != -1) {
//                            _allCustomers[index] = customer
//                            // Optionally, call applyFiltersAndSearch() if you want an immediate
//                            // local reflection without waiting for the EventBus based refresh.
//                            // applyFiltersAndSearch() // This would be a local-only update before EventBus.
//                            // For this solution, we are aiming for EventBus driven refresh.
//                        }
//                        resultLiveData.value = Result.success(Unit)
//                    },
//                    onFailure = { error ->
//                        Log.e("CustomerViewModel", "updateCustomer: Error updating customer", error)
//                        resultLiveData.value = Result.failure(error)
//                        _errorMessage.value = error.message
//                    }
//                )
//            } finally {
//                // isLoading will be handled by the refreshData call triggered via EventBus,
//                // or if applyFiltersAndSearch were called.
//                // If neither happens directly, ensure isLoading is reset.
//                // For this pattern, refreshData() (via EventBus) will handle isLoading.
//                // If relying on local update above (applyFiltersAndSearch), it should handle it.
//                // For safety, if no refresh/filter happens, reset here.
//                // However, the EventBus flow should handle the loading state.
//                // The refreshData() called by EventBus will set isLoading = false in its finally block.
//                // So, we might not need to set _isLoading.value = false here if success leads to EventBus path.
//                // Let's assume EventBus path handles it. On failure, it's handled.
//                if (resultLiveData.value?.isFailure == true) {
//                    _isLoading.value = false
//                }
//            }
//        }
//        return resultLiveData
//    }
    fun getCustomerById(customerId: String): LiveData<Result<Customer>> {
        val resultLiveData = MutableLiveData<Result<Customer>>()
        _isLoading.value = true
        viewModelScope.launch {
            repository.getCustomerById(customerId).fold(
                onSuccess = { customer ->
                    resultLiveData.value = Result.success(customer)
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                }
            )
            _isLoading.value = false
        }
        return resultLiveData
    }

    fun getCustomerInvoiceCount(customerId: String): LiveData<Result<Int>> {
        val resultLiveData = MutableLiveData<Result<Int>>()
        _isLoading.value = true
        viewModelScope.launch {
            repository.getCustomerInvoiceCount(customerId).fold(
                onSuccess = { count ->
                    resultLiveData.value = Result.success(count)
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                }
            )
            _isLoading.value = false
        }
        return resultLiveData
    }


    fun deleteCustomer(customerId: String): LiveData<Result<Unit>> { // Assuming you have this for Paging 3
        val resultLiveData = MutableLiveData<Result<Unit>>()
        _isActionLoading.value = true
        viewModelScope.launch {
            // Choose one: moveCustomerToRecycleBin or the actual deleteCustomer
            // For this example, using moveCustomerToRecycleBin as it seems to be the preferred way
            repository.moveCustomerToRecycleBin(customerId).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                }
            )
            _isActionLoading.value = false
        }
        return resultLiveData
    }

//    fun deleteCustomer(customerId: String): LiveData<Result<Unit>> {
//        val resultLiveData = MutableLiveData<Result<Unit>>()
//        _isLoading.value = true
//        viewModelScope.launch {
//            repository.deleteCustomer(customerId).fold(
//                onSuccess = {
//                    resultLiveData.value = Result.success(Unit)
//                    refreshData()
//                },
//                onFailure = { error ->
//                    resultLiveData.value = Result.failure(error)
//                    _errorMessage.value = error.message
//                    _isLoading.value = false
//                }
//            )
//            // isLoading set to false inside refreshData -> loadFirstPage -> applyFiltersAndSearch
//        }
//        return resultLiveData
//    }

    // Clear error message
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Remove observers to prevent memory leaks
        _activeCustomerType.removeObserver { }
        Log.d("CustomerViewModel", "onCleared: ViewModel is being cleared.")
    }

    /**
     * Gets the total count of customers for the current user/shop
     * Used for subscription limit checks
     */
//    suspend fun getTotalCustomerCount(): Int {
//        return withContext(Dispatchers.IO) {
//            try {
//                // First try to use the local list if it's loaded
//                if (_allCustomers.isNotEmpty()) {
//                    return@withContext _allCustomers.size
//                }
//
//                // Otherwise, fetch the count from repository
//                val countResult = repository.getCustomerCount()
//
//                if (countResult.isSuccess) {
//                    return@withContext countResult.getOrDefault(0)
//                } else {
//                    Log.e("CustomerViewModel", "Error getting customer count: ${countResult.exceptionOrNull()?.message}")
//                    throw countResult.exceptionOrNull() ?: Exception("Unknown error getting customer count")
//                }
//            } catch (e: Exception) {
//                Log.e("CustomerViewModel", "Error in getTotalCustomerCount: ${e.message}")
//                throw e
//            }
//        }
//    }
    suspend fun getTotalCustomerCount(): Int {
        // This might need to be re-evaluated. If _allCustomers is gone,
        // you'd have to rely solely on repository.getCustomerCount().
        return try {
            val countResult =
                repository.getCustomerCount() // Assuming this method exists and is efficient
            if (countResult.isSuccess) {
                countResult.getOrDefault(0)
            } else {
                Log.e(
                    "CustomerViewModel",
                    "Error getting customer count: ${countResult.exceptionOrNull()?.message}"
                )
                throw countResult.exceptionOrNull()
                    ?: Exception("Unknown error getting customer count")
            }
        } catch (e: Exception) {
            Log.e("CustomerViewModel", "Error in getTotalCustomerCount: ${e.message}")
            throw e // Or return a default/error indicator
        }
    }
}