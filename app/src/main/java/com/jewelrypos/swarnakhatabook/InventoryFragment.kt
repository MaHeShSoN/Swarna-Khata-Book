package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.JewelleryAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.InventoryViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.Utilitys.AnimationUtils
import com.jewelrypos.swarnakhatabook.Utilitys.FeatureChecker
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.ShopSwitcherViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInventoryBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.transform.CircleCropTransformation
import coil3.size.Scale
import coil3.request.transformations
import kotlinx.coroutines.flow.collectLatest
import androidx.paging.LoadState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class InventoryFragment : Fragment(), ItemBottomSheetFragment.OnItemAddedListener,
    JewelleryAdapter.OnItemClickListener {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    // Get the shared shop switcher view model
    private val shopSwitcherViewModel: ShopSwitcherViewModel by activityViewModels()

    private val inventoryViewModel: InventoryViewModel by navGraphViewModels(R.id.inner_nav_graph) {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        InventoryViewModelFactory(repository, connectivityManager, requireContext())
    }
    private lateinit var adapter: JewelleryAdapter
    private var isSearchActive = false // Track search state (still useful for UI state)

    private var currentShopId: String? = null
    // Define filter type constants matching ViewModel
    companion object {
        // Use constants from ViewModel directly
        private const val FILTER_GOLD = InventoryViewModel.FILTER_GOLD
        private const val FILTER_SILVER = InventoryViewModel.FILTER_SILVER
        private const val FILTER_OTHER = InventoryViewModel.FILTER_OTHER
        private const val FILTER_LOW_STOCK = InventoryViewModel.FILTER_LOW_STOCK
    }

    private var isLayoutStateRestored = false // Flag for state restoration
    private var scrollToTopAfterAdd = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)

         isLayoutStateRestored = false // Reset state restoration flag

        binding.addItemFab.setOnClickListener {
            AnimationUtils.pulse(it)
            addItemButton()
        }

        setUpRecyclerView()
        setupSearchView()
        setupFilterChips() // Setup chip listeners
        setupObservers() // Setup observers for PagingData and LoadState
        setupEventBusObservers()
        setupSwipeRefresh()
        setupEmptyStateButtons()

        currentShopId = shopSwitcherViewModel.activeShop.value?.shopId // Initialize with current value
        Log.d("InventoryFragment", "onViewCreated - Initial currentShopId: $currentShopId")


        // Observe shop changes
        observeShopChanges()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isLayoutStateRestored = false
        scrollToTopAfterAdd = false
        Log.d("InventoryFragment", "onViewCreated: Resetting flags.")

//        setupToolbar()
        
        // Restore search view state if there's an active search
        val currentSearchQuery = inventoryViewModel.searchQuery.value
        if (currentSearchQuery.isNotEmpty()) {
            val searchItem = binding.topAppBar.menu.findItem(R.id.action_search)
            searchItem.expandActionView()
            val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
            searchView.setQuery(currentSearchQuery, false)
            isSearchActive = true
        }

//        setupRecyclerView()
        setupSearchView()
        setupFilterChips() // Setup chip listeners
        setupObservers() // Setup observers for PagingData and LoadState
        setupEventBusObservers()
        setupSwipeRefresh()
        setupEmptyStateButtons()

        currentShopId = shopSwitcherViewModel.activeShop.value?.shopId // Initialize with current value
        Log.d("InventoryFragment", "onViewCreated - Initial currentShopId: $currentShopId")

        // Observe shop changes
        observeShopChanges()
    }

    // In InventoryFragment.kt
    private fun observeShopChanges() {
        shopSwitcherViewModel.activeShop.observe(viewLifecycleOwner) { shop ->
            val newShopId = shop?.shopId // Assuming your Shop object has a unique 'shopId'

            Log.d("InventoryFragment", "Shop change event. New shop ID: $newShopId, Current shop ID: $currentShopId")

            if (newShopId != currentShopId) {
                Log.d("InventoryFragment", "Actual shop ID changed from $currentShopId to $newShopId. Refreshing data and clearing filters.")
                currentShopId = newShopId // Update current shop ID

                // Only refresh if there's a valid new shop or if it explicitly becomes null from a non-null state
                if (newShopId != null || (newShopId == null && currentShopId != null) ) {
                    inventoryViewModel.refreshDataAndClearFilters()
                }

            } else {
                Log.d("InventoryFragment", "Shop ID $newShopId is the same as current, or both null. No refresh triggered by shop change observer.")
            }
        }
    }

