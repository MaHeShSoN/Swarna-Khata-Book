package com.jewelrypos.swarnakhatabook.ViewModle

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryViewModel(
    private val repository: InventoryRepository,
    private val connectivityManager: ConnectivityManager,
    private val context: Context
) : ViewModel() {

    // Original list of all items
    private val _allJewelleryItems = mutableListOf<JewelleryItem>()

    // Filtered list based on filters and search query
    private val _jewelleryItems = MutableLiveData<List<JewelleryItem>>()
    val jewelleryItems: LiveData<List<JewelleryItem>> = _jewelleryItems

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // --- Filter State ---
    private val _activeFilters = MutableLiveData<Set<String>>(emptySet())
    val activeFilters: LiveData<Set<String>> = _activeFilters

    // --- Search State ---
    var searchJob: Job? = null // Made public for cancellation in Fragment's onDestroyView
    private var currentSearchQuery = ""

    // --- Constants ---
    companion object {
        private const val LOW_STOCK_THRESHOLD = 5.0
        private const val FILTER_GOLD = "GOLD"
        private const val FILTER_SILVER = "SILVER"
        private const val FILTER_OTHER = "OTHER"
        private const val FILTER_LOW_STOCK = "LOW_STOCK"
        private val TYPE_FILTERS = setOf(FILTER_GOLD, FILTER_SILVER, FILTER_OTHER)
    }


    init {
        loadFirstPage()
    }

    /**
     * Toggles a specific filter type. Handles mutual exclusivity for type filters.
     */
    fun toggleFilter(filterType: String, isActive: Boolean) {
        val currentFilters = _activeFilters.value?.toMutableSet() ?: mutableSetOf()

        if (isActive) {
            // If activating a TYPE filter, remove other TYPE filters first
            if (TYPE_FILTERS.contains(filterType)) {
                currentFilters.removeAll(TYPE_FILTERS)
            }
            // Add the new filter
            currentFilters.add(filterType)
        } else {
            // Simply remove the filter if deactivated
            currentFilters.remove(filterType)
        }

        // Update the LiveData only if the set has actually changed
        if (_activeFilters.value != currentFilters) {
            _activeFilters.value = currentFilters
            applyFiltersAndSearch() // Apply filters whenever the active set changes
        }
    }


    /**
     * Clears all active filters.
     */
    fun clearAllFilters() {
        if (_activeFilters.value?.isNotEmpty() == true) {
            _activeFilters.value = emptySet()
            applyFiltersAndSearch()
        }
    }

    /**
     * Applies the current search query, debouncing the call.
     */
    fun searchItems(query: String) {
        val newQuery = query.trim().lowercase()
        if(currentSearchQuery == newQuery) return

        currentSearchQuery = newQuery
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            applyFiltersAndSearch()
        }
    }

    /**
     * Applies all active filters (type, low stock) and the search query
     * to the master list (_allJewelleryItems) asynchronously.
     */
    private fun applyFiltersAndSearch() {
        viewModelScope.launch {
            _isLoading.value = true

            val filteredList = withContext(Dispatchers.Default) {
                var tempList = _allJewelleryItems.toList()
                val activeFiltersSet = _activeFilters.value ?: emptySet()

                // 1. Apply Type Filters (only one of GOLD, SILVER, OTHER can be active now)
                val activeTypeFilter = activeFiltersSet.intersect(TYPE_FILTERS).firstOrNull()
                if (activeTypeFilter != null) {
                    tempList = tempList.filter { item ->
                        item.itemType.equals(activeTypeFilter, ignoreCase = true)
                    }
                }

                // 2. Apply Low Stock Filter (independent)
                if (activeFiltersSet.contains(FILTER_LOW_STOCK)) {
                    tempList = tempList.filter { item ->
                        item.stock <= LOW_STOCK_THRESHOLD
                    }
                }

                // 3. Apply Search Query Filter
                if (currentSearchQuery.isNotEmpty()) {
                    tempList = tempList.filter { item ->
                        item.displayName.contains(currentSearchQuery, ignoreCase = true) ||
                                item.jewelryCode.contains(currentSearchQuery, ignoreCase = true) ||
                                item.category.contains(currentSearchQuery, ignoreCase = true) ||
                                item.itemType.contains(currentSearchQuery, ignoreCase = true) ||
                                item.location.contains(currentSearchQuery, ignoreCase = true) ||
                                item.purity.contains(currentSearchQuery, ignoreCase = true)
                    }
                }
                tempList
            }

            _jewelleryItems.value = filteredList
            _isLoading.value = false
            Log.d("InventoryViewModel", "Applied filters: ${_activeFilters.value} & search: '$currentSearchQuery'. Result count: ${filteredList.size}")
        }
    }


    // --- Data Loading & Refresh ---

    fun refreshData() {

        loadFirstPage()
    }


    fun refreshDataAndClearFilters() {
        // Clear active filters
        if (_activeFilters.value?.isNotEmpty() == true) {
            _activeFilters.value = emptySet()
        }

        // Clear search query
        currentSearchQuery = ""

        // Cancel any ongoing search job
        searchJob?.cancel()

        // Reload first page of data
        loadFirstPage()

        Log.d("InventoryViewModel", "Refreshed data and cleared all filters")
    }

    private fun loadFirstPage() {
        _isLoading.value = true
        viewModelScope.launch {
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE
            repository.fetchJewelleryItemsPaginated(loadNextPage = false, source = source).fold(
                onSuccess = { fetchedItems ->
                    _allJewelleryItems.clear()
                    _allJewelleryItems.addAll(fetchedItems)
                    applyFiltersAndSearch() // Apply filters after loading
                },
                onFailure = { error ->
                    _errorMessage.value = "Error loading inventory: ${error.message}"
                    _isLoading.value = false // Ensure loading stops on failure
                    Log.e("InventoryViewModel", "Error loading first page", error)
                }
            )
            // isLoading is set to false inside applyFiltersAndSearch
        }
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return

        _isLoading.value = true
        viewModelScope.launch {
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE
            repository.fetchJewelleryItemsPaginated(loadNextPage = true, source = source).fold(
                onSuccess = { newItems ->
                    if (newItems.isNotEmpty()) {
                        _allJewelleryItems.addAll(newItems)
                        applyFiltersAndSearch() // Apply filters including the new items
                    } else {
                        _isLoading.value = false // No more items, stop loading
                    }
                },
                onFailure = { error ->
                    _errorMessage.value = "Error loading next page: ${error.message}"
                    _isLoading.value = false
                    Log.e("InventoryViewModel", "Error loading next page", error)
                }
            )
            // isLoading is set to false inside applyFiltersAndSearch
        }
    }

    // --- Item Modification ---

    fun addJewelleryItem(jewelleryItem: JewelleryItem) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.addJewelleryItem(jewelleryItem).fold(
                onSuccess = {
                    refreshData() // Reload data after adding
                },
                onFailure = { error ->
                    _errorMessage.value = "Error adding item: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun updateJewelleryItem(item: JewelleryItem) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateJewelleryItem(item)
                refreshData() // Reload data after updating
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update item: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun getAllItemsForDropdown(): LiveData<List<JewelleryItem>> {
        val liveData = MutableLiveData<List<JewelleryItem>>()
        viewModelScope.launch {
            repository.getAllInventoryItems().fold(
                onSuccess = { items ->
                    liveData.value = items
                },
                onFailure = {
                    liveData.value = emptyList()
                }
            )
        }
        return liveData
    }

    fun deleteJewelleryItem(itemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.deleteJewelleryItem(itemId).fold(
                onSuccess = {
                    refreshData() // Reload data after deletion
                },
                onFailure = { error ->
                    _errorMessage.value = "Failed to delete item: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * Moves a jewelry item to the recycle bin instead of permanent deletion
     */
    fun moveJewelleryItemToRecycleBin(item: JewelleryItem): LiveData<Result<Unit>> {
        val resultLiveData = MutableLiveData<Result<Unit>>()
        viewModelScope.launch {
            _isLoading.value = true
            repository.moveItemToRecycleBin(item).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                    refreshData() // Reload data after moving to recycle bin
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = "Failed to move item to recycle bin: ${error.message}"
                    _isLoading.value = false
                }
            )
        }
        return resultLiveData
    }

    /**
     * Get the total count of inventory items
     */
    suspend fun getTotalInventoryCount(): Int {
        return repository.getTotalInventoryCount().fold(
            onSuccess = { count ->
                count
            },
            onFailure = { error ->
                _errorMessage.value = "Error getting inventory count: ${error.message}"
                0 // Return 0 on error
            }
        )
    }

    // --- Helper Functions ---

    private fun isOnline(): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }
}