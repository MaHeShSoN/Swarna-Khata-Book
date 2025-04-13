package com.jewelrypos.swarnakhatabook

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.PDFExportUtility
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentSalesReportBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

class SalesReportFragment : Fragment() {

    private var _binding: FragmentSalesReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private val currencyFormatter = DecimalFormat("#,##,##0.00")
    private val dateFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupUI()
        setupObservers()

        // Generate the report
        viewModel.generateSalesReport()
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

        // Initialize charts
        setupCategoryChart()
        setupCustomerTypeChart()
        setupSalesTrendChart()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.contentScrollView.visibility = if (isLoading) View.GONE else View.VISIBLE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }

        viewModel.salesReportData.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                updateSalesReportUI(data)
            }
        }

        // Also observe date range changes
        viewModel.startDate.observe(viewLifecycleOwner) { _ ->
            // If date range changes, regenerate report
            viewModel.generateSalesReport()
        }

        viewModel.endDate.observe(viewLifecycleOwner) { _ ->
            // If date range changes, regenerate report
            viewModel.generateSalesReport()
        }
    }

    private fun updateSalesReportUI(data: com.jewelrypos.swarnakhatabook.DataClasses.SalesReportData) {
        // Update summary
        binding.totalSalesValue.text = "₹${currencyFormatter.format(data.totalSales)}"
        binding.totalPaidValue.text = "₹${currencyFormatter.format(data.totalPaid)}"
        binding.totalUnpaidValue.text = "₹${currencyFormatter.format(data.totalSales - data.totalPaid)}"
        binding.invoiceCountValue.text = data.invoiceCount.toString()

        // Collection rate percentage
        val collectionRate = if (data.totalSales > 0) (data.totalPaid / data.totalSales) * 100 else 0.0
        binding.collectionRateValue.text = "${DecimalFormat("#0.00").format(collectionRate)}%"

        // Update category pie chart
        updateCategoryChart(data.salesByCategory)

        // Update customer type pie chart
        updateCustomerTypeChart(data.salesByCustomerType)

        // Update sales trend bar chart
        updateSalesTrendChart(data.salesByDate)
    }

    private fun setupCategoryChart() {
        binding.categoryPieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
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
            centerText = "Sales by Category"
            setCenterTextSize(16f)
            legend.isEnabled = true
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(12f)
            animateY(1400)
        }
    }

    private fun setupCustomerTypeChart() {
        binding.customerTypePieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
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
            centerText = "Sales by Customer Type"
            setCenterTextSize(16f)
            legend.isEnabled = true
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(12f)
            animateY(1400)
        }
    }

    private fun setupSalesTrendChart() {
        binding.salesTrendChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            isHighlightFullBarEnabled = false
            setPinchZoom(false)
            setScaleEnabled(false)

            val xAxis = xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.labelRotationAngle = -45f

            axisLeft.setDrawGridLines(true)
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false

            legend.isEnabled = true

            animateY(1500)
        }
    }

    private fun updateCategoryChart(categories: List<com.jewelrypos.swarnakhatabook.DataClasses.SalesByCategoryItem>) {
        val entries = ArrayList<PieEntry>()

        // Add entries
        categories.forEach { category ->
            entries.add(PieEntry(category.amount.toFloat(), category.category))
        }

        val dataSet = PieDataSet(entries, "Categories")
        dataSet.colors = getColorList(categories.size)
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(binding.categoryPieChart))
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.WHITE)

        binding.categoryPieChart.data = data
        binding.categoryPieChart.invalidate()
    }

    private fun updateCustomerTypeChart(customerTypes: List<com.jewelrypos.swarnakhatabook.DataClasses.SalesByCustomerTypeItem>) {
        val entries = ArrayList<PieEntry>()

        // Add entries
        customerTypes.forEach { type ->
            entries.add(PieEntry(type.amount.toFloat(), type.customerType))
        }

        val dataSet = PieDataSet(entries, "Customer Types")
        dataSet.colors = getColorList(customerTypes.size)
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(binding.customerTypePieChart))
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.WHITE)

        binding.customerTypePieChart.data = data
        binding.customerTypePieChart.invalidate()
    }

    private fun updateSalesTrendChart(salesByDate: List<com.jewelrypos.swarnakhatabook.DataClasses.SalesByDateItem>) {
        val entries = ArrayList<BarEntry>()
        val xLabels = ArrayList<String>()

        // Add entries
        salesByDate.forEachIndexed { index, item ->
            entries.add(BarEntry(index.toFloat(), item.amount.toFloat()))
            xLabels.add(dateFormat.format(item.date))
        }

        val dataSet = BarDataSet(entries, "Sales Trend")
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.my_light_primary)

        val data = BarData(dataSet)
        data.setValueTextSize(10f)
        data.barWidth = 0.9f

        binding.salesTrendChart.data = data
        binding.salesTrendChart.xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)
        binding.salesTrendChart.invalidate()
    }

    private fun getColorList(size: Int): List<Int> {
        val colors = ArrayList<Int>()

        // Use MATERIAL colors from MPAndroidChart
        for (i in 0 until size) {
            colors.add(ColorTemplate.MATERIAL_COLORS[i % ColorTemplate.MATERIAL_COLORS.size])
        }

        return colors
    }

    private fun exportReportToPdf() {
        val data = viewModel.salesReportData.value ?: return

        try {
            val startDate = viewModel.startDate.value?.let { dateFormat.format(it) } ?: ""
            val endDate = viewModel.endDate.value?.let { dateFormat.format(it) } ?: ""

            val pdfExporter = PDFExportUtility(requireContext())
            val success = pdfExporter.exportSalesReport(
                startDate = startDate,
                endDate = endDate,
                salesData = data
            )

            if (success) {
                Toast.makeText(requireContext(), "Report exported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Error exporting report", Toast.LENGTH_SHORT).show()
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