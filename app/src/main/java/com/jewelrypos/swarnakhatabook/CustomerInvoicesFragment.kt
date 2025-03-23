package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.InvoicesAdapter
import com.jewelrypos.swarnakhatabook.Factorys.SalesViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerSelectionManager
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentCustomerInvoicesBinding

class CustomerInvoicesFragment : Fragment() {

    private var _binding: FragmentCustomerInvoicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: InvoicesAdapter
    private lateinit var customerId: String

    private val salesViewModel: SalesViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager)
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

        // Initial load of customer invoices
        loadCustomerInvoices()


        binding.createInvoiceButton.setOnClickListener {
            navigateToCreateInvoice()
        }
    }


    // Add this new method to the class
    private fun navigateToCreateInvoice() {
        // Navigate to the invoice creation screen with the customer ID pre-selected
        val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
        val action = MainScreenFragmentDirections.actionMainScreenFragmentToInvoiceCreationFragment()
        mainNavController.navigate(action)

        // Pre-select the customer in the invoice screen
        // This requires you to add a mechanism to pre-select a customer in the InvoiceCreationFragment
        // For example, through a shared ViewModel or arguments
        customerId.let { id ->
            // You could use a singleton to pass the ID or modify InvoiceCreationFragment
            // to accept the customer ID as an argument
            CustomerSelectionManager.selectedCustomerId = id
        }
    }

    private fun setupRecyclerView() {
        adapter = InvoicesAdapter(emptyList())
        adapter.onItemClickListener = { invoice ->
            // Navigate to invoice details
            val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
            val action = MainScreenFragmentDirections.actionMainScreenFragmentToInvoiceDetailFragment(invoice.invoiceNumber)
            mainNavController.navigate(action)
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

            adapter.updateInvoices(invoices)

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