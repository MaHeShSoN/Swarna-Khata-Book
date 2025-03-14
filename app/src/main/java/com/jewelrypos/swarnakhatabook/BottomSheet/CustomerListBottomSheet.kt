package com.jewelrypos.swarnakhatabook.BottomSheet

import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.CustomerAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.databinding.BottomsheetCustomerSelectionBinding

class CustomerListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetCustomerSelectionBinding? = null
    private val binding get() = _binding!!

    private var onCustomerSelectedListener: ((Customer) -> Unit)? = null
    private lateinit var adapter: CustomerAdapter

    private val customerViewModel: CustomerViewModel by viewModels {
        // Create the repository and pass it to the factory
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager)
    }

    fun setOnCustomerSelectedListener(listener: (Customer) -> Unit) {
        onCustomerSelectedListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetCustomerSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (view.parent as View).setBackgroundColor(Color.TRANSPARENT)

        setupRecyclerView()
        setupSearchView()
        setupNewCustomerButton()
        setupObservers()

        // Load customers initially
        customerViewModel.refreshData()
    }

    private fun setupRecyclerView() {
        adapter = CustomerAdapter(emptyList(), object : CustomerAdapter.OnCustomerClickListener {
            override fun onCustomerClick(customer: Customer) {
                onCustomerSelectedListener?.invoke(customer)
                dismiss()
            }
        })

        binding.recyclerViewCustomers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CustomerListBottomSheet.adapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                customerViewModel.searchCustomers(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                customerViewModel.searchCustomers(newText ?: "")
                return true
            }
        })
    }

    private fun setupNewCustomerButton() {
        binding.btnAddNewCustomer.setOnClickListener {
            showCustomerCreationBottomSheet()
        }
    }

    private fun setupObservers() {
        customerViewModel.customers.observe(viewLifecycleOwner) { customers ->
            adapter.updateList(customers)
            updateEmptyState(customers.isEmpty())
        }

        customerViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        customerViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                // Show error message
                binding.errorText.text = errorMessage
                binding.errorText.visibility = View.VISIBLE
            } else {
                binding.errorText.visibility = View.GONE
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewCustomers.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewCustomers.visibility = View.VISIBLE
        }
    }

    private fun showCustomerCreationBottomSheet() {
        val bottomSheet = CustomerBottomSheetFragment.newInstance()
        bottomSheet.setCustomerOperationListener(object : CustomerBottomSheetFragment.CustomerOperationListener {
            override fun onCustomerAdded(customer: Customer) {
                customerViewModel.addCustomer(customer)
                // Optional: Automatically select the newly created customer
                onCustomerSelectedListener?.invoke(customer)
                dismiss()
            }

            override fun onCustomerUpdated(customer: Customer) {
                // Not needed for customer creation
            }
        })
        bottomSheet.show(parentFragmentManager, CustomerBottomSheetFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CustomerListBottomSheet"

        fun newInstance(): CustomerListBottomSheet {
            return CustomerListBottomSheet()
        }
    }
}