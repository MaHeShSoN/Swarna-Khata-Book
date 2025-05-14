package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

class InventoryFragment : Fragment(), ItemBottomSheetFragment.OnItemAddedListener,
    JewelleryAdapter.OnItemClickListener {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    // Get the shared shop switcher view model
    private val shopSwitcherViewModel: ShopSwitcherViewModel by activityViewModels()

    private val inventoryViewModel: InventoryViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        InventoryViewModelFactory(repository, connectivityManager, requireContext())
    }
    private lateinit var adapter: JewelleryAdapter
    private var isSearchActive = false // Track search state

    // Define filter type constants matching ViewModel
    companion object {
        private const val FILTER_GOLD = "GOLD"
        private const val FILTER_SILVER = "SILVER"
        private const val FILTER_OTHER = "OTHER"
        private const val FILTER_LOW_STOCK = "LOW_STOCK"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)

        binding.addItemFab.setOnClickListener {
            AnimationUtils.pulse(it)
            addItemButton()
        }

        setUpRecyclerView()
        setupSearchView()
        setupFilterChips() // Setup chip listeners
        setupObservers()
        setupEventBusObservers()
        setupSwipeRefresh()
        setupEmptyStateButtons()

        // Observe shop changes
        observeShopChanges()

        return binding.root
    }

    private fun observeShopChanges() {
        // Observe shop changes from the shop switcher view model
        shopSwitcherViewModel.activeShop.observe(viewLifecycleOwner) { shop ->
            shop?.let {
                Log.d("InventoryFragment", "Shop changed to: ${shop.shopName}")
                // Refresh data when shop changes
                inventoryViewModel.refreshDataAndClearFilters()
            }
        }
    }

    private fun setupEventBusObservers() {
        // Observe inventory update events
        EventBus.inventoryUpdatedEvent.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                // Refresh data and clear filters when an inventory update event is received
                inventoryViewModel.refreshDataAndClearFilters()
                // Reset the event to avoid handling it multiple times
                EventBus.resetInventoryUpdatedEvent()

                // Show a brief message to the user
                Toast.makeText(context, "Inventory has been updated", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe inventory delete events
        EventBus.inventoryDeletedEvent.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                // Refresh data and clear filters when an inventory delete event is received
                inventoryViewModel.refreshDataAndClearFilters()
                // Reset the event to avoid handling it multiple times
                EventBus.resetInventoryDeletedEvent()

                // Show a brief message to the user
                Toast.makeText(context, "An item has been removed from inventory", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun setupObservers() {
        inventoryViewModel.jewelleryItems.observe(viewLifecycleOwner) { items ->
            binding.swipeRefreshLayout.isRefreshing = false // Stop refreshing UI
            adapter.updateList(items) // Update the adapter with filtered/searched list
            
            // Restore state after the adapter update has been processed by layout
            binding.recyclerViewInventory.post {
                if (_binding != null &&inventoryViewModel.layoutManagerState != null && items.isNotEmpty()) {
                    binding.recyclerViewInventory.layoutManager?.onRestoreInstanceState(inventoryViewModel.layoutManagerState)
                    // Don't clear the state after restoration, so it can be used in onResume
                }
            }
            
            updateUIState(items.isEmpty()) // Update empty state based on the filtered list
        }

        inventoryViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) { // Check if message is not null or empty
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
            binding.swipeRefreshLayout.isRefreshing = false // Stop refreshing UI on error
        }

        inventoryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show progress bar only if not already refreshing via swipe
            if (!binding.swipeRefreshLayout.isRefreshing) {
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Observe active filters to update chip UI state
        inventoryViewModel.activeFilters.observe(viewLifecycleOwner) { activeFilters ->
            syncChipStates(activeFilters) // Call helper to update chip visuals
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
                        isSearchActive = it.isNotEmpty()
                        inventoryViewModel.searchItems(it)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // Trigger search with debounce in ViewModel
                    isSearchActive = !newText.isNullOrEmpty()
                    inventoryViewModel.searchItems(newText ?: "")
                    return true
                }
            })

            // Handle clearing the search
            setOnCloseListener {
                isSearchActive = false
                inventoryViewModel.searchItems("") // Trigger filter with empty query
                clearFocus()
                onActionViewCollapsed() // Collapse the search view
                true
            }
            // Optional: Handle closing the search view by clicking the 'X' icon inside it
            val closeButton = findViewById<View>(androidx.appcompat.R.id.search_close_btn)
            closeButton?.setOnClickListener {
                setQuery("", false) // Clear the query
                onActionViewCollapsed() // Collapse the search view
                isSearchActive = false
                inventoryViewModel.searchItems("") // Trigger filter with empty query
                clearFocus()
            }
        }
    }

    private fun setupFilterChips() {
        // --- Important: Use a helper function to attach listeners ---
        // This prevents issues when observing activeFilters LiveData later
        attachChipListeners()
    }

    private fun attachChipListeners() {
        binding.chipGold.setOnCheckedChangeListener { _, isChecked ->
            inventoryViewModel.toggleFilter(FILTER_GOLD, isChecked)
        }
        binding.chipSilver.setOnCheckedChangeListener { _, isChecked ->
            inventoryViewModel.toggleFilter(FILTER_SILVER, isChecked)
        }
        binding.chipOther.setOnCheckedChangeListener { _, isChecked ->
            inventoryViewModel.toggleFilter(FILTER_OTHER, isChecked)
        }
        binding.chipLowStock.setOnCheckedChangeListener { _, isChecked ->
            inventoryViewModel.toggleFilter(FILTER_LOW_STOCK, isChecked)
        }
    }

    private fun syncChipStates(activeFilters: Set<String>) {
        // --- Detach listeners temporarily to prevent feedback loops ---
        binding.chipGold.setOnCheckedChangeListener(null)
        binding.chipSilver.setOnCheckedChangeListener(null)
        binding.chipOther.setOnCheckedChangeListener(null)
        binding.chipLowStock.setOnCheckedChangeListener(null)

        // --- Set checked states based on ViewModel ---
        binding.chipGold.isChecked = activeFilters.contains(FILTER_GOLD)
        binding.chipSilver.isChecked = activeFilters.contains(FILTER_SILVER)
        binding.chipOther.isChecked = activeFilters.contains(FILTER_OTHER)
        binding.chipLowStock.isChecked = activeFilters.contains(FILTER_LOW_STOCK)

        // --- Re-attach listeners ---
        attachChipListeners()
    }


    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            inventoryViewModel.refreshData()
        }
    }

    private fun setUpObserver() {
        inventoryViewModel.jewelleryItems.observe(viewLifecycleOwner) { items ->
            binding.swipeRefreshLayout.isRefreshing = false
            adapter.updateList(items) // Update the adapter with filtered/searched list
            updateUIState(items.isEmpty()) // Update empty state based on the filtered list
        }

        inventoryViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
            binding.swipeRefreshLayout.isRefreshing = false
        }

        inventoryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show progress bar only if not refreshing via swipe
            if (!binding.swipeRefreshLayout.isRefreshing) {
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Observe active filters to update chip UI
        inventoryViewModel.activeFilters.observe(viewLifecycleOwner) { activeFilters ->
            syncChipStates(activeFilters)
        }
    }

    private fun updateUIState(isEmpty: Boolean) {
        val hasActiveFilters = inventoryViewModel.activeFilters.value?.isNotEmpty() == true

        if (isEmpty && (isSearchActive || hasActiveFilters)) {
            // No results matching search or filters
            binding.emptySearchLayout.visibility = View.VISIBLE
            binding.recyclerViewInventory.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.GONE
        } else if (isEmpty) {
            // No items in inventory at all
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewInventory.visibility = View.GONE
            binding.emptySearchLayout.visibility = View.GONE
        } else {
            // Show recycler view with items
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
            // Clear search view
            val searchView =
                binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            searchView.setQuery("", false) // Clear query
            searchView.isIconified = true // Collapse the view
            searchView.onActionViewCollapsed(); // Ensure it fully collapses


            // Clear filters in ViewModel
            inventoryViewModel.clearAllFilters() // This should also trigger applyFiltersAndSearch
        }

        // Add item button in the empty search state
        binding.addNewItemButton.setOnClickListener {
            addItemButton()
        }
    }

    private fun setUpRecyclerView() {
        val recyclerView: RecyclerView = binding.recyclerViewInventory
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // Pass 'this' as the click listener
        adapter = JewelleryAdapter(emptyList(), this)
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Load more when user is near the end of the list
                if (inventoryViewModel.isLoading.value == false &&
                    totalItemCount > 0 && // Ensure list is not empty
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 && // Trigger near end
                    firstVisibleItemPosition >= 0
                ) {
                    inventoryViewModel.loadNextPage()
                }
                
                // Implement efficient Coil preloading
                if (dy > 0) { // Scrolling down
                    // Get the image loader from the context
                    val imageLoader = requireContext().imageLoader
                    
                    // Calculate the range of items to preload (5 items ahead)
                    val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                    val preloadPosition = lastVisiblePosition + 1
                    val preloadCount = 5 // Number of items to preload ahead
                    val endPosition = minOf(preloadPosition + preloadCount, adapter.itemCount)
                    
                    // Preload images in the calculated range
                    for (i in preloadPosition until endPosition) {
                        // Get the item data safely using the adapter helper function
                        adapter.getItem(i)?.let { item ->
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
                                    // Instead of priority, use placeholderMemoryCacheKey for caching
                                    .placeholderMemoryCacheKey(item.imageUrl)
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
        
        // Preload initial visible images
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val visibleCount = layoutManager.childCount
            
            // Get the image loader from the context
            val imageLoader = requireContext().imageLoader
            
            // Preload initial visible items
            for (i in firstVisible until (firstVisible + visibleCount)) {
                adapter.getItem(i)?.let { item ->
                    if (item.imageUrl.isNotEmpty()) {
                        val request = ImageRequest.Builder(requireContext())
                            .data(item.imageUrl)
                            .size(JewelleryAdapter.TARGET_WIDTH, JewelleryAdapter.TARGET_HEIGHT)
                            .scale(Scale.FILL)
                            .transformations(CircleCropTransformation())
                            .target(null)
                            // Instead of priority, use placeholderMemoryCacheKey for caching
                            .placeholderMemoryCacheKey(item.imageUrl)
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
        
        // Check inventory count in the background
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                checkInventoryLimitInBackground()
            } catch (e: Exception) {
                Log.e("InventoryFragment", "Error checking inventory limits: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error checking inventory limits: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Checks inventory limits in the background and dismisses the bottom sheet if the limit is reached
     */
    private suspend fun checkInventoryLimitInBackground() {
        try {
            // Get total inventory item count from repository
            val inventoryCount = inventoryViewModel.getTotalInventoryCount()
            
            // Check if inventory limit is reached
            val (isLimitReached, maxLimit) = FeatureChecker.isInventoryLimitReached(requireContext(), inventoryCount)
            
            // If limit is reached, dismiss the bottom sheet and show upgrade dialog
            if (isLimitReached) {
                withContext(Dispatchers.Main) {
                    // Find and dismiss the bottom sheet
                    val bottomSheet = childFragmentManager.findFragmentByTag("ItemBottomSheet") as? ItemBottomSheetFragment
                    bottomSheet?.dismissAllowingStateLoss()
                    
                    // Show upgrade dialog
                    FeatureChecker.showUpgradeDialogForLimit(requireContext(), "inventory items", maxLimit)
                }
            }
        } catch (e: Exception) {
            Log.e("InventoryFragment", "Error in background inventory limit check: ${e.message}", e)
            // Don't dismiss the sheet if there's an error checking, just log it
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
        inventoryViewModel.addJewelleryItem(item)
        // Optionally show a success message
        Toast.makeText(requireContext(), "Item added successfully", Toast.LENGTH_SHORT).show()
    }

    override fun onItemUpdated(item: JewelleryItem) {
        inventoryViewModel.updateJewelleryItem(item)
        // Optionally show a success message
        Toast.makeText(requireContext(), "Item updated successfully", Toast.LENGTH_SHORT).show()
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
    }

    override fun onDestroyView() {
        // Save the RecyclerView scroll position
        binding.recyclerViewInventory.layoutManager?.let { lm ->
            inventoryViewModel.layoutManagerState = lm.onSaveInstanceState()
        }
        
        // Cancel any ongoing search job to prevent leaks
        inventoryViewModel.searchItems("") // Clear search on destroy
        inventoryViewModel.searchJob?.cancel()
        super.onDestroyView()
        _binding = null
    }
    
    // Add onResume method to handle restoring state when returning to the fragment
    override fun onResume() {
        super.onResume()
        
        // Restore scroll position if we have a saved state and adapter has items
        if (inventoryViewModel.layoutManagerState != null && adapter.itemCount > 0) {
            binding.recyclerViewInventory.layoutManager?.onRestoreInstanceState(inventoryViewModel.layoutManagerState)
        }
    }
}