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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.CustomerAdapter
import com.jewelrypos.swarnakhatabook.Adapters.InvoicesAdapter
import com.jewelrypos.swarnakhatabook.Adapters.JewelleryAdapter
import com.jewelrypos.swarnakhatabook.Adapters.NotificationAdapter
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

    private lateinit var recentInvoicesAdapter: InvoicesAdapter
    private lateinit var lowStockAdapter: JewelleryAdapter
    private lateinit var topCustomersAdapter: CustomerAdapter
    private lateinit var upcomingEventsAdapter: NotificationAdapter

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
        setupRecyclerViews()
        setupQuickActions()
        setupViewAllButtons()

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

    private fun setupRecyclerViews() {
        // Recent Invoices
        recentInvoicesAdapter = InvoicesAdapter(emptyList())
        recentInvoicesAdapter.onItemClickListener = { invoice ->
            navigateToInvoiceDetail(invoice.invoiceNumber)
        }
        binding.recentInvoicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recentInvoicesAdapter
        }

        // Low Stock Items
        lowStockAdapter = JewelleryAdapter(emptyList(), object : JewelleryAdapter.OnItemClickListener {
            override fun onItemClick(item: com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem) {
                // Navigate to item detail when clicked
                // This would be implemented with a navigation call
            }
        })
        binding.lowStockRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = lowStockAdapter
        }

        // Top Customers
        topCustomersAdapter = CustomerAdapter(emptyList(), object : CustomerAdapter.OnCustomerClickListener {
            override fun onCustomerClick(customer: com.jewelrypos.swarnakhatabook.DataClasses.Customer) {
                navigateToCustomerDetail(customer.id)
            }
        })
        binding.topCustomersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = topCustomersAdapter
        }

        // Upcoming Events
        upcomingEventsAdapter = NotificationAdapter(emptyList())
        upcomingEventsAdapter.setOnNotificationActionListener(object : NotificationAdapter.OnNotificationActionListener {
            override fun onNotificationClick(notification: com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification) {
                // Handle notification click
            }

            override fun onActionButtonClick(notification: com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification) {
                // Handle action button click
            }

            override fun onDismissButtonClick(notification: com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification) {
                // Handle dismiss button click
            }
        })
        binding.upcomingEventsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = upcomingEventsAdapter
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
    }

    private fun setupViewAllButtons() {
        // View all invoices
        binding.viewAllInvoices.setOnClickListener {
            navigateToSalesTab()
        }

        // View all inventory
        binding.viewAllInventory.setOnClickListener {
            navigateToInventoryTab()
        }

        // View all customers
        binding.viewAllCustomers.setOnClickListener {
            navigateToCustomersTab()
        }
    }

    private fun loadDashboardData() {
        // Load data for each section of the dashboard
        loadSalesData(binding.periodSelector.selectedItem.toString())
        loadInventoryData()
        loadCustomerData()
        loadNotificationData()
    }

    private fun loadSalesData(period: String) {
        // Here we would filter the sales data based on the selected period
        // For now, we'll just show all invoices
        salesViewModel.refreshInvoices()

        // Listen for changes to invoices data
        salesViewModel.invoices.observe(viewLifecycleOwner) { invoices ->
            // Update recent invoices list
            val recentInvoices = invoices.take(5) // Only show top 5 recent invoices
            recentInvoicesAdapter.updateInvoices(recentInvoices)

            // Update empty state visibility
            binding.emptyRecentInvoicesState.visibility = if (recentInvoices.isEmpty()) View.VISIBLE else View.GONE
            binding.recentInvoicesRecyclerView.visibility = if (recentInvoices.isEmpty()) View.GONE else View.VISIBLE

            // Update sales metrics
            updateSalesMetrics(invoices)
        }
    }

    private fun updateSalesMetrics(invoices: List<com.jewelrypos.swarnakhatabook.DataClasses.Invoice>) {
        val formatter = DecimalFormat("#,##,##0.00")

        // Calculate total sales
        val totalSales = invoices.sumOf { it.totalAmount }
        binding.totalSalesValue.text = "₹${formatter.format(totalSales)}"

        // Count of invoices
        binding.invoiceCountValue.text = invoices.size.toString()

        // Calculate average sale
        val averageSale = if (invoices.isNotEmpty()) totalSales / invoices.size else 0.0
        binding.averageSaleValue.text = "₹${formatter.format(averageSale)}"
    }

    private fun loadInventoryData() {
        inventoryViewModel.refreshData()

        inventoryViewModel.jewelleryItems.observe(viewLifecycleOwner) { items ->
            // Count total items
            binding.totalItemsValue.text = items.size.toString()

            // Count gold items
            val goldItems = items.count { it.itemType.equals("gold", ignoreCase = true) }
            binding.goldItemsValue.text = goldItems.toString()

            // Find low stock items (arbitrary threshold of 5 units)
            val lowStockItems = items.filter { it.stock in 0.0..5.0 }
            binding.lowStockValue.text = lowStockItems.size.toString()

            // Update low stock recycler view
            lowStockAdapter.updateList(lowStockItems)

            // Update empty state visibility
            binding.emptyLowStockState.visibility = if (lowStockItems.isEmpty()) View.VISIBLE else View.GONE
            binding.lowStockRecyclerView.visibility = if (lowStockItems.isEmpty()) View.GONE else View.VISIBLE
        }
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

            val formatter = DecimalFormat("#,##,##0.00")
            binding.outstandingBalanceValue.text = "₹${formatter.format(outstandingBalance)}"

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

            // Get top customers by purchase value (We would need to join with invoices data)
            // For now, we'll just show the first few customers
            val topCustomers = customers.take(3)
            topCustomersAdapter.updateList(topCustomers)

            // Update empty state visibility
            binding.emptyCustomersState.visibility = if (customers.isEmpty()) View.VISIBLE else View.GONE
            binding.topCustomersRecyclerView.visibility = if (customers.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun loadNotificationData() {
        notificationViewModel.loadNotifications()

        notificationViewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            // Filter for upcoming events (birthdays, anniversaries, payment reminders)
            val upcomingEvents = notifications.filter {
                it.type == com.jewelrypos.swarnakhatabook.Enums.NotificationType.BIRTHDAY ||
                        it.type == com.jewelrypos.swarnakhatabook.Enums.NotificationType.ANNIVERSARY ||
                        it.type == com.jewelrypos.swarnakhatabook.Enums.NotificationType.PAYMENT_DUE
            }

            upcomingEventsAdapter.updateNotifications(upcomingEvents)

            // Update empty state visibility
            binding.emptyEventsState.visibility = if (upcomingEvents.isEmpty()) View.VISIBLE else View.GONE
            binding.upcomingEventsRecyclerView.visibility = if (upcomingEvents.isEmpty()) View.GONE else View.VISIBLE
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
        // This would open the inventory fragment and then trigger the add item dialog
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(R.id.inventoryFragment)
        // You'll need to add code to automatically open the add item dialog
    }

    private fun navigateToPayments() {
        // Navigate to a payments management screen
        Toast.makeText(context, "Payments functionality to be implemented", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToInvoiceDetail(invoiceId: String) {
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(MainScreenFragmentDirections.actionMainScreenFragmentToInvoiceDetailFragment(invoiceId))
    }

    private fun navigateToCustomerDetail(customerId: String) {
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(MainScreenFragmentDirections.actionMainScreenFragmentToCustomerDetailFragment(customerId))
    }

    private fun navigateToSalesTab() {
        // Navigate to sales tab in bottom navigation
        val parentFragment = parentFragment
        if (parentFragment is MainScreenFragment) {
            val navigator = MainScreenNavigator(parentFragment)
            navigator.navigateToSalesTab()
        }
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

    private fun navigateToNotifications() {
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(R.id.action_mainScreenFragment_to_notificationFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}