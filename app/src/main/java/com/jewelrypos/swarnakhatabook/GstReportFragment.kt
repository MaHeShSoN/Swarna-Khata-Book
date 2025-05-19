package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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
        _binding = FragmentGstReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupUI()
        setupObservers()

        // Generate the report
        viewModel.generateGstReport()
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
        adapter = GstReportAdapter()
        binding.gstRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GstReportFragment.adapter
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.contentLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }

        viewModel.gstReportItems.observe(viewLifecycleOwner) { items ->
            if (items != null) {
                adapter.submitList(items)
                updateSummary(items)
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
                // If date range changes, regenerate report
                viewModel.generateGstReport()
            }
        }

        viewModel.endDate.observe(viewLifecycleOwner) { endDate ->
            endDate?.let {
                val endDateStr = dateFormat.format(it)
                viewModel.startDate.value?.let { startDate ->
                    val startDateStr = dateFormat.format(startDate)
                    binding.dateRangeText.text = "$startDateStr to $endDateStr"
                }
                // If date range changes, regenerate report
                viewModel.generateGstReport()
            }
        }
    }

    private fun updateSummary(items: List<com.jewelrypos.swarnakhatabook.DataClasses.GstReportItem>) {
        val totalTaxableAmount = items.sumOf { it.taxableAmount }
        val totalCgst = items.sumOf { it.cgst }
        val totalSgst = items.sumOf { it.sgst }
        val totalTax = items.sumOf { it.totalTax }

        binding.taxableAmountValue.text = "₹${currencyFormatter.format(totalTaxableAmount)}"
        binding.cgstValue.text = "₹${currencyFormatter.format(totalCgst)}"
        binding.sgstValue.text = "₹${currencyFormatter.format(totalSgst)}"
        binding.totalTaxValue.text = "₹${currencyFormatter.format(totalTax)}"
    }

    private fun exportReportToPdf() {
        val items = viewModel.gstReportItems.value ?: return

        try {
            val startDate = viewModel.startDate.value?.let { dateFormat.format(it) } ?: ""
            val endDate = viewModel.endDate.value?.let { dateFormat.format(it) } ?: ""

            val job = viewLifecycleOwner.lifecycleScope.launch {
                val pdfExporter = PDFExportUtility(requireContext())
                val success = pdfExporter.exportGstReport(startDate, endDate, items)

                if (success) {
                    Toast.makeText(requireContext(), "Report exported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Error exporting report", Toast.LENGTH_SHORT).show()
                }
            }
            coroutineJobs.add(job)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        // Cancel all coroutine jobs to prevent memory leaks
        coroutineJobs.forEach { it.cancel() }
        coroutineJobs.clear()
        
        super.onDestroyView()
        _binding = null
    }
}