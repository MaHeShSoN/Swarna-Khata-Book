package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.LowStockAdapter
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.PDFExportUtility
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentLowStockReportBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LowStockReportFragment : Fragment() {

    private var _binding: FragmentLowStockReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: LowStockAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLowStockReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupUI()
        setupObservers()

        // Generate the report
        viewModel.generateLowStockReport()
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

        // Set current date
        binding.reportDateText.text = "Report Date: ${dateFormat.format(Date())}"

        // Setup RecyclerView
        adapter = LowStockAdapter()
        binding.lowStockRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@LowStockReportFragment.adapter
        }

        // Setup category filter chip group
        binding.categoryFilterChipGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.chipAllItems -> adapter.filter("")
                R.id.chipGold -> adapter.filter("GOLD")
                R.id.chipSilver -> adapter.filter("SILVER")
                R.id.chipOther -> adapter.filter("OTHER")
            }
            updateItemCount()
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

        viewModel.lowStockItems.observe(viewLifecycleOwner) { items ->
            if (items != null) {
                adapter.submitList(items)
                updateItemCount()

                // Update empty state visibility
                binding.emptyStateLayout.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                binding.lowStockRecyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun updateItemCount() {
        val filteredItems = adapter.getFilteredItems()
        binding.itemCountText.text = "Items with Low Stock: ${filteredItems.size}"
    }

    private fun exportReportToPdf() {
        val items = adapter.getFilteredItems()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "No items to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfExporter = PDFExportUtility(requireContext())
            val success = pdfExporter.exportLowStockReport(items)

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