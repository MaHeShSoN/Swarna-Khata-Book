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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.navGraphViewModels
import androidx.paging.LoadState
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
import com.jewelrypos.swarnakhatabook.Utilitys.AnimationUtils
import com.jewelrypos.swarnakhatabook.Utilitys.FeatureChecker
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentCustomerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerFragment : Fragment(), CustomerBottomSheetFragment.CustomerOperationListener,
    CustomerAdapter.OnCustomerClickListener {

    private var _binding: FragmentCustomerBinding? = null
    private val binding get() = _binding!!

    private val customerViewModel: CustomerViewModel by navGraphViewModels(R.id.inner_nav_graph) {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth,requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager,requireContext())
    }
    private lateinit var adapter: CustomerAdapter

    private var scrollToTopAfterAdd = false
    private var isLayoutStateRestored = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isLayoutStateRestored = false
        scrollToTopAfterAdd = false
        Log.d("CustomerFragment", "onViewCreated: Resetting flags.")

        binding.addCustomerFab.setOnClickListener {
            AnimationUtils.pulse(it)
            addCustomerButton()
        }

        setupRecyclerViewAndAdapter()
        setupSearchView()
        setupFilterChips()
        setupObservers()
        setupSwipeRefresh()
        setupEmptyStateButtons()
        setupEventBusObservers()
    }

    private fun setupEventBusObservers() {
        EventBus.customerAddedEvent.observe(viewLifecycleOwner) { added ->
            if (added) {
                Log.d("CustomerFragment", "Customer add event received, refreshing PagingData.")
                adapter.refresh()
                EventBus.resetCustomerAddedEvent()
            }
        }

        EventBus.customerUpdatedEvent.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                Log.d("CustomerFragment", "Customer update event received, refreshing PagingData.")
                adapter.refresh()
                EventBus.resetCustomerUpdatedEvent()
            }
        }

        EventBus.customerDeletedEvent.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                Log.d("CustomerFragment", "Customer delete event received, refreshing PagingData.")
                adapter.refresh()
                EventBus.resetCustomerDeletedEvent()
                Toast.makeText(context, "Customer removed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSearchView() {
        val searchView = binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
        with(searchView) {
            queryHint = "Search customers..."
            inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    Log.d("CustomerFragment", "Search submitted with query: ${query ?: ""}")
                    customerViewModel.searchCustomers(query ?: "")
                    searchView.clearFocus()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    Log.d("CustomerFragment", "Search text changed: ${newText ?: ""}")
                    customerViewModel.searchCustomers(newText ?: "")
                    return true
                }
            })
            setOnCloseListener {
                Log.d("CustomerFragment", "Search view closed")
                customerViewModel.searchCustomers("")
                true
            }
        }
    }

    private fun setupFilterChips() {
        customerViewModel.activeCustomerType.observe(viewLifecycleOwner) { activeType ->
            binding.filterChipGroup.setOnCheckedStateChangeListener(null)
            binding.chipConsumer.isChecked = activeType == CustomerViewModel.FILTER_CONSUMER
            binding.chipWholeseller.isChecked = activeType == CustomerViewModel.FILTER_WHOLESALER
            binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                updateFilterFromChips(checkedIds)
            }
        }
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            updateFilterFromChips(checkedIds)
        }
    }

    private fun updateFilterFromChips(checkedIds: List<Int>) {
        val selectedType = when (checkedIds.firstOrNull()) {
            R.id.chipConsumer -> CustomerViewModel.FILTER_CONSUMER
            R.id.chipWholeseller -> CustomerViewModel.FILTER_WHOLESALER
            else -> null
        }
        Log.d("CustomerFragment", "Filter chip changed. Selected type: $selectedType")
        customerViewModel.applyCustomerTypeFilter(selectedType)
    }

    private fun syncChipStates(activeType: String?) {
        binding.filterChipGroup.setOnCheckedStateChangeListener(null)

        binding.chipConsumer.isChecked = activeType == CustomerViewModel.FILTER_CONSUMER
        binding.chipWholeseller.isChecked = activeType == CustomerViewModel.FILTER_WHOLESALER

        setupFilterChips()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d("CustomerFragment", "Swipe to refresh triggered.")
            adapter.refresh()
        }
    }

    private fun setupObservers() {
        Log.d("CustomerFragment", "setupObservers: Method called")

        viewLifecycleOwner.lifecycleScope.launch {
            customerViewModel.pagedCustomers.collectLatest { pagingData ->
                Log.d("CustomerFragment", "New PagingData received, submitting to adapter.")
                adapter.submitData(pagingData)

                Log.d("CustomerFragment", "PagingData submitted. Scroll to top will be handled by LoadState observer if needed.")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                Log.d("CustomerFragment", "Load states updated: $loadStates")
                _binding?.let { currentBinding ->
                    val refreshState = loadStates.refresh
                    val isListEmpty = adapter.itemCount == 0
                    Log.d("CustomerFragment", "Is list empty: $isListEmpty")

                    currentBinding.swipeRefreshLayout.isRefreshing = refreshState is LoadState.Loading

                    when (refreshState) {
                        is LoadState.Loading -> {
                            currentBinding.progressBar.visibility = View.VISIBLE
                            currentBinding.recyclerViewCustomers.visibility = View.GONE
                            currentBinding.emptyStateLayout.visibility = View.GONE
                            currentBinding.emptySearchLayout.visibility = View.GONE
                            Log.d("CustomerFragment", "LoadState: Loading")
                        }
                        is LoadState.Error -> {
                            currentBinding.progressBar.visibility = View.GONE
                            currentBinding.swipeRefreshLayout.isRefreshing = false
                            currentBinding.recyclerViewCustomers.visibility = View.GONE
                            currentBinding.emptyStateLayout.visibility = View.GONE
                            currentBinding.emptySearchLayout.visibility = View.GONE

                            val errorMessage = refreshState.error.localizedMessage ?: "Unknown error loading customers"
                            Toast.makeText(requireContext(), "Error loading customers: $errorMessage", Toast.LENGTH_LONG).show()
                            Log.e("CustomerFragment", "Paging refresh error: $errorMessage")
                        }
                        is LoadState.NotLoading -> {
                            currentBinding.progressBar.visibility = View.GONE
                            currentBinding.swipeRefreshLayout.isRefreshing = false
                             Log.d("CustomerFragment", "LoadState NotLoading, checking item count: ${adapter.itemCount}")

                            val currentSearchQuery = customerViewModel.searchQuery.value
                            val currentFilterType = customerViewModel.activeCustomerType.value

                            if (isListEmpty) {
                                if (currentSearchQuery.isNotEmpty() || currentFilterType != null) {
                                    Log.d("CustomerFragment", "List is empty, showing empty search layout (filters/search active).")
                                    currentBinding.emptySearchLayout.visibility = View.VISIBLE
                                    currentBinding.recyclerViewCustomers.visibility = View.GONE
                                    currentBinding.emptyStateLayout.visibility = View.GONE
                                } else {
                                    Log.d("CustomerFragment", "List is empty, showing default empty state.")
                                    currentBinding.emptyStateLayout.visibility = View.VISIBLE
                                    currentBinding.recyclerViewCustomers.visibility = View.GONE
                                    currentBinding.emptySearchLayout.visibility = View.GONE
                                }
                            } else {
                                Log.d("CustomerFragment", "List is not empty, showing RecyclerView.")
                                currentBinding.recyclerViewCustomers.visibility = View.VISIBLE
                                currentBinding.emptyStateLayout.visibility = View.GONE
                                currentBinding.emptySearchLayout.visibility = View.GONE

                                if (!isLayoutStateRestored && customerViewModel.layoutManagerState != null) {
                                    currentBinding.recyclerViewCustomers.post {
                                        Log.d("CustomerFragment", "Posting layout manager state restoration.")
                                        if (_binding != null && !isLayoutStateRestored && customerViewModel.layoutManagerState != null) {
                                            Log.d("CustomerFragment", "Restoring layout manager state inside post block.")
                                            _binding?.recyclerViewCustomers?.layoutManager?.onRestoreInstanceState(customerViewModel.layoutManagerState)
                                            isLayoutStateRestored = true
                                            Log.d("CustomerFragment", "Set isLayoutStateRestored to true after posting restoration.")
                                        } else {
                                            Log.d("CustomerFragment", "Skipping state restoration inside post block (inner conditions not met).")
                                        }
                                    }
                                } else {
                                     Log.d("CustomerFragment", "Skipping immediate state restoration check (outer conditions not met: isLayoutStateRestored=${isLayoutStateRestored}, layoutManagerState=${customerViewModel.layoutManagerState != null}).")
                                }

                                if (scrollToTopAfterAdd) {
                                     currentBinding.recyclerViewCustomers.post {
                                         Log.d("CustomerFragment", "Posting scroll to top after customer add.")
                                         if (_binding != null && scrollToTopAfterAdd && adapter.itemCount > 0) {
                                            _binding?.recyclerViewCustomers?.smoothScrollToPosition(0)
                                            Log.d("CustomerFragment", "Smooth scrolling to position 0 after customer add.")
                                            scrollToTopAfterAdd = false
                                            Log.d("CustomerFragment", "Reset scrollToTopAfterAdd flag.")
                                         } else {
                                            Log.d("CustomerFragment", "Skipping scroll to top after add (conditions not met).")
                                         }
                                     }
                                }
                            }
                        }
                    }
                }
            }
        }

        customerViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Log.e("CustomerFragment", "Action Error message: $errorMessage")
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                customerViewModel.clearErrorMessage()
            }
        }

        customerViewModel.isActionLoading.observe(viewLifecycleOwner) { isLoading ->
             _binding?.let { currentBinding ->
                if (isLoading && !currentBinding.swipeRefreshLayout.isRefreshing && currentBinding.progressBar.visibility == View.GONE) {
                    // Show a specific action progress dialog or indicator if needed
                }
            }
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
            customerViewModel.clearAllFilters()
        }
        binding.addNewCustomerButton.setOnClickListener {
            addCustomerButton()
        }
    }

    private fun setupRecyclerViewAndAdapter() {
        adapter = CustomerAdapter(this)
        binding.recyclerViewCustomers.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCustomers.adapter = adapter
    }

    private fun addCustomerButton() {
        showCustomerBottomSheet()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                checkCustomerLimitInBackground()
            } catch (e: Exception) {
                Log.e("CustomerFragment", "Error checking customer limits: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error checking customer limits: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun checkCustomerLimitInBackground() {
        try {
            val customerCount = customerViewModel.getTotalCustomerCount()
            
            val (isLimitReached, maxLimit) = FeatureChecker.isCustomerLimitReached(requireContext(), customerCount)
            
            if (isLimitReached) {
                withContext(Dispatchers.Main) {
                    val bottomSheet = parentFragmentManager.findFragmentByTag(CustomerBottomSheetFragment.TAG) as? CustomerBottomSheetFragment
                    bottomSheet?.dismissAllowingStateLoss()
                    
                    FeatureChecker.showUpgradeDialogForLimit(requireContext(), "customers", maxLimit)
                }
            }
        } catch (e: Exception) {
            Log.e("CustomerFragment", "Error in background customer limit check: ${e.message}", e)
        }
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

    override fun onCustomerAdded(customer: Customer) {
        Log.d("CustomerFragment", "onCustomerAdded: Starting customer addition process")
        customerViewModel.addCustomer(customer).observe(viewLifecycleOwner) { result ->
            Log.d("CustomerFragment", "onCustomerAdded: Received result from ViewModel")
            if (result.isSuccess) {
                EventBus.postCustomerAdded()
                Log.d("CustomerFragment", "onCustomerAdded: Customer added successfully, posting event.")

                scrollToTopAfterAdd = true
                Log.d("CustomerFragment", "Set scrollToTopAfterAdd flag to true.")

                Toast.makeText(requireContext(), "${customer.firstName} added successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Error adding customer: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                Log.e("CustomerFragment", "onCustomerAdded: Error adding customer via ViewModel", result.exceptionOrNull())
                scrollToTopAfterAdd = false
                Log.d("CustomerFragment", "Customer add failed, ensuring scrollToTopAfterAdd flag is false.")
            }
        }
    }

    override fun onCustomerUpdated(customer: Customer) {
        Log.d("CustomerFragment", "onCustomerUpdated: Starting customer update process for ${customer.firstName}")
        customerViewModel.updateCustomer(customer).observe(viewLifecycleOwner) { result ->
            if (result.isSuccess) {
                Log.d("CustomerFragment", "onCustomerUpdated: Customer update successful via ViewModel, posting event.")
                EventBus.postCustomerUpdated()
                Toast.makeText(requireContext(), "${customer.firstName} updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error updating customer"
                Log.e("CustomerFragment", "onCustomerUpdated: Error updating customer via ViewModel: $errorMessage")
                Toast.makeText(requireContext(), "Error updating ${customer.firstName}: $errorMessage", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCustomerClick(customer: Customer) {
        val navController = requireActivity().findNavController(R.id.nav_host_fragment)
        val action = MainScreenFragmentDirections.actionMainScreenFragmentToCustomerDetailFragment(customer.id)
        navController.navigate(action)
    }

    override fun onDestroyView() {
        if (_binding?.recyclerViewCustomers?.adapter?.itemCount ?: 0 > 0) {
             binding.recyclerViewCustomers.layoutManager?.let { lm ->
                 customerViewModel.layoutManagerState = lm.onSaveInstanceState()
                 Log.d("CustomerFragment", "Saved layout manager state in onDestroyView (list not empty). State: ${customerViewModel.layoutManagerState}")
             }
        } else {
             if (customerViewModel.layoutManagerState == null) {
                 Log.d("CustomerFragment", "List is empty in onDestroyView, and ViewModel state is null. Clearing saved state.")
                 customerViewModel.layoutManagerState = null
             } else {
                 Log.d("CustomerFragment", "List is empty in onDestroyView, but ViewModel state is NOT null. Keeping previously saved state.")
             }
        }

        super.onDestroyView()
        _binding = null
        Log.d("CustomerFragment", "onDestroyView: binding set to null.")
    }

    override fun onResume() {
        super.onResume()
        Log.d("CustomerFragment", "onResume called.")
    }
}