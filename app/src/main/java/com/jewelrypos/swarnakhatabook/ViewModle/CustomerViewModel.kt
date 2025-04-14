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
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerViewModel(
    private val repository: CustomerRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    // --- Constants for Filters ---
    companion object {
        const val FILTER_CONSUMER = "Consumer"
        const val FILTER_WHOLESALER = "Wholesaler"
        // Add constants for payment status if used elsewhere
        const val PAYMENT_TO_PAY = "Debit"
        const val PAYMENT_TO_RECEIVE = "Credit"
    }

    // Original list of all customers
    private val _allCustomers = mutableListOf<Customer>()

    // Filtered list based on search query and filters
    private val _customers = MutableLiveData<List<Customer>>()
    val customers: LiveData<List<Customer>> = _customers

    private val _errorMessage = MutableLiveData<String?>() // Use nullable String
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // --- Filter States ---
    private val _activeCustomerType = MutableLiveData<String?>()
    val activeCustomerType: LiveData<String?> = _activeCustomerType


    // Combined indicator for UI
    private val _isFilterActive = MutableLiveData<Boolean>(false)


    // Search query
    private var currentSearchQuery = ""


    init {
        loadFirstPage()
        // Observe filter changes to update the combined active indicator
        _activeCustomerType.observeForever { updateFilterActiveState() }
    }

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
            applyFiltersAndSearch() // Trigger filtering
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

        if (changed) {
            updateFilterActiveState()
            applyFiltersAndSearch()
            Log.d("CustomerViewModel", "All filters cleared")
        }
    }


    /**
     * Applies active filters and search query asynchronously.
     */
    private fun applyFiltersAndSearch() {
        viewModelScope.launch {
            _isLoading.value = true

            val filteredList = withContext(Dispatchers.Default) {
                var tempList = _allCustomers.toList() // Work on a copy

                // 1. Apply Customer Type Filter
                _activeCustomerType.value?.let { typeFilter ->
                    tempList = tempList.filter { customer ->
                        customer.customerType.equals(typeFilter, ignoreCase = true)
                    }
                }


                // 3. Apply Search Query
                if (currentSearchQuery.isNotEmpty()) {
                    tempList = tempList.filter { customer ->
                        customer.firstName.contains(currentSearchQuery, ignoreCase = true) ||
                                customer.lastName.contains(currentSearchQuery, ignoreCase = true) ||
                                customer.phoneNumber.contains(currentSearchQuery, ignoreCase = true) ||
                                (customer.email?.contains(currentSearchQuery, ignoreCase = true) == true) || // Null safe email check
                                (customer.businessName?.contains(currentSearchQuery, ignoreCase = true) == true) || // Null safe business name check
                                "${customer.firstName} ${customer.lastName}".contains(currentSearchQuery, ignoreCase = true)
                    }
                }

                tempList // Return filtered and sorted list
            }

            // Update LiveData on the main thread
            _customers.value = filteredList
            _isLoading.value = false
            Log.d("CustomerViewModel", "Filtered/Sorted to ${filteredList.size} customers. Filters: Type=${_activeCustomerType.value}, Search='$currentSearchQuery'")
        }
    }


    fun searchCustomers(query: String) {
        val newQuery = query.trim().lowercase()
        if (currentSearchQuery != newQuery) {
            currentSearchQuery = newQuery
            applyFiltersAndSearch() // Apply all filters including the new search term
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            loadFirstPage()
        }
    }

    private fun isOnline(): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun loadFirstPage() {
        _isLoading.value = true
        viewModelScope.launch {
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE
            repository.fetchCustomersPaginated(loadNextPage = false, source = source).fold(
                onSuccess = { fetchedCustomers ->
                    _allCustomers.clear()
                    _allCustomers.addAll(fetchedCustomers)
                    applyFiltersAndSearch() // Apply filters after loading
                    // isLoading set to false inside applyFiltersAndSearch
                },
                onFailure = { error ->
                    _errorMessage.value = error.message
                    _isLoading.value = false
                    Log.d("CustomerViewModel", "Error loading first page: ${error.message}")
                }
            )
        }
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return

        _isLoading.value = true
        viewModelScope.launch {
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE
            repository.fetchCustomersPaginated(loadNextPage = true, source = source).fold(
                onSuccess = { newCustomers ->
                    if (newCustomers.isNotEmpty()) {
                        _allCustomers.addAll(newCustomers)
                        applyFiltersAndSearch() // Apply filters after loading more
                    } else {
                        _isLoading.value = false // No more data, stop loading
                    }
                    // isLoading set to false inside applyFiltersAndSearch
                },
                onFailure = { error ->
                    _errorMessage.value = error.message
                    _isLoading.value = false
                    Log.d("CustomerViewModel", "Error loading next page: ${error.message}")
                }
            )
        }
    }

    fun moveCustomerToRecycleBin(customerId: String): LiveData<Result<Unit>> {
        val resultLiveData = MutableLiveData<Result<Unit>>()
        _isLoading.value = true
        viewModelScope.launch {
            repository.moveCustomerToRecycleBin(customerId).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                    refreshData()
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                    _isLoading.value = false
                }
            )
        }
        return resultLiveData
    }

    fun addCustomer(customer: Customer): LiveData<Result<Customer>> {
        val resultLiveData = MutableLiveData<Result<Customer>>()
        viewModelScope.launch {
            _isLoading.value = true
            repository.addCustomer(customer).fold(
                onSuccess = { newCustomer ->
                    resultLiveData.value = Result.success(newCustomer)
                    refreshData() // Refresh data after adding
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                    _isLoading.value = false
                }
            )
            // isLoading set to false inside refreshData -> loadFirstPage -> applyFiltersAndSearch
        }
        return resultLiveData
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.updateCustomer(customer).fold(
                onSuccess = {
                    refreshData()
                },
                onFailure = { error ->
                    _errorMessage.value = error.message
                    _isLoading.value = false
                }
            )
            // isLoading set to false inside refreshData -> loadFirstPage -> applyFiltersAndSearch
        }
    }

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

    fun deleteCustomer(customerId: String): LiveData<Result<Unit>> {
        val resultLiveData = MutableLiveData<Result<Unit>>()
        _isLoading.value = true
        viewModelScope.launch {
            repository.deleteCustomer(customerId).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                    refreshData()
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = error.message
                    _isLoading.value = false
                }
            )
            // isLoading set to false inside refreshData -> loadFirstPage -> applyFiltersAndSearch
        }
        return resultLiveData
    }

    // Clear error message
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Remove observers to prevent memory leaks
        _activeCustomerType.removeObserver { }
    }
}