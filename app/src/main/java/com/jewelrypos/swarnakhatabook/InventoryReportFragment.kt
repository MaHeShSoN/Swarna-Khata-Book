package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.InventoryValueAdapter
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.PDFExportUtility
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInventoryReportBinding
import java.text.DecimalFormat

class InventoryReportFragment : Fragment() {

    private var _binding: FragmentInventoryReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var adapter: InventoryValueAdapter
    private val currencyFormatter = DecimalFormat("#,##,##0.00")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        setupUI()
        setupObservers()

        // Generate the report
        viewModel.generateInventoryReport()
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

        viewModel.inventoryItems.observe(viewLifecycleOwner) { items ->
            if (items != null) {
                adapter.submitList(items)
                updateTotalValue()
            }
        }

        viewModel.totalInventoryValue.observe(viewLifecycleOwner) { totalValue ->
            binding.totalValueOriginal.text = "₹${currencyFormatter.format(totalValue)}"
        }
    }

    private fun updateTotalValue() {
        // Calculate filtered total
        val filteredTotal = adapter.getFilteredItems().sumOf { it.totalStockValue }
        binding.totalValueFiltered.text = "₹${currencyFormatter.format(filteredTotal)}"
    }

    private fun exportReportToPdf() {
        val items = adapter.getFilteredItems()
        val totalValue = items.sumOf { it.totalStockValue }

        try {
            val pdfExporter = PDFExportUtility(requireContext())
            val success = pdfExporter.exportInventoryReport(items, totalValue)

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