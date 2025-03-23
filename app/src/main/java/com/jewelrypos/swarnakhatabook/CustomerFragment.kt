package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.CustomerAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
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
            showFilterDialog()
            true
        }
    }

    private fun showFilterDialog() {
        // Create the dialog using ThemedM3Dialog (your custom dialog class)
        val filterDialog = ThemedM3Dialog(requireContext())
            .setTitle("Filter Customers")
            .setLayout(R.layout.dialog_customer_filter)
            .setPositiveButton("Apply") { dialog, dialogView ->
                // Handle Apply button click
                applyFilters(dialogView)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog ->
                dialog.dismiss()
            }

        // Get the dialog view before showing
        val dialogView = filterDialog.getDialogView()

        // Set up the Clear Filters button
        dialogView?.findViewById<Button>(R.id.btnClearFilters)?.setOnClickListener {
            clearFilters(dialogView)
        }

        // Pre-select the current filter options (if any)
        setupCurrentFilters(dialogView)

        // Show the dialog
        filterDialog.show()
    }

    private fun setupCurrentFilters(dialogView: View?) {
        dialogView?.let { view ->
            // Get reference to radio groups
            val customerTypeGroup = view.findViewById<RadioGroup>(R.id.customerTypeGroup)
            val sortOrderGroup = view.findViewById<RadioGroup>(R.id.sortOrderGroup)
            val paymentStatusGroup = view.findViewById<RadioGroup>(R.id.paymentStatusGroup)

            // Set selected options based on current filters in ViewModel
            when (customerViewModel.activeCustomerType.value) {
                "Wholesaler" -> customerTypeGroup.check(R.id.rbWholeseller)
                "Consumer" -> customerTypeGroup.check(R.id.rbConsumer)
                else -> customerTypeGroup.clearCheck()
            }

            // Set sort order selection
            when (customerViewModel.activeSortOrder.value) {
                "ASC" -> sortOrderGroup.check(R.id.rbAscending)
                "DESC" -> sortOrderGroup.check(R.id.rbDescending)
                else -> sortOrderGroup.check(R.id.rbAscending) // Default to ascending
            }

            // Set payment status selection
            when (customerViewModel.activePaymentStatus.value) {
                "Debit" -> paymentStatusGroup.check(R.id.rbToPay)
                "Credit" -> paymentStatusGroup.check(R.id.rbToReceive)
                else -> paymentStatusGroup.clearCheck()
            }
        }
    }

    private fun applyFilters(dialogView: View?) {
        dialogView?.let { view ->
            // Get selected customer type
            val customerTypeGroup = view.findViewById<RadioGroup>(R.id.customerTypeGroup)
            val customerType = when (customerTypeGroup.checkedRadioButtonId) {
                R.id.rbWholeseller -> "Wholesaler"
                R.id.rbConsumer -> "Consumer"
                else -> null
            }

            // Get selected sort order
            val sortOrderGroup = view.findViewById<RadioGroup>(R.id.sortOrderGroup)
            val sortOrder = when (sortOrderGroup.checkedRadioButtonId) {
                R.id.rbAscending -> "ASC"
                R.id.rbDescending -> "DESC"
                else -> "ASC" // Default
            }

            // Get selected payment status
            val paymentStatusGroup = view.findViewById<RadioGroup>(R.id.paymentStatusGroup)
            val paymentStatus = when (paymentStatusGroup.checkedRadioButtonId) {
                R.id.rbToPay -> "Debit"
                R.id.rbToReceive -> "Credit"
                else -> null
            }

            // Apply filters through ViewModel
            customerViewModel.applyFilters(customerType, sortOrder, paymentStatus)
        }
    }

    private fun clearFilters(dialogView: View?) {
        dialogView?.let { view ->
            // Clear all selections
            view.findViewById<RadioGroup>(R.id.customerTypeGroup).clearCheck()
            view.findViewById<RadioGroup>(R.id.sortOrderGroup).check(R.id.rbAscending) // Default
            view.findViewById<RadioGroup>(R.id.paymentStatusGroup).clearCheck()
        }
    }
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
        // Navigate to the customer details screen
        val navController = requireActivity().findNavController(R.id.nav_host_fragment)
        val action = MainScreenFragmentDirections.actionMainScreenFragmentToCustomerDetailFragment(customer.id)
        navController.navigate(action)


//        showCustomerBottomSheet(customer)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}