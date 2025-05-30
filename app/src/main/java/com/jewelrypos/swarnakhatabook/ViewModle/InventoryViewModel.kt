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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull // Keep filterNotNull if needed for initial combine value
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryViewModel(
    private val repository: InventoryRepository,
    private val connectivityManager: ConnectivityManager,
    private val context: Context
) : ViewModel() {

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // --- Filter State ---
    // Use StateFlows to trigger PagingData updates when search or filters change
    private val _activeFilters = MutableStateFlow<Set<String>>(emptySet())
    // Expose as StateFlow to allow synchronous access to its current value using .value
    val activeFilters: StateFlow<Set<String>> = _activeFilters.asStateFlow()


    // --- Search State ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow() // Expose as StateFlow

    // --- Paging 3 Data Flow ---
    // This Flow combines the search query and active filters StateFlows.
    // Whenever either changes, a new PagingSource is created and its flow is collected.
    val jewelleryItemsFlow: Flow<PagingData<JewelleryItem>> = combine(
        searchQuery.debounce(300), // Removed distinctUntilChanged() - redundant for StateFlow
        activeFilters // Removed distinctUntilChanged() - redundant for StateFlow
    ) { searchQuery, activeFilters ->
        // This block is executed when either searchQuery or activeFilters change.
        // It creates a new Pager with the updated criteria.
        createPager(searchQuery, activeFilters)
    }
        .flatMapLatest { pager -> pager.flow } // Collect the flow from the new Pager, cancelling the previous one
        .cachedIn(viewModelScope) // Cache the PagingData stream


    // --- RecyclerView state preservation ---
    var layoutManagerState: Parcelable? = null

    // --- Constants ---
    companion object {
        internal const val LOW_STOCK_THRESHOLD = 5.0
        internal const val LOW_STOCK_WEIGHT_THRESHOLD = 100.0 // Weight threshold in grams
        const val FILTER_GOLD = "GOLD"
        const val FILTER_SILVER = "SILVER"
        const val FILTER_OTHER = "OTHER"
        const val FILTER_LOW_STOCK = "LOW_STOCK"
        private val TYPE_FILTERS = setOf(FILTER_GOLD, FILTER_SILVER, FILTER_OTHER)
    }


    /**
     * Configures and creates the Pager. This is called whenever search/filters change.
     */
    private fun createPager(searchQuery: String, activeFilters: Set<String>): Pager<QuerySnapshot, JewelleryItem> {
        Log.d("InventoryViewModel", "Creating new Pager for search: '$searchQuery', filters: $activeFilters")
        return Pager(
            config = PagingConfig(
                pageSize = InventoryRepository.PAGE_SIZE,
                enablePlaceholders = false // Set to true if you want placeholders
            ),
            pagingSourceFactory = {
                // Create a new PagingSource with the current search and filters
                repository.getInventoryPagingSource(searchQuery, activeFilters.map { it.uppercase() }.toSet(), getDataSource())
            }
        )
    }


    /**
     * Toggles a specific filter type. Handles mutual exclusivity for type filters.
     */
    fun toggleFilter(filterType: String, isActive: Boolean) {
        val currentFilters = _activeFilters.value.toMutableSet() // Access private StateFlow's value

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

        // Update the private MutableStateFlow (triggers PagingData refresh via combine)
        if (_activeFilters.value != currentFilters) {
            _activeFilters.value = currentFilters
            Log.d("InventoryViewModel", "Toggled filter: $filterType to $isActive. Active filters: ${_activeFilters.value}")
            // PagingData will be refreshed automatically by observing _activeFilters StateFlow
        }
    }


    /**
     * Clears all active filters.
     */
    fun clearAllFilters() {
        if (_activeFilters.value.isNotEmpty()) { // Access private StateFlow's value
            _activeFilters.value = emptySet() // Update private MutableStateFlow
            Log.d("InventoryViewModel", "Cleared all filters.")
            // PagingData will be refreshed automatically by observing _activeFilters StateFlow
        }
    }

    /**
     * Updates the search query.
     */
    fun searchItems(query: String) {
        val newQuery = query.trim().lowercase()
        if(_searchQuery.value == newQuery) return // Access private StateFlow's value

        _searchQuery.value = newQuery // Update private MutableStateFlow
        Log.d("InventoryViewModel", "Updated search query: '$newQuery'")
        // PagingData will be refreshed automatically by observing _searchQuery StateFlow
    }


    // --- Data Loading & Refresh ---
    // Refresh is now handled by calling adapter.refresh() in the Fragment,
    // which internally invalidates the PagingSource, causing it to reload.


    fun refreshDataAndClearFilters() {
        // Clear active filters
        if (_activeFilters.value.isNotEmpty()) { // Access private StateFlow's value
            _activeFilters.value = emptySet() // Update private MutableStateFlow
        }

        // Clear search query
        if (_searchQuery.value.isNotEmpty()) { // Access private StateFlow's value
            _searchQuery.value = "" // Update private MutableStateFlow
        }

        // Clearing filters/search will trigger a new Pager via the combine flow
        // and thus refresh the PagingData stream.
        Log.d("InventoryViewModel", "Refreshed data and cleared all filters")
    }


    // --- Item Modification ---

    // After modifying an item (add, update, delete), we need to trigger a refresh
    // of the PagingData stream so the changes are reflected in the UI.

    fun addJewelleryItem(jewelleryItem: JewelleryItem): LiveData<Result<Unit>> {
        val resultLiveData = MutableLiveData<Result<Unit>>()
        viewModelScope.launch {
            repository.addJewelleryItem(jewelleryItem).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                    Log.d("InventoryViewModel", "Item added successfully. Will trigger refresh.")
                    // Trigger PagingData refresh
                    triggerPagingRefresh()
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = "Error adding item: ${error.message}"
                    Log.e("InventoryViewModel", "Error adding item", error)
                }
            )
        }
        return resultLiveData // Return LiveData for Fragment to observe result
    }

    fun updateJewelleryItem(item: JewelleryItem): LiveData<Result<Unit>> {
        val resultLiveData = MutableLiveData<Result<Unit>>()
        viewModelScope.launch {
            try {
                repository.updateJewelleryItem(item)
                resultLiveData.value = Result.success(Unit)
                Log.d("InventoryViewModel", "Item updated successfully. Will trigger refresh.")
                triggerPagingRefresh()
            } catch (e: Exception) {
                resultLiveData.value = Result.failure(e)
                _errorMessage.value = "Failed to update item: ${e.message}"
                Log.e("InventoryViewModel", "Failed to update item", e)
            }
        }
        return resultLiveData // Return LiveData for Fragment to observe result
    }


    fun deleteJewelleryItem(itemId: String): LiveData<Result<Unit>> {
        val resultLiveData = MutableLiveData<Result<Unit>>()
        viewModelScope.launch {
            repository.deleteJewelleryItem(itemId).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                    Log.d("InventoryViewModel", "Item deleted successfully. Will trigger refresh.")
                    triggerPagingRefresh()
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = "Failed to delete item: ${error.message}"
                    Log.e("InventoryViewModel", "Failed to delete item", error)
                }
            )
        }
        return resultLiveData // Return LiveData for Fragment to observe result
    }

    /**
     * Moves a jewelry item to the recycle bin instead of permanent deletion
     */
    fun moveJewelleryItemToRecycleBin(item: JewelleryItem): LiveData<Result<Unit>> {
        val resultLiveData = MutableLiveData<Result<Unit>>()
        viewModelScope.launch {
            repository.moveItemToRecycleBin(item).fold(
                onSuccess = {
                    resultLiveData.value = Result.success(Unit)
                    Log.d("InventoryViewModel", "Item moved to recycle bin successfully. Will trigger refresh.")
                    triggerPagingRefresh()
                },
                onFailure = { error ->
                    resultLiveData.value = Result.failure(error)
                    _errorMessage.value = "Failed to move item to recycle bin: ${error.message}"
                    Log.e("InventoryViewModel", "Failed to move item to recycle bin", error)
                }
            )
        }
        return resultLiveData
    }

    /**
     * Helper to trigger a refresh of the PagingData.
     * This is done by updating the private mutable state flows, which
     * causes the public StateFlows to emit (if the value changes) and
     * triggers the combine flow to create a new Pager.
     */
    private fun triggerPagingRefresh() {
        // Re-set the current values of the private mutable state flows.
        // This serves as a signal to the combine flow to potentially
        // create a new Pager. distinctUntilChanged() is not needed on StateFlow
        // directly due to operator fusion, but the change in the underlying
        // mutable state flow's value *will* trigger the public StateFlow
        // to emit if the value is distinct, which then triggers the combine.
        // If a strict refresh is needed even if the state doesn't change,
        // consider an incrementing counter in a StateFlow combined with search/filters.
        viewModelScope.launch {
            _searchQuery.value = _searchQuery.value // Re-setting value
            _activeFilters.value = _activeFilters.value // Re-setting value
            Log.d("InventoryViewModel", "Triggered PagingData refresh by re-setting state flows.")
        }
    }


    /**
     * Get the total count of inventory items
     * Note: This fetches ALL items to count. For large datasets,
     * a dedicated Firestore count query would be more efficient if available/needed frequently.
     */
    suspend fun getTotalInventoryCount(): Int {
        return repository.getTotalInventoryCount().fold(
            onSuccess = { count ->
                count
            },
            onFailure = { error ->
                _errorMessage.value = "Error getting inventory count: ${error.message}"
                Log.e("InventoryViewModel", "Error getting total inventory count", error)
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

    private fun getDataSource(): Source {
        return if (isOnline()) Source.DEFAULT else Source.CACHE
    }
}