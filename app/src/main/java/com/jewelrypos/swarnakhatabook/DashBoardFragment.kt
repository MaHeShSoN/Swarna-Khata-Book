package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.Factorys.InventoryViewModelFactory
import com.jewelrypos.swarnakhatabook.Factorys.NotificationViewModelFactory
import com.jewelrypos.swarnakhatabook.Factorys.SalesViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.Utilitys.MainScreenNavigator
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentDashBoardBinding
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

class DashBoardFragment : Fragment() {

    private var _binding: FragmentDashBoardBinding? = null
    private val binding get() = _binding!!

    private val salesViewModel: SalesViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager)
    }

    private val inventoryViewModel: InventoryViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        InventoryViewModelFactory(repository, connectivityManager)
    }

    private val customerViewModel: CustomerViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager)
    }

    private val notificationViewModel: NotificationViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = NotificationRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        NotificationViewModelFactory(repository, connectivityManager)
    }

    // Currency formatter
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashBoardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupPeriodSelector()
        setupQuickActions()

        // Load data for dashboard
        loadDashboardData()
    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    loadDashboardData()
                    true
                }
                R.id.action_notifications -> {
                    navigateToNotifications()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPeriodSelector() {
        // Set up time period spinner
        val periods = arrayOf("Today", "This Week", "This Month", "All Time")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, periods)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.periodSelector.adapter = adapter

        // Set default selection
        binding.periodSelector.setSelection(2) // Default to "This Month"

        // Add listener for period changes
        binding.periodSelector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Reload sales data with new period
                loadSalesData(periods[position])
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupQuickActions() {
        // Create Invoice Action
        binding.createInvoiceAction.setOnClickListener {
            navigateToCreateInvoice()
        }

        // Add Customer Action
        binding.addCustomerAction.setOnClickListener {
            navigateToAddCustomer()
        }

        // Add Inventory Action
        binding.addInventoryAction.setOnClickListener {
            navigateToAddInventory()
        }

        // Record Payment Action
        binding.recordPaymentAction.setOnClickListener {
            navigateToPayments()
        }

        binding.viewAllCustomers.setOnClickListener {
            navigateToCustomersTab()
        }
    }

    private fun loadDashboardData() {
        // Load data for each section of the dashboard
        loadSalesData(binding.periodSelector.selectedItem.toString())

        loadCustomerData()

        // Hide low stock recycler view container since we're not using it
        binding.lowStockRecyclerView.visibility = View.GONE
        binding.emptyLowStockState.visibility = View.GONE

        // Hide recent invoices section
        binding.recentInvoicesRecyclerView.visibility = View.GONE
        binding.emptyRecentInvoicesState.visibility = View.GONE

    }

    private fun loadSalesData(period: String) {
        // Refresh sales data from the repository
        salesViewModel.refreshInvoices()

        // Listen for changes to invoices data
        salesViewModel.invoices.observe(viewLifecycleOwner) { invoices ->
            // Apply period filter
            val filteredInvoices = when (period) {
                "Today" -> {
                    val today = System.currentTimeMillis()
                    val dayStart = today - (today % 86400000) // Start of day in millis
                    invoices.filter { it.invoiceDate >= dayStart }
                }
                "This Week" -> {
                    val today = System.currentTimeMillis()
                    val weekStart = today - ((today % 86400000) + ((System.currentTimeMillis() / 86400000) % 7) * 86400000)
                    invoices.filter { it.invoiceDate >= weekStart }
                }
                "This Month" -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    val monthStart = calendar.timeInMillis
                    invoices.filter { it.invoiceDate >= monthStart }
                }
                else -> invoices // "All Time"
            }

            // Update sales metrics
            updateSalesMetrics(filteredInvoices)
        }
    }

    private fun updateSalesMetrics(invoices: List<com.jewelrypos.swarnakhatabook.DataClasses.Invoice>) {
        // Calculate total sales
        val totalSales = invoices.sumOf { it.totalAmount }
        binding.totalSalesValue.text = currencyFormatter.format(totalSales)

        // Count of invoices
        binding.invoiceCountValue.text = invoices.size.toString()

        // Calculate average sale
        val averageSale = if (invoices.isNotEmpty()) totalSales / invoices.size else 0.0
        binding.averageSaleValue.text = currencyFormatter.format(averageSale)
    }



    private fun loadCustomerData() {
        customerViewModel.refreshData()

        customerViewModel.customers.observe(viewLifecycleOwner) { customers ->
            // Count total customers
            binding.totalCustomersValue.text = customers.size.toString()

            // Calculate total outstanding balance
            val outstandingBalance = customers
                .filter { it.balanceType == "Credit" } // Only include credit customers
                .sumOf { it.currentBalance }

            binding.outstandingBalanceValue.text = currencyFormatter.format(outstandingBalance)

            // Check for birthdays today
            val today = java.time.LocalDate.now()
            val birthdaysToday = customers.count { customer ->
                if (customer.birthday.isNotEmpty()) {
                    try {
                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        val birthday = dateFormat.parse(customer.birthday)
                        val cal = java.util.Calendar.getInstance()
                        cal.time = birthday

                        // Check if month and day match today
                        val birthdayMonth = cal.get(java.util.Calendar.MONTH)
                        val birthdayDay = cal.get(java.util.Calendar.DAY_OF_MONTH)

                        birthdayMonth == today.monthValue - 1 && birthdayDay == today.dayOfMonth
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }
            }
            binding.birthdaysTodayValue.text = birthdaysToday.toString()
        }
    }

    // Navigation methods
    private fun navigateToCreateInvoice() {
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(R.id.action_mainScreenFragment_to_invoiceCreationFragment)
    }

    private fun navigateToAddCustomer() {
        // First navigate to the customers tab
        navigateToCustomersTab()

        // Then show the customer bottom sheet
        val customerBottomSheet = com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment.newInstance()
        customerBottomSheet.show(parentFragmentManager, com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment.TAG)
    }

    private fun navigateToAddInventory() {
        // Navigate to inventory tab first
        navigateToInventoryTab()

        // Let the inventory fragment know that we want to add an item
        // This would be implemented as a callback or event bus mechanism
    }

    private fun navigateToPayments() {
        // Navigate to the payments management screen
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(R.id.paymentsFragment)
    }

    private fun navigateToNotifications() {
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(R.id.action_mainScreenFragment_to_notificationFragment)
    }

    private fun navigateToInventoryTab() {
        // Navigate to inventory tab in bottom navigation
        val parentFragment = parentFragment
        if (parentFragment is MainScreenFragment) {
            val navigator = MainScreenNavigator(parentFragment)
            navigator.navigateToInventoryTab()
        }
    }

    private fun navigateToCustomersTab() {
        // Navigate to customers tab in bottom navigation
        val parentFragment = parentFragment
        if (parentFragment is MainScreenFragment) {
            val navigator = MainScreenNavigator(parentFragment)
            navigator.navigateToCustomersTab()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}