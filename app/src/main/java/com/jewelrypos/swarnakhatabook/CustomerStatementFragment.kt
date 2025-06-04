package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.CustomerStatementAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.PDFExportUtility
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentCustomerStatementBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerListBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class CustomerStatementFragment : Fragment() {

    companion object {
        private const val TAG = "CustomerStatementFragment"
    }

    private var _binding: FragmentCustomerStatementBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: CustomerStatementAdapter
    private val currencyFormatter = DecimalFormat("#,##,##0.00")
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    // Track active coroutines
    private val activeJobs = mutableListOf<Job>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Creating view")
        _binding = FragmentCustomerStatementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Starting setup")
        val startTime = System.currentTimeMillis()

        setupViewModel()
        Log.d(TAG, "onViewCreated: ViewModel setup completed in ${System.currentTimeMillis() - startTime}ms")
        
        setupUI()
        Log.d(TAG, "onViewCreated: UI setup completed in ${System.currentTimeMillis() - startTime}ms")
        
        setupObservers()
        Log.d(TAG, "onViewCreated: Initial setup completed in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun setupViewModel() {
        Log.d(TAG, "setupViewModel: Initializing ReportViewModel")
        val factory = ReportViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), factory)[ReportViewModel::class.java]
        Log.d(TAG, "setupViewModel: ReportViewModel initialized successfully")
    }

    private fun setupUI() {
        Log.d(TAG, "setupUI: Setting up UI components")
        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            if (!viewModel.isLoading.value!!) {
                requireActivity().onBackPressed()
            } else {
                Toast.makeText(requireContext(), "Please wait while data is loading", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup export button
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_export_pdf -> {
                    if (!viewModel.isLoading.value!!) {
                        exportReportToPdf()
                    } else {
                        Toast.makeText(requireContext(), "Please wait while data is loading", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }

        // Display date range
        viewModel.startDate.value?.let { startDate ->
            viewModel.endDate.value?.let { endDate ->
                val startDateStr = dateFormat.format(startDate)
                val endDateStr = dateFormat.format(endDate)
                binding.dateRangeText.text = "$startDateStr to $endDateStr"
            }
        }

        // Setup RecyclerView
        adapter = CustomerStatementAdapter()
        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@CustomerStatementFragment.adapter
        }

        // Setup customer selection button
        binding.selectCustomerButton.setOnClickListener {
            Log.d(TAG, "Select customer button clicked")
            if (!viewModel.isLoading.value!!) {
                showCustomerSelectionBottomSheet()
            } else {
                Toast.makeText(requireContext(), "Please wait while data is loading", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Setup change customer button
        binding.changeCustomerButton.setOnClickListener {
            if (!viewModel.isLoading.value!!) {
                showCustomerSelectionBottomSheet()
            } else {
                Toast.makeText(requireContext(), "Please wait while data is loading", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        Log.d(TAG, "setupObservers: Setting up LiveData observers")
        
        viewModel.customerTransactions.observe(viewLifecycleOwner) { transactions ->
            val startTime = System.currentTimeMillis()
            if (transactions != null) {
                Log.d(TAG, "Received ${transactions.size} customer transactions")
                adapter.submitList(transactions)
                updateEmptyState(transactions.isEmpty())
                Log.d(TAG, "Customer transactions processed in ${System.currentTimeMillis() - startTime}ms")
            }
        }

        viewModel.selectedCustomer.observe(viewLifecycleOwner) { customer ->
            val startTime = System.currentTimeMillis()
            if (customer != null) {
                Log.d(TAG, "Customer selected: ${customer.firstName}")
                updateSelectedCustomerUI(customer)
                viewModel.loadCustomerStatement(customer)
                Log.d(TAG, "Customer selection processed in ${System.currentTimeMillis() - startTime}ms")
            }
        }

        viewModel.openingBalance.observe(viewLifecycleOwner) { balance ->
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Opening balance updated: $balance")
            binding.openingBalanceValue.text = "₹${currencyFormatter.format(balance)}"
            Log.d(TAG, "Opening balance update processed in ${System.currentTimeMillis() - startTime}ms")
        }

        viewModel.closingBalance.observe(viewLifecycleOwner) { balance ->
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Closing balance updated: $balance")
            binding.closingBalanceValue.text = "₹${currencyFormatter.format(balance)}"
            Log.d(TAG, "Closing balance update processed in ${System.currentTimeMillis() - startTime}ms")
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state changed: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Disable/enable UI elements based on loading state
            binding.selectCustomerButton.isEnabled = !isLoading
            binding.changeCustomerButton.isEnabled = !isLoading
            binding.topAppBar.menu.findItem(R.id.action_export_pdf)?.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Log.e(TAG, "Error occurred: $message")
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }

        // Also observe date range changes
        viewModel.startDate.observe(viewLifecycleOwner) { startDate ->
            startDate?.let {
                val startDateStr = dateFormat.format(it)
                viewModel.endDate.value?.let { endDate ->
                    val endDateStr = dateFormat.format(endDate)
                    binding.dateRangeText.text = "$startDateStr to $endDateStr"
                }
                // If date range changes and customer is selected, reload statement
                viewModel.selectedCustomer.value?.let { customer ->
                    val job = viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            viewModel.loadCustomerStatement(customer)
                        } catch (e: Exception) {
                            Log.e("CustomerStatement", "Error loading statement: ${e.message}")
                            Toast.makeText(requireContext(), "Error loading statement: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    activeJobs.add(job)
                }
            }
        }

        viewModel.endDate.observe(viewLifecycleOwner) { endDate ->
            endDate?.let {
                val endDateStr = dateFormat.format(it)
                viewModel.startDate.value?.let { startDate ->
                    val startDateStr = dateFormat.format(startDate)
                    binding.dateRangeText.text = "$startDateStr to $endDateStr"
                }
                // If date range changes and customer is selected, reload statement
                viewModel.selectedCustomer.value?.let { customer ->
                    val job = viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            viewModel.loadCustomerStatement(customer)
                        } catch (e: Exception) {
                            Log.e("CustomerStatement", "Error loading statement: ${e.message}")
                            Toast.makeText(requireContext(), "Error loading statement: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    activeJobs.add(job)
                }
            }
        }
    }

    private fun showCustomerSelectionBottomSheet() {
        val customerListBottomSheet = CustomerListBottomSheet.newInstance()
        customerListBottomSheet.setOnCustomerSelectedListener { selectedCustomer ->
            Log.d("CustomerStatement", "Customer selected: ${selectedCustomer.firstName}")
            viewModel.selectCustomer(selectedCustomer)
        }
        customerListBottomSheet.show(parentFragmentManager, CustomerListBottomSheet.TAG)
    }

    private fun updateSelectedCustomerUI(customer: Customer?) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "updateSelectedCustomerUI: Starting update")
        
        if (customer == null) {
            binding.noCustomerSelectedLayout.visibility = View.VISIBLE
            binding.customerDetailsLayout.visibility = View.GONE
            binding.statementLayout.visibility = View.GONE
            return
        }

        binding.noCustomerSelectedLayout.visibility = View.GONE
        binding.customerDetailsLayout.visibility = View.VISIBLE
        binding.statementLayout.visibility = View.VISIBLE

        binding.customerNameText.text = "${customer.firstName} ${customer.lastName}"
        binding.customerTypeText.text = customer.customerType
        binding.customerPhoneText.text = customer.phoneNumber
        binding.customerAddressText.text = "${customer.streetAddress}, ${customer.city}, ${customer.state} ${customer.postalCode}"
        
        Log.d(TAG, "updateSelectedCustomerUI: Completed in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun showNoCustomerSelected() {
        Log.d(TAG, "Showing no customer selected state")
        binding.emptyStateLayout.visibility = View.VISIBLE
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        Log.d(TAG, "Updating empty state: $isEmpty")
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.transactionsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun exportReportToPdf() {
        Log.d(TAG, "Starting PDF export")
        val customer = viewModel.selectedCustomer.value
        val transactions = viewModel.customerTransactions.value
        val openingBalance = viewModel.openingBalance.value
        val closingBalance = viewModel.closingBalance.value

        if (customer == null || transactions == null || openingBalance == null || closingBalance == null) {
            Toast.makeText(requireContext(), "Please select a customer first", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val startDate = viewModel.startDate.value?.let { dateFormat.format(it) } ?: ""
            val endDate = viewModel.endDate.value?.let { dateFormat.format(it) } ?: ""

            val pdfExporter = PDFExportUtility(requireContext())
            val success = pdfExporter.exportCustomerStatement(
                customer,
                startDate,
                endDate,
                openingBalance,
                closingBalance,
                transactions
            )

            if (success) {
                Log.d(TAG, "PDF export completed successfully")
                Toast.makeText(requireContext(), "Statement exported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "PDF export failed")
                Toast.makeText(requireContext(), "Error exporting statement", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during PDF export", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Cleaning up resources")
        super.onDestroyView()
        // Cancel all running coroutines
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
        _binding = null
    }
}
