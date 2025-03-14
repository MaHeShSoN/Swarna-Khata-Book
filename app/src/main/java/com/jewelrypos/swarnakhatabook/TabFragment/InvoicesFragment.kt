package com.jewelrypos.swarnakhatabook.TabFragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.InvoicesAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoiceBinding

class InvoicesFragment : Fragment() {

    private var _binding: FragmentInvoiceBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: InvoicesAdapter
    private var allInvoices = listOf<Invoice>()
    private var currentSearchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadInvoices()
    }

    private fun setupRecyclerView() {
        adapter = InvoicesAdapter(emptyList())
        adapter.setOnItemClickListener { invoice ->
            showInvoiceDetails(invoice)
        }

        binding.recyclerViewInvoices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@InvoicesFragment.adapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshInvoices()
        }
    }

    private fun loadInvoices() {
        // In a real implementation, this would load from repository
        // For now, just set empty state
        updateEmptyState(true)
    }

    fun refreshInvoices() {
        binding.swipeRefreshLayout.isRefreshing = true
        // In a real implementation, this would refresh from repository
        binding.swipeRefreshLayout.isRefreshing = false
        updateEmptyState(true)
    }

    fun searchInvoices(query: String) {
        currentSearchQuery = query.trim().lowercase()

        if (currentSearchQuery.isEmpty()) {
            adapter.updateInvoices(allInvoices)
        } else {
            val filteredInvoices = allInvoices.filter { invoice ->
                invoice.invoiceNumber.lowercase().contains(currentSearchQuery) ||
                        invoice.customerName.lowercase().contains(currentSearchQuery)
            }
            adapter.updateInvoices(filteredInvoices)
        }

        updateEmptyState(adapter.itemCount == 0)
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewInvoices.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewInvoices.visibility = View.VISIBLE
        }
    }

    private fun showInvoiceDetails(invoice: Invoice) {
        // Show invoice details, perhaps in a bottom sheet or new fragment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}