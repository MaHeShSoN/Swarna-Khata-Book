package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.InvoiceAdapter
import com.jewelrypos.swarnakhatabook.Enums.DateFilterType
import com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.SalesViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Utilitys.AnimationUtils
import com.jewelrypos.swarnakhatabook.Utilitys.EnhancedCsvExportUtil
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentSalesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.LoadState
import kotlinx.coroutines.flow.collectLatest

class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    private val salesViewModel: SalesViewModel by navGraphViewModels(R.id.inner_nav_graph) {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager, requireContext())
    }

    private lateinit var adapter: InvoiceAdapter
    private var isSearchActive = false
    private var scrollToTopAfterAdd = false
    private var isLayoutStateRestored = false
    // Assume salesViewModel has: var layoutManagerState: Parcelable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isLayoutStateRestored = false
        scrollToTopAfterAdd = false
        Log.d("SalesFragment", "onViewCreated: Resetting flags.")

        setupToolbar()
        
        // Restore search view state if there's an active search
        val currentSearchQuery = salesViewModel.getCurrentSearchQuery()
        if (currentSearchQuery.isNotEmpty()) {
            val searchItem = binding.topAppBar.menu.findItem(R.id.action_search)
            searchItem.expandActionView()
            val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
            searchView.setQuery(currentSearchQuery, false)
            isSearchActive = true
        }

        setupRecyclerView()
        setupObservers()
        setUpClickListner() // Original spelling
        setupDateFilterChips()
        setupStatusFilterChip()

        // Call this method to ensure chips reflect current filters when view is (re)created
        syncFiltersWithViewModel()

        setupEmptyStateButtons()

        binding.topAppBar.overflowIcon =
            ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)

        EventBus.invoiceAddedEvent.observe(viewLifecycleOwner) { added ->
            if (added) {
                salesViewModel.refreshInvoices()
                scrollToTopAfterAdd = true
                Log.d("SalesFragment", "Invoice added event received, set scrollToTopAfterAdd to true.")
                // EventBus.resetInvoiceAddedEvent() // If applicable
            }
        }

        EventBus.invoiceDeletedEvent.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                salesViewModel.refreshInvoices()
                // EventBus.resetInvoiceDeletedEvent() // If applicable
            }
        }

        EventBus.invoiceUpdatedEvent.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                salesViewModel.refreshInvoices()
                // EventBus.resetInvoiceUpdatedEvent() // If applicable
            }
        }
    }

    private fun syncFiltersWithViewModel() {
        _binding?.let { currentBinding ->
            Log.d("SalesFragment", "Syncing filter chips with ViewModel state.")
            val currentDateFilter = salesViewModel.getCurrentDateFilter()
            currentBinding.chipToday.isChecked = currentDateFilter == DateFilterType.TODAY
            currentBinding.chipYesterday.isChecked = currentDateFilter == DateFilterType.YESTERDAY
            currentBinding.chipThisWeek.isChecked = currentDateFilter == DateFilterType.THIS_WEEK
            currentBinding.chipThisMonth.isChecked = currentDateFilter == DateFilterType.THIS_MONTH
            currentBinding.chipLastMonth.isChecked = currentDateFilter == DateFilterType.LAST_MONTH
            currentBinding.chipThisQuarter.isChecked = currentDateFilter == DateFilterType.THIS_QUARTER
            currentBinding.chipThisYear.isChecked = currentDateFilter == DateFilterType.THIS_YEAR
            // Default to All Time if no specific filter or if it's explicitly ALL_TIME
            currentBinding.chipAllTime.isChecked = currentDateFilter == DateFilterType.ALL_TIME || currentDateFilter == null


            val currentStatusFilter = salesViewModel.getCurrentStatusFilter()
            currentBinding.chipStatus.text = when (currentStatusFilter) {
                PaymentStatusFilter.ALL -> "All Status"
                PaymentStatusFilter.PAID -> "Paid"
                PaymentStatusFilter.PARTIAL -> "Partial"
                PaymentStatusFilter.UNPAID -> "Unpaid"
                else -> "All Status" // Default text
            }
            // If chipStatus is also checkable and should reflect a selected state visually,
            // you might need to manage its isChecked property here too.
            // For example:
            // currentBinding.chipStatus.isChecked = currentStatusFilter != PaymentStatusFilter.ALL && currentStatusFilter != null
            // However, its current behavior is to uncheck when clicked to show a popup.
        }
    }

    private fun setupStatusFilterChip() {
        binding.chipStatus.setOnClickListener {
            // When the status chip is clicked, it visually unchecks itself to show the popup.
            // This behavior might be intentional for this specific chip.
            binding.chipStatus.isChecked = false
            val popup = PopupMenu(requireContext(), binding.chipStatus)
            popup.menuInflater.inflate(R.menu.status_filter_menu, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                val newFilter = when (menuItem.itemId) {
                    R.id.status_all -> PaymentStatusFilter.ALL
                    R.id.status_paid -> PaymentStatusFilter.PAID
                    R.id.status_partial -> PaymentStatusFilter.PARTIAL
                    R.id.status_unpaid -> PaymentStatusFilter.UNPAID
                    else -> null
                }
                newFilter?.let {
                    salesViewModel.setPaymentStatusFilter(it)
                    // After setting filter, update text immediately (syncFiltersWithViewModel will also be called if navigating back)
                    binding.chipStatus.text = when (it) {
                        PaymentStatusFilter.ALL -> "All Status"
                        PaymentStatusFilter.PAID -> "Paid"
                        PaymentStatusFilter.PARTIAL -> "Partial"
                        PaymentStatusFilter.UNPAID -> "Unpaid"
                    }
                }
                true
            }
            popup.show()
        }
    }

    private fun setupDateFilterChips() {
        // Set up click listeners for date filter chips
        binding.chipAllTime.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.ALL_TIME); syncFiltersWithViewModel() }
        binding.chipToday.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.TODAY); syncFiltersWithViewModel() }
        binding.chipYesterday.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.YESTERDAY); syncFiltersWithViewModel() }
        binding.chipThisWeek.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.THIS_WEEK); syncFiltersWithViewModel() }
        binding.chipThisMonth.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.THIS_MONTH); syncFiltersWithViewModel() }
        binding.chipLastMonth.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.LAST_MONTH); syncFiltersWithViewModel() }
        binding.chipThisQuarter.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.THIS_QUARTER); syncFiltersWithViewModel() }
        binding.chipThisYear.setOnClickListener { salesViewModel.setDateFilter(DateFilterType.THIS_YEAR); syncFiltersWithViewModel() }
    }

    // ... (rest of your SalesFragment.kt code from the previous correct version) ...
    // Make sure to include the previous implementations for state restoration and scroll to top.
    // The following methods are stubs from your existing code and should be kept:

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search -> {
                    setupSearchView()
                    true
                }
                R.id.action_export_excel -> {
                    exportSalesReport()
                    true
                }
                else -> false
            }
        }
    }

    private fun getFilterDescription(): String {
        val dateFilter = when (salesViewModel.getCurrentDateFilter()) {
            DateFilterType.TODAY -> "Today"
            DateFilterType.YESTERDAY -> "Yesterday"
            DateFilterType.THIS_WEEK -> "This Week"
            DateFilterType.THIS_MONTH -> "This Month"
            DateFilterType.LAST_MONTH -> "Last Month"
            DateFilterType.THIS_QUARTER -> "This Quarter"
            DateFilterType.THIS_YEAR -> "This Year"
            DateFilterType.ALL_TIME -> "All Time"
            else -> "All Time"
        }
        val statusFilter = when (salesViewModel.getCurrentStatusFilter()) {
            PaymentStatusFilter.PAID -> "Paid Invoices"
            PaymentStatusFilter.PARTIAL -> "Partially Paid Invoices"
            PaymentStatusFilter.UNPAID -> "Unpaid Invoices"
            PaymentStatusFilter.ALL -> "All Payment Statuses"
            else -> "All Payment Statuses"
        }
        val searchQuery = salesViewModel.getCurrentSearchQuery()
        val searchDescription = if (searchQuery.isNotEmpty()) {
            " (Search: \"$searchQuery\")"
        } else {
            ""
        }
        return "$dateFilter, $statusFilter$searchDescription"
    }

    private fun exportSalesReport() {
        val currentInvoices = adapter.snapshot().items
        if (currentInvoices.isEmpty()) {
            Toast.makeText(requireContext(), "No sales data to export", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        val filterDescription = getFilterDescription()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("SalesFragment", "Generating enhanced sales report...")
                val csvFileUri = EnhancedCsvExportUtil.exportSalesReport(
                    requireContext(),
                    currentInvoices,
                    filterDescription
                )
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    if (csvFileUri != null) {
                        Toast.makeText(
                            requireContext(),
                            "Comprehensive sales report created successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        showReportShareOptions(csvFileUri)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Failed to generate sales report",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SalesFragment", "Error exporting enhanced report", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Export failed: ${e.message ?: "Unknown error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showReportShareOptions(fileUri: Uri) {
        val themedDialog = ThemedM3Dialog(requireContext())
            .setTitle("Sales Report Ready")
            .setLayout(R.layout.dialog_report_options)
        val dialogView = themedDialog.getDialogView()
        dialogView?.findViewById<MaterialCardView>(R.id.viewReportCard)?.setOnClickListener {
            openReportFile(fileUri)
            themedDialog.create().dismiss()
        }
        dialogView?.findViewById<MaterialCardView>(R.id.shareReportCard)?.setOnClickListener {
            shareReportFile(fileUri)
            themedDialog.create().dismiss()
        }
        themedDialog.setNegativeButton("Cancel") { dialog ->
            dialog.dismiss()
        }
        themedDialog.show()
    }

    private fun openReportFile(fileUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                val textIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "text/plain")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(textIntent, "Open with"))
            }
        } catch (e: Exception) {
            Log.e("SalesFragment", "Error opening report file", e)
            Toast.makeText(
                requireContext(),
                "Cannot open report: No compatible app found",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareReportFile(fileUri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Sales Report"))
        } catch (e: Exception) {
            Log.e("SalesFragment", "Error sharing report file", e)
            Toast.makeText(requireContext(), "Cannot share report", Toast.LENGTH_SHORT).show()
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
                    clearFocus()
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
                // Reset filters when search is closed
                salesViewModel.setDateFilter(DateFilterType.ALL_TIME)
                salesViewModel.setPaymentStatusFilter(PaymentStatusFilter.ALL)
                syncFiltersWithViewModel() // Update UI to reflect filter reset
                clearFocus()
                true
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            isLayoutStateRestored = false
            Log.d("SalesFragment", "Swipe refresh: isLayoutStateRestored set to false.")
            salesViewModel.refreshInvoices(resetFilters = true)
            syncFiltersWithViewModel()

            if (isSearchActive) {
                val searchView =
                    binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
                searchView.setQuery("", false)
                searchView.clearFocus()
                searchView.isIconified = true
                searchView.onActionViewCollapsed()
                isSearchActive = false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = InvoiceAdapter()
        adapter.onItemClickListener = { invoice ->
            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
            val action =
                MainScreenFragmentDirections.actionMainScreenFragmentToInvoiceSummeryFragmnet(
                    invoice.invoiceNumber
                )
            parentNavController.navigate(action)
        }

        binding.recyclerViewSales.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SalesFragment.adapter
            AnimationUtils.animateRecyclerView(this)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            salesViewModel.pagedInvoices.collectLatest { pagingData ->
                if (isAdded && _binding != null) {
                    adapter.submitData(pagingData)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                if (!isAdded || _binding == null) return@collectLatest

                binding.progressBar.visibility =
                    if (loadStates.refresh is LoadState.Loading && !binding.swipeRefreshLayout.isRefreshing) View.VISIBLE else View.GONE

                val error = when {
                    loadStates.refresh is LoadState.Error -> loadStates.refresh as LoadState.Error
                    loadStates.append is LoadState.Error -> loadStates.append as LoadState.Error
                    loadStates.prepend is LoadState.Error -> loadStates.prepend as LoadState.Error
                    else -> null
                }
                error?.let {
                    Log.e("SalesFragment", "Error loading data: ${it.error}")
                    Toast.makeText(requireContext(), it.error.localizedMessage, Toast.LENGTH_LONG)
                        .show()
                }

                val refreshState = loadStates.refresh
                if (refreshState is LoadState.NotLoading) {
                    Log.d("SalesFragment", "LoadState NotLoading, checking item count: ${adapter.itemCount}")
                    _binding?.let { currentBinding ->
                        if (adapter.itemCount > 0) {
                            currentBinding.recyclerViewSales.visibility = View.VISIBLE
                            currentBinding.emptyStateLayout.visibility = View.GONE
                            currentBinding.emptySearchLayout.visibility = View.GONE

                            if (!isLayoutStateRestored && salesViewModel.layoutManagerState != null) {
                                currentBinding.recyclerViewSales.post {
                                    Log.d("SalesFragment", "Posting layout manager state restoration.")
                                    if (_binding != null && !isLayoutStateRestored && salesViewModel.layoutManagerState != null) {
                                        _binding?.recyclerViewSales?.layoutManager?.onRestoreInstanceState(salesViewModel.layoutManagerState)
                                        isLayoutStateRestored = true
                                        Log.d("SalesFragment", "Set isLayoutStateRestored to true after posting restoration.")
                                    } else {
                                        Log.d("SalesFragment", "Skipping state restoration inside post block (inner conditions not met).")
                                    }
                                }
                            } else {
                                Log.d("SalesFragment", "Skipping immediate state restoration check (outer conditions not met: isLayoutStateRestored=${isLayoutStateRestored}, layoutManagerState=${salesViewModel.layoutManagerState != null}).")
                            }

                            if (scrollToTopAfterAdd) {
                                currentBinding.recyclerViewSales.post {
                                    Log.d("SalesFragment", "Posting scroll to top after invoice add.")
                                    if (_binding != null && scrollToTopAfterAdd && adapter.itemCount > 0) {
                                        _binding?.recyclerViewSales?.smoothScrollToPosition(0)
                                        Log.d("SalesFragment", "Smooth scrolling to position 0 after invoice add.")
                                        scrollToTopAfterAdd = false
                                        Log.d("SalesFragment", "Reset scrollToTopAfterAdd flag.")
                                    } else {
                                        Log.d("SalesFragment", "Skipping scroll to top after add (conditions not met).")
                                    }
                                }
                            }
                        }
                    }
                }
                val isEmpty = loadStates.refresh is LoadState.NotLoading && adapter.itemCount == 0
                updateUIState(isEmpty)
            }
        }
    }

    private fun setupObservers() {
        salesViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!binding.swipeRefreshLayout.isRefreshing) {
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }

        salesViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Log.d("SalesFragment", errorMessage)
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                binding.swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            salesViewModel.pagedInvoices.collectLatest {
                if (isAdded && _binding != null) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun setUpClickListner() {
        binding.addSaleFab.setOnClickListener {
            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
            parentNavController.navigate(R.id.action_mainScreenFragment_to_invoiceCreationFragment)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            isLayoutStateRestored = false
            Log.d("SalesFragment", "Swipe refresh: isLayoutStateRestored set to false.")
            salesViewModel.refreshInvoices(resetFilters = true)
            syncFiltersWithViewModel()

            if (isSearchActive) {
                val searchView =
                    binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
                searchView.setQuery("", false)
                searchView.clearFocus()
                searchView.isIconified = true
                searchView.onActionViewCollapsed()
                isSearchActive = false
            }
        }

        binding.clearFilterButton.setOnClickListener {
            val searchView =
                binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
            searchView.setQuery("", false)
            searchView.clearFocus()
            searchView.isIconified = true
            searchView.onActionViewCollapsed()
            isSearchActive = false
            salesViewModel.setDateFilter(DateFilterType.ALL_TIME)
            salesViewModel.setPaymentStatusFilter(PaymentStatusFilter.ALL)
            salesViewModel.searchInvoices("")
            syncFiltersWithViewModel() // Sync chips after clearing filters
        }
    }

    private fun setupEmptyStateButtons() {
        binding.addNewInvoiceEmptyButton.setOnClickListener {
            navigateToCreateInvoice()
        }
        binding.addNewItemButton.setOnClickListener {
            navigateToCreateInvoice()
        }
    }

    private fun navigateToCreateInvoice() {
        val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
        parentNavController.navigate(R.id.action_mainScreenFragment_to_invoiceCreationFragment)
    }

    private fun updateUIState(isEmpty: Boolean) {
        _binding?.let { currentBinding ->
            if (isEmpty && isSearchActive) {
                currentBinding.emptySearchLayout.visibility = View.VISIBLE
                currentBinding.emptyStateLayout.visibility = View.GONE
                currentBinding.recyclerViewSales.visibility = View.GONE
            } else if (isEmpty) {
                currentBinding.emptyStateLayout.visibility = View.VISIBLE
                currentBinding.emptySearchLayout.visibility = View.GONE
                currentBinding.recyclerViewSales.visibility = View.GONE
            } else {
                currentBinding.recyclerViewSales.visibility = View.VISIBLE
                currentBinding.emptyStateLayout.visibility = View.GONE
                currentBinding.emptySearchLayout.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        _binding?.recyclerViewSales?.let { rv ->
            if (rv.adapter?.itemCount ?: 0 > 0) {
                rv.layoutManager?.let { lm ->
                    salesViewModel.layoutManagerState = lm.onSaveInstanceState()
                    Log.d("SalesFragment", "Saved layout manager state in onDestroyView. State: ${salesViewModel.layoutManagerState}")
                }
            } else {
                if (salesViewModel.layoutManagerState == null) {
                    Log.d("SalesFragment", "List is empty in onDestroyView, and ViewModel state is null. Clearing saved state.")
                    salesViewModel.layoutManagerState = null
                } else {
                    Log.d("SalesFragment", "List is empty in onDestroyView, but ViewModel state is NOT null. Keeping previously saved state.")
                }
            }
        }
        super.onDestroyView()
        _binding = null
        Log.d("SalesFragment", "onDestroyView: binding set to null.")
    }
}