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

class CustomerListBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetCustomerSelectionBinding? = null
    private val binding get() = _binding!!

    private var onCustomerSelectedListener: ((Customer) -> Unit)? = null
    private lateinit var adapter: CustomerAdapter

    private val customerViewModel: CustomerViewModel by viewModels {
        // Create the repository and pass it to the factory
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth,requireContext())
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

        (view.parent as View).setBackgroundColor(Color.TRANSPARENT)

        setupRecyclerView()
        setupSearchView()
        setupNewCustomerButton()
        setupObservers()

        binding.searchView.requestFocus()

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
        // Remove the search icon from the left side
        binding.searchView.setIconifiedByDefault(false)

        // Get the search icon and hide it
        val searchIcon = binding.searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchIcon?.visibility = View.GONE

        // Remove any left margin on the search text field to use the full width
        val searchPlate = binding.searchView.findViewById<View>(androidx.appcompat.R.id.search_src_text)
        val params = searchPlate.layoutParams as ViewGroup.MarginLayoutParams
        params.leftMargin = 0

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
            Log.d("Debuging",customers.size.toString() + " is the size of cusotmers")
            updateEmptyState(customers.isEmpty())
        }

        customerViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        customerViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                // Show error message
                Log.d("Debuging",errorMessage.toString())

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

//    private fun showCustomerCreationBottomSheet() {
//        val bottomSheet = CustomerBottomSheetFragment.newInstance()
//        bottomSheet.setCustomerOperationListener(object : CustomerBottomSheetFragment.CustomerOperationListener {
//            override fun onCustomerAdded(customer: Customer) {
//                customerViewModel.addCustomer(customer)
//                // Optional: Automatically select the newly created customer
//                onCustomerSelectedListener?.invoke(customer)
//                dismiss()
//            }
//
//            override fun onCustomerUpdated(customer: Customer) {
//                // Not needed for customer creation
//            }
//        })
//        bottomSheet.show(parentFragmentManager, CustomerBottomSheetFragment.TAG)
//    }

    private fun showCustomerCreationBottomSheet() {
        val bottomSheet = CustomerBottomSheetFragment.newInstance()

        bottomSheet.setCalledFromInvoiceCreation(true)

        bottomSheet.setCustomerOperationListener(object : CustomerBottomSheetFragment.CustomerOperationListener {
            override fun onCustomerAdded(customer: Customer) {
                customerViewModel.addCustomer(customer).observe(viewLifecycleOwner) { result ->
                    result.onSuccess { newCustomer ->
                        // Customer added successfully, newCustomer now contains the ID
                        onCustomerSelectedListener?.invoke(newCustomer)
                        dismiss()
                    }.onFailure { e ->
                        Log.e(TAG, "Error adding customer: ${e.message}")
                        // Handle error, perhaps show a toast
                    }
                }
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