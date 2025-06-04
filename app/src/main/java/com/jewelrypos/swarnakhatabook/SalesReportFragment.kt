package com.jewelrypos.swarnakhatabook

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.MPPointF
import com.google.android.material.datepicker.MaterialDatePicker
import com.jewelrypos.swarnakhatabook.Adapters.TopCustomersAdapter // Import new adapter
import com.jewelrypos.swarnakhatabook.Adapters.TopItemsAdapter // Import new adapter
import com.jewelrypos.swarnakhatabook.DataClasses.SalesReportData
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.PDFExportUtility // Assuming you might add PDF export later
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentSalesReportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class SalesReportFragment : Fragment() {

    companion object {
        private const val TAG = "SalesReportFragment"
    }

    private var _binding: FragmentSalesReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private val currencyFormatter = DecimalFormat("₹#,##,##0.00")
    private val percentFormatter = DecimalFormat("0.0'%'")
    private val dateFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault()) // Short format for charts/display

    // New Adapters
    private lateinit var topItemsAdapter: TopItemsAdapter
    private lateinit var topCustomersAdapter: TopCustomersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Creating view")
        _binding = FragmentSalesReportBinding.inflate(inflater, container, false)
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
        
        setupCharts()
        Log.d(TAG, "onViewCreated: Charts setup completed in ${System.currentTimeMillis() - startTime}ms")
        
        setupObservers()
        Log.d(TAG, "onViewCreated: Observers setup completed in ${System.currentTimeMillis() - startTime}ms")

        Log.d(TAG, "onViewCreated: Initial setup completed in ${System.currentTimeMillis() - startTime}ms")

        // Initial load might be triggered by ViewModel init or date setting
        // viewModel.loadSalesReport() // Ensure data is loaded if not done automatically
    }

    private fun setupViewModel() {
        Log.d(TAG, "setupViewModel: Initializing ReportViewModel")
        val factory = ReportViewModelFactory(requireActivity().application)
        // Scope to Activity to share date range with ReportsFragment and CustomerStatementFragment
        viewModel = ViewModelProvider(requireActivity(), factory)[ReportViewModel::class.java]
        Log.d(TAG, "setupViewModel: ReportViewModel initialized successfully")
    }

    private fun setupUI() {
        Log.d(TAG, "setupUI: Setting up UI components")
        // Toolbar
        binding.topAppBar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {

                R.id.action_export_pdf -> {
                    exportReportToPdf() // Implement this function
                    true
                }
                else -> false
            }
        }

        // Date Range Card Click Listener
        binding.dateRangeCard.setOnClickListener { showDateRangePicker() }

        // Initialize New RecyclerViews
        topItemsAdapter = TopItemsAdapter()
        binding.topItemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = topItemsAdapter
            // Optional: Add item decoration for dividers
        }

        topCustomersAdapter = TopCustomersAdapter()
        binding.topCustomersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = topCustomersAdapter
            // Optional: Add item decoration for dividers
        }

        // Initial Date Display
        updateDateRangeText()
    }

    private fun setupCharts() {
        Log.d(TAG, "setupCharts: Initializing charts")
        // Common Pie Chart Setup
        fun setupPieChart(chart: com.github.mikephil.charting.charts.PieChart) {
            chart.apply {
                setUsePercentValues(true)
                description.isEnabled = false
                setExtraOffsets(5f, 10f, 5f, 5f)
                dragDecelerationFrictionCoef = 0.95f
                isDrawHoleEnabled = true
                setHoleColor(Color.WHITE)
                setTransparentCircleColor(Color.WHITE)
                setTransparentCircleAlpha(110)
                holeRadius = 58f
                transparentCircleRadius = 61f
                setDrawCenterText(true)
                rotationAngle = 0f
                isRotationEnabled = true
                isHighlightPerTapEnabled = true
                animateY(1400, Easing.EaseInOutQuad)
                legend.isEnabled = false // Keep legend off for cleaner look with small charts
                setEntryLabelColor(Color.BLACK)
                setEntryLabelTextSize(10f) // Smaller text size
            }
        }

        // Common Bar Chart Setup
        fun setupBarChart(chart: com.github.mikephil.charting.charts.BarChart) {
            chart.apply {
                description.isEnabled = false
                setPinchZoom(false)
                setDrawGridBackground(false)
                setDrawBarShadow(false)
                setDrawValueAboveBar(true)
                // X Axis settings
                xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f // only intervals of 1 day
                xAxis.labelCount = 7 // Adjust based on expected data points
                xAxis.valueFormatter = object : ValueFormatter() { // Custom formatter if needed
                    // override fun getFormattedValue(value: Float): String { return yourDateLabels[value.toInt()] }
                }
                // Y Axis settings (Left)
                axisLeft.setDrawGridLines(false)
                axisLeft.axisMinimum = 0f // start at zero
                // Y Axis settings (Right) - Disable
                axisRight.isEnabled = false
                // Legend settings
                legend.isEnabled = false
                // Animation
                animateY(1500)
            }
        }


        setupPieChart(binding.salesByCategoryChart)
//        setupPieChart(binding.salesByCustomerTypeChart)
        setupBarChart(binding.salesByDateChart) // Setup bar chart style
    }


    private fun setupObservers() {
        Log.d(TAG, "setupObservers: Setting up LiveData observers")
        
        viewModel.salesReportData.observe(viewLifecycleOwner) { data ->
            val startTime = System.currentTimeMillis()
            if (data != null) {
                Log.d(TAG, "Received sales report data: totalSales=${data.totalSales}, invoiceCount=${data.invoiceCount}")
                updateSummaryUI(data)
                updateSalesByCategoryChart(data)
                updateSalesByDateChart(data)
                Log.d(TAG, "Sales report data processed in ${System.currentTimeMillis() - startTime}ms")
            }
        }

        viewModel.topSellingItems.observe(viewLifecycleOwner) { items ->
            val startTime = System.currentTimeMillis()
            if (items != null) {
                Log.d(TAG, "Received ${items.size} top selling items")
                topItemsAdapter.submitList(items)
                Log.d(TAG, "Top selling items processed in ${System.currentTimeMillis() - startTime}ms")
            }
        }

        viewModel.topCustomers.observe(viewLifecycleOwner) { customers ->
            val startTime = System.currentTimeMillis()
            if (customers != null) {
                Log.d(TAG, "Received ${customers.size} top customers")
                topCustomersAdapter.submitList(customers)
                Log.d(TAG, "Top customers processed in ${System.currentTimeMillis() - startTime}ms")
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state changed: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Disable interaction while loading?
            binding.dateRangeCard.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Log.e(TAG, "Error occurred: $message")
                binding.errorTextView.text = message
                binding.errorTextView.visibility = View.VISIBLE
                // Hide other content on error
                binding.summaryCard.visibility = View.GONE
                binding.salesTrendCard.visibility = View.GONE
                binding.salesCategoryCard.visibility = View.GONE
//                binding.salesCustomerTypeCard.visibility = View.GONE
                binding.topItemsCard.visibility = View.GONE
                binding.topCustomersCard.visibility = View.GONE
                viewModel.clearErrorMessage()
            } else {
                binding.errorTextView.visibility = View.GONE
                // Make sure content is visible again if error is cleared
                // Visibility handled by data observers below
            }
        }

        // Observe date changes to update display text
        viewModel.startDate.observe(viewLifecycleOwner) { updateDateRangeText() }
        viewModel.endDate.observe(viewLifecycleOwner) { updateDateRangeText() }
    }

    private fun updateSummaryUI(data: SalesReportData) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "updateSummaryUI: Starting summary update")
        
        binding.totalSalesValue.text = currencyFormatter.format(data.totalSales)
        binding.totalPaidValue.text = currencyFormatter.format(data.paidAmount)
        binding.totalUnpaidValue.text = currencyFormatter.format(data.unpaidAmount)
        binding.collectionRateValue.text = percentFormatter.format(data.collectionRate)
        binding.invoiceCountValue.text = data.invoiceCount.toString()
        
        Log.d(TAG, "updateSummaryUI: Completed in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun updateSalesByCategoryChart(data: SalesReportData) {
        Log.d(TAG, "updateSalesByCategoryChart: Updating category chart")
        val entries = ArrayList<PieEntry>()
        data.salesByCategory.forEach {
            // Only add entries with significant value to avoid clutter
            if (it.amount > 0) {
                entries.add(PieEntry(it.amount.toFloat(), it.category))
            }
        }

        if (entries.isEmpty()){
            Log.d(TAG, "No category data available for chart")
            binding.salesByCategoryChart.clear()
            binding.salesByCategoryChart.setNoDataText(getString(R.string.no_category_data_available))
            binding.salesByCategoryChart.invalidate()
            return
        }


        val dataSet = PieDataSet(entries, "Sales by Category")
        dataSet.setDrawIcons(false)
        dataSet.sliceSpace = 3f
        dataSet.iconsOffset = MPPointF(0f, 40f)
        dataSet.selectionShift = 5f

        // Colors
        val colors = ArrayList<Int>()
        for (c in ColorTemplate.VORDIPLOM_COLORS) colors.add(c)
        for (c in ColorTemplate.JOYFUL_COLORS) colors.add(c)
        for (c in ColorTemplate.COLORFUL_COLORS) colors.add(c)
        for (c in ColorTemplate.LIBERTY_COLORS) colors.add(c)
        for (c in ColorTemplate.PASTEL_COLORS) colors.add(c)
        colors.add(ColorTemplate.getHoloBlue())
        dataSet.colors = colors

        val pieData = PieData(dataSet)
        pieData.setValueFormatter(PercentFormatter(binding.salesByCategoryChart)) // Use chart context
        pieData.setValueTextSize(11f)
        pieData.setValueTextColor(Color.BLACK)


        binding.salesByCategoryChart.data = pieData
        binding.salesByCategoryChart.highlightValues(null)
        binding.salesByCategoryChart.invalidate()
        binding.salesByCategoryChart.animateY(1400, Easing.EaseInOutQuad)

    }

