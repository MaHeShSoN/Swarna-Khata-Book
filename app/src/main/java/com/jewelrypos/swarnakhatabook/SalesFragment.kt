package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
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

    private val salesViewModel: SalesViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager, requireContext())
    }

    private lateinit var adapter: InvoiceAdapter
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
        setupEmptyStateButtons()


        binding.topAppBar.overflowIcon =
            ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)

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

                R.id.action_export_excel -> {
                    // Handle Excel export
                    exportSalesReport()
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Creates a comprehensive description of the currently applied filters
     */
    private fun getFilterDescription(): String {
        // Get date filter description
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

        // Get payment status filter description
        val statusFilter = when (salesViewModel.getCurrentStatusFilter()) {
            PaymentStatusFilter.PAID -> "Paid Invoices"
            PaymentStatusFilter.PARTIAL -> "Partially Paid Invoices"
            PaymentStatusFilter.UNPAID -> "Unpaid Invoices"
            PaymentStatusFilter.ALL -> "All Payment Statuses"
            else -> "All Payment Statuses"
        }

        // Add search query if present
        val searchQuery = salesViewModel.getCurrentSearchQuery()
        val searchDescription = if (searchQuery.isNotEmpty()) {
            " (Search: \"$searchQuery\")"
        } else {
            ""
        }

        return "$dateFilter, $statusFilter$searchDescription"
    }

    private fun exportSalesReport() {
        // Check if we have data to export
        val currentInvoices = adapter.snapshot().items
        if (currentInvoices.isEmpty()) {
            Toast.makeText(requireContext(), "No sales data to export", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading indicator
        binding.progressBar.visibility = View.VISIBLE

        // Generate a comprehensive filter description
        val filterDescription = getFilterDescription()

        // Perform export in background thread
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("SalesFragment", "Generating enhanced sales report...")

                // Use the enhanced export utility
                val csvFileUri = EnhancedCsvExportUtil.exportSalesReport(
                    requireContext(),
                    currentInvoices,
                    filterDescription
                )

                // Switch back to main thread to update UI
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE

                    if (csvFileUri != null) {
                        // Show success message
                        Toast.makeText(
                            requireContext(),
                            "Comprehensive sales report created successfully",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Ask the user if they want to view the file
                        showReportShareOptions(csvFileUri)
                    } else {
                        // Show error message
                        Toast.makeText(
                            requireContext(),
                            "Failed to generate sales report",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SalesFragment", "Error exporting enhanced report", e)

                // Update UI on main thread
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

    /**
     * Shows options to view, share, or email the exported report
     */
    /**
     * Shows a themed dialog with options to view, share, or email the exported report
     */
    private fun showReportShareOptions(fileUri: Uri) {
        // Create the themed dialog
        val themedDialog = ThemedM3Dialog(requireContext())
            .setTitle("Sales Report Ready")
            .setLayout(R.layout.dialog_report_options)

        // Get reference to dialog view
        val dialogView = themedDialog.getDialogView()

        // Set up button click listeners
        dialogView?.findViewById<MaterialCardView>(R.id.viewReportCard)?.setOnClickListener {
            openReportFile(fileUri)
            themedDialog.create().dismiss()
        }

        dialogView?.findViewById<MaterialCardView>(R.id.shareReportCard)?.setOnClickListener {
            shareReportFile(fileUri)
            themedDialog.create().dismiss()
        }


        // Set up close button
        themedDialog.setNegativeButton("Cancel") { dialog ->
            dialog.dismiss()
        }

        // Show the dialog
        themedDialog.show()
    }

    /**
     * Opens the report file with an appropriate app
     */
    private fun openReportFile(fileUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                // Try with a more generic MIME type if no CSV handler is found
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

    /**
     * Shares the report file with other apps
     */
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
        adapter = InvoiceAdapter()

        // Set click listener for adapter
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

            // Add RecyclerView animations
            AnimationUtils.animateRecyclerView(this)


        }

        // Collect paged data
        viewLifecycleOwner.lifecycleScope.launch {
            salesViewModel.pagedInvoices.collectLatest { pagingData ->
                if (isAdded && _binding != null) {
                    adapter.submitData(pagingData)
                }
            }
        }

        // Observe adapter loading state
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                if (!isAdded || _binding == null) return@collectLatest

                // Update UI based on load states
                binding.progressBar.visibility =
                    if (loadStates.refresh is LoadState.Loading) View.VISIBLE else View.GONE

                // Handle errors
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

                // Update empty state visibility
                val isEmpty = loadStates.refresh is LoadState.NotLoading && adapter.itemCount == 0
                updateUIState(isEmpty)
            }
        }
    }

    private fun setupObservers() {
        salesViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Only show progress bar if not already refreshing via swipe
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

        // Observe paged data to handle refresh state
        viewLifecycleOwner.lifecycleScope.launch {
            salesViewModel.pagedInvoices.collectLatest { pagingData ->
                if (isAdded && _binding != null) {
                    adapter.submitData(pagingData)
                    // Stop refresh animation after data is loaded
                    binding.swipeRefreshLayout.isRefreshing = false
                }
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
    }

    private fun setupEmptyStateButtons() {
        // Button within the main empty state (no invoices at all)
        binding.addNewInvoiceEmptyButton.setOnClickListener {
            navigateToCreateInvoice() // Navigate using helper
        }

        // Button within the empty search state
        binding.addNewItemButton.setOnClickListener {
            navigateToCreateInvoice() // Navigate using helper
        }
    }

    private fun navigateToCreateInvoice() {
        val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
        parentNavController.navigate(R.id.action_mainScreenFragment_to_invoiceCreationFragment)
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