package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.jewelrypos.swarnakhatabook.Adapters.InvoicesAdapter
//import com.jewelrypos.swarnakhatabook.Adapters.SalesViewPagerAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerListBottomSheet
//import com.jewelrypos.swarnakhatabook.BottomSheet.OrderListBottomSheet
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentSalesBinding

class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!
    private val salesViewModel: SalesViewModel by viewModels()
    private lateinit var adapter: InvoicesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setUpClickListner()
        setupRecyclerView()
    }

    private fun setUpClickListner() {

        binding.addSaleFab.setOnClickListener {
            //Navigate to the InvoiceCreationFragment
            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
            parentNavController.navigate(R.id.action_mainScreenFragment_to_invoiceCreationFragment)
        }


        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshInvoices()
        }




    }


    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search -> {
                    // Handle search
                    true
                }
                R.id.action_filter -> {
                    // Handle filter
                    true
                }
                else -> false
            }
        }
    }



    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewSales.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewSales.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        adapter = InvoicesAdapter(emptyList())

        binding.recyclerViewSales.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SalesFragment.adapter
        }

    }

    private fun refreshInvoices() {

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}