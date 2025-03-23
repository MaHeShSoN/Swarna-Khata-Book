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
        val repository = CustomerRepository(firestore, auth)
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

        // Set up back button
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.topAppBar.menu.findItem(R.id.action_edit).setOnMenuItemClickListener { menuItem ->
            customer?.let { openCustomerEditBottomSheet(it) }
            true
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}