//    private fun updateSalesByCustomerTypeChart(data: SalesReportData) {
//        val entries = ArrayList<PieEntry>()
//        data.salesByCustomerType.forEach {
//            if (it.amount > 0) {
//                entries.add(PieEntry(it.amount.toFloat(), it.customerType))
//            }
//        }
//
////        if (entries.isEmpty()){
////            binding.salesByCustomerTypeChart.clear()
////            binding.salesByCustomerTypeChart.setNoDataText(getString(R.string.no_customer_type_data_available))
////            binding.salesByCustomerTypeChart.invalidate()
////            return
////        }
//
//        val dataSet = PieDataSet(entries, "Sales by Customer Type")
//        // ... similar setup as category chart ...
//        dataSet.setDrawIcons(false)
//        dataSet.sliceSpace = 3f
//        dataSet.selectionShift = 5f
//        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList() // Different colors
//
//        val pieData = PieData(dataSet)
//        pieData.setValueFormatter(PercentFormatter(binding.salesByCustomerTypeChart))
//        pieData.setValueTextSize(11f)
//        pieData.setValueTextColor(Color.BLACK)
//
//        binding.salesByCustomerTypeChart.data = pieData
//        binding.salesByCustomerTypeChart.highlightValues(null)
//        binding.salesByCustomerTypeChart.invalidate()
//        binding.salesByCustomerTypeChart.animateY(1400, Easing.EaseInOutQuad)
//    }

    private fun updateSalesByDateChart(data: SalesReportData) {
        val entries = ArrayList<BarEntry>()
        val dateLabels = ArrayList<String>()

        // Sort data by date if necessary (assuming SalesByDateItem has a parseable date string or Date object)
        // val sortedSalesByDate = data.salesByDate.sortedBy { /* parse date */ }

        data.salesByDate.forEachIndexed { index, dailySale ->
            entries.add(BarEntry(index.toFloat(), dailySale.amount.toFloat()))
            // Format the date for the label (e.g., "dd MMM")
            // val formattedDate = /* format dailySale.dateString or dateObject */
            dateLabels.add(dailySale.date.toString()) // Assuming dailySale.date is already the formatted string label
        }

        if (entries.isEmpty()){
            binding.salesByDateChart.clear()
            binding.salesByDateChart.setNoDataText(getString(R.string.no_sales_trend_data_available))
            binding.salesByDateChart.invalidate()
            return
        }


        val dataSet = BarDataSet(entries, "Daily Sales")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.setDrawValues(true) // Show values on bars
        dataSet.valueTextSize = 10f

        val barData = BarData(dataSet)
        barData.barWidth = 0.9f // Adjust bar width

        binding.salesByDateChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val index = value.toInt()
                return if (index >= 0 && index < dateLabels.size) dateLabels[index] else ""
            }
        }
        // Adjust label count based on number of entries to prevent overlap
        binding.salesByDateChart.xAxis.labelCount = entries.size.coerceAtMost(7)
        binding.salesByDateChart.xAxis.labelRotationAngle = -45f // Rotate labels if they overlap


        binding.salesByDateChart.data = barData
        binding.salesByDateChart.setFitBars(true) // make the x-axis fit exactly all bars
        binding.salesByDateChart.invalidate() // refresh
        binding.salesByDateChart.animateY(1500)
    }


    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.select_date_range_title)

        // Set initial selection based on ViewModel
        val start = viewModel.startDate.value?.time ?: MaterialDatePicker.todayInUtcMilliseconds()
        val end = viewModel.endDate.value?.time ?: MaterialDatePicker.todayInUtcMilliseconds()
        builder.setSelection(androidx.core.util.Pair(start, end))

        val datePicker = builder.build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = Date(selection.first)
            // Ensure end date includes the whole day
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = selection.second
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endDate = calendar.time

            Log.d("SalesReport", "Date Range Selected: $startDate to $endDate")
            viewModel.setDateRange(startDate, endDate) // This will trigger data reload via observer
        }

        datePicker.show(parentFragmentManager, datePicker.toString())
    }

    // Helper to update date range text consistently
    private fun updateDateRangeText() {
        val startDate = viewModel.startDate.value
        val endDate = viewModel.endDate.value
        if (startDate != null && endDate != null) {
            val startDateStr = dateFormat.format(startDate)
            val endDateStr = dateFormat.format(endDate)
            binding.dateRangeText.text = "$startDateStr to $endDateStr"
        } else {
            binding.dateRangeText.text = getString(R.string.select_a_date_range) // Use string resource
        }
    }

    // --- Implemented PDF Export Function ---
    private fun exportReportToPdf() {
        // Get current data from ViewModel
        // Ensure you have the correct LiveData names as defined in your ViewModel
        val reportData = viewModel.salesReportData.value
        val topItems = viewModel.topSellingItems.value
        val topCustomers = viewModel.topCustomers.value
        val startDate = viewModel.startDate.value
        val endDate = viewModel.endDate.value

        // --- Null Checks ---
        // Use requireContext() safely as Fragment should be attached when action is triggered
        val currentContext = context ?: return // Exit if context is null

        if (reportData == null) {
            // Use string resources for user-facing messages
            Toast.makeText(currentContext, R.string.error_report_data_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }
        if (startDate == null || endDate == null) {
            Toast.makeText(currentContext, R.string.error_date_range_not_set, Toast.LENGTH_SHORT).show()
            return
        }
        // topItems and topCustomers can be empty, but check for null if LiveData allows it
        if (topItems == null || topCustomers == null) {
            Toast.makeText(currentContext, R.string.error_list_data_not_loaded, Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress indicator (optional, but recommended)
        // binding.progressBarPdf.visibility = View.VISIBLE // Use a dedicated PDF progress bar if you add one
        binding.progressBar.visibility = View.VISIBLE // Reuse main progress bar
        binding.topAppBar.menu.findItem(R.id.action_export_pdf)?.isEnabled = false // Disable button during export

        // Launch a coroutine for PDF generation (background thread)
        viewLifecycleOwner.lifecycleScope.launch {
            var success = false
            // Use string resources for messages
            var message = currentContext.getString(R.string.error_exporting_report) // Default error message
            var generatedFileUri: Uri? = null // To store the URI if successful

            try {
                val pdfExporter = PDFExportUtility(requireContext()) // Use requireContext() for non-null context
                // Call the method in PDFExportUtility (needs implementation from pdf_export_utility_sales_report immersive)
                // This should run file IO on a background thread (handled by withContext in the utility method)
                generatedFileUri = pdfExporter.exportSalesReport(
                    startDate,
                    endDate,
                    reportData,
                    topItems, // Pass the non-null list (can be empty)
                    topCustomers // Pass the non-null list (can be empty)
                )

                // Check result (back on main thread is handled in finally block)
                if (generatedFileUri != null) {
                    success = true
                    message = currentContext.getString(R.string.report_exported_success)
                }
                // No need for explicit else, default message covers failure

            } catch (e: Exception) {
                Log.e("SalesReportFragment", "Error during PDF export", e)
                // Update message (will be shown in finally block)
                message = "${currentContext.getString(R.string.error_exporting_report)}: ${e.localizedMessage}"

            } finally {
                // Switch back to main thread to update UI
                withContext(Dispatchers.Main) {
                    // binding.progressBarPdf.visibility = View.GONE
                    binding.progressBar.visibility = View.GONE // Hide progress bar
                    binding.topAppBar.menu.findItem(R.id.action_export_pdf)?.isEnabled = true // Re-enable button
                    Toast.makeText(currentContext, message, Toast.LENGTH_LONG).show()

                    // Optional: If successful, offer to share or open the PDF
                    if (success && generatedFileUri != null) {
                        // You can uncomment these lines if you implement sharePdf/openPdf in PDFExportUtility
                         val pdfUtility = PDFExportUtility(requireContext())
                         pdfUtility.sharePdf(generatedFileUri)
                        // OR
                        // pdfUtility.openPdf(generatedFileUri)
                    }
                }
            }
        }
    }
    // --- End of Implemented PDF Export Function ---


    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Cleaning up resources")
        super.onDestroyView()
        // Avoid memory leaks with charts
        binding.salesByCategoryChart.clear()
//        binding.salesByCustomerTypeChart.clear()
        binding.salesByDateChart.clear()
        _binding = null
    }
}
