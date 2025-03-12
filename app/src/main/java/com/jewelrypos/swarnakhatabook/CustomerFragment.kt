package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.CustomerAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentCustomerBinding

class CustomerFragment : Fragment(), CustomerBottomSheetFragment.CustomerOperationListener,
    CustomerAdapter.OnCustomerClickListener {

    private var _binding: FragmentCustomerBinding? = null
    private val binding get() = _binding!!

    private val customerViewModel: CustomerViewModel by viewModels {
        // Create the repository and pass it to the factory
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager)
    }
    private lateinit var adapter: CustomerAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerBinding.inflate(inflater, container, false)

        binding.addCustomerFab.setOnClickListener {
            addCustomerButton()
        }

        setupRecyclerView()
        setupSearchView()
        setupFilterMenu()
        setupObservers()
        setupSwipeRefresh()
        setupEmptyStateButtons()

        return binding.root
    }

    private fun setupSearchView() {
        with(binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView) {
            queryHint = "Search customers..."
            inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setOnQueryTextListener(object :
                androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        customerViewModel.searchCustomers(it)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let { customerViewModel.searchCustomers(it) }
                    return true
                }
            })
            setOnCloseListener {
                val searchView =
                    binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
                onActionViewCollapsed()
                searchView.setQuery("", false)
                searchView.clearFocus()
                customerViewModel.searchCustomers("")
                true
            }
        }
    }

    private fun setupFilterMenu() {
        binding.topAppBar.menu.findItem(R.id.action_filter).setOnMenuItemClickListener { menuItem ->
//            showFilterPopup(menuItem.actionView ?: binding.topAppBar)
            true
        }
    }

//    private fun showFilterPopup(view: View) {
//        val filterItem = binding.topAppBar.menu.findItem(R.id.action_filter) ?: binding.topAppBar
//
//        val popup = PopupMenu(requireContext(), filterItem)
//        popup.menuInflater.inflate(R.menu.customer_filter_menu, popup.menu)
//
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//            popup.gravity = android.view.Gravity.TOP or android.view.Gravity.START
//        }
//
//        popup.setOnMenuItemClickListener { item ->
//            when (item.itemId) {
//                R.id.filter_consumer -> {
//                    customerViewModel.filterByType("Consumer")
//                    true
//                }
//                R.id.filter_wholesaler -> {
//                    customerViewModel.filterByType("Wholesaler")
//                    true
//                }
//                R.id.filter_all -> {
//                    customerViewModel.filterByType(null)
//                    true
//                }
//                else -> false
//            }
//        }
//
//        popup.show()
//    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            customerViewModel.refreshData()
        }
    }

    private fun setupObservers() {
        customerViewModel.customers.observe(viewLifecycleOwner) { customers ->
            binding.swipeRefreshLayout.isRefreshing = false
            adapter.updateList(customers)
            val searchView =
                binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            val isSearchActive = searchView.query?.isNotEmpty() == true

            if (customers.isEmpty() && isSearchActive) {
                binding.emptySearchLayout.visibility = View.VISIBLE
                binding.recyclerViewCustomers.visibility = View.GONE
            } else if (customers.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.recyclerViewCustomers.visibility = View.GONE
                binding.emptySearchLayout.visibility = View.GONE
            } else {
                binding.emptySearchLayout.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.GONE
                binding.recyclerViewCustomers.visibility = View.VISIBLE
            }
        }

        customerViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage != null) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
            binding.swipeRefreshLayout.isRefreshing = false
        }

        customerViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        customerViewModel.activeFilter.observe(viewLifecycleOwner) { filterType ->
            val filterMenuItem = binding.topAppBar.menu.findItem(R.id.action_filter)
            if (filterType != null) {
                filterMenuItem.setIcon(R.drawable.ic_filter_active)
            } else {
                filterMenuItem.setIcon(R.drawable.ic_filter)
            }
        }
    }

    private fun setupEmptyStateButtons() {
        binding.addNewCustomerEmptyButton.setOnClickListener {
            addCustomerButton()
        }

        binding.clearFilterButton.setOnClickListener {
            val searchView =
                binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            searchView.clearFocus()
            searchView.setQuery("", false)
            customerViewModel.searchCustomers("")
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

                // Load more when user is near the end of the list
                if (!customerViewModel.isLoading.value!! &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 &&
                    firstVisibleItemPosition >= 0
                ) {
                    customerViewModel.loadNextPage()
                }
            }
        })
    }

    private fun addCustomerButton() {
        // Clear any active search when adding new customers for better context
        customerViewModel.searchCustomers("")
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

    override fun onCustomerAdded(customer: Customer) {
        customerViewModel.addCustomer(customer)
    }

    override fun onCustomerUpdated(customer: Customer) {
        customerViewModel.updateCustomer(customer)
    }

    override fun onCustomerClick(customer: Customer) {
        showCustomerBottomSheet(customer)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}