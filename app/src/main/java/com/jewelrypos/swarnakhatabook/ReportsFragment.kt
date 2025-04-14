package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.jewelrypos.swarnakhatabook.Adapters.ReportTypeAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.ReportType
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentReportsBinding

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var reportAdapter: ReportTypeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
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
        viewModel = ViewModelProvider(this, factory)[ReportViewModel::class.java]
    }

    private fun setupUI() {
        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        // Setup date range selector
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.dateRangeText.text = getString(
            R.string.date_range_format,
            dateFormat.format(viewModel.startDate.value!!),
            dateFormat.format(viewModel.endDate.value!!)
        )

        binding.dateRangeCard.setOnClickListener {
            showDateRangePicker()
        }

        // Setup report types recycler view
        reportAdapter = ReportTypeAdapter { reportType ->
            navigateToReportDetail(reportType)
        }

        binding.reportsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reportAdapter
        }

        // Set report types
        val reportTypes = listOf(
            ReportType(
                id = "sales_report",
                title = "Sales Report",
                description = "View sales summary by product type, customer type, and time period",
                iconResId = R.drawable.hugeicons__chart_up
            ),
            ReportType(
                id = "inventory_valuation",
                title = "Inventory Valuation",
                description = "Current inventory value and stock levels by product category",
                iconResId = R.drawable.mingcute__inventory_line
            ),
            ReportType(
                id = "customer_statement",
                title = "Customer Account Statement",
                description = "Detailed transaction history for individual customers",
                iconResId = R.drawable.f7__doc_person
            ),
            ReportType(
                id = "gst_report",
                title = "GST Report",
                description = "Summary of GST collected for tax filing",
                iconResId = R.drawable.hugeicons__taxes
            ),
            ReportType(
                id = "low_stock",
                title = "Low Stock Report",
                description = "Items below reorder threshold that need attention",
                iconResId = R.drawable.lets_icons__box_light
            )
        )

        reportAdapter.submitList(reportTypes)
    }

    private fun setupObservers() {
        // Observe date range changes
        viewModel.startDate.observe(viewLifecycleOwner) { startDate ->
            viewModel.endDate.value?.let { endDate ->
                updateDateRangeDisplay(startDate, endDate)
            }
        }

        viewModel.endDate.observe(viewLifecycleOwner) { endDate ->
            viewModel.startDate.value?.let { startDate ->
                updateDateRangeDisplay(startDate, endDate)
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun updateDateRangeDisplay(startDate: Date, endDate: Date) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.dateRangeText.text = getString(
            R.string.date_range_format,
            dateFormat.format(startDate),
            dateFormat.format(endDate)
        )
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setSelection(
                androidx.core.util.Pair(
                    viewModel.startDate.value!!.time,
                    viewModel.endDate.value!!.time
                )
            )
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = Date(selection.first)
            val endDate = Date(selection.second)

            // Add day end time to end date (23:59:59)
            val calendar = Calendar.getInstance()
            calendar.time = endDate
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)

            viewModel.setDateRange(startDate, calendar.time)
        }

        dateRangePicker.show(parentFragmentManager, "DATE_PICKER")
    }

    private fun navigateToReportDetail(reportType: ReportType) {
        when (reportType.id) {
            "sales_report" -> navigateToSalesReport()
            "inventory_valuation" -> navigateToInventoryReport()
            "customer_statement" -> navigateToCustomerStatementReport()
            "gst_report" -> navigateToGstReport()
            "low_stock" -> navigateToLowStockReport()
        }
    }

    private fun navigateToSalesReport() {
        findNavController().navigate(R.id.action_reportsFragment_to_salesReportFragment)
    }

    private fun navigateToInventoryReport() {
        findNavController().navigate(R.id.action_reportsFragment_to_inventoryReportFragment)

    }

    private fun navigateToCustomerStatementReport() {
        findNavController().navigate(R.id.action_reportsFragment_to_customerStatementFragment)
    }

    private fun navigateToGstReport() {
        findNavController().navigate(R.id.action_reportsFragment_to_gstReportFragment)
    }

    private fun navigateToLowStockReport() {
        findNavController().navigate(R.id.action_reportsFragment_to_lowStockReportFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}