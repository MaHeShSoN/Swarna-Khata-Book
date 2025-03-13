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
import kotlinx.coroutines.launch

class CustomerViewModel(
    private val repository: CustomerRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    // Original list of all customers
    private val _allCustomers = mutableListOf<Customer>()

    // Filtered list based on search query
    private val _customers = MutableLiveData<List<Customer>>()
    val customers: LiveData<List<Customer>> = _customers

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _activeFilter = MutableLiveData<String?>()
    val activeFilter: LiveData<String?> = _activeFilter

    // Search query
    private var currentSearchQuery = ""

    // Current filter
    private var currentFilter: String? = null


    // Add these properties to CustomerViewModel
    private val _activeCustomerType = MutableLiveData<String?>()
    val activeCustomerType: LiveData<String?> = _activeCustomerType

    private val _activeSortOrder = MutableLiveData<String?>("ASC")
    val activeSortOrder: LiveData<String?> = _activeSortOrder

    private val _activePaymentStatus = MutableLiveData<String?>()
    val activePaymentStatus: LiveData<String?> = _activePaymentStatus


    init {
        loadFirstPage()
    }


    // Method to apply all filters at once
    fun applyFilters(customerType: String?, sortOrder: String?, paymentStatus: String?) {
        _activeCustomerType.value = customerType
        if (sortOrder != null) _activeSortOrder.value = sortOrder
        _activePaymentStatus.value = paymentStatus

        // Update the _activeFilter value for the UI indicator
        _activeFilter.value = if (customerType != null || paymentStatus != null) {
            "Active" // Just a marker that some filter is active
        } else {
            null
        }

        // Apply the filters
        applyFiltersAndSearch()
    }

    // Modify the applyFiltersAndSearch method to include sorting and payment status
    private fun applyFiltersAndSearch() {
        var filteredList = _allCustomers.toList()

        // Apply type filter if set
        if (!_activeCustomerType.value.isNullOrEmpty()) {
            filteredList = filteredList.filter {
                it.customerType.equals(_activeCustomerType.value, ignoreCase = true)
            }
        }

        // Apply payment status filter if set
        if (!_activePaymentStatus.value.isNullOrEmpty()) {
            filteredList = filteredList.filter {
                it.balanceType.equals(_activePaymentStatus.value, ignoreCase = true)
            }
        }

        // Apply search query if set
        if (currentSearchQuery.isNotEmpty()) {
            filteredList = filteredList.filter { customer ->
                customer.firstName.contains(currentSearchQuery, ignoreCase = true) ||
                        customer.lastName.contains(currentSearchQuery, ignoreCase = true) ||
                        customer.phoneNumber.contains(currentSearchQuery, ignoreCase = true) ||
                        customer.email.contains(currentSearchQuery, ignoreCase = true) ||
                        customer.businessName.contains(currentSearchQuery, ignoreCase = true) ||
                        "${customer.firstName} ${customer.lastName}".contains(currentSearchQuery, ignoreCase = true)
            }
        }

        // Apply sorting
        filteredList = if (_activeSortOrder.value == "ASC") {
            filteredList.sortedBy { "${it.firstName} ${it.lastName}" }
        } else {
            filteredList.sortedByDescending { "${it.firstName} ${it.lastName}" }
        }

        _customers.value = filteredList
        Log.d("CustomerViewModel", "Filtered to ${filteredList.size} customers")
    }




    // Filter customers by type (Consumer, Wholesaler)
    fun filterByType(type: String?) {
        currentFilter = type
        _activeFilter.value = type
        applyFiltersAndSearch()
    }

//    private fun applyFiltersAndSearch() {
//        var filteredList = _allCustomers.toList()
//
//        // Apply type filter if set
//        if (!currentFilter.isNullOrEmpty()) {
//            filteredList = filteredList.filter {
//                it.customerType.equals(currentFilter, ignoreCase = true)
//            }
//        }
//
//        // Apply search query if set
//        if (currentSearchQuery.isNotEmpty()) {
//            filteredList = filteredList.filter { customer ->
//                customer.firstName.contains(currentSearchQuery, ignoreCase = true) ||
//                        customer.lastName.contains(currentSearchQuery, ignoreCase = true) ||
//                        customer.phoneNumber.contains(currentSearchQuery, ignoreCase = true) ||
//                        customer.email.contains(currentSearchQuery, ignoreCase = true) ||
//                        customer.businessName.contains(currentSearchQuery, ignoreCase = true) ||
//                        "${customer.firstName} ${customer.lastName}".contains(currentSearchQuery, ignoreCase = true)
//            }
//        }
//
//        _customers.value = filteredList
//        Log.d("CustomerViewModel", "Filtered to ${filteredList.size} customers")
//    }

    fun searchCustomers(query: String) {
        currentSearchQuery = query.trim().lowercase()
        applyFiltersAndSearch()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            loadFirstPage() // Don't clear data first, let loadFirstPage do it
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
        _allCustomers.clear()
        viewModelScope.launch {
            // Choose source based on connectivity
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            repository.fetchCustomersPaginated(false, source).fold(
                onSuccess = {
                    _allCustomers.addAll(it)
                    // Apply current search filter
                    searchCustomers(currentSearchQuery)
                    _isLoading.value = false
                    Log.d("CustomerViewModel", "First page loaded: ${it.size} customers")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("CustomerViewModel", "Error loading first page: ${it.message}")
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

            repository.fetchCustomersPaginated(true, source).fold(
                onSuccess = { newCustomers ->
                    _allCustomers.addAll(newCustomers)
                    // Apply current search filter
                    searchCustomers(currentSearchQuery)
                    _isLoading.value = false
                    Log.d("CustomerViewModel", "Next page loaded: ${newCustomers.size} customers")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("CustomerViewModel", "Error loading next page: ${it.message}")
                }
            )
        }
    }

    fun addCustomer(customer: Customer) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.addCustomer(customer).fold(
                onSuccess = {
                    loadFirstPage() // Refresh to show updated data
                    Log.d("CustomerViewModel", "Customer added successfully")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("CustomerViewModel", "Error adding customer: ${it.message}")
                }
            )
        }
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.updateCustomer(customer).fold(
                onSuccess = {
                    refreshData() // Refresh to show updated data
                    Log.d("CustomerViewModel", "Customer updated successfully")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("CustomerViewModel", "Error updating customer: ${it.message}")
                }
            )
        }
    }
}