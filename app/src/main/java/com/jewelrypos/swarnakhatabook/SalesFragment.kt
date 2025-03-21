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
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.InvoicesAdapter
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.SalesViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentSalesBinding

class SalesFragment : Fragment() {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    private val salesViewModel: SalesViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager)
    }

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
        setupRecyclerView()
        setupObservers()
        setUpClickListner()

        EventBus.invoiceAddedEvent.observe(viewLifecycleOwner) { added ->
            if (added) {
                // Refresh the invoices list
                salesViewModel.refreshInvoices()
            }
        }

        EventBus.invoiceDeletedEvent.observe(viewLifecycleOwner) { deleted ->
            if (deleted) {
                // Refresh the invoices list
                salesViewModel.refreshInvoices()
            }
        }

    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search -> {
                    // Handle search - you can implement search functionality later
                    true
                }

                R.id.action_filter -> {
                    // Handle filter - you can implement filter functionality later
                    true
                }

                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = InvoicesAdapter(emptyList())

        // Set click listener for adapter
        adapter.onItemClickListener = { invoice ->

//            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
//            parentNavController.navigate(R.id.action_mainScreenFragment_to_invoiceCreationFragment)

            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)

            // Using the generated NavDirections class
            val action =
                MainScreenFragmentDirections.actionMainScreenFragmentToInvoiceDetailFragment(invoice.invoiceNumber)
            parentNavController.navigate(action)
            // Handle invoice click - you can navigate to details or show actions
            Toast.makeText(
                requireContext(),
                "Invoice: ${invoice.invoiceNumber}",
                Toast.LENGTH_SHORT
            ).show()
            // You could navigate to details using:
            // findNavController().navigate(SalesFragmentDirections.actionSalesFragmentToInvoiceDetailsFragment(invoice.invoiceNumber))
        }

        binding.recyclerViewSales.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SalesFragment.adapter

            // Add scroll listener for pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    // Load more when user is near the end of the list
                    if (!salesViewModel.isLoading.value!! &&
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 &&
                        firstVisibleItemPosition >= 0
                    ) {
                        salesViewModel.loadNextPage()
                    }
                }
            })
        }
    }


    private fun setupObservers() {
        salesViewModel.invoices.observe(viewLifecycleOwner) { invoices ->
            adapter.updateInvoices(invoices)
            binding.swipeRefreshLayout.isRefreshing = false
            binding.progressBar.visibility = View.GONE

            // Update empty state
            updateEmptyState(invoices.isEmpty())
        }

        salesViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        salesViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Log.d("SalesFragment", errorMessage)
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                binding.swipeRefreshLayout.isRefreshing = false
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setUpClickListner() {
        binding.addSaleFab.setOnClickListener {
            // Navigate to the InvoiceCreationFragment
            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
            parentNavController.navigate(R.id.action_mainScreenFragment_to_invoiceCreationFragment)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshInvoices()
        }

        // Clear search button in empty search state
        binding.clearFilterButton.setOnClickListener {
            // Clear search and refresh data
            salesViewModel.refreshInvoices()
            binding.emptySearchLayout.visibility = View.GONE
        }

        // Add new item button in empty search state
        binding.addNewItemButton.setOnClickListener {
            binding.addSaleFab.performClick()
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

    private fun refreshInvoices() {
        binding.swipeRefreshLayout.isRefreshing = true
        salesViewModel.refreshInvoices()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}