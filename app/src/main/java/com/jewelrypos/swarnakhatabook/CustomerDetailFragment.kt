package com.jewelrypos.swarnakhatabook


import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.CustomerDetailViewPagerAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentCustomerDetailBinding

class CustomerDetailFragment : Fragment(), CustomerBottomSheetFragment.CustomerOperationListener {

    private var _binding: FragmentCustomerDetailBinding? = null
    private val binding get() = _binding!!

    private val args: CustomerDetailFragmentArgs by navArgs()

    private val customerViewModel: CustomerViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth,requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager)
    }

    private var customer: Customer? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load customer details
        loadCustomerDetails(args.customerId)

        binding.topAppBar.overflowIcon = ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)

        // Set up back button
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    customer?.let { openCustomerEditBottomSheet(it) }
                    true
                }
                R.id.action_delete -> {
                    customer?.let { confirmDeleteCustomer(it) }
                    true
                }
                else -> false
            }
        }
    }

    // Add to CustomerDetailFragment.kt
    private fun confirmDeleteCustomer(customer: Customer) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Customer")
            .setMessage("Are you sure you want to delete ${customer.firstName} ${customer.lastName}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // Check for related invoices first
                checkForRelatedInvoices(customer)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Add to CustomerDetailFragment.kt
    private fun checkForRelatedInvoices(customer: Customer) {
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE

        // Get invoices for this customer
        customerViewModel.getCustomerInvoiceCount(customer.id).observe(viewLifecycleOwner) { result ->
            binding.progressBar.visibility = View.GONE

            result.fold(
                onSuccess = { count ->
                    if (count > 0) {
                        // Customer has invoices, show warning
                        showDeleteWithInvoicesWarning(customer, count)
                    } else {
                        // No invoices, proceed with deletion
                        deleteCustomer(customer)
                    }
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Error checking invoices: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun showDeleteWithInvoicesWarning(customer: Customer, invoiceCount: Int) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Customer Has Invoices")
            .setMessage("This customer has $invoiceCount ${if (invoiceCount == 1) "invoice" else "invoices"}. Deleting this customer will make these invoices inaccessible through customer views. The invoices will remain in the system but won't be linked to any customer.\n\nDo you want to proceed?")
            .setPositiveButton("Delete Anyway") { _, _ ->
                deleteCustomer(customer)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Add to CustomerDetailFragment.kt
//    private fun deleteCustomer(customer: Customer) {
//        binding.progressBar.visibility = View.VISIBLE
//
//        customerViewModel.deleteCustomer(customer.id).observe(viewLifecycleOwner) { result ->
//            binding.progressBar.visibility = View.GONE
//
//            result.fold(
//                onSuccess = {
//                    Toast.makeText(requireContext(), "Customer deleted successfully", Toast.LENGTH_SHORT).show()
//                    EventBus.postCustomerDeleted()
//                    // Navigate back to customer list
//                    findNavController().navigateUp()
//                },
//                onFailure = { error ->
//                    Toast.makeText(requireContext(), "Error deleting customer: ${error.message}", Toast.LENGTH_SHORT).show()
//                }
//            )
//        }
//    }

    // Update in CustomerDetailFragment
    private fun deleteCustomer(customer: Customer) {
        binding.progressBar.visibility = View.VISIBLE

        // Change this to use moveCustomerToRecycleBin instead of deleteCustomer
        customerViewModel.moveCustomerToRecycleBin(customer.id).observe(viewLifecycleOwner) { result ->
            binding.progressBar.visibility = View.GONE

            result.fold(
                onSuccess = {
                    Toast.makeText(requireContext(), "Customer moved to recycling bin", Toast.LENGTH_SHORT).show()
                    EventBus.postCustomerDeleted()
                    // Navigate back to customer list
                    findNavController().navigateUp()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Error moving customer to recycling bin: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun loadCustomerDetails(customerId: String) {
        binding.progressBar.visibility = View.VISIBLE

        customerViewModel.getCustomerById(customerId).observe(viewLifecycleOwner) { result ->
            binding.progressBar.visibility = View.GONE

            if (result.isSuccess) {
                customer = result.getOrNull()
                customer?.let {
                    setupUI(it)
                }
            } else {
                // Show error
                binding.errorLayout.visibility = View.VISIBLE
                binding.errorText.text =
                    result.exceptionOrNull()?.message ?: "Failed to load customer details"
            }
        }
    }

    private fun setupUI(customer: Customer) {
        // Set toolbar title
        binding.topAppBar.title = "${customer.firstName} ${customer.lastName}"

        // Set up ViewPager with TabLayout
        val adapter = CustomerDetailViewPagerAdapter(this, customer)
        binding.viewPager.adapter = adapter

        // Connect TabLayout with ViewPager
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Dashboard"
                1 -> "Invoices"
                else -> "Dashboard"
            }
        }.attach()

        // Notify the dashboard fragment to update if it's already created
        val currentFragment = childFragmentManager.findFragmentByTag("f${binding.viewPager.currentItem}")
        if (currentFragment is CustomerDashboardFragment) {
            currentFragment.updateCustomer(customer)
        }

    }

    private fun openCustomerEditBottomSheet(customer: Customer) {
        val bottomSheet = CustomerBottomSheetFragment.newInstance(customer)
        bottomSheet.setCustomerOperationListener(this)
        bottomSheet.setCalledFromInvoiceCreation(true)
        bottomSheet.show(parentFragmentManager, CustomerBottomSheetFragment.TAG)
    }

    override fun onCustomerAdded(customer: Customer) {
        // Not needed for edit operation
    }

    override fun onCustomerUpdated(customer: Customer) {
        // Update the database through the ViewModel
        customerViewModel.updateCustomer(customer)

        // Refresh customer details after update
        this.customer = customer
        setupUI(customer)
        EventBus.postCustomerUpdated()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}