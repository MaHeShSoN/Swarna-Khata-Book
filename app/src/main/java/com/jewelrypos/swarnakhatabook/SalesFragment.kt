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
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.InvoicesAdapter
import com.jewelrypos.swarnakhatabook.Enums.DateFilterType
import com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.SalesViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentSalesBinding

class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    private val salesViewModel: SalesViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager)
    }

    private lateinit var adapter: InvoicesAdapter
    private var isSearchActive = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setUpClickListner()
        setupDateFilterChips()
        setupStatusFilterChip()

        EventBus.invoiceAddedEvent.observe(viewLifecycleOwner) { added ->
            if (added) {
                // Refresh the invoices list
                salesViewModel.refreshInvoices()
            }
        }

        EventBus.invoiceDeletedEvent.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                // Refresh the invoices list
                salesViewModel.refreshInvoices()
            }
        }

        EventBus.invoiceUpdatedEvent.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                // Refresh the invoices list
                salesViewModel.refreshInvoices()
            }
        }


    }

    private fun syncFiltersWithViewModel() {
        // Update date filter chips
        val chip = when (salesViewModel.getCurrentDateFilter()) {
            DateFilterType.TODAY -> binding.chipToday
            DateFilterType.YESTERDAY -> binding.chipYesterday
            DateFilterType.THIS_WEEK -> binding.chipThisWeek
            DateFilterType.THIS_MONTH -> binding.chipThisMonth
            DateFilterType.ALL_TIME -> binding.chipAllTime
            else -> binding.chipAllTime
        }
        chip.isChecked = true

        // Update status filter chip text
        binding.chipStatus.text = when (salesViewModel.getCurrentStatusFilter()) {
            PaymentStatusFilter.ALL -> "All Status"
            PaymentStatusFilter.PAID -> "Paid"
            PaymentStatusFilter.PARTIAL -> "Partial"
            PaymentStatusFilter.UNPAID -> "Unpaid"
            else -> "All Status"
        }
    }

    private fun setupStatusFilterChip() {
        // When the status chip is clicked, show a popup menu

        binding.chipStatus.setOnClickListener {
            binding.chipStatus.isChecked = false
            val popup = PopupMenu(requireContext(), binding.chipStatus)
            popup.menuInflater.inflate(R.menu.status_filter_menu, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.status_all -> {
                        binding.chipStatus.text = "All Status"
                        salesViewModel.setPaymentStatusFilter(PaymentStatusFilter.ALL)
                        true
                    }

                    R.id.status_paid -> {
                        binding.chipStatus.text = "Paid"
                        salesViewModel.setPaymentStatusFilter(PaymentStatusFilter.PAID)
                        true
                    }

                    R.id.status_partial -> {
                        binding.chipStatus.text = "Partial"
                        salesViewModel.setPaymentStatusFilter(PaymentStatusFilter.PARTIAL)
                        true
                    }

                    R.id.status_unpaid -> {
                        binding.chipStatus.text = "Unpaid"
                        salesViewModel.setPaymentStatusFilter(PaymentStatusFilter.UNPAID)
                        true
                    }

                    else -> false
                }
            }

            popup.show()
        }
    }

    private fun setupDateFilterChips() {
        // Set up click listeners for date filter chips
        binding.chipAllTime.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.ALL_TIME) }
        binding.chipToday.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.TODAY) }
        binding.chipYesterday.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.YESTERDAY) }
        binding.chipThisWeek.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.THIS_WEEK) }
        binding.chipThisMonth.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.THIS_MONTH) }
        binding.chipLastMonth.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.LAST_MONTH) }
        binding.chipThisQuarter.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.THIS_QUARTER) }
        binding.chipThisYear.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.THIS_YEAR) }
    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search -> {
                    // Setup search functionality
                    setupSearchView()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupSearchView() {
        with(binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView) {
            queryHint = "Search invoice number, customer..."
            inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
            isIconified = false

            setOnQueryTextListener(object :
                androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        isSearchActive = true
                        salesViewModel.searchInvoices(it)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let {
                        isSearchActive = it.isNotEmpty()
                        salesViewModel.searchInvoices(it)
                    }
                    return true
                }
            })

            setOnCloseListener {
                isSearchActive = false
                salesViewModel.searchInvoices("")
                clearFocus()
                true
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = InvoicesAdapter(emptyList())

        // Set click listener for adapter
        adapter.onItemClickListener = { invoice ->

            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)

            // Using the generated NavDirections class
            val action =
                MainScreenFragmentDirections.actionMainScreenFragmentToInvoiceDetailFragment(invoice.invoiceNumber)
            parentNavController.navigate(action)
            // Handle invoice click - you can navigate to details or show actions
            Toast.makeText(
                requireContext(),
                "Invoice: ${invoice.invoiceNumber}",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.recyclerViewSales.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SalesFragment.adapter

            // Add scroll listener for pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    // Load more when user is near the end of the list
                    if (!salesViewModel.isLoading.value!! &&
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 &&
                        firstVisibleItemPosition >= 0
                    ) {
                        salesViewModel.loadNextPage()
                    }
                }
            })
        }
    }

    private fun setupObservers() {
        salesViewModel.invoices.observe(viewLifecycleOwner) { invoices ->
            adapter.updateInvoices(invoices)
            binding.swipeRefreshLayout.isRefreshing = false
            binding.progressBar.visibility = View.GONE
            // Synchronize UI with ViewModel filter state
            syncFiltersWithViewModel()
            // Update UI based on search results or empty state
            updateUIState(invoices.isEmpty())
        }

        salesViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        salesViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Log.d("SalesFragment", errorMessage)
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                binding.swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setUpClickListner() {
        binding.addSaleFab.setOnClickListener {
            // Navigate to the InvoiceCreationFragment
            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
            parentNavController.navigate(R.id.action_mainScreenFragment_to_invoiceCreationFragment)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            // Reset filters when manually refreshing
            salesViewModel.refreshInvoices(resetFilters = true)

            // Also reset UI state
            binding.chipAllTime.isChecked = true
            binding.chipStatus.text = "All Status"

            // Clear search if active
            if (isSearchActive) {
                val searchView =
                    binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
                searchView.setQuery("", false)
                searchView.clearFocus()
                isSearchActive = false
            }
        }

        // Clear search button in empty search state
        binding.clearFilterButton.setOnClickListener {
            // Clear search
            val searchView =
                binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            searchView.setQuery("", false)
            searchView.clearFocus()
            isSearchActive = false

            // Reset date filter
            binding.chipAllTime.isChecked = true

            // Reset status filter
            binding.chipStatus.text = "All Status"

            // Apply filters
            salesViewModel.setDateFilter(DateFilterType.ALL_TIME)
            salesViewModel.setPaymentStatusFilter(PaymentStatusFilter.ALL)
            salesViewModel.searchInvoices("")
        }


        // Add new item button in empty search state
        binding.addNewItemButton.setOnClickListener {
            binding.addSaleFab.performClick()
        }
    }

    private fun updateUIState(isEmpty: Boolean) {
        if (isEmpty && isSearchActive) {
            // No search results
            binding.emptySearchLayout.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewSales.visibility = View.GONE
        } else if (isEmpty) {
            // No invoices at all
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.emptySearchLayout.visibility = View.GONE
            binding.recyclerViewSales.visibility = View.GONE
        } else {
            // Show recycler view with results
            binding.recyclerViewSales.visibility = View.VISIBLE
            binding.emptyStateLayout.visibility = View.GONE
            binding.emptySearchLayout.visibility = View.GONE
        }
    }

    private fun refreshInvoices() {
        binding.swipeRefreshLayout.isRefreshing = true
        salesViewModel.refreshInvoices()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}