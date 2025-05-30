package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.InvoiceAdapter
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.SalesViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerSelectionManager
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentCustomerInvoicesBinding
import kotlinx.coroutines.launch
import androidx.paging.PagingData

class CustomerInvoicesFragment : Fragment() {

    private var _binding: FragmentCustomerInvoicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: InvoiceAdapter
    private lateinit var customerId: String

    private val salesViewModel: SalesViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth,requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager,requireContext())
    }

    companion object {
        private const val ARG_CUSTOMER_ID = "customer_id"

        fun newInstance(customerId: String): CustomerInvoicesFragment {
            val fragment = CustomerInvoicesFragment()
            val args = Bundle()
            args.putString(ARG_CUSTOMER_ID, customerId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            customerId = it.getString(ARG_CUSTOMER_ID) ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerInvoicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        setupObservers()
        setupEventListeners()
        // Initial load of customer invoices
        loadCustomerInvoices()


        binding.createInvoiceButton.setOnClickListener {
            navigateToCreateInvoice()
        }
    }

    private fun setupEventListeners() {
        // Listen for invoice updates
        EventBus.invoiceUpdatedEvent.observe(viewLifecycleOwner) { updated ->
            if (updated) {
                loadCustomerInvoices()
                EventBus.resetInvoiceUpdatedEvent()
            }
        }

        // Listen for invoice deletions
        EventBus.invoiceDeletedEvent.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                loadCustomerInvoices()
                EventBus.resetInvoiceDeletedEvent()
            }
        }

        // Listen for invoice additions
        EventBus.invoiceAddedEvent.observe(viewLifecycleOwner) { added ->
            if (added) {
                loadCustomerInvoices()
                EventBus.resetInvoiceAddedEvent()
            }
        }
    }


    private fun navigateToCreateInvoice() {
        try {
            // Add a bundle with necessary information
            val bundle = Bundle().apply {
                putString("customerId", customerId)
                putBoolean("FROM_CUSTOMER_INVOICE", true)
            }

            // Get the nav controller
            val navController = findNavController()

            // Navigate with the bundle
            navController.navigate(R.id.action_customerDetailFragment_to_invoiceCreationFragment, bundle)

            // Keep using CustomerSelectionManager as well for backward compatibility
            CustomerSelectionManager.selectedCustomerId = customerId

        } catch (e: Exception) {
            // Log the error and show a message
            Log.e("CustomerInvoicesFragment", "Navigation error", e)
            Toast.makeText(context, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()

            // Fallback navigation if needed
            try {
                val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
                mainNavController.navigate(R.id.invoiceCreationFragment)
                CustomerSelectionManager.selectedCustomerId = customerId
            } catch (e: Exception) {
                Log.e("CustomerInvoicesFragment", "Fallback navigation failed", e)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = InvoiceAdapter()

        // In CustomerInvoicesFragment.kt
        adapter.onItemClickListener = { invoice ->
            // Navigate to invoice details
            try {
                val bundle = Bundle().apply {
                    putString("invoiceId", invoice.invoiceNumber)
                    putBoolean("FROM_CUSTOMER_INVOICE", true)
                }
                findNavController().navigate(R.id.action_customerDetailFragment_to_invoiceDetailFragment, bundle)
            } catch (e: Exception) {
                Log.e("CustomerInvoicesFragment", "Navigation error", e)
                Toast.makeText(context, "Error opening invoice: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.recyclerViewInvoices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@CustomerInvoicesFragment.adapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadCustomerInvoices()
        }
    }

    private fun setupObservers() {
        salesViewModel.customerInvoices.observe(viewLifecycleOwner) { invoices ->
            binding.swipeRefreshLayout.isRefreshing = false
            binding.progressBar.visibility = View.GONE

            // Convert the list to PagingData and submit it
            viewLifecycleOwner.lifecycleScope.launch {
                adapter.submitData(PagingData.from(invoices))
            }

            // Update empty state
            if (invoices.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.recyclerViewInvoices.visibility = View.GONE
            } else {
                binding.emptyStateLayout.visibility = View.GONE
                binding.recyclerViewInvoices.visibility = View.VISIBLE
            }
        }

        salesViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        salesViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                binding.swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadCustomerInvoices() {
        if (customerId.isNotEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
            salesViewModel.loadCustomerInvoices(customerId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}