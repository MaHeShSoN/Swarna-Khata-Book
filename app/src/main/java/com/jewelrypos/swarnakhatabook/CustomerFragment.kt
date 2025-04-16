package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.CustomerAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentCustomerBinding

class CustomerFragment : Fragment(), CustomerBottomSheetFragment.CustomerOperationListener,
    CustomerAdapter.OnCustomerClickListener {

    private var _binding: FragmentCustomerBinding? = null
    private val binding get() = _binding!!

    private val customerViewModel: CustomerViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth,requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager)
    }
    private lateinit var adapter: CustomerAdapter
    private var isSearchActive = false // Keep track of search state

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.addCustomerFab.setOnClickListener {
            addCustomerButton()
        }

        setupRecyclerView()
        setupSearchView()
        setupFilterChips() // Add this line
        setupObservers()
        setupSwipeRefresh()
        setupEmptyStateButtons()
        setupEventBusObservers()
    }


    private fun setupEventBusObservers() {
        EventBus.customerAddedEvent.observe(viewLifecycleOwner) { added ->
            if (added) {
                customerViewModel.refreshData() // Refresh the list
                EventBus.resetCustomerAddedEvent() // Reset the event
                Log.d("CustomerFragment", "Customer add event received, refreshing data.")
            }
        }

        EventBus.customerUpdatedEvent.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                customerViewModel.refreshData() // Refresh the list
                EventBus.resetCustomerUpdatedEvent() // Reset the event
                Log.d("CustomerFragment", "Customer update event received, refreshing data.")
            }
        }

        EventBus.customerDeletedEvent.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                customerViewModel.refreshData() // Refresh the list
                EventBus.resetCustomerDeletedEvent() // Reset the event
                Log.d("CustomerFragment", "Customer delete event received, refreshing data.")
                Toast.makeText(context, "Customer removed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSearchView() {
        val searchView = binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
        with(searchView) {
            queryHint = "Search customers..."
            inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setOnQueryTextListener(object :
                androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        isSearchActive = it.isNotEmpty()
                        customerViewModel.searchCustomers(it)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    isSearchActive = !newText.isNullOrEmpty()
                    customerViewModel.searchCustomers(newText ?: "")
                    return true
                }
            })
            setOnCloseListener {
                isSearchActive = false
                customerViewModel.searchCustomers("")
                clearFocus()
                onActionViewCollapsed()
                true
            }
            val closeButton = findViewById<View>(androidx.appcompat.R.id.search_close_btn)
            closeButton?.setOnClickListener {
                setQuery("", false)
                onActionViewCollapsed()
                isSearchActive = false
                customerViewModel.searchCustomers("")
                clearFocus()
            }
        }
    }

    // --- Add this function ---
    private fun setupFilterChips() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            // Since singleSelection=true, checkedIds will contain at most one ID
            val selectedType = when (checkedIds.firstOrNull()) {
                R.id.chipConsumer -> CustomerViewModel.FILTER_CONSUMER
                R.id.chipWholeseller -> CustomerViewModel.FILTER_WHOLESALER
                else -> null // No type chip is selected
            }
            // Apply the filter using the main applyFilters function
            customerViewModel.applyFilters(customerType = selectedType)
        }
    }
    // -----------------------

    // --- Add this function ---
    private fun syncChipStates(activeType: String?) {
        // Temporarily remove listener to prevent loop
        binding.filterChipGroup.setOnCheckedStateChangeListener(null)

        binding.chipConsumer.isChecked = activeType == CustomerViewModel.FILTER_CONSUMER
        binding.chipWholeseller.isChecked = activeType == CustomerViewModel.FILTER_WHOLESALER

        // Re-attach listener
        setupFilterChips()
    }
    // -----------------------



    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            customerViewModel.refreshData()
        }
    }

    private fun setupObservers() {
        customerViewModel.customers.observe(viewLifecycleOwner) { customers ->
            binding.swipeRefreshLayout.isRefreshing = false
            adapter.updateList(customers)
            updateUIState(customers.isEmpty()) // Update empty state based on filtered list
        }

        customerViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) { // Check if message is not null or empty
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                customerViewModel.clearErrorMessage() // Clear error after showing
            }
            binding.swipeRefreshLayout.isRefreshing = false
        }

        customerViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!binding.swipeRefreshLayout.isRefreshing) { // Don't show linear progress if swipe refresh is active
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        // Observe activeCustomerType to sync chip states
        customerViewModel.activeCustomerType.observe(viewLifecycleOwner) { activeType ->
            syncChipStates(activeType)
        }

        // Observe the combined filter active state

    }

    private fun updateUIState(isEmpty: Boolean) {

        if (isEmpty && (isSearchActive)) {
            binding.emptySearchLayout.visibility = View.VISIBLE
            binding.recyclerViewCustomers.visibility = View.GONE
            binding.emptyStateLayout.visibility = View.GONE
        } else if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewCustomers.visibility = View.GONE
            binding.emptySearchLayout.visibility = View.GONE
        } else {
            binding.recyclerViewCustomers.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
            binding.emptySearchLayout.visibility = View.GONE
        }
    }


    private fun setupEmptyStateButtons() {
        binding.addNewCustomerEmptyButton.setOnClickListener {
            addCustomerButton()
        }
        binding.clearFilterButton.setOnClickListener {
            val searchView = binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            searchView.setQuery("", false)
            searchView.isIconified = true
            searchView.onActionViewCollapsed();
            customerViewModel.clearAllFilters() // Use the ViewModel's clear function
        }
        binding.addNewCustomerButton.setOnClickListener {
            addCustomerButton()
        }
    }

    private fun setupRecyclerView() {
        val recyclerView: RecyclerView = binding.recyclerViewCustomers
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = CustomerAdapter(emptyList(), this)
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (customerViewModel.isLoading.value == false &&
                    totalItemCount > 0 &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 &&
                    firstVisibleItemPosition >= 0)
                {
                    customerViewModel.loadNextPage()
                }
            }
        })
    }

    private fun addCustomerButton() {
        showCustomerBottomSheet()
    }

    private fun showCustomerBottomSheet(customer: Customer? = null) {
        val bottomSheet = if (customer == null) {
            CustomerBottomSheetFragment.newInstance()
        } else {
            CustomerBottomSheetFragment.newInstance(customer)
        }
        bottomSheet.setCustomerOperationListener(this)
        bottomSheet.show(parentFragmentManager, CustomerBottomSheetFragment.TAG)
    }

    // --- CustomerOperationListener Implementation ---
    override fun onCustomerAdded(customer: Customer) {
        // Call the ViewModel to add the customer
        customerViewModel.addCustomer(customer).observe(viewLifecycleOwner) { result ->
            if (result.isSuccess) {
                // Post the event instead of directly refreshing here
                EventBus.postCustomerAdded()
                Log.d("CustomerFragment", "Posted customer added event.")
                Toast.makeText(requireContext(), "Client added successfully", Toast.LENGTH_SHORT).show()
                // The EventBus observer below will handle the refresh
            } else {
                // Handle error (ViewModel should update errorMessage LiveData)
                Log.e("CustomerFragment", "Error adding customer via ViewModel", result.exceptionOrNull())
            }
        }
    }

    override fun onCustomerUpdated(customer: Customer) {
        customerViewModel.updateCustomer(customer)
    }

    // --- CustomerAdapter.OnCustomerClickListener Implementation ---
    override fun onCustomerClick(customer: Customer) {
        val navController = requireActivity().findNavController(R.id.nav_host_fragment)
        val action = MainScreenFragmentDirections.actionMainScreenFragmentToCustomerDetailFragment(customer.id)
        navController.navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}