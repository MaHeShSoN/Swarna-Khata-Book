package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.graphics.Color
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import kotlinx.coroutines.withContext
import com.github.mikephil.charting.components.Legend
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
        CustomerViewModelFactory(repository, connectivityManager, requireContext())
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
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("DashBoardFragment", "onCreateView: Creating view")
        _binding = FragmentDashBoardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("DashBoardFragment", "onViewCreated: Setting up fragment")

        setupToolbar()
        setupQuickActions()

        // Load active shop info
        loadActiveShopInfo()

        // Observe shop changes
        observeShopChanges()

        // Observe dashboard data
        observeDashboardData()
    }

    private fun setupToolbar() {
        Log.d("DashBoardFragment", "setupToolbar: Initializing toolbar")
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_notifications -> {
                    Log.d("DashBoardFragment", "setupToolbar: Notifications menu item clicked")
                    navigateToNotifications()
                    true
                }

                R.id.action_switch_shop -> {
                    Log.d("DashBoardFragment", "setupToolbar: Switch shop menu item clicked")
                    navigateToShopSelection()
                    true
                }

                else -> false
            }
        }
        setupNotificationBadge()
        updateToolbarTitle()
    }

    private fun setupNotificationBadge() {
        Log.d("DashBoardFragment", "setupNotificationBadge: Setting up notification badge")
        // Get the menu item view
        val menuItem = binding.topAppBar.menu.findItem(R.id.action_notifications) ?: return
        val actionView = menuItem.actionView ?: return

        // Find the badge text view within the action view
        val badgeCountView = actionView.findViewById<TextView>(R.id.badgeCount)

        // Set click listener for the entire action view
        actionView.setOnClickListener {
            Log.d("DashBoardFragment", "setupNotificationBadge: Badge clicked")
            navigateToNotifications()
        }

        // Observe unread notification count from view model
        notificationViewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            Log.d("DashBoardFragment", "setupNotificationBadge: Unread count updated: $count")
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
            Log.d("DashBoardFragment", "setupNotificationBadge: Shop changed, new ID: $shopId")
            shopId?.let {
                notificationViewModel.setCurrentShop(it)
                notificationViewModel.refreshUnreadCount()
            }
        }
    }

    private fun loadActiveShopInfo() {
        Log.d("DashBoardFragment", "loadActiveShopInfo: Loading active shop information")
        val userId = SessionManager.getCurrentUserId()
        if (userId != null) {
            Log.d("DashBoardFragment", "loadActiveShopInfo: User ID found: $userId")
            shopSwitcherViewModel.loadInitialData(userId, requireContext())

            // Handle errors
            shopSwitcherViewModel.error.observe(viewLifecycleOwner) { error ->
                error?.let {
                    Log.e("DashBoardFragment", "loadActiveShopInfo: Error loading shop info: $it")
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    shopSwitcherViewModel.clearError()
                }
            }
        } else {
            Log.e("DashBoardFragment", "loadActiveShopInfo: No user ID found")
        }
    }

    private fun updateToolbarTitle() {
        Log.d("DashBoardFragment", "updateToolbarTitle: Updating toolbar title")
        // Observe active shop to update the toolbar title
        shopSwitcherViewModel.activeShop.observe(viewLifecycleOwner) { shop ->
            if (shop != null) {
                Log.d("DashBoardFragment", "updateToolbarTitle: Setting title to shop name: ${shop.shopName}")
                binding.topAppBar.title = shop.shopName
            } else {
                Log.d("DashBoardFragment", "updateToolbarTitle: Setting default title")
                binding.topAppBar.title = getString(R.string.menu_home)
            }
        }
    }

    private fun navigateToShopSelection() {
        requireActivity().findNavController(R.id.nav_host_fragment)
            .navigate(R.id.action_mainScreenFragment_to_shopSelectionFragment)
    }

    private fun setupQuickActions() {
        Log.d("DashBoardFragment", "setupQuickActions: Setting up quick action buttons")
        // Create Invoice Action
        binding.createInvoiceAction.setOnClickListener {
            Log.d("DashBoardFragment", "setupQuickActions: Create invoice clicked")
            navigateToCreateInvoice()
        }

        // Add Customer Action
        binding.addCustomerAction.setOnClickListener {
            Log.d("DashBoardFragment", "setupQuickActions: Add customer clicked")
            navigateToAddCustomer()
        }

        // Add Inventory Action
        binding.addInventoryAction.setOnClickListener {
            Log.d("DashBoardFragment", "setupQuickActions: Add inventory clicked")
            navigateToAddInventory()
        }

        // Record Payment Action
        binding.recordPaymentAction.setOnClickListener {
            Log.d("DashBoardFragment", "setupQuickActions: Record payment clicked")
            navigateToPayments()
        }
    }

    // Data class to hold sales information
    data class InvoiceSalesData(
        val itemName: String,
        val quantity: Int,
        val totalSales: Double
    )

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
        Log.d("DashBoardFragment", "onResume: Fragment resumed")
        // Only refresh notification count if needed
        if (isAdded) {
            Log.d("DashBoardFragment", "onResume: Refreshing notification count")
            notificationViewModel.refreshUnreadCount()
        }
    }

    override fun onDestroyView() {
        Log.d("DashBoardFragment", "onDestroyView: Cleaning up fragment")
        super.onDestroyView()
        _binding = null
    }

    override fun onCustomerAdded(customer: Customer) {
        Log.d("DashBoardFragment", "onCustomerAdded: New customer added: ${customer.firstName} ${customer.lastName}")
        viewLifecycleOwner.lifecycleScope.launch {
            customerViewModel.addCustomer(customer)
            Snackbar.make(binding.root, "Customer added successfully", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCustomerUpdated(customer: Customer) {
        TODO("Not yet implemented")
    }

    override fun onItemAdded(item: JewelleryItem) {
        Log.d("DashBoardFragment", "onItemAdded: New item added: ${item.displayName}")
        viewLifecycleOwner.lifecycleScope.launch {
            inventoryViewModel.addJewelleryItem(item)
            Snackbar.make(binding.root, "Item added successfully", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onItemUpdated(item: JewelleryItem) {
        TODO("Not yet implemented")
    }

    private fun observeDashboardData() {
        Log.d("DashBoardFragment", "observeDashboardData: Starting dashboard data observation")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Observe sales data
                salesViewModel.dashboardData.observe(viewLifecycleOwner) { data ->
                    if (data == null) {
                        Log.d("DashBoardFragment", "observeDashboardData: Received null dashboard data - waiting for initial data load")
                    } else {
                        Log.d("DashBoardFragment", """
                            observeDashboardData: Received new dashboard data:
                            - Total Amount: ${currencyFormatter.format(data.totalAmount)}
                            - Paid Amount: ${currencyFormatter.format(data.paidAmount)}
                            - Unpaid Amount: ${currencyFormatter.format(data.unpaidAmount)}
                            - Today's Sales: ${currencyFormatter.format(data.todaySales)}
                            - Sales by Date entries: ${data.salesByDate.size}
                        """.trimIndent())

                        // Update pie chart
                        setupSalesPieChart(data.totalAmount, data.paidAmount, data.unpaidAmount)

                        // Update text views
                        binding.tvTotalAmount.text = currencyFormatter.format(data.totalAmount)
                        binding.tvPaidAmount.text = currencyFormatter.format(data.paidAmount)
                        binding.tvUnpaidAmount.text = currencyFormatter.format(data.unpaidAmount)
                        binding.tvTodaySales.text = currencyFormatter.format(data.todaySales)

                        // Calculate and update sales insights
                        if (data.salesByDate.isNotEmpty()) {
                            calculateSalesInsights(data.salesByDate)
                        } else {
                            Log.d("DashBoardFragment", "No sales data available for insights calculation")
                            // Set default values for insights
                            binding.tvPeakSalesDay.text = "No data"
                            binding.tvPeakSalesTime.text = "No data"
                            binding.tvMonthlyTrend.text = "No data"
                            binding.tvSeasonalPeak.text = "No data"
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("DashBoardFragment", "observeDashboardData: Data observation cancelled")
                } else {
                    Log.e("DashBoardFragment", "observeDashboardData: Error observing dashboard data: ${e.message}", e)
                    Toast.makeText(requireContext(), "Error loading dashboard data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSalesPieChart(total: Double, paid: Double, unpaid: Double) {
        Log.d("DashBoardFragment", """
            setupSalesPieChart: Setting up pie chart:
            - Total Sales: ${currencyFormatter.format(total)}
            - Total Received: ${currencyFormatter.format(paid)}
            - Total Due: ${currencyFormatter.format(unpaid)}
            - Received Percentage: ${if (total > 0) (paid/total * 100) else 0}%
        """.trimIndent())

        if (total == 0.0) {
            Log.d("DashBoardFragment", "setupSalesPieChart: No sales data available - showing empty state")
            binding.salesPieChart.setNoDataText("No sales data available")
            binding.salesPieChart.invalidate()
            return
        }

        val entries = ArrayList<PieEntry>().apply {
            add(PieEntry(paid.toFloat(), "Total Received"))
            add(PieEntry(unpaid.toFloat(), "Total Due"))
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                ContextCompat.getColor(requireContext(), R.color.status_completed),  // Green for received
                ContextCompat.getColor(requireContext(), R.color.status_cancelled)   // Red for due
            )
            valueTextColor = Color.BLACK
            valueTextSize = 12f
            sliceSpace = 3f
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueLinePart1OffsetPercentage = 80f
            valueLinePart1Length = 0.3f
            valueLinePart2Length = 0.4f
        }

        val data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(binding.salesPieChart))
            setValueTextSize(12f)
            setValueTextColor(Color.BLACK)
        }

        binding.salesPieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleRadius(58f)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(12f)
            animateY(1000)
            legend.isEnabled = true
            legend.textSize = 12f
            legend.textColor = Color.BLACK
            legend.formSize = 12f
            legend.formLineWidth = 2f
            legend.form = Legend.LegendForm.CIRCLE
            legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.setDrawInside(false)
            legend.xEntrySpace = 20f
            legend.yEntrySpace = 10f
            setDrawEntryLabels(false)
            setCenterText("Total Sales\n${currencyFormatter.format(total)}")
            setCenterTextSize(14f)
            setCenterTextColor(Color.BLACK)
            this.data = data
            invalidate()
        }
    }

    private fun calculateSalesInsights(salesByDate: Map<String, Double>) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Calculate peak sales day
                val peakDay = salesByDate.maxByOrNull { it.value }
                binding.tvPeakSalesDay.text = peakDay?.let { 
                    "${formatDate(it.key)} (${currencyFormatter.format(it.value)})"
                } ?: "No data"

                // Calculate peak sales time
                val peakTime = calculatePeakSalesTime(salesByDate)
                binding.tvPeakSalesTime.text = peakTime

                // Calculate monthly trend
                val monthlyTrend = calculateMonthlyTrend(salesByDate)
                binding.tvMonthlyTrend.text = monthlyTrend

                // Calculate seasonal peak
                val seasonalPeak = calculateSeasonalPeak(salesByDate)
                binding.tvSeasonalPeak.text = seasonalPeak
            } catch (e: Exception) {
                Log.e("DashBoardFragment", "Error calculating sales insights: ${e.message}")
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val date = LocalDate.parse(dateStr)
            date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun calculatePeakSalesTime(salesByDate: Map<String, Double>): String {
        // Group sales by hour and find the peak hour
        val salesByHour = salesByDate.entries.groupBy { 
            LocalDateTime.parse(it.key).hour 
        }.mapValues { entry ->
            entry.value.sumOf { it.value }
        }
        
        val peakHour = salesByHour.maxByOrNull { it.value }
        return peakHour?.let {
            val hour = it.key
            val amPm = if (hour < 12) "AM" else "PM"
            val displayHour = when (hour) {
                0 -> 12
                in 13..23 -> hour - 12
                else -> hour
            }
            "$displayHour:00 $amPm (${currencyFormatter.format(it.value)})"
        } ?: "No data"
    }

    private fun calculateMonthlyTrend(salesByDate: Map<String, Double>): String {
        // Group sales by month and calculate trend
        val salesByMonth = salesByDate.entries.groupBy { 
            LocalDate.parse(it.key).month 
        }.mapValues { entry ->
            entry.value.sumOf { it.value }
        }

        val currentMonth = LocalDate.now().month
        val lastMonth = currentMonth.minus(1)
        
        val currentMonthSales = salesByMonth[currentMonth] ?: 0.0
        val lastMonthSales = salesByMonth[lastMonth] ?: 0.0
        
        val trend = if (lastMonthSales > 0) {
            ((currentMonthSales - lastMonthSales) / lastMonthSales) * 100
        } else 0.0

        return when {
            trend > 0 -> "↑ ${String.format("%.1f", trend)}% from last month"
            trend < 0 -> "↓ ${String.format("%.1f", -trend)}% from last month"
            else -> "Same as last month"
        }
    }

    private fun calculateSeasonalPeak(salesByDate: Map<String, Double>): String {
        // Group sales by month and find the peak month
        val salesByMonth = salesByDate.entries.groupBy { 
            LocalDate.parse(it.key).month 
        }.mapValues { entry ->
            entry.value.sumOf { it.value }
        }

        val peakMonth = salesByMonth.maxByOrNull { it.value }
        return peakMonth?.let {
            "${it.key.name} (${currencyFormatter.format(it.value)})"
        } ?: "No data"
    }

    private fun observeShopChanges() {
        Log.d("DashBoardFragment", "observeShopChanges: Starting shop changes observation")
        shopSwitcherViewModel.activeShop.observe(viewLifecycleOwner) { shop ->
            if (shop == null) {
                Log.d("DashBoardFragment", "observeShopChanges: No active shop selected")
                return@observe
            }
            
            Log.d("DashBoardFragment", """
                observeShopChanges: Shop changed:
                - Shop Name: ${shop.shopName}
                - Shop ID: ${shop.shopId}
                - Previous Shop ID: ${SessionManager.activeShopIdLiveData.value}
            """.trimIndent())

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Refresh notification count
                    Log.d("DashBoardFragment", "observeShopChanges: Refreshing notification count for shop: ${shop.shopName}")
                    notificationViewModel.setCurrentShop(shop.shopId)
                    notificationViewModel.refreshUnreadCount()

                    // Refresh dashboard data
                    Log.d("DashBoardFragment", "observeShopChanges: Refreshing dashboard data for shop: ${shop.shopName}")
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        salesViewModel.refreshInvoices()
                    }
                    
                    // Move inventory refresh to main thread
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        inventoryViewModel.refreshDataAndClearFilters()
                    }
                    
                    Log.d("DashBoardFragment", "observeShopChanges: Data refresh completed for shop: ${shop.shopName}")
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d("DashBoardFragment", "observeShopChanges: Data refresh cancelled")
                    } else {
                        Log.e("DashBoardFragment", "observeShopChanges: Error refreshing data after shop change: ${e.message}", e)
                    }
                }
            }
        }
    }
}