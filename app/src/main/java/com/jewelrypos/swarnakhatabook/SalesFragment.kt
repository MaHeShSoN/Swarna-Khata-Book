package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.tabs.TabLayoutMediator
import com.jewelrypos.swarnakhatabook.Adapters.SalesViewPagerAdapter
import com.jewelrypos.swarnakhatabook.databinding.FragmentSalesBinding


class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabLayout()
        setupFab()
    }

    private fun setupTabLayout() {
        val viewPager = binding.viewPager
        val tabLayout = binding.tabLayout

        val adapter = SalesViewPagerAdapter(childFragmentManager, lifecycle)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "Orders"
                1 -> "Invoices"
                else -> null
            }
        }.attach()
    }

    private fun setupFab() {
        binding.mainFab.setOnClickListener {
            // Show/hide the expanded FAB options
            toggleFabMenu()
        }

        binding.newOrderFab.setOnClickListener {
            // Launch new order creation flow
            showNewOrderSheet()
            toggleFabMenu(false)
        }

        binding.newInvoiceFab.setOnClickListener {
            // Launch new invoice creation flow
            showNewInvoiceSheet()
            toggleFabMenu(false)
        }
    }

    private fun toggleFabMenu(show: Boolean? = null) {
        val isVisible = show ?: (binding.fabMenu.visibility != View.VISIBLE)

        if (isVisible) {
            binding.fabMenu.visibility = View.VISIBLE
            binding.fabBackdrop.visibility = View.VISIBLE
            binding.mainFab.setImageResource(R.drawable.material_symbols__close_rounded)

            // Animate the FABs
            binding.newOrderFab.show()
            binding.newInvoiceFab.show()
        } else {
            binding.fabMenu.visibility = View.GONE
            binding.fabBackdrop.visibility = View.GONE
            binding.mainFab.setImageResource(R.drawable.material_symbols__add_rounded)

            // Animate the FABs
            binding.newOrderFab.hide()
            binding.newInvoiceFab.hide()
        }
    }

    private fun showNewOrderSheet() {
        // To be implemented
        Toast.makeText(context, "Create New Order", Toast.LENGTH_SHORT).show()
    }

    private fun showNewInvoiceSheet() {
        // To be implemented
        Toast.makeText(context, "Create New Invoice", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}