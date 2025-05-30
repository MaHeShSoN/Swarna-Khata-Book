package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import androidx.lifecycle.lifecycleScope // Add this import
import kotlinx.coroutines.flow.collectLatest // Add this import
import androidx.paging.LoadState // Add this import
import androidx.paging.CombinedLoadStates // Add this import
import kotlinx.coroutines.launch


class CustomerListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetCustomerSelectionBinding? = null
    private val binding get() = _binding!!

    private var onCustomerSelectedListener: ((Customer) -> Unit)? = null
    private lateinit var adapter: CustomerAdapter

    private val customerViewModel: CustomerViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth,requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager,requireContext()) // Use requireContext() for context
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
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
        return binding.root
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                setupFullHeight(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true

            }
        }

        return dialog
    }
    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("CustomerListBottomSheet", "View created")

        (view.parent as View).setBackgroundColor(Color.TRANSPARENT)

        setupRecyclerView() // Modified for Paging 3
        setupSearchView()
        setupNewCustomerButton()
        setupObservers() // Modified for Paging 3 LoadState

        binding.searchView.requestFocus()

    }

    private fun setupRecyclerView() {
        // Instantiate PagingDataAdapter correctly
        adapter = CustomerAdapter(object : CustomerAdapter.OnCustomerClickListener {
            override fun onCustomerClick(customer: Customer) {
                Log.d("CustomerListBottomSheet", "Customer selected: ${customer.firstName} ${customer.lastName} (ID: ${customer.id})")
                onCustomerSelectedListener?.invoke(customer)
                dismiss()
            }
        })

        binding.recyclerViewCustomers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CustomerListBottomSheet.adapter

            // Remove manual scroll listener, Paging 3 handles pagination automatically
            // addOnScrollListener(object : RecyclerView.OnScrollListener() { ... })
        }
    }

    private fun setupSearchView() {
        binding.searchView.setIconifiedByDefault(false)

        val searchIcon = binding.searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchIcon?.visibility = View.GONE

        val searchPlate = binding.searchView.findViewById<View>(androidx.appcompat.R.id.search_src_text)
        val params = searchPlate.layoutParams as ViewGroup.MarginLayoutParams
        params.leftMargin = 0

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d("CustomerListBottomSheet", "Search submitted with query: $query")
                customerViewModel.searchCustomers(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d("CustomerListBottomSheet", "Search text changed: $newText")
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
        // Collect PagingData from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            customerViewModel.pagedCustomers.collectLatest { pagingData ->
                Log.d("CustomerListBottomSheet", "New PagingData received, submitting to adapter.")
                adapter.submitData(pagingData)
            }
        }

        // Observe LoadStates for UI changes (loading, error, empty)
        viewLifecycleOwner.lifecycleScope.launch {
            adapter.loadStateFlow.collectLatest { loadStates ->
                val refreshState = loadStates.refresh // Represents the initial load or refresh operation

                binding.progressBar.visibility = if (refreshState is LoadState.Loading) View.VISIBLE else View.GONE

                // Error state for initial load or refresh
                if (refreshState is LoadState.Error) {
                    Log.e("CustomerListBottomSheet", "Paging refresh error: ${refreshState.error.localizedMessage}")
                    binding.errorText.text = "Error: ${refreshState.error.localizedMessage}"
                    binding.errorText.visibility = View.VISIBLE
                } else {
                    binding.errorText.visibility = View.GONE
                }

                updateUIState(loadStates) // Use new unified UI state method
            }
        }

        // Observe ViewModel's action error messages (e.g., from addCustomer)
        customerViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Log.e("CustomerListBottomSheet", "Action error message received: $errorMessage")
                // You might want a different UI for action errors vs. list loading errors
                // For simplicity, showing in errorText for now.
                // binding.errorText.text = errorMessage
                // binding.errorText.visibility = View.VISIBLE
            }
        }
    }

    // Modified to handle CombinedLoadStates from Paging 3
    private fun updateUIState(loadStates: CombinedLoadStates) {
        val refreshState = loadStates.refresh
        val isListEmpty = refreshState is LoadState.NotLoading && adapter.itemCount == 0

        if (isListEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewCustomers.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewCustomers.visibility = View.VISIBLE
        }
    }

    private fun showCustomerCreationBottomSheet() {
        Log.d("CustomerListBottomSheet", "Showing customer creation bottom sheet")
        val bottomSheet = CustomerBottomSheetFragment.newInstance()
        bottomSheet.setCalledFromInvoiceCreation(true)

        bottomSheet.setCustomerOperationListener(object : CustomerBottomSheetFragment.CustomerOperationListener {
            override fun onCustomerAdded(customer: Customer) {
                Log.d("CustomerListBottomSheet", "New customer added: ${customer.firstName} ${customer.lastName} (ID: ${customer.id})")
                customerViewModel.addCustomer(customer).observe(viewLifecycleOwner) { result ->
                    result.onSuccess { newCustomer ->
                        Log.d("CustomerListBottomSheet", "Customer added successfully with ID: ${newCustomer.id}")
                        onCustomerSelectedListener?.invoke(newCustomer)
                        // If this bottom sheet should refresh its list immediately after a successful add,
                        // you would call adapter.refresh() here. However, typically for selection
                        // bottom sheets, they are dismissed after selection/creation.
                        dismiss()
                    }.onFailure { e ->
                        Log.e("CustomerListBottomSheet", "Error adding customer: ${e.message}", e)
                    }
                }
            }

            override fun onCustomerUpdated(customer: Customer) {
                // Not needed for customer creation context in this bottom sheet
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