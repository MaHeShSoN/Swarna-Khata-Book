package com.jewelrypos.swarnakhatabook.TabFragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.OrdersAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.Order
import com.jewelrypos.swarnakhatabook.databinding.FragmentOrderBinding

class OrdersFragment : Fragment() {

    private var _binding: FragmentOrderBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: OrdersAdapter
    private var allOrders = listOf<Order>()
    private var currentSearchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadOrders()
    }

    private fun setupRecyclerView() {
        adapter = OrdersAdapter(emptyList())
        adapter.setOnItemClickListener { order ->
            showOrderDetails(order)
        }

        binding.recyclerViewOrders.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@OrdersFragment.adapter
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshOrders()
        }
    }

    private fun loadOrders() {
        // In a real implementation, this would load from repository
        // For now, just set empty state
        updateEmptyState(true)
    }

    fun refreshOrders() {
        binding.swipeRefreshLayout.isRefreshing = true
        // In a real implementation, this would refresh from repository
        binding.swipeRefreshLayout.isRefreshing = false
        updateEmptyState(true)
    }

    fun searchOrders(query: String) {
        currentSearchQuery = query.trim().lowercase()

        if (currentSearchQuery.isEmpty()) {
            adapter.updateOrders(allOrders)
        } else {
            val filteredOrders = allOrders.filter { order ->
                order.orderNumber.lowercase().contains(currentSearchQuery) ||
                        order.customerName.lowercase().contains(currentSearchQuery) ||
                        order.status.lowercase().contains(currentSearchQuery)
            }
            adapter.updateOrders(filteredOrders)
        }

        updateEmptyState(adapter.itemCount == 0)
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

    private fun showOrderDetails(order: Order) {
        // Show order details, perhaps in a bottom sheet or new fragment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}