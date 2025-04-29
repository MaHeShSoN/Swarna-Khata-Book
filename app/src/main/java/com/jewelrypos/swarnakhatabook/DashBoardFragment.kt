package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.Factorys.InventoryViewModelFactory
import com.jewelrypos.swarnakhatabook.Factorys.NotificationViewModelFactory
import com.jewelrypos.swarnakhatabook.Factorys.SalesViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.MainScreenNavigator
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.ShopSwitcherViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentDashBoardBinding
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class DashBoardFragment : Fragment(),
    CustomerBottomSheetFragment.CustomerOperationListener,
    ItemBottomSheetFragment.OnItemAddedListener {

    private var _binding: FragmentDashBoardBinding? = null
    private val binding get() = _binding!!

    // Get the shared shop switcher view model
    private val shopSwitcherViewModel: ShopSwitcherViewModel by activityViewModels()

    private val customerViewModel: CustomerViewModel by activityViewModels {
        // Create the repository and pass it to the factory
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager)
    }
    private val salesViewModel: SalesViewModel by activityViewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager, requireContext())
    }
    private val inventoryViewModel: InventoryViewModel by activityViewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        InventoryViewModelFactory(repository, connectivityManager, requireContext())
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

        // Load active shop info
        loadActiveShopInfo()
        
        // Observe shop changes
        observeShopChanges()

        // Load data for dashboard
        loadDashboardData()
        loadItemPerformanceData()
    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_notifications -> {
                    navigateToNotifications()
                    true
                }
                
                R.id.action_switch_shop -> {
                    navigateToShopSelection()
                    true
                }

                else -> false
            }
        }
        setupNotificationBadge()
        
        // Update the shop name in the toolbar title
        updateToolbarTitle()
    }

    private fun setupNotificationBadge() {
        // Get the menu item view
        val menuItem = binding.topAppBar.menu.findItem(R.id.action_notifications) ?: return
        val actionView = menuItem.actionView ?: return

        // Find the badge text view within the action view
        val badgeCountView = actionView.findViewById<TextView>(R.id.badgeCount)

        // Set click listener for the entire action view
        actionView.setOnClickListener {
            navigateToNotifications()
        }

        // Debug: Log the current shop ID
        Log.d(
            "DashBoardFragment",
            "Setting up notification badge, active shop: ${
                SessionManager.getActiveShopId(requireContext())
            }"
        )

        // Observe unread notification count from view model
        notificationViewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            Log.d("DashBoardFragment", "Unread notification count updated: $count")
            if (count > 0) {
                // Show badge with count
                badgeCountView.visibility = View.VISIBLE
                badgeCountView.text = if (count > 99) "99+" else count.toString()
            } else {
                // Hide badge when no unread notifications
                badgeCountView.visibility = View.GONE
            }
        }

        // Listen for shop changes to refresh notifications
        SessionManager.activeShopIdLiveData.observe(viewLifecycleOwner) { shopId ->
            Log.d(
                "DashBoardFragment",
                "Active shop changed, refreshing notification count: $shopId"
            )
            notificationViewModel.setCurrentShop(shopId!!)
            notificationViewModel.refreshUnreadCount()
        }

        // Ensure we refresh the count now
        notificationViewModel.refreshUnreadCount()
    }

    private fun loadActiveShopInfo() {
        val userId = SessionManager.getCurrentUserId()
        if (userId != null) {
            shopSwitcherViewModel.loadInitialData(userId, requireContext())
            
            // Handle errors
            shopSwitcherViewModel.error.observe(viewLifecycleOwner) { error ->
                error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    shopSwitcherViewModel.clearError()
                }
            }
        }
    }

    private fun updateToolbarTitle() {
        // Observe active shop to update the toolbar title
        shopSwitcherViewModel.activeShop.observe(viewLifecycleOwner) { shop ->
            if (shop != null) {
                binding.topAppBar.title = shop.shopName
            } else {
                binding.topAppBar.title = getString(R.string.menu_home)
            }
        }
    }

    private fun navigateToShopSelection() {
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(R.id.action_mainScreenFragment_to_shopSelectionFragment)
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
        binding.periodSelector.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
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

//        binding.viewAllCustomers.setOnClickListener {
//            navigateToCustomersTab()
//        }
    }

    private fun setupItemPerformanceChart(invoices: List<Invoice>) {
        // Check if invoices list is empty
        if (invoices.isEmpty()) {
            binding.itemPerformanceChart.visibility = View.GONE
            return
        }

        try {
            // Calculate item sales performance by quantity
            val itemSalesPerformance = invoices.flatMap { invoice ->
                invoice.items.map { invoiceItem ->
                    InvoiceSalesData(
                        itemName = invoiceItem.itemDetails.displayName,
                        quantity = invoiceItem.quantity,
                        totalSales = invoiceItem.quantity * invoiceItem.price
                    )
                }
            }

            // Group and aggregate item sales by quantity
            val aggregatedSales = itemSalesPerformance
                .groupBy { it.itemName }
                .mapValues { (_, sales) ->
                    InvoiceSalesData(
                        itemName = sales.first().itemName,
                        quantity = sales.sumOf { it.quantity },
                        totalSales = sales.sumOf { it.totalSales }
                    )
                }

            // Sort and take top 5 items by quantity sold
            val topItems = aggregatedSales.values
                .sortedByDescending { it.quantity }
                .take(5)

            // Prepare chart entries
            val entries = ArrayList<BarEntry>()
            val labels = ArrayList<String>()

            topItems.forEachIndexed { index, item ->
                entries.add(BarEntry(index.toFloat(), item.quantity.toFloat()))
                labels.add(item.itemName)
            }

            val dataSet = BarDataSet(entries, "Top Selling Items (by Quantity)").apply {
                colors = listOf(
                    ContextCompat.getColor(requireContext(), R.color.my_light_primary),
                    ContextCompat.getColor(requireContext(), R.color.my_light_secondary),
                    ContextCompat.getColor(requireContext(), R.color.status_partial)
                )
                valueTextColor =
                    ContextCompat.getColor(requireContext(), R.color.my_light_on_surface)
                valueTextSize = 10f
            }

            val barData = BarData(dataSet)

            binding.itemPerformanceChart.apply {
                data = barData
                setDrawBarShadow(false)
                setDrawValueAboveBar(true)
                setPinchZoom(false)
                setDrawGridBackground(false)

                // X-Axis customization
                xAxis.apply {
                    valueFormatter = IndexAxisValueFormatter(labels)
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    isGranularityEnabled = true
                    labelRotationAngle = -45f // Rotate labels for better readability
                }

                // Left axis
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = ContextCompat.getColor(requireContext(), R.color.my_light_outline)
                    textColor =
                        ContextCompat.getColor(requireContext(), R.color.my_light_on_surface)
                }

                // Remove right axis
                axisRight.isEnabled = false

                // Description
                description.apply {
                    text = "Top Selling Items (Quantity)"
                    textColor = ContextCompat.getColor(requireContext(), R.color.my_light_secondary)
                    textSize = 12f
                }

                // Add interaction
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        e?.let { entry ->
                            val item = topItems[entry.x.toInt()]
                            showItemSalesDetailsDialog(item)
                        }
                    }

                    override fun onNothingSelected() {
                        // Handle when no value is selected
                    }
                })

                // Remove animation
                invalidate()
            }
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Error setting up sales performance chart", e)
            binding.itemPerformanceChart.visibility = View.GONE
        }
    }

    private fun showItemSalesDetailsDialog(item: InvoiceSalesData) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.itemName)
            .setMessage(
                """
            Quantity Sold: ${item.quantity}
            Total Sales: ₹${String.format("%.2f", item.totalSales)}
        """.trimIndent()
            )
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // Data class to hold sales information
    data class InvoiceSalesData(
        val itemName: String,
        val quantity: Int,
        val totalSales: Double
    )


    private fun loadItemPerformanceData() {
        salesViewModel.invoices.observe(viewLifecycleOwner) { invoices ->
            setupItemPerformanceChart(invoices)
            // Other existing dashboard data loading logic
        }
    }

    private fun loadDashboardData() {
        // Load data for each section of the dashboard
        loadSalesData(binding.periodSelector.selectedItem.toString())
        loadBusinessInsights()

        // Hide low stock recycler view container since we're not using it
        binding.lowStockRecyclerView.visibility = View.GONE
        binding.emptyLowStockState.visibility = View.GONE

        // Hide recent invoices section
        binding.recentInvoicesRecyclerView.visibility = View.GONE
        binding.emptyRecentInvoicesState.visibility = View.GONE
    }

    private fun loadBusinessInsights() {
        salesViewModel.invoices.observe(viewLifecycleOwner) { invoices ->
            if (invoices.isEmpty()) return@observe

            // Calculate popular categories
            updatePopularCategories(invoices)

            // Calculate peak business days
            updatePeakBusinessDays(invoices)

            // Calculate customer metrics
            updateCustomerMetrics(invoices)

            // Calculate sales growth
            updateSalesGrowth(invoices)

            // Calculate average purchase interval
            updateAveragePurchaseInterval(invoices)
        }
    }

    private fun updatePopularCategories(invoices: List<Invoice>) {
        // Group items by category (handle null/empty) and calculate total quantity sold
        val categorySales = invoices.flatMap { invoice ->
            invoice.items.map { item ->
                // Use "Uncategorized" if category is null or empty
                val categoryName =
                    item.itemDetails?.category?.takeIf { it.isNotEmpty() } ?: "Uncategorized"
                categoryName to item.quantity
            }
        }.groupBy({ it.first }, { it.second }) // Group by category name, sum quantities
            .mapValues { it.value.sum() }
            .toList()
            .sortedByDescending { it.second } // Sort by quantity descending
            .take(3) // Take top 3

        // Calculate total quantity for percentage calculation (use Double for safety)
        val totalQuantitySold =
            categorySales.sumOf { it.second }.toDouble() // Use Double for division

        // Get references to the UI layout containers and the "No Data" text view
        val categoryLayouts =
            listOf(binding.category1Layout, binding.category2Layout, binding.category3Layout)
        val categoryNames =
            listOf(binding.category1Name, binding.category2Name, binding.category3Name)
        val categoryPercentagesText = listOf(
            binding.category1Percentage,
            binding.category2Percentage,
            binding.category3Percentage
        )
        val categoryProgressBars =
            listOf(binding.category1Progress, binding.category2Progress, binding.category3Progress)
        val noDataTextView = binding.noCategoryDataText // Assuming ID from previous step


        // --- Visibility Logic ---
        if (categorySales.isEmpty() || totalQuantitySold <= 0) {
            // No data or zero total quantity, hide all category layouts and show "No Data" text
            categoryLayouts.forEach { it.visibility = View.GONE }
            noDataTextView.visibility = View.VISIBLE
            return // Exit the function early
        }

        // Data exists, hide the "No Data" text
        noDataTextView.visibility = View.GONE

        // --- Update UI for categories with data ---
        categorySales.forEachIndexed { index, (category, quantity) ->
            if (index < categoryLayouts.size) { // Ensure we don't exceed available UI elements
                val percentage =
                    (quantity.toDouble() / totalQuantitySold * 100) // Use Double for calculation
                categoryLayouts[index].visibility = View.VISIBLE // Make the layout visible
                categoryNames[index].text = category // Set category name
                categoryPercentagesText[index].text =
                    String.format(Locale.getDefault(), "%.1f%%", percentage) // Set percentage text
                categoryProgressBars[index].progress = percentage.toInt() // Set progress bar value
            }
        }

        // --- Hide layouts for ranks that don't have data ---
        // (e.g., if only 1 or 2 categories exist, hide the 2nd/3rd or 3rd layout)
        for (i in categorySales.size until categoryLayouts.size) {
            categoryLayouts[i].visibility = View.GONE // Hide unused layouts
        }
    }

    private fun updatePeakBusinessDays(invoices: List<Invoice>) {
        // Group invoices by day of week
        val dayOfWeekCounts = invoices.groupBy { invoice ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = invoice.invoiceDate
            }
            calendar.get(Calendar.DAY_OF_WEEK)
        }.mapValues { it.value.size }

        // Prepare data for the chart
        val entries = (Calendar.SUNDAY..Calendar.SATURDAY).map { day ->
            BarEntry(
                (day - Calendar.SUNDAY).toFloat(),
                dayOfWeekCounts[day]?.toFloat() ?: 0f
            )
        }

        val dataSet = BarDataSet(entries, "Sales by Day").apply {
            color = ContextCompat.getColor(requireContext(), R.color.my_light_primary)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.my_light_on_surface)
            valueTextSize = 10f
        }

        val daysOfWeek = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        binding.peakDaysChart.apply {
            data = BarData(dataSet)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(daysOfWeek)
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
            }
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false

            // Remove animation
            invalidate()
        }
    }

    private fun updateCustomerMetrics(invoices: List<Invoice>) {
        customerViewModel.customers.observe(viewLifecycleOwner) { customers ->
            // Update total customers
            binding.totalCustomersValue.text = customers.size.toString()

            // Calculate new customers this month
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH)
            val currentYear = calendar.get(Calendar.YEAR)

            val newCustomersThisMonth = customers.count { customer ->
                val customerCal = Calendar.getInstance().apply {
                    timeInMillis = customer.createdAt
                }
                customerCal.get(Calendar.MONTH) == currentMonth &&
                        customerCal.get(Calendar.YEAR) == currentYear
            }

            // Calculate new customers last month for comparison
            calendar.add(Calendar.MONTH, -1)
            val lastMonth = calendar.get(Calendar.MONTH)
            val lastMonthYear = calendar.get(Calendar.YEAR)

            val newCustomersLastMonth = customers.count { customer ->
                val customerCal = Calendar.getInstance().apply {
                    timeInMillis = customer.createdAt
                }
                customerCal.get(Calendar.MONTH) == lastMonth &&
                        customerCal.get(Calendar.YEAR) == lastMonthYear
            }

            // Update UI
            binding.newCustomersValue.text = newCustomersThisMonth.toString()

            // Calculate and show percentage change
            if (newCustomersLastMonth > 0) {
                val percentageChange =
                    ((newCustomersThisMonth - newCustomersLastMonth).toFloat() / newCustomersLastMonth * 100)
                val changeText = String.format("%+.1f%%", percentageChange)
                binding.newCustomersChange.apply {
                    text = changeText
                    setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (percentageChange >= 0) R.color.status_paid else R.color.status_unpaid
                        )
                    )
                }
            } else {
                binding.newCustomersChange.visibility = View.GONE
            }
        }
    }

    private fun updateSalesGrowth(invoices: List<Invoice>) {
        // Calculate current month's sales
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val currentMonthSales = invoices.filter { invoice ->
            val invoiceCal = Calendar.getInstance().apply {
                timeInMillis = invoice.invoiceDate
            }
            invoiceCal.get(Calendar.MONTH) == currentMonth &&
                    invoiceCal.get(Calendar.YEAR) == currentYear
        }.sumOf { it.totalAmount }

        // Calculate last month's sales
        calendar.add(Calendar.MONTH, -1)
        val lastMonth = calendar.get(Calendar.MONTH)
        val lastMonthYear = calendar.get(Calendar.YEAR)

        val lastMonthSales = invoices.filter { invoice ->
            val invoiceCal = Calendar.getInstance().apply {
                timeInMillis = invoice.invoiceDate
            }
            invoiceCal.get(Calendar.MONTH) == lastMonth &&
                    invoiceCal.get(Calendar.YEAR) == lastMonthYear
        }.sumOf { it.totalAmount }

        // Calculate growth percentage
        if (lastMonthSales > 0) {
            val growthPercentage = ((currentMonthSales - lastMonthSales) / lastMonthSales * 100)
            val isPositiveGrowth = growthPercentage >= 0

            // Update UI
            binding.salesGrowthValue.apply {
                text = String.format("%+.1f%%", growthPercentage)
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isPositiveGrowth) R.color.status_paid else R.color.status_unpaid
                    )
                )
            }

            binding.salesGrowthIndicator.apply {
                setImageResource(
                    if (isPositiveGrowth) R.drawable.si__arrow_upward_fill else R.drawable.si__arrow_downward_fill
                )
                setColorFilter(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isPositiveGrowth) R.color.status_paid else R.color.status_unpaid
                    )
                )
            }
        } else {
            binding.salesGrowthValue.text = "N/A"
            binding.salesGrowthIndicator.visibility = View.GONE
        }
    }

    private fun updateAveragePurchaseInterval(invoices: List<Invoice>) {
        // Group invoices by customer
        val customerPurchases = invoices.groupBy { it.customerId }

        // Calculate average interval for each customer
        val intervals = customerPurchases.mapNotNull { (_, purchases) ->
            if (purchases.size < 2) return@mapNotNull null

            // Sort purchases by date
            val sortedPurchases = purchases.sortedBy { it.invoiceDate }

            // Calculate average interval
            val totalInterval = sortedPurchases.zipWithNext { a, b ->
                b.invoiceDate - a.invoiceDate
            }.sum()

            totalInterval / (sortedPurchases.size - 1)
        }

        // Calculate overall average interval
        if (intervals.isNotEmpty()) {
            val averageInterval = intervals.average()
            val days = (averageInterval / (24 * 60 * 60 * 1000)).toInt()

            binding.avgPurchaseIntervalValue.text =
                if (days > 30) "${days / 30} months" else "$days days"
        } else {
            binding.avgPurchaseIntervalValue.text = "N/A"
        }
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
                    val weekStart =
                        today - ((today % 86400000) + ((System.currentTimeMillis() / 86400000) % 7) * 86400000)
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

    private fun updateSalesMetrics(invoices: List<Invoice>) {
        // Calculate total sales
        val totalSales = invoices.sumOf { it.totalAmount }
        binding.totalSalesValue.text = currencyFormatter.format(totalSales)

        // Count of invoices
        binding.invoiceCountValue.text = invoices.size.toString()

        // Calculate truly outstanding balance (unpaid amount)
        val outstandingBalance = invoices
            .filter { it.totalAmount > it.paidAmount }
            .sumOf { it.totalAmount - it.paidAmount }

        binding.outstandingBalanceValue.text = currencyFormatter.format(outstandingBalance)
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
        val customerBottomSheet =
            CustomerBottomSheetFragment.newInstance()
        customerBottomSheet.setCustomerOperationListener(this)
        customerBottomSheet.show(
            parentFragmentManager,
            CustomerBottomSheetFragment.TAG
        )
    }

    private fun navigateToAddInventory() {
        // Navigate to inventory tab first
        navigateToInventoryTab()

        val bottomSheetFragment = ItemBottomSheetFragment.newInstance()
        bottomSheetFragment.setOnItemAddedListener(this)
        bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)

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


    override fun onResume() {
        super.onResume()
        // Refresh notification count when returning to this fragment
        Log.d("DashBoardFragment", "onResume, refreshing notification count")
        notificationViewModel.refreshUnreadCount()

        // Check managed shops when returning to this fragment
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onCustomerAdded(customer: Customer) {
        viewLifecycleOwner.lifecycleScope.launch {
            customerViewModel.addCustomer(customer)
            Snackbar.make(binding.root, "Customer added successfully", Snackbar.LENGTH_SHORT).show()
            // fetchDashboardData() // Optional: Refresh if needed
        }
    }

    override fun onCustomerUpdated(customer: Customer) {
        TODO("Not yet implemented")
    }

    override fun onItemAdded(item: JewelleryItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            inventoryViewModel.addJewelleryItem(item)
            Snackbar.make(binding.root, "Item added successfully", Snackbar.LENGTH_SHORT).show()
            // Optionally refresh top items if desired
            // salesViewModel.fetchTopSellingItems(5)
        }
    }

    override fun onItemUpdated(item: JewelleryItem) {
        TODO("Not yet implemented")
    }

    private fun observeShopChanges() {
        // Observe shop changes from the shop switcher view model
        shopSwitcherViewModel.activeShop.observe(viewLifecycleOwner) { shop ->
            shop?.let {
                Log.d("DashBoardFragment", "Shop changed to: ${shop.shopName}")
                
                // First refresh notification count when shop changes
                notificationViewModel.setCurrentShop(shop.shopId)
                notificationViewModel.refreshUnreadCount()
                
                // Refresh all data sources when shop changes
                salesViewModel.refreshInvoices()
                customerViewModel.refreshData()
                inventoryViewModel.refreshDataAndClearFilters()
                
                // Reload dashboard data
                loadDashboardData()
                loadItemPerformanceData()
            }
        }
    }
}