//    private fun observeShopChanges() {
//        // Observe shop changes from the shop switcher view model
//        shopSwitcherViewModel.activeShop.observe(viewLifecycleOwner) { shop ->
//            shop?.let {
//                Log.d("InventoryFragment", "Shop changed to: ${shop.shopName}")
//                // Refresh data and clear filters when shop changes
//                inventoryViewModel.refreshDataAndClearFilters()
//            }
//        }
//    }

    private fun setupEventBusObservers() {
        // Observe inventory update events
        EventBus.inventoryUpdatedEvent.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                // Refresh data when an inventory update event is received
                Log.d("InventoryFragment", "Inventory update event received, refreshing PagingData.")
                 adapter.refresh() // Trigger Paging 3 refresh
                // Reset the event to avoid handling it multiple times
                EventBus.resetInventoryUpdatedEvent()

                // Show a brief message to the user
                Toast.makeText(context, "Inventory has been updated", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe inventory delete events
        EventBus.inventoryDeletedEvent.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                // Refresh data when an inventory delete event is received
                Log.d("InventoryFragment", "Inventory delete event received, refreshing PagingData.")
                 adapter.refresh() // Trigger Paging 3 refresh
                // Reset the event to avoid handling it multiple times
                EventBus.resetInventoryDeletedEvent()

                // Show a brief message to the user
                Toast.makeText(context, "An item has been removed from inventory", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun setupObservers() {
        Log.d("InventoryFragment", "setupObservers: Method called")

        // Observe the PagingData flow from the ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            inventoryViewModel.jewelleryItemsFlow.collectLatest { pagingData ->
                Log.d("InventoryFragment", "New PagingData received, submitting to adapter.")
                adapter.submitData(pagingData) // Submit PagingData to the adapter
                 Log.d("InventoryFragment", "PagingData submitted. Scroll/state restoration will be handled by LoadState observer.")
            }
        }

        // Observe LoadState from the adapter to handle UI state (loading, empty)
        // Also triggers state restoration when NotLoading and list is not empty
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                Log.d("InventoryFragment", "Load states updated: $loadStates")
                _binding?.let { currentBinding ->
                    val refreshState = loadStates.refresh // State of the initial load or refresh
                    val isListEmpty = adapter.itemCount == 0 // Check item count in the adapter after updates
                     Log.d("InventoryFragment", "Is list empty: $isListEmpty (Adapter count: ${adapter.itemCount})")

                    // Update swipe refresh indicator
                    currentBinding.swipeRefreshLayout.isRefreshing = refreshState is LoadState.Loading

                    when (refreshState) {
                        is LoadState.Loading -> {
                             // Show progress bar only if not already refreshing via swipe
                             if (!currentBinding.swipeRefreshLayout.isRefreshing) {
                                currentBinding.progressBar.visibility = View.VISIBLE
                             } else {
                                // Hide progress bar if swipe refresh is active
                                currentBinding.progressBar.visibility = View.GONE
                             }
                            currentBinding.recyclerViewInventory.visibility = View.GONE
                            currentBinding.emptyStateLayout.visibility = View.GONE
                            currentBinding.emptySearchLayout.visibility = View.GONE
                            Log.d("InventoryFragment", "LoadState: Loading (Refresh)")
                        }
                        is LoadState.Error -> {
                            currentBinding.progressBar.visibility = View.GONE
                            currentBinding.swipeRefreshLayout.isRefreshing = false // Stop refreshing on error
                            currentBinding.recyclerViewInventory.visibility = View.GONE
                            currentBinding.emptyStateLayout.visibility = View.GONE
                            currentBinding.emptySearchLayout.visibility = View.GONE

                            val errorMessage = refreshState.error.localizedMessage ?: "Unknown error loading inventory"
                            Toast.makeText(requireContext(), "Error loading inventory: $errorMessage", Toast.LENGTH_LONG).show()
                            Log.e("InventoryFragment", "Paging refresh error: $errorMessage", refreshState.error)

                            // Optionally show empty state or a specific error view if list is empty and error occurs
                            val isListEmptyAfterError = adapter.itemCount == 0 // Check item count after error
                             if (isListEmptyAfterError) {
                                 // Decide which empty state to show based on search/filters
                                 showEmptyState(inventoryViewModel.searchQuery.value, inventoryViewModel.activeFilters.value) // Use public flow value
                             }
                        }
                        is LoadState.NotLoading -> {
                            currentBinding.progressBar.visibility = View.GONE
                            currentBinding.swipeRefreshLayout.isRefreshing = false // Stop refreshing

                            val isListEmptyAfterLoad = adapter.itemCount == 0 // Check item count AFTER NotLoading state

                            Log.d("InventoryFragment", "LoadState: NotLoading. Is list empty: $isListEmptyAfterLoad (Adapter count: ${adapter.itemCount})")

                            // Update UI state based on whether the list is empty and filters/search are active
                            showEmptyState(inventoryViewModel.searchQuery.value, inventoryViewModel.activeFilters.value)

                            if (!isListEmptyAfterLoad) {
                                // List is NOT empty after loading, proceed with potential state restoration
                                if (!isLayoutStateRestored && inventoryViewModel.layoutManagerState != null) {
                                    currentBinding.recyclerViewInventory.post {
                                         Log.d("InventoryFragment", "Posting layout manager state restoration.")
                                        // Ensure binding is still valid and state hasn't been restored in another post
                                        if (_binding != null && !isLayoutStateRestored && inventoryViewModel.layoutManagerState != null) {
                                            Log.d("InventoryFragment", "Restoring layout manager state inside post block.")
                                            _binding?.recyclerViewInventory?.layoutManager?.onRestoreInstanceState(inventoryViewModel.layoutManagerState)
                                            isLayoutStateRestored = true // Set flag after attempting restoration
                                            Log.d("InventoryFragment", "Set isLayoutStateRestored to true after posting restoration.")
                                        } else {
                                            Log.d("InventoryFragment", "Skipping state restoration inside post block (inner conditions not met).")
                                        }
                                    }
                                } else {
                                     Log.d("InventoryFragment", "Skipping immediate state restoration check (outer conditions not met: isLayoutStateRestored=${isLayoutStateRestored}, layoutManagerState=${inventoryViewModel.layoutManagerState != null}). List is not empty.")
                                }
                            } else {
                                // List IS empty after loading. Clear any potentially saved state
                                // as it's not relevant for an empty list resulting from the data load.
                                Log.d("InventoryFragment", "List is empty after loading, showing empty state.")
                            }
                        }
                    }
                }
                 // Observe append and prepend states if you want separate indicators
                 // val appendState = loadStates.append
                 // val prependState = loadStates.prepend
            }
        }

        // Observe search query and active filters to update UI state reactively
         viewLifecycleOwner.lifecycleScope.launch {
             inventoryViewModel.searchQuery
                 .collectLatest { query ->
                 isSearchActive = query.isNotEmpty() // Update search active flag
                 Log.d("InventoryFragment", "Observed search query change: '$query', isSearchActive=$isSearchActive")
                  // UI state update is now primarily driven by LoadState observer
             }
         }

         viewLifecycleOwner.lifecycleScope.launch {
             inventoryViewModel.activeFilters
                  .collectLatest { filters ->
                  Log.d("InventoryFragment", "Observed active filters change: $filters")
                  // Sync chip states when filters change
                  syncChipStates(filters)
                  // UI state update is now primarily driven by LoadState observer
             }
         }


        inventoryViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) { // Check if message is not null or empty
                Log.e("InventoryFragment", "Action Error message: $errorMessage")
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                 // Optionally clear the error message in ViewModel after showing
                 // inventoryViewModel.clearErrorMessage() // Add this method if needed
            }
        }
    }

    private fun setupSearchView() {
        val searchView =
            binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
        with(searchView) {
            queryHint = "Search Jewellery Items..."
            inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setOnQueryTextListener(object :
                androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    // Submitting usually implies the user is done typing
                    query?.let {
                        inventoryViewModel.searchItems(it) // Update ViewModel StateFlow
                    }
                    searchView.clearFocus() // Hide keyboard
                    return true // Indicate query is handled
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // Trigger search with debounce in ViewModel
                    inventoryViewModel.searchItems(newText ?: "") // Update ViewModel StateFlow
                    return true // Indicate query is handled
                }
            })

            // Handle clearing the search by clicking the 'X' icon
            setOnCloseListener {
                inventoryViewModel.searchItems("") // Clear search in ViewModel
                 true // Indicate close is handled
            }
        }
    }

    private fun setupFilterChips() {
        // Initial sync might be needed if ViewModel already has state
         syncChipStates(inventoryViewModel.activeFilters.value) // Use public flow value for initial sync

        // Use setOnCheckedStateChangeListener for Material3 ChipGroup (Metal Types)
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
             // Convert checked IDs to filter constants and update ViewModel
             handleMetalTypeChipChange(checkedIds)
        }

        // Set OnCheckedChangeListener for the Low Stock chip (independent)
         binding.chipLowStock.setOnCheckedChangeListener { _, isChecked ->
             handleLowStockChipChange(isChecked)
         }
    }

    // Handles state changes for the Metal Type ChipGroup
    private fun handleMetalTypeChipChange(checkedIds: List<Int>) {
         Log.d("InventoryFragment", "Metal Type chip group state changed. Checked IDs: $checkedIds")

         // Determine the selected metal type filter based on checked IDs (should be only one)
         val selectedMetalType = when (checkedIds.firstOrNull()) {
             R.id.chipGold -> FILTER_GOLD
             R.id.chipSilver -> FILTER_SILVER
             R.id.chipOther -> FILTER_OTHER
             else -> null // No metal type chip selected
         }

         // Update the ViewModel's filters. Leverage ViewModel's toggleFilter
         // to ensure mutual exclusivity is handled correctly internally.
         // We need to inform the ViewModel about the new *single* selected type,
         // and it will handle turning off the others.

         val currentFilters = inventoryViewModel.activeFilters.value.toMutableSet()
         val newFilters = mutableSetOf<String>()

         // Add the selected metal type filter if any
         if (selectedMetalType != null) {
             newFilters.add(selectedMetalType)
         }

         // Keep the state of the Low Stock filter as it's independent
         if (currentFilters.contains(FILTER_LOW_STOCK)) {
             newFilters.add(FILTER_LOW_STOCK)
         }

         // Update the ViewModel's entire set of active filters
         // The ViewModel should have a method to set the *entire* filter set,
         // or we use toggleFilter appropriately. Let's use toggleFilter.

         // Turn off all existing type filters first
         if (currentFilters.contains(FILTER_GOLD)) inventoryViewModel.toggleFilter(FILTER_GOLD, false)
         if (currentFilters.contains(FILTER_SILVER)) inventoryViewModel.toggleFilter(FILTER_SILVER, false)
         if (currentFilters.contains(FILTER_OTHER)) inventoryViewModel.toggleFilter(FILTER_OTHER, false)

         // Turn on the newly selected type filter (if any)
         if (selectedMetalType != null) {
             inventoryViewModel.toggleFilter(selectedMetalType, true)
         }

         // The low stock state is handled by its own listener.
         Log.d("InventoryFragment", "Processed Metal Type chip change. ViewModel filter state will be updated.")
    }

    // Handles state changes for the Low Stock chip
    private fun handleLowStockChipChange(isChecked: Boolean) {
         Log.d("InventoryFragment", "Low Stock chip state changed: $isChecked")
         inventoryViewModel.toggleFilter(FILTER_LOW_STOCK, isChecked) // Toggle the LOW_STOCK filter in ViewModel
         Log.d("InventoryFragment", "Toggled LOW_STOCK filter in ViewModel to $isChecked.")
    }


