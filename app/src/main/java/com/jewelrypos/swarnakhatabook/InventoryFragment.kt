package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.JewelleryAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Factorys.InventoryViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInventoryBinding

class InventoryFragment : Fragment(), ItemBottomSheetFragment.OnItemAddedListener,
    JewelleryAdapter.OnItemClickListener {


    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    private val inventoryViewModel: InventoryViewModel by viewModels {
        // Create the repository and pass it to the factory
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        InventoryViewModelFactory(repository, connectivityManager)
    }
    private lateinit var adapter: JewelleryAdapter

    // Add this as a class property


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)

        binding.addItemFab.setOnClickListener {
            addItemButton()
        }



        setUpRecyclerView()
        // Call setupMenu() before observers to ensure searchView is initialized
        setupSearchView()
        setupFilterMenu()
        setUpObserver()
        setupSwipeRefresh()
        setupEmptyStateButtons()
        // Inflate the layout for this fragment
        return binding.root
    }


    private fun setupSearchView() {
        with(binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView) {
            queryHint = "Search Jewellery Items..."
            inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setOnQueryTextListener(object :
                androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        Log.d("InventoryViewModel", "Search submitted: $it")
                        inventoryViewModel.searchItems(it)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let { inventoryViewModel.searchItems(it) }
                    return true
                }
            })
            setOnCloseListener {
                val seachVie = binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
                onActionViewCollapsed()
                seachVie.setQuery("", false)
                seachVie.clearFocus()
                inventoryViewModel.searchItems("")
                true
            }
        }
    }

// Add this to your onCreateView or after setupSearchView()
    private fun setupFilterMenu() {
        binding.topAppBar.menu.findItem(R.id.action_filter).setOnMenuItemClickListener { menuItem ->
            showFilterPopup(menuItem.actionView ?: binding.topAppBar)
            true
        }
    }

    private fun showFilterPopup(view: View) {
        val leftAnchorView = binding.topAppBar.menu.findItem(R.id.action_filter) ?: binding.topAppBar

        val popup = PopupMenu(requireContext(), leftAnchorView)
        popup.menuInflater.inflate(R.menu.filter_menu, popup.menu)




        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            popup.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }



        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.filter_gold -> {
                    inventoryViewModel.filterByType("gold")
                    true
                }
                R.id.filter_silver -> {
                    inventoryViewModel.filterByType("silver")
                    true
                }
                R.id.filter_other -> {
                    inventoryViewModel.filterByType("other")
                    true
                }
                R.id.filter_all -> {
                    inventoryViewModel.filterByType(null)
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            inventoryViewModel.refreshData() // Call a refresh function in your ViewModel
        }
    }

    private fun setUpObserver() {

        inventoryViewModel.jewelleryItems.observe(viewLifecycleOwner) { items ->
            binding.swipeRefreshLayout.isRefreshing = false // Stop refreshing
            adapter.updateList(items)
            val searchVie =
                binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            val isSearchActive = searchVie.query?.isNotEmpty() == true


            if (items.isEmpty() && isSearchActive) {
                binding.emptySearchLayout.visibility = View.VISIBLE
                binding.recyclerViewInventory.visibility = View.GONE
            } else {
                binding.emptySearchLayout.visibility = View.GONE
                binding.recyclerViewInventory.visibility = View.VISIBLE
            }
        }
        inventoryViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage != null) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
            binding.swipeRefreshLayout.isRefreshing = false // Stop refreshing
        }
        inventoryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        inventoryViewModel.activeFilter.observe(viewLifecycleOwner) { filterType ->
            // Update UI to show active filter
            val filterMenuItem = binding.topAppBar.menu.findItem(R.id.action_filter)
            if (filterType != null) {
                filterMenuItem.setIcon(R.drawable.ic_filter_active) // Use a different icon when filter is active
            } else {
                filterMenuItem.setIcon(R.drawable.ic_filter)
            }
        }
    }


    private fun setupEmptyStateButtons() {
        // Clear filter button
        binding.clearFilterButton.setOnClickListener {
            val searchVie =
                binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            searchVie.clearFocus()
            searchVie.setQuery("", false)
            inventoryViewModel.searchItems("")
        }

        // Add new item button
        binding.addNewItemButton.setOnClickListener {
            // Open the add item bottom sheet
            showBottomSheetDialog()
        }
    }


    private fun setUpRecyclerView() {

        val recyclerView: RecyclerView = binding.recyclerViewInventory
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = JewelleryAdapter(emptyList(), this) // Initialize with an empty list
        recyclerView.adapter = adapter


        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Load more when user is near the end of the list
                if (!inventoryViewModel.isLoading.value!! &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 12 && // Load earlier
                    firstVisibleItemPosition >= 0
                ) {
                    inventoryViewModel.loadNextPage()
                }

            }
        })

    }

    private fun addItemButton() {
        // Clear any active search when adding new items for better context
        inventoryViewModel.searchItems("")
        //make a bottom sheet that appear and show inputs
        showBottomSheetDialog()
    }

    override fun onItemAdded(item: JewelleryItem) {
        inventoryViewModel.addJewelleryItem(item)
    }

    private fun showBottomSheetDialog() {
        val bottomSheetFragment = ItemBottomSheetFragment.newInstance()
        bottomSheetFragment.setOnItemAddedListener(this)
        bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
    }

    override fun onItemClick(item: JewelleryItem) {
        // Open ItemBottomSheetFragment with item data for editing
        showBottomSheetDialog(item)
    }

    // Inside your InventoryFragment class:
    private fun showBottomSheetDialog(item: JewelleryItem? = null) {
        val bottomSheetFragment = ItemBottomSheetFragment.newInstance()
        bottomSheetFragment.setOnItemAddedListener(this)

        // If item is not null, we're in edit mode
        if (item != null) {
            bottomSheetFragment.setItemForEdit(item)
        }

        bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
    }

    // Add method for handling updates
    override fun onItemUpdated(item: JewelleryItem) {
        inventoryViewModel.updateJewelleryItem(item)
    }

}