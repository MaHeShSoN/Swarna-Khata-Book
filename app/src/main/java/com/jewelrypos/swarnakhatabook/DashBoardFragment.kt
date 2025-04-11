package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
import com.jewelrypos.swarnakhatabook.Utilitys.MainScreenNavigator
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentDashBoardBinding
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Calendar
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
        loadItemPerformanceData()
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
        setupNotificationBadge()
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

        // Observe unread notification count from view model
        notificationViewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            if (count > 0) {
                // Show badge with count
                badgeCountView.visibility = View.VISIBLE
                badgeCountView.text = if (count > 99) "99+" else count.toString()
            } else {
                // Hide badge when no unread notifications
                badgeCountView.visibility = View.GONE
            }
        }

        // Refresh the unread count
        notificationViewModel.refreshUnreadCount()
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

                // Animate and refresh
                animateY(1500, Easing.EaseInOutQuad)
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
        // Group items by category and calculate total quantity sold
        val categorySales = invoices.flatMap { invoice ->
            invoice.items.map { item ->
                item.itemDetails.category to item.quantity
            }
        }.groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        // Calculate percentages
        val totalSales = categorySales.sumOf { it.second }
        val categoryPercentages = categorySales.map {
            it.first to (it.second.toFloat() / totalSales * 100)
        }

        // Update UI for each category
        categoryPercentages.getOrNull(0)?.let { (category, percentage) ->
            binding.category1Name.text = category
            binding.category1Percentage.text = String.format("%.1f%%", percentage)
            binding.category1Progress.progress = percentage.toInt()
        }

        categoryPercentages.getOrNull(1)?.let { (category, percentage) ->
            binding.category2Name.text = category
            binding.category2Percentage.text = String.format("%.1f%%", percentage)
            binding.category2Progress.progress = percentage.toInt()
        }

        categoryPercentages.getOrNull(2)?.let { (category, percentage) ->
            binding.category3Name.text = category
            binding.category3Percentage.text = String.format("%.1f%%", percentage)
            binding.category3Progress.progress = percentage.toInt()
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

            animateY(1000)
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
                val percentageChange = ((newCustomersThisMonth - newCustomersLastMonth).toFloat() / newCustomersLastMonth * 100)
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
            com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment.newInstance()
        customerBottomSheet.show(
            parentFragmentManager,
            com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment.TAG
        )
    }

    private fun navigateToAddInventory() {
        // Navigate to inventory tab first
        navigateToInventoryTab()

        val bottomSheetFragment = ItemBottomSheetFragment.newInstance()
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
        notificationViewModel.refreshUnreadCount()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}