// In InventoryFragment.kt

    private fun syncChipStates(activeFilters: Set<String>) {
        // Get the binding safely. If it's null (e.g., view is destroyed), do nothing.
        val currentBinding = _binding ?: return

        // --- Temporarily detach listeners to prevent feedback loops ---
        currentBinding.filterChipGroup.setOnCheckedStateChangeListener(null)
        currentBinding.chipLowStock.setOnCheckedChangeListener(null) // Detach Low Stock listener

        // --- Set checked states based on ViewModel's active filters ---
        currentBinding.chipGold.isChecked = activeFilters.contains(FILTER_GOLD)
        currentBinding.chipSilver.isChecked = activeFilters.contains(FILTER_SILVER)
        currentBinding.chipOther.isChecked = activeFilters.contains(FILTER_OTHER)
        currentBinding.chipLowStock.isChecked = activeFilters.contains(FILTER_LOW_STOCK)

        // --- Re-attach listeners ---
        currentBinding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            handleMetalTypeChipChange(checkedIds)
        }
        currentBinding.chipLowStock.setOnCheckedChangeListener { _, isChecked ->
            handleLowStockChipChange(isChecked)
        }
        Log.d("InventoryFragment", "Synced chip states with filters: $activeFilters")
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("InventoryFragment", "Swipe to refresh triggered.")
             adapter.refresh() // Trigger Paging 3 refresh
        }
    }

    /**
     * Updates the visibility of empty state layouts based on list content, search, and filters.
     */
    private fun showEmptyState(currentSearchQuery: String, activeFilters: Set<String>) {
        val isListEmpty = adapter.itemCount == 0
        val hasActiveFiltersOrSearch = currentSearchQuery.isNotEmpty() || activeFilters.isNotEmpty()

        if (isListEmpty) {
            if (hasActiveFiltersOrSearch) { // Check if any filter OR search is active
                Log.d("InventoryFragment", "List is empty, showing empty search layout (filters/search active).")
            binding.emptySearchLayout.visibility = View.VISIBLE
            binding.recyclerViewInventory.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.GONE
            } else {
                Log.d("InventoryFragment", "List is empty, showing default empty state.")
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewInventory.visibility = View.GONE
            binding.emptySearchLayout.visibility = View.GONE
            }
        } else {
            Log.d("InventoryFragment", "List is not empty, showing RecyclerView.")
            binding.recyclerViewInventory.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
            binding.emptySearchLayout.visibility = View.GONE
        }
    }


    private fun setupEmptyStateButtons() {
        // Add item button in the main empty state
        binding.addNewInventoryItemEmptyButton.setOnClickListener {
            addItemButton()
        }

        // Clear filters/search button in the empty search state
        binding.clearFilterButton.setOnClickListener {
            // Clear search view UI
            val searchView =
                binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            searchView.setQuery("", false) // Clear query visually
            searchView.isIconified = true // Collapse the view
            searchView.onActionViewCollapsed() // Ensure it fully collapses

            // Clear filters and search in ViewModel (this triggers PagingData refresh)
            inventoryViewModel.refreshDataAndClearFilters()
        }

        // Add item button in the empty search state
        binding.addNewItemButton.setOnClickListener {
            addItemButton()
        }
    }

    private fun setUpRecyclerView() {
        val recyclerView: RecyclerView = binding.recyclerViewInventory
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // Pass 'this' as the click listener to the PagingDataAdapter constructor
        adapter = JewelleryAdapter(this)
        recyclerView.adapter = adapter

        // Add scroll listener for FAB animation
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var isFabVisible = true

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && isFabVisible) {
                    // Scrolling down
                    hideFab()
                } else if (dy < 0 && !isFabVisible) {
                    // Scrolling up
                    showFab()
                }
            }

            private fun showFab() {
                binding.addItemFab.animate().cancel() // Cancel any ongoing animation
                binding.addItemFab.show()
                binding.addItemFab.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
                isFabVisible = true
            }

            private fun hideFab() {
                binding.addItemFab.animate().cancel() // Cancel any ongoing animation
                binding.addItemFab.animate()
                    .translationY(binding.addItemFab.height.toFloat() + (binding.addItemFab.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin)
                    .alpha(0f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        binding.addItemFab.hide()
                    }
                    .start()
                isFabVisible = false
            }
        })

        // Coil preloading logic - keep but adapt to use adapter.peek(position) or adapter.getItem(position)
        // No longer need manual loadNextPage checks here as Paging 3 handles it
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = adapter.itemCount // Use adapter.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                
                // Implement efficient Coil preloading
                if (dy > 0) { // Scrolling down
                    // Get the image loader from the context
                    val imageLoader = requireContext().imageLoader
                    
                    // Calculate the range of items to preload (5 items ahead)
                    val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                    val preloadPosition = lastVisiblePosition + 1
                    val preloadCount = 5 // Number of items to preload ahead
                    // Use adapter.itemCount from PagingDataAdapter
                    val endPosition = minOf(preloadPosition + preloadCount, adapter.itemCount)
                    
                    // Preload images in the calculated range
                    for (i in preloadPosition until endPosition) {
                        // Get the item data safely using adapter.peek() or adapter.getItem()
                         // peek is more efficient as it doesn't trigger loading
                        adapter.peek(i)?.let { item ->
                            // Only preload if there's a valid image URL
                            if (item.imageUrl.isNotEmpty()) {
                                // Create an image request configured for efficient background caching
                                val request = ImageRequest.Builder(requireContext())
                                    .data(item.imageUrl)
                                    // Match the same size as in the adapter for cache consistency
                                    .size(JewelleryAdapter.TARGET_WIDTH, JewelleryAdapter.TARGET_HEIGHT)
                                    .scale(Scale.FILL)
                                    // Apply the same transformations for cache consistency
                                    .transformations(CircleCropTransformation())
                                    // Set to null target for cache-only loading (no view attached)
                                    .target(null)
                                    // Use data as memory cache key for cache consistency
//                                    .memoryCacheKey(item.imageUrl)
                                    // Ensure we're using memory cache
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build()
                                
                                // Enqueue the request
                                imageLoader.enqueue(request)
                            }
                        }
                    }
                }
            }
        })
        
        // Preload initial visible images - keep this
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val visibleCount = layoutManager.childCount
            
            // Get the image loader from the context
            val imageLoader = requireContext().imageLoader
            
            // Preload initial visible items
            for (i in firstVisible until (firstVisible + visibleCount)) {
                 // Use adapter.peek()
                adapter.peek(i)?.let { item ->
                    if (item.imageUrl.isNotEmpty()) {
                        val request = ImageRequest.Builder(requireContext())
                            .data(item.imageUrl)
                            .size(JewelleryAdapter.TARGET_WIDTH, JewelleryAdapter.TARGET_HEIGHT)
                            .scale(Scale.FILL)
                            .transformations(CircleCropTransformation())
                            .target(null)
                            // Use data as memory cache key for cache consistency
                            .memoryCacheKey(item.imageUrl)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build()
                        
                        imageLoader.enqueue(request)
                    }
                }
            }
        }
    }

    private fun addItemButton() {
        // Show the bottom sheet immediately for responsive UI
        showBottomSheetDialog()
        
        // Check inventory count in the background BEFORE showing the bottom sheet
        // This logic should be called BEFORE showing the sheet to prevent opening
        // the sheet only to immediately dismiss it.
        // Let's move the check outside the showBottomSheetDialog() call.
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val inventoryCount = inventoryViewModel.getTotalInventoryCount()

                val (isLimitReached, maxLimit) = FeatureChecker.isInventoryLimitReached(requireContext(), inventoryCount)

                if (isLimitReached) {
                     withContext(Dispatchers.Main) {
                         // Dismiss the bottom sheet if it was shown before checking
                         val bottomSheet = childFragmentManager.findFragmentByTag("ItemBottomSheet") as? ItemBottomSheetFragment
                         bottomSheet?.dismissAllowingStateLoss()

                         FeatureChecker.showUpgradeDialogForLimit(requireContext(), "inventory items", maxLimit)
                     }
                } else {
                     // If limit is NOT reached, then show the bottom sheet
                     // showBottomSheetDialog() // Already called above
                }
            } catch (e: Exception) {
                Log.e("InventoryFragment", "Error checking inventory limits: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error checking inventory limits: ${e.message}", Toast.LENGTH_SHORT).show()
                     // If there was an error checking limit, you might still want to show the sheet,
                     // or handle the error differently. For now, we log and toast.
                }
            }
        }
    }

    private fun navigateToItemDetail(item: JewelleryItem) {
        val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
        val action =
            MainScreenFragmentDirections.actionMainScreenFragmentToItemDetailFragment(itemId = item.id)
        parentNavController.navigate(action)
    }

    // --- ItemBottomSheetFragment.OnItemAddedListener Implementation ---
    override fun onItemAdded(item: JewelleryItem) {
         Log.d("InventoryFragment", "onItemAdded callback received.")
        inventoryViewModel.addJewelleryItem(item).observe(viewLifecycleOwner) { result ->
            if (result.isSuccess) {
                adapter.refresh()
                 // EventBus.postInventoryUpdated() // ViewModel triggers refresh, EventBus might be redundant here or used for other screens
                Toast.makeText(requireContext(), "${item.displayName} added successfully", Toast.LENGTH_SHORT).show()
                 // PagingData refresh is triggered by ViewModel after successful add
            } else {
                Toast.makeText(requireContext(), "Error adding item: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                 Log.e("InventoryFragment", "Error adding item via ViewModel", result.exceptionOrNull())
            }
        }
    }

    override fun onItemUpdated(item: JewelleryItem) {
         Log.d("InventoryFragment", "onItemUpdated callback received.")
        inventoryViewModel.updateJewelleryItem(item).observe(viewLifecycleOwner) { result ->
            if (result.isSuccess) {
                 // EventBus.postInventoryUpdated() // ViewModel triggers refresh
                Toast.makeText(requireContext(), "${item.displayName} updated successfully", Toast.LENGTH_SHORT).show()
                 // PagingData refresh is triggered by ViewModel after successful update
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error updating item"
                Log.e("InventoryFragment", "Error updating item via ViewModel: $errorMessage")
                Toast.makeText(requireContext(), "Error updating ${item.displayName}: $errorMessage", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- JewelleryAdapter.OnItemClickListener Implementation ---
    override fun onItemClick(item: JewelleryItem) {
        navigateToItemDetail(item)
    }

    // --- Helper to show BottomSheet ---
    private fun showBottomSheetDialog(item: JewelleryItem? = null) {
        val bottomSheetFragment = ItemBottomSheetFragment.newInstance()
        bottomSheetFragment.setOnItemAddedListener(this)
        item?.let { bottomSheetFragment.setItemForEdit(it) } // Pass item for editing if provided
        // Ensure you use childFragmentManager if calling from within another fragment
        bottomSheetFragment.show(childFragmentManager, "ItemBottomSheet")
         Log.d("InventoryFragment", "Showing item bottom sheet dialog.")
    }

    override fun onDestroyView() {
        // Save the RecyclerView scroll position if the list is not empty
        if (_binding?.recyclerViewInventory?.adapter?.itemCount ?: 0 > 0) {
        binding.recyclerViewInventory.layoutManager?.let { lm ->
            inventoryViewModel.layoutManagerState = lm.onSaveInstanceState()
                 Log.d("InventoryFragment", "Saved layout manager state in onDestroyView (list not empty). State: ${inventoryViewModel.layoutManagerState}")
             }
        } else {
             // If the list is empty when the view is destroyed, the saved state (if any) is not valid for this empty list state, so clear it.
             Log.d("InventoryFragment", "List is empty in onDestroyView, clearing saved state.")
            if (inventoryViewModel.layoutManagerState != null) {
                // If a state was previously saved, keep it. It might be needed if the list repopulates later.
                Log.d("InventoryFragment", "List is empty in onDestroyView, but ViewModel state is NOT null. Keeping previously saved state.")
                // Do nothing here to keep inventoryViewModel.layoutManagerState
            } else {
                // List is empty and no state was previously saved.
                Log.d("InventoryFragment", "List is empty in onDestroyView, and ViewModel state is null. No state to save or clear further.")
                inventoryViewModel.layoutManagerState = null // Or simply do nothing, as it's already null
            }
        }

        super.onDestroyView()
        _binding = null
        Log.d("InventoryFragment", "onDestroyView: binding set to null.")
    }
    
    // Add onResume method to handle restoring state when returning to the fragment
    override fun onResume() {
        super.onResume()
        Log.d("InventoryFragment", "onResume called.")
         // State restoration is now handled in the LoadState observer after data is loaded.
         // This ensures state is restored only when the list has items.
         // The flag `isLayoutStateRestored` helps prevent multiple restorations.
         // It's reset in onCreateView.
    }
}