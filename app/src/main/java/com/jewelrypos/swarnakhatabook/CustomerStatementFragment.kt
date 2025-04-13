package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.util.Log // Import Log
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
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerListBottomSheet // Import the BottomSheet

class CustomerStatementFragment : Fragment() {

    private var _binding: FragmentCustomerStatementBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: CustomerStatementAdapter
    private val currencyFormatter = DecimalFormat("#,##,##0.00")
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) // Line 31 (Corrected)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerStatementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupUI()
        setupObservers()
    }

    private fun setupViewModel() {
        val factory = ReportViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), factory)[ReportViewModel::class.java]
    }

    private fun setupUI() {
        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        // Setup export button
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_export_pdf -> {
                    exportReportToPdf()
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
            showCustomerSelectionBottomSheet() // Changed to use BottomSheet
        }
        // Setup change customer button (if needed)
        binding.changeCustomerButton.setOnClickListener {
            showCustomerSelectionBottomSheet() // Changed to use BottomSheet
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }

        // No need to observe customers here for the dropdown anymore

        viewModel.selectedCustomer.observe(viewLifecycleOwner) { customer ->
            updateSelectedCustomerUI(customer)
            // Load statement only if a customer is actually selected
            customer?.let { viewModel.loadCustomerStatement(it) }
        }

        viewModel.customerTransactions.observe(viewLifecycleOwner) { transactions ->
            if (transactions != null) {
                adapter.submitList(transactions)
                // Show/hide empty state
                binding.emptyStateLayout.visibility = if (transactions.isEmpty()) View.VISIBLE else View.GONE
                binding.transactionsRecyclerView.visibility = if (transactions.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        viewModel.openingBalance.observe(viewLifecycleOwner) { balance ->
            binding.openingBalanceValue.text = "₹${currencyFormatter.format(balance)}"
        }

        viewModel.closingBalance.observe(viewLifecycleOwner) { balance ->
            binding.closingBalanceValue.text = "₹${currencyFormatter.format(balance)}"
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
                    viewModel.loadCustomerStatement(customer)
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
                    viewModel.loadCustomerStatement(customer)
                }
            }
        }
    }

    // **MODIFIED FUNCTION**
    private fun showCustomerSelectionBottomSheet() {
        val customerListBottomSheet = CustomerListBottomSheet.newInstance()
        customerListBottomSheet.setOnCustomerSelectedListener { selectedCustomer ->
            // This lambda is called when a customer is selected in the bottom sheet
            Log.d("CustomerStatement", "Customer selected: ${selectedCustomer.firstName}")
            viewModel.selectCustomer(selectedCustomer)
            // Bottom sheet dismisses itself after selection (handled within CustomerListBottomSheet)
        }
        // Use parentFragmentManager as this is a fragment showing another fragment (bottom sheet)
        customerListBottomSheet.show(parentFragmentManager, CustomerListBottomSheet.TAG)
    }


    private fun updateSelectedCustomerUI(customer: Customer?) {
        if (customer == null) {
            binding.noCustomerSelectedLayout.visibility = View.VISIBLE
            binding.customerDetailsLayout.visibility = View.GONE
            binding.statementLayout.visibility = View.GONE
            return
        }

        // Show customer details and statement
        binding.noCustomerSelectedLayout.visibility = View.GONE
        binding.customerDetailsLayout.visibility = View.VISIBLE
        binding.statementLayout.visibility = View.VISIBLE

        // Update customer details
        binding.customerNameText.text = "${customer.firstName} ${customer.lastName}"
        binding.customerTypeText.text = customer.customerType
        binding.customerPhoneText.text = customer.phoneNumber
        binding.customerAddressText.text = "${customer.streetAddress}, ${customer.city}, ${customer.state} ${customer.postalCode}"
    }

    private fun exportReportToPdf() {
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
                Toast.makeText(requireContext(), "Statement exported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Error exporting statement", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
