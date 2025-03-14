package com.jewelrypos.swarnakhatabook.BottomSheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jewelrypos.swarnakhatabook.Adapters.OrdersAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.Order
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.BottomsheetOrderSelectionBinding

class OrderListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetOrderSelectionBinding? = null
    private val binding get() = _binding!!

    private var onOrderSelectedListener: ((Order) -> Unit)? = null
    private lateinit var adapter: OrdersAdapter

    private val salesViewModel: SalesViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    fun setOnOrderSelectedListener(listener: (Order) -> Unit) {
        onOrderSelectedListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetOrderSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()
        setupObservers()

        // Load pending orders only
        loadPendingOrders()
    }

    private fun setupRecyclerView() {
        adapter = OrdersAdapter(emptyList())
        adapter.setOnItemClickListener { order ->
            onOrderSelectedListener?.invoke(order)
            dismiss()
        }

        binding.recyclerViewOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@OrderListBottomSheet.adapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchOrders(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchOrders(newText ?: "")
                return true
            }
        })
    }

    private fun searchOrders(query: String) {
        salesViewModel.searchOrders(query, onlyPending = true)
    }

    private fun setupObservers() {
        salesViewModel.pendingOrders.observe(viewLifecycleOwner) { orders ->
            adapter.updateOrders(orders)
            updateEmptyState(orders.isEmpty())
        }

        salesViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewOrders.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewOrders.visibility = View.VISIBLE
        }
    }

    private fun loadPendingOrders() {
        salesViewModel.loadPendingOrders()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OrderListBottomSheet"

        fun newInstance(): OrderListBottomSheet {
            return OrderListBottomSheet()
        }
    }
}