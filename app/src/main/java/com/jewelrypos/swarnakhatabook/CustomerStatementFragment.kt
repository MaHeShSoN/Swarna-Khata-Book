package com.jewelrypos.swarnakhatabook

import android.content.Intent
import android.net.Uri
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
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import kotlin.math.absoluteValue

class CustomerStatementFragment : Fragment() {

    companion object {
        private const val TAG = "CustomerStatementFragment"
    }

    private var _binding: FragmentCustomerStatementBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: CustomerStatementAdapter
    private val currencyFormatter = DecimalFormat("#,##,##0.00")
    // private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) // Removed: No longer needed for date range display

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

        // Display date range - REMOVED
        // viewModel.startDate.value?.let { startDate ->
        //     viewModel.endDate.value?.let { endDate ->
        //         val startDateStr = dateFormat.format(startDate)
        //         val endDateStr = dateFormat.format(endDate)
        //         binding.dateRangeText.text = "$startDateStr to $endDateStr"
        //     }
        // }

        // Setup RecyclerView with click listener
        adapter = CustomerStatementAdapter(object : CustomerStatementAdapter.OnInvoiceClickListener {
            override fun onInvoiceClick(invoiceId: String) {
                // Handle invoice click - navigate to invoice details
                navigateToInvoiceDetails(invoiceId)
            }
        })
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
            val formattedBalance = currencyFormatter.format(balance.toDouble().absoluteValue)
            val balanceDisplay = "₹$formattedBalance"
            binding.openingBalanceValue.text = balanceDisplay
            Log.d(TAG, "Opening balance update processed in ${System.currentTimeMillis() - startTime}ms")
        }

        viewModel.closingBalance.observe(viewLifecycleOwner) { balance ->
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Closing balance updated: $balance")
            val customer = viewModel.selectedCustomer.value // Get the selected customer
            val formattedBalance = currencyFormatter.format(balance.toDouble().absoluteValue)

            val balanceDisplay = if (customer != null) {
                when (customer.balanceType) {
                    "Baki" -> requireContext().getString(R.string.baki_amount, currencyFormatter.format(customer.currentBalance))
                    "Jama" -> requireContext().getString(R.string.jama_amount, currencyFormatter.format(customer.currentBalance))
                    else -> requireContext().getString(R.string.settled_amount, "0.00") // Assuming 0 balance is settled
                }
            } else {
                // Fallback if customer is not selected or null
                "Settled ₹0.00"
            }
            binding.closingBalanceValue.text = balanceDisplay
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

        // Also observe date range changes - REMOVED
        // viewModel.startDate.observe(viewLifecycleOwner) { startDate ->
        //     startDate?.let {
        //         val startDateStr = dateFormat.format(it)
        //         viewModel.endDate.value?.let { endDate ->
        //             val endDateStr = dateFormat.format(endDate)
        //             binding.dateRangeText.text = "$startDateStr to $endDateStr"
        //         }
        //         // If date range changes and customer is selected, reload statement
        //         viewModel.selectedCustomer.value?.let { customer ->
        //             val job = viewLifecycleOwner.lifecycleScope.launch {
        //                 try {
        //                     viewModel.loadCustomerStatement(customer)
        //                 } catch (e: Exception) {
        //                     Log.e("CustomerStatement", "Error loading statement: ${e.message}")
        //                     Toast.makeText(requireContext(), "Error loading statement: ${e.message}", Toast.LENGTH_SHORT).show()
        //                 }
        //             }
        //             activeJobs.add(job)
        //         }
        //     }
        // }

        // viewModel.endDate.observe(viewLifecycleOwner) { endDate ->
        //     endDate?.let {
        //         val endDateStr = dateFormat.format(it)
        //         viewModel.startDate.value?.let { startDate ->
        //             val startDateStr = dateFormat.format(startDate)
        //             binding.dateRangeText.text = "$startDateStr to $endDateStr"
        //         }
        //         // If date range changes and customer is selected, reload statement
        //         viewModel.selectedCustomer.value?.let { customer ->
        //             val job = viewLifecycleOwner.lifecycleScope.launch {
        //                 try {
        //                     viewModel.loadCustomerStatement(customer)
        //                 } catch (e: Exception) {
        //                     Log.e("CustomerStatement", "Error loading statement: ${e.message}")
        //                     Toast.makeText(requireContext(), "Error loading statement: ${e.message}", Toast.LENGTH_SHORT).show()
        //                 }
        //             }
        //             activeJobs.add(job)
        //         }
        //     }
        // }
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
            // val startDate = viewModel.startDate.value?.let { dateFormat.format(it) } ?: "" // Removed
            // val endDate = viewModel.endDate.value?.let { dateFormat.format(it) } ?: "" // Removed

            val pdfExporter = PDFExportUtility(requireContext())
            val success = pdfExporter.exportCustomerStatement(
                customer,
                "", // Pass empty string or null for startDate if not used by PDF exporter
                "", // Pass empty string or null for endDate if not used by PDF exporter
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

    private fun navigateToInvoiceDetails(invoiceId: String) {
        try {
                // Construct the deep link URI
                val uri = Uri.parse("android-app://com.jewelrypos.swarnakhatabook/invoice_detail/$invoiceId")

                // Create an Intent to navigate using the deep link URI
                val intent = Intent(Intent.ACTION_VIEW, uri)

                // Explicitly set the package to your application's package name.
                intent.setPackage(requireContext().packageName)

                // Add flags to ensure the correct Activity is brought to the foreground and back stack is managed
//                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                // ADD THIS LINE: Add an extra to indicate the navigation source
                intent.putExtra("from_reports_activity", true)

                // Start the Activity that handles this deep link (e.g., MainActivity)
                startActivity(intent)


            } catch (e: Exception) {
            Log.e(TAG, "Error navigating to invoice details", e)
            Toast.makeText(
                requireContext(),
                "Error opening invoice details  ${e.message} ",
                Toast.LENGTH_SHORT
            ).show()
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
