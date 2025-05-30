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

class GstReportFragment : Fragment() {

    private val TAG = "GstReportFragment"

    private var _binding: FragmentGstReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: GstReportAdapter
    private val currencyFormatter = DecimalFormat("#,##,##0.00")
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
        Log.d(TAG, "onViewCreated: Setting up fragment")

        setupViewModel()
        setupUI()
        setupObservers()

        // Generate the report
        Log.d(TAG, "onViewCreated: Generating initial GST report")
        viewModel.generateGstReport()
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
            if (isAdded) {
                findNavController().navigateUp()
            }
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

        // Display date range
        viewModel.startDate.value?.let { startDate ->
            viewModel.endDate.value?.let { endDate ->
                val startDateStr = dateFormat.format(startDate)
                val endDateStr = dateFormat.format(endDate)
                Log.d(TAG, "setupUI: Setting date range: $startDateStr to $endDateStr")
                binding.dateRangeText.text = "$startDateStr to $endDateStr"
            }
        }

        // Setup RecyclerView
        Log.d(TAG, "setupUI: Setting up RecyclerView")
        adapter = GstReportAdapter()
        binding.gstRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GstReportFragment.adapter
        }
        Log.d(TAG, "setupUI: UI setup completed")
    }

    private fun setupObservers() {
        Log.d(TAG, "setupObservers: Setting up LiveData observers")
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isAdded) return@observe
            Log.d(TAG, "setupObservers: Loading state changed: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.contentLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
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
            if (!isAdded) return@observe
            if (items != null) {
                Log.d(TAG, "setupObservers: Received ${items.size} GST report items")
                adapter.submitList(items)
                updateSummary(items)
            }
        }

        viewModel.startDate.observe(viewLifecycleOwner) { startDate ->
            if (!isAdded) return@observe
            startDate?.let {
                val startDateStr = dateFormat.format(it)
                Log.d(TAG, "setupObservers: Start date changed to: $startDateStr")
                viewModel.endDate.value?.let { endDate ->
                    val endDateStr = dateFormat.format(endDate)
                    binding.dateRangeText.text = "$startDateStr to $endDateStr"
                }
                viewModel.generateGstReport()
            }
        }

        viewModel.endDate.observe(viewLifecycleOwner) { endDate ->
            if (!isAdded) return@observe
            endDate?.let {
                val endDateStr = dateFormat.format(it)
                Log.d(TAG, "setupObservers: End date changed to: $endDateStr")
                viewModel.startDate.value?.let { startDate ->
                    val startDateStr = dateFormat.format(startDate)
                    binding.dateRangeText.text = "$startDateStr to $endDateStr"
                }
                viewModel.generateGstReport()
            }
        }
        Log.d(TAG, "setupObservers: All observers set up successfully")
    }

    private fun updateSummary(items: List<com.jewelrypos.swarnakhatabook.DataClasses.GstReportItem>) {
        if (!isAdded) return
        Log.d(TAG, "updateSummary: Updating summary for ${items.size} items")
        
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

        binding.taxableAmountValue.text = "₹${currencyFormatter.format(totalTaxableAmount)}"
        binding.cgstValue.text = "₹${currencyFormatter.format(totalCgst)}"
        binding.sgstValue.text = "₹${currencyFormatter.format(totalSgst)}"
        binding.totalTaxValue.text = "₹${currencyFormatter.format(totalTax)}"
        
        Log.d(TAG, "updateSummary: Summary updated successfully")
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