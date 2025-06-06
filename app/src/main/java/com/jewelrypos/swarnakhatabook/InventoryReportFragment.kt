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
import com.jewelrypos.swarnakhatabook.Adapters.InventoryValueAdapter
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.PDFExportUtility
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInventoryReportBinding
import java.text.DecimalFormat

class InventoryReportFragment : Fragment() {

    companion object {
        private const val TAG = "InventoryReportFragment"
    }

    private var _binding: FragmentInventoryReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: InventoryValueAdapter
    private val currencyFormatter = DecimalFormat("#,##,##0.00")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Creating view")
        _binding = FragmentInventoryReportBinding.inflate(inflater, container, false)
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

        Log.d(TAG, "Generating initial inventory report")
        viewModel.generateInventoryReport()
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

        // Setup RecyclerView
        adapter = InventoryValueAdapter()
        binding.inventoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@InventoryReportFragment.adapter
        }

        // Setup category filter chip group
        binding.categoryFilterChipGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.chipAllItems -> adapter.filter("")
                R.id.chipGold -> adapter.filter("GOLD")
                R.id.chipSilver -> adapter.filter("SILVER")
                R.id.chipOther -> adapter.filter("OTHER")
            }
            updateTotalValue()
        }
    }

    private fun setupObservers() {
        Log.d(TAG, "setupObservers: Setting up LiveData observers")
        
        viewModel.inventoryItems.observe(viewLifecycleOwner) { items ->
            val startTime = System.currentTimeMillis()
            if (items != null) {
                Log.d(TAG, "Received ${items.size} inventory items")
                adapter.submitList(items)
                updateTotalValue()
                // Ensure visibility is correctly set when items arrive
                binding.inventoryRecyclerView.visibility = View.VISIBLE
                Log.d(TAG, "Inventory items processed in ${System.currentTimeMillis() - startTime}ms")
            } else {
                // If items become null (e.g., on error)
                binding.inventoryRecyclerView.visibility = View.GONE
            }
        }

        viewModel.totalInventoryValue.observe(viewLifecycleOwner) { totalValue ->
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Total inventory value updated: $totalValue")
            binding.totalValueOriginal.text = "₹${currencyFormatter.format(totalValue)}"
            Log.d(TAG, "Total value update processed in ${System.currentTimeMillis() - startTime}ms")
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state changed: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Also control visibility of other elements based on loading state
            if (isLoading) {
                binding.inventoryRecyclerView.visibility = View.GONE
            } else {
                // When loading stops, the inventoryItems observer should handle visibility
                // based on whether data was successfully loaded.
                // However, we can add a fallback to show/hide the RecyclerView.
                if (viewModel.inventoryItems.value.isNullOrEmpty()) {
                    binding.inventoryRecyclerView.visibility = View.GONE
                } else {
                    binding.inventoryRecyclerView.visibility = View.VISIBLE
                }
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Log.e(TAG, "setupObservers: Error occurred: $message")
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                binding.inventoryRecyclerView.visibility = View.GONE // Hide list on error
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun updateTotalValue() {
        val startTime = System.currentTimeMillis()
        val filteredTotal = adapter.getFilteredItems().sumOf { it.totalStockValue }
        Log.d(TAG, "Updating filtered total value: $filteredTotal")
        binding.totalValueFiltered.text = "₹${currencyFormatter.format(filteredTotal)}"
        Log.d(TAG, "Total value update completed in ${System.currentTimeMillis() - startTime}ms")
    }

    private fun exportReportToPdf() {
        Log.d(TAG, "Starting PDF export")
        val items = adapter.getFilteredItems()
        val totalValue = items.sumOf { it.totalStockValue }

        try {
            val pdfExporter = PDFExportUtility(requireContext())
            val success = pdfExporter.exportInventoryReport(items, totalValue)

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