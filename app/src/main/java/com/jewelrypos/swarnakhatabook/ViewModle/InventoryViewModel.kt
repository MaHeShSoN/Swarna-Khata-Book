package com.jewelrypos.swarnakhatabook.ViewModle

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
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val repository: InventoryRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    // Original list of all items
    private val _allJewelleryItems = mutableListOf<JewelleryItem>()

    // Filtered list based on search query
    private val _jewelleryItems = MutableLiveData<List<JewelleryItem>>()
    val jewelleryItems: LiveData<List<JewelleryItem>> = _jewelleryItems

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _activeFilter = MutableLiveData<String?>()
    val activeFilter: LiveData<String?> = _activeFilter

    // Search query
    private var currentSearchQuery = ""

    private var currentFilter: String? = null

    private val _activeFilters = MutableLiveData<Set<String>>(setOf())
    val activeFilters: LiveData<Set<String>> = _activeFilters
    private val LOW_STOCK_THRESHOLD = 5.0

    // Add a method to filter items by type
    fun filterByType(type: String?) {
        currentFilter = type
        _activeFilter.value = type
        applyFiltersAndSearch()
    }

    init {
        loadFirstPage()
    }

    fun toggleFilter(filterType: String, isActive: Boolean) {
        // Get current filters
        val currentFilters = _activeFilters.value?.toMutableSet() ?: mutableSetOf()

        // Update filter set based on action
        if (isActive) {
            currentFilters.add(filterType)
        } else {
            currentFilters.remove(filterType)
        }

        // Update LiveData
        _activeFilters.value = currentFilters

        // Apply filters
        applyFiltersAndSearch()

        Log.d("InventoryViewModel", "Filters updated: $currentFilters")
    }

    fun clearAllFilters() {
        _activeFilters.value = setOf()
        applyFiltersAndSearch()
        Log.d("InventoryViewModel", "All filters cleared")
    }

    // Method to filter items based on search query
//    fun searchItems(query: String) {
//        currentSearchQuery = query.trim().lowercase()
//        Log.d("InventoryViewModel", "Searching with query: '$currentSearchQuery'")
//        if (currentSearchQuery.isEmpty()) {
//            // Show all items
//            _jewelleryItems.value = _allJewelleryItems.toList()
//            Log.d("InventoryViewModel", "Showing all ${_allJewelleryItems.size} items")
//        } else {
//            // Filter based on query
//            _jewelleryItems.value = _allJewelleryItems.filter { item ->
//                item.displayName.lowercase().contains(currentSearchQuery) ||
//                        item.jewelryCode.lowercase().contains(currentSearchQuery) ||
//                        item.category.lowercase().contains(currentSearchQuery) ||
//                        item.itemType.lowercase().contains(currentSearchQuery) ||
//                        item.location.lowercase().contains(currentSearchQuery) ||
//                        item.purity.lowercase().contains(currentSearchQuery)
//            }
//        }
//    }


    private fun applyFiltersAndSearch() {
        var filteredList = _allJewelleryItems.toList()
        val activeFilters = _activeFilters.value ?: setOf()

        // Check if we have category filters (gold, silver, other)
        val categoryFilters = activeFilters.intersect(setOf("GOLD", "SILVER", "OTHER"))

        // If we have category filters, apply them
        if (categoryFilters.isNotEmpty()) {
            filteredList = filteredList.filter { item ->
                categoryFilters.contains(item.itemType.uppercase())
            }
            Log.d(
                "InventoryViewModel",
                "Applied category filters: $categoryFilters, items: ${filteredList.size}"
            )
        }

        // Apply low stock filter if active
        if (activeFilters.contains("LOW_STOCK")) {
            filteredList = filteredList.filter { item ->
                item.stock <= LOW_STOCK_THRESHOLD
            }
            Log.d("InventoryViewModel", "Applied low stock filter, items: ${filteredList.size}")
        }

        // Apply search query if set
        if (currentSearchQuery.isNotEmpty()) {
            filteredList = filteredList.filter { item ->
                item.displayName.lowercase().contains(currentSearchQuery) ||
                        item.jewelryCode.lowercase().contains(currentSearchQuery) ||
                        item.category.lowercase().contains(currentSearchQuery) ||
                        item.itemType.lowercase().contains(currentSearchQuery) ||
                        item.location.lowercase().contains(currentSearchQuery) ||
                        item.purity.lowercase().contains(currentSearchQuery)
            }
            Log.d(
                "InventoryViewModel",
                "Applied search: '$currentSearchQuery', items: ${filteredList.size}"
            )
        }

        _jewelleryItems.value = filteredList

        // Log detailed filter information for debugging
        Log.d(
            "InventoryViewModel", "Filter summary: total=${_allJewelleryItems.size}, " +
                    "filtered=${filteredList.size}, filters=$activeFilters, search='$currentSearchQuery'"
        )
    }


    fun searchItems(query: String) {
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
        _allJewelleryItems.clear()
        viewModelScope.launch {
            // Choose source based on connectivity
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            repository.fetchJewelleryItemsPaginated(false, source).fold(
                onSuccess = {
                    _allJewelleryItems.addAll(it)
                    // Apply current search filter
                    searchItems(currentSearchQuery)
                    _isLoading.value = false
                    Log.d("invFrag", "First page loaded: ${it.size} items")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("invFrag", "Error loading first page: ${it.message}")
                }
            )
        }
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return

        _isLoading.value = true
        viewModelScope.launch {
            // Choose source based on connectivity, same as in loadFirstPage
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            repository.fetchJewelleryItemsPaginated(true, source).fold(
                onSuccess = { newItems ->
                    _allJewelleryItems.addAll(newItems)
                    // Apply current search filter
                    searchItems(currentSearchQuery)
                    _isLoading.value = false
                    Log.d("invFrag", "Next page loaded: ${newItems.size} items")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("invFrag", "Error loading next page: ${it.message}")
                }
            )
        }
    }

    fun addJewelleryItem(jewelleryItem: JewelleryItem) {
        viewModelScope.launch {
            repository.addJewelleryItem(jewelleryItem).fold(
                onSuccess = {
                    loadFirstPage()
                    Log.d("invFrag", "Item added successfully")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    Log.d("invFrag", "Error adding item: ${it.message}")
                }
            )
        }
    }


    fun updateJewelleryItem(item: JewelleryItem) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.updateJewelleryItem(item)
                refreshData() // Refresh to show updated data
            } catch (e: Exception) {
                Log.d("updateJewelleryItem", e.message.toString())
                _errorMessage.value = "Failed to update item: ${e.message}"
            } finally {
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
}