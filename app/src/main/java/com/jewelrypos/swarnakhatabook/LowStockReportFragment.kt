package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
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

    companion object {
        private const val TAG = "LowStockReportFragment"
    }

    private var _binding: FragmentLowStockReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: LowStockAdapter
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Creating view")
        _binding = FragmentLowStockReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Setting up fragment")

        setupViewModel()
        setupUI()
        setupObservers()

        Log.d(TAG, "Generating initial low stock report")
        viewModel.generateLowStockReport()
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
            findNavController().navigateUp()
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
        Log.d(TAG, "setupObservers: Setting up LiveData observers")
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state changed: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.contentLayout.visibility = if (isLoading) View.GONE else View.VISIBLE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Log.e(TAG, "Error occurred: $message")
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }

        viewModel.lowStockItems.observe(viewLifecycleOwner) { items ->
            if (items != null) {
                Log.d(TAG, "Received ${items.size} low stock items")
                adapter.submitList(items)
                updateItemCount()
            } else {
                Log.d(TAG, "No low stock items available")
                updateEmptyState(true)
            }
        }
    }

    private fun updateItemCount() {
        val filteredItems = adapter.getFilteredItems()
        binding.itemCountText.text = "Items with Low Stock: ${filteredItems.size}"
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        Log.d(TAG, "Updating empty state: $isEmpty")
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.lowStockRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun exportReportToPdf() {
        Log.d(TAG, "Starting PDF export")
        val items = adapter.getFilteredItems()
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), "No items to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfExporter = PDFExportUtility(requireContext())
            val success = pdfExporter.exportLowStockReport(items)

            if (success) {
                Log.d(TAG, "PDF export completed successfully")
                Toast.makeText(requireContext(), "Report exported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "PDF export failed")
                Toast.makeText(requireContext(), "Error exporting report", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during PDF export", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Cleaning up resources")
        super.onDestroyView()
        _binding = null
    }
}