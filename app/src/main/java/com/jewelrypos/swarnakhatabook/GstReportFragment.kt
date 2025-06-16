package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.GstReportAdapter
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.PDFExportUtility
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentGstReportBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.google.android.material.datepicker.MaterialDatePicker
import androidx.core.util.Pair
import java.util.Date
import java.util.Calendar
import java.util.TimeZone

class GstReportFragment : Fragment() {

    private val TAG = "GstReportFragment"

    private var _binding: FragmentGstReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: GstReportAdapter
    private val currencyFormatter = DecimalFormat("#,##,##0.00")
    private val integerFormatter = DecimalFormat("#,##,##0")
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    // Track coroutine jobs to prevent memory leaks
    private val coroutineJobs = mutableListOf<Job>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Initializing fragment view")
        _binding = FragmentGstReportBinding.inflate(inflater, container, false)
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
        Log.d(TAG, "onViewCreated: Observers setup completed in ${System.currentTimeMillis() - startTime}ms")

        // Generate the report
        Log.d(TAG, "onViewCreated: Starting initial GST report generation")
        viewModel.generateGstReport()
        Log.d(TAG, "onViewCreated: Initial setup completed in ${System.currentTimeMillis() - startTime}ms")

        viewModel.selectedChipId.value?.let { chipId ->
            Log.d(TAG, "onViewCreated: Setting initial chip selection to: $chipId")
            binding.dateFilterChipGroup.check(chipId)
        }
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
            Log.d(TAG, "setupUI: Navigation back button clicked")
                findNavController().navigateUp()
        }

        // Setup export button
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_export_pdf -> {
                    Log.d(TAG, "setupUI: Export PDF option selected")
                    if (isAdded) {
                        exportReportToPdf()
                    }
                    true
                }
                else -> false
            }
        }

        // Setup date range card click listener
        binding.dateRangeCard.setOnClickListener {
            showDateRangePicker()
        }

        // Setup date filter chips
        setupDateFilterChips()

        // Initial Date Display
        updateDateRangeText()

        // Setup RecyclerView
        Log.d(TAG, "setupUI: Setting up RecyclerView")
        adapter = GstReportAdapter()
        binding.gstRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GstReportFragment.adapter
        }
        Log.d(TAG, "setupUI: UI setup completed")
    }

    private fun setupDateFilterChips() {
        binding.dateFilterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            val selectedChipId = checkedIds[0]
            viewModel.setSelectedChipGst(selectedChipId)
            val dateRange = when (selectedChipId) {
                R.id.chipThisWeek -> getThisWeekDateRange()
                R.id.chipThisMonth -> getThisMonthDateRange()
                R.id.chipLastMonth -> getLastMonthDateRange()
                R.id.chipThisQuarter -> getThisQuarterDateRange()
                R.id.chipThisYear -> getThisYearDateRange()
                R.id.chipLastYear -> getLastYearDateRange()
                R.id.chipCustom -> {
                    showDateRangePicker()
                    return@setOnCheckedStateChangeListener
                }
                else -> return@setOnCheckedStateChangeListener
            }
            
            viewModel.setDateRange(dateRange.first, dateRange.second, true)
        }
    }

    private fun getThisWeekDateRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return Pair(startDate, endDate)
    }

    private fun getThisMonthDateRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return Pair(startDate, endDate)
    }

    private fun getLastMonthDateRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.MONTH, -1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return Pair(startDate, endDate)
    }

    private fun getThisQuarterDateRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val currentMonth = calendar.get(Calendar.MONTH)
        val quarterStartMonth = (currentMonth / 3) * 3

        calendar.set(Calendar.MONTH, quarterStartMonth)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        calendar.set(Calendar.MONTH, quarterStartMonth + 2)
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return Pair(startDate, endDate)
    }

    private fun getThisYearDateRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        
        // Set to January 1st of current year
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        // Set to December 31st of current year
        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
        calendar.set(Calendar.DAY_OF_MONTH, 31)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return Pair(startDate, endDate)
    }

    private fun getLastYearDateRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.YEAR, -1)
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
        calendar.set(Calendar.DAY_OF_MONTH, 31)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endDate = calendar.time

        return Pair(startDate, endDate)
    }

    private fun setupObservers() {
        Log.d(TAG, "setupObservers: Setting up LiveData observers")
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state changed: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.contentLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
            binding.dateRangeCard.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!isAdded) return@observe
            if (!message.isNullOrEmpty()) {
                Log.e(TAG, "setupObservers: Error message received: $message")
                context?.let {
                    Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
                }
                viewModel.clearErrorMessage()
            }
        }

        viewModel.gstReportItems.observe(viewLifecycleOwner) { items ->
            val startTime = System.currentTimeMillis()
            if (items != null) {
                Log.d(TAG, "Received ${items.size} GST report items")
                adapter.submitList(items)
                updateSummary(items)
                Log.d(TAG, "GST report items processed in ${System.currentTimeMillis() - startTime}ms")
            }
        }

        viewModel.startDate.observe(viewLifecycleOwner) { startDate ->
            if (!isAdded) return@observe
            startDate?.let {
                updateDateRangeText()
            }
        }

        viewModel.endDate.observe(viewLifecycleOwner) { endDate ->
            if (!isAdded) return@observe
            endDate?.let {
                updateDateRangeText()
            }
        }
        Log.d(TAG, "setupObservers: All observers set up successfully")
    }

    private fun updateSummary(items: List<com.jewelrypos.swarnakhatabook.DataClasses.GstReportItem>) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "updateSummary: Starting summary update for ${items.size} items")
        
        val totalTaxableAmount = items.sumOf { it.taxableAmount }
        val totalCgst = items.sumOf { it.cgst }
        val totalSgst = items.sumOf { it.sgst }
        val totalTax = items.sumOf { it.totalTax }

        Log.d(TAG, """
            updateSummary: Totals calculated:
            - Taxable Amount: $totalTaxableAmount
            - CGST: $totalCgst
            - SGST: $totalSgst
            - Total Tax: $totalTax
        """.trimIndent())

        binding.taxableAmountValue.text = "₹${integerFormatter.format(totalTaxableAmount)}"
        binding.cgstValue.text = "₹${integerFormatter.format(totalCgst)}"
        binding.sgstValue.text = "₹${integerFormatter.format(totalSgst)}"
        binding.totalTaxValue.text = "₹${integerFormatter.format(totalTax)}"
        
        Log.d(TAG, "updateSummary: Completed in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.select_date_range_title)
            .setTheme(R.style.CustomDatePickerTheme)

        // Set initial selection based on ViewModel
        val start = viewModel.startDate.value?.time ?: MaterialDatePicker.todayInUtcMilliseconds()
        val end = viewModel.endDate.value?.time ?: MaterialDatePicker.todayInUtcMilliseconds()
        builder.setSelection(Pair(start, end))

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

            Log.d(TAG, "Date Range Selected: $startDate to $endDate")
            viewModel.setDateRange(startDate, endDate)
        }

        datePicker.show(parentFragmentManager, datePicker.toString())
    }

    private fun updateDateRangeText() {
        val startDate = viewModel.startDate.value
        val endDate = viewModel.endDate.value
        if (startDate != null && endDate != null) {
            val startDateStr = dateFormat.format(startDate)
            val endDateStr = dateFormat.format(endDate)
            binding.dateRangeText.text = "$startDateStr to $endDateStr"
        } else {
            binding.dateRangeText.text = getString(R.string.select_a_date_range)
        }
    }

    private fun exportReportToPdf() {
        if (!isAdded) return
        val items = viewModel.gstReportItems.value ?: return
        Log.d(TAG, "exportReportToPdf: Starting PDF export for ${items.size} items")

        try {
            val startDate = viewModel.startDate.value?.let { dateFormat.format(it) } ?: ""
            val endDate = viewModel.endDate.value?.let { dateFormat.format(it) } ?: ""
            Log.d(TAG, "exportReportToPdf: Date range - $startDate to $endDate")

            val job = viewLifecycleOwner.lifecycleScope.launch {
                try {
                    Log.d(TAG, "exportReportToPdf: Creating PDF exporter")
                    context?.let { ctx ->
                        val pdfExporter = PDFExportUtility(ctx)
                        val success = pdfExporter.exportGstReport(startDate, endDate, items)

                        if (isAdded) {
                            if (success) {
                                Log.d(TAG, "exportReportToPdf: PDF export successful")
                                Toast.makeText(ctx, "Report exported successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Log.e(TAG, "exportReportToPdf: PDF export failed")
                                Toast.makeText(ctx, "Error exporting report", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "exportReportToPdf: Error during PDF export", e)
                    if (isAdded) {
                        context?.let { ctx ->
                            Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            coroutineJobs.add(job)
            Log.d(TAG, "exportReportToPdf: Export job added to coroutine jobs")
        } catch (e: Exception) {
            Log.e(TAG, "exportReportToPdf: Error initializing PDF export", e)
            if (isAdded) {
                context?.let { ctx ->
                    Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Cleaning up resources")
        // Cancel all coroutine jobs to prevent memory leaks
        coroutineJobs.forEach { 
            Log.d(TAG, "onDestroyView: Cancelling coroutine job")
            it.cancel() 
        }
        coroutineJobs.clear()
        
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView: Cleanup completed")
    }
}