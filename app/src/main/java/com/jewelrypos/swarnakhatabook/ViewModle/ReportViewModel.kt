package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.CustomerSalesData
import com.jewelrypos.swarnakhatabook.DataClasses.GstReportItem
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InventoryValueItem
import com.jewelrypos.swarnakhatabook.DataClasses.ItemSalesData
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.LowStockItem
import com.jewelrypos.swarnakhatabook.DataClasses.SalesReportData
import com.jewelrypos.swarnakhatabook.DataClasses.SalesByCategoryItem
import com.jewelrypos.swarnakhatabook.DataClasses.SalesByCustomerTypeItem
import com.jewelrypos.swarnakhatabook.DataClasses.SalesByDateItem
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "ReportViewModel"

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val activeShopId: String?

    private val invoiceRepository: InvoiceRepository
    private val customerRepository: CustomerRepository
    private val inventoryRepository: InventoryRepository

    // Synchronization objects
    private val salesReportLock = Any()
    private val gstReportLock = Any()
    private var isInitialized = false
    private var isSalesReportLoading = false
    private var isGstReportLoading = false

    // Date range for reports
    private val _startDate = MutableLiveData<Date>()
    val startDate: LiveData<Date> = _startDate

    private val _endDate = MutableLiveData<Date>()
    val endDate: LiveData<Date> = _endDate

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error message
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    // Sales Report Data
    private val _salesReportData = MutableLiveData<SalesReportData?>()
    val salesReportData: LiveData<SalesReportData?> = _salesReportData

    // Inventory Report Data
    private val _inventoryItems = MutableLiveData<List<InventoryValueItem>>()
    val inventoryItems: LiveData<List<InventoryValueItem>> = _inventoryItems

    private val _totalInventoryValue = MutableLiveData<Double>(0.0)
    val totalInventoryValue: LiveData<Double> = _totalInventoryValue

    // GST Report Data
    private val _gstReportItems = MutableLiveData<List<GstReportItem>>()
    val gstReportItems: LiveData<List<GstReportItem>> = _gstReportItems

    // Low Stock Report Data
    private val _lowStockItems = MutableLiveData<List<LowStockItem>>()
    val lowStockItems: LiveData<List<LowStockItem>> = _lowStockItems

    // Customers for selection
    private val _customers = MutableLiveData<List<Customer>>()
    val customers: LiveData<List<Customer>> = _customers

    // Selected customer for account statement
    private val _selectedCustomer = MutableLiveData<Customer?>()
    val selectedCustomer: LiveData<Customer?> = _selectedCustomer

    // Customer statement data
    private val _customerTransactions =
        MutableLiveData<List<Any>>() // Will contain invoices and payments
    val customerTransactions: LiveData<List<Any>> = _customerTransactions

    private val _openingBalance = MutableLiveData<Double>(0.0)
    val openingBalance: LiveData<Double> = _openingBalance

    private val _closingBalance = MutableLiveData<Double>(0.0)
    val closingBalance: LiveData<Double> = _closingBalance

    private val _topSellingItems = MutableLiveData<List<ItemSalesData>?>()
    val topSellingItems: LiveData<List<ItemSalesData>?> = _topSellingItems

    private val _topCustomers = MutableLiveData<List<CustomerSalesData>?>()
    val topCustomers: LiveData<List<CustomerSalesData>?> = _topCustomers

    private var isGeneratingReport = false
    private var currentReportJob: kotlinx.coroutines.Job? = null

    init {
        Log.d(TAG, "Initializing ReportViewModel")
        if (isInitialized) {
            Log.d(TAG, "ReportViewModel already initialized, skipping")
        }

        // Get the active shop ID from SessionManager
        activeShopId = SessionManager.getActiveShopId(application.applicationContext)
        Log.d(TAG, "Active shop ID: $activeShopId")

        // Initialize repositories with application context
        invoiceRepository = InvoiceRepository(firestore, auth, application.applicationContext)
        customerRepository = CustomerRepository(firestore, auth, application.applicationContext)
        inventoryRepository = InventoryRepository(firestore, auth, application.applicationContext)
        Log.d(TAG, "Repositories initialized")

        // Set default date range to current month
        val calendar = Calendar.getInstance()

        // Set end date to today at 23:59:59
        val endDate = calendar.time

        // Set start date to first day of current month at 00:00:00
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time

        _startDate.value = startDate
        _endDate.value = endDate
        Log.d(TAG, "Default date range set: $startDate to $endDate")

        // Load customers for selection
//        loadCustomers()
//
        // Initialize reports
//        viewModelScope.launch {
//            loadSalesReport()
//            generateGstReport()
//        }
        
        isInitialized = true
        Log.d(TAG, "ReportViewModel initialization completed")
    }

    fun setDateRange(start: Date, end: Date) {
        Log.d(TAG, "Setting new date range: $start to $end")
        _startDate.value = start
        _endDate.value = end
        loadSalesReport()
    }

    fun selectCustomer(customer: Customer) {
        Log.d(TAG, "Selecting customer: ${customer.firstName} (ID: ${customer.id})")
        _selectedCustomer.value = customer
        loadCustomerStatement(customer)
    }

    fun clearSelectedCustomer() {
        Log.d(TAG, "Clearing selected customer")
        _selectedCustomer.value = null
        _customerTransactions.value = emptyList()
    }

    fun loadSalesReport() {
        synchronized(salesReportLock) {
            if (isSalesReportLoading) {
                Log.d(TAG, "loadSalesReport: Already loading, skipping")
                return
            }

            val start = _startDate.value ?: return
            val end = _endDate.value ?: return
            Log.d(TAG, "loadSalesReport: Starting report generation for period: $start to $end")

            if (activeShopId.isNullOrEmpty()) {
                Log.e(TAG, "loadSalesReport: No active shop selected")
                _errorMessage.postValue("No active shop selected")
                return
            }

            isSalesReportLoading = true
            _isLoading.value = true
            
            viewModelScope.launch {
                val totalStartTime = System.currentTimeMillis()
                try {
                    Log.d(TAG, "loadSalesReport: Fetching invoices between dates")
                    val fetchStartTime = System.currentTimeMillis()
                    val invoices = invoiceRepository.getInvoicesBetweenDates(start, end)
                    Log.d(TAG, "loadSalesReport: Retrieved ${invoices?.size ?: 0} invoices in ${System.currentTimeMillis() - fetchStartTime}ms")

                    if (invoices.isNullOrEmpty()) {
                        Log.d(TAG, "loadSalesReport: No sales data found for the selected period")
                        _salesReportData.postValue(null)
                        _topSellingItems.postValue(emptyList())
                        _topCustomers.postValue(emptyList())
                    } else {
                        Log.d(TAG, "loadSalesReport: Processing sales data")
                        val processStartTime = System.currentTimeMillis()
                        
                        // --- Calculate Sales Report Data ---
                        val totalSales = invoices.sumOf { it.totalAmount }
                        val totalPaid = invoices.sumOf { it.paidAmount }
                        val unpaidAmount = totalSales - totalPaid
                        val collectionRate = if (totalSales > 0) (totalPaid / totalSales) * 100 else 0.0
                        val invoiceCount = invoices.size

                        Log.d(TAG, """
                            loadSalesReport: Basic calculations completed in ${System.currentTimeMillis() - processStartTime}ms
                            - Total Sales: $totalSales
                            - Total Paid: $totalPaid
                            - Unpaid Amount: $unpaidAmount
                            - Collection Rate: $collectionRate%
                            - Invoice Count: $invoiceCount
                        """.trimIndent())

                        // Calculate Sales by Category
                        val categoryStartTime = System.currentTimeMillis()
                        val salesByCategory = invoices.flatMap { it.items }
                            .groupBy { it.itemDetails?.category ?: "Uncategorized" }
                            .mapValues { entry -> entry.value.sumOf { item -> item.price * item.quantity } }
                            .map { SalesByCategoryItem(it.key, it.value) }
                        Log.d(TAG, "loadSalesReport: Category calculations completed in ${System.currentTimeMillis() - categoryStartTime}ms")

                        // Calculate Sales by Date
                        val dateStartTime = System.currentTimeMillis()
                        val salesByDate = invoices
                            .groupBy { invoice ->
                                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                dateFormat.format(Date(invoice.invoiceDate))
                            }
                            .mapValues { entry -> entry.value.sumOf { inv -> inv.totalAmount } }
                            .map { SalesByDateItem(it.key, it.value) }
                            .sortedBy { it.date }
                        Log.d(TAG, "loadSalesReport: Date calculations completed in ${System.currentTimeMillis() - dateStartTime}ms")

                        val reportData = SalesReportData(
                            totalSales = totalSales,
                            paidAmount = totalPaid,
                            unpaidAmount = unpaidAmount,
                            collectionRate = collectionRate,
                            invoiceCount = invoiceCount,
                            salesByCategory = salesByCategory,
                            salesByCustomerType = emptyList(),
                            salesByDate = salesByDate
                        )
                        _salesReportData.postValue(reportData)

                        // --- Calculate Top Selling Items ---
                        val topItemsStartTime = System.currentTimeMillis()
                        val itemSales = invoices.flatMap { it.items }
                            .groupBy { it.itemDetails?.displayName ?: "Unknown Item" }
                            .map { entry ->
                                val name = entry.key
                                val quantity = entry.value.sumOf { item -> item.quantity }
                                val revenue = entry.value.sumOf { item -> item.price * item.quantity }
                                ItemSalesData(name, quantity, revenue)
                            }
                            .sortedByDescending { it.totalRevenue }
                            .take(5)
                        _topSellingItems.postValue(itemSales)
                        Log.d(TAG, "loadSalesReport: Top items calculations completed in ${System.currentTimeMillis() - topItemsStartTime}ms")

                        // --- Calculate Top Customers ---
                        val topCustomersStartTime = System.currentTimeMillis()
                        val customerSales = invoices
                            .filter { it.customerId.isNotBlank() }
                            .groupBy { it.customerId }
                            .map { entry ->
                                val customerId = entry.key
                                val customerInvoices = entry.value
                                val totalValue = customerInvoices.sumOf { inv -> inv.totalAmount }
                                val count = customerInvoices.size
                                val name = customerInvoices.firstOrNull()?.customerName?.trim() ?: customerId

                                CustomerSalesData(
                                    name.ifBlank { customerId },
                                    totalValue,
                                    count
                                )
                            }
                            .sortedByDescending { it.totalPurchaseValue }
                            .take(5)
                        _topCustomers.postValue(customerSales)
                        Log.d(TAG, "loadSalesReport: Top customers calculations completed in ${System.currentTimeMillis() - topCustomersStartTime}ms")

                        Log.d(TAG, "loadSalesReport: All calculations completed in ${System.currentTimeMillis() - processStartTime}ms")
                        _errorMessage.postValue(null)
                    }

                    Log.d(TAG, "loadSalesReport: Total execution time: ${System.currentTimeMillis() - totalStartTime}ms")
                } catch (e: Exception) {
                    Log.e(TAG, "loadSalesReport: Error loading sales report", e)
                    _errorMessage.postValue("Error loading sales report: ${e.localizedMessage}")
                    _salesReportData.postValue(null)
                    _topSellingItems.postValue(null)
                    _topCustomers.postValue(null)
                } finally {
                    isSalesReportLoading = false
                    _isLoading.postValue(false)
                }
            }
        }
    }

    // Load customers for customer statement selection
    fun loadCustomers() {
        Log.d(TAG, "loadCustomers: Starting customer load")
        if (activeShopId.isNullOrEmpty()) {
            Log.e(TAG, "loadCustomers: No active shop selected")
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                Log.d(TAG, "loadCustomers: Fetching customers from repository")
                customerRepository.fetchCustomersPaginated(
                    loadNextPage = false,
                    source = Source.DEFAULT
                ).fold(
                    onSuccess = { customers ->
                        Log.d(TAG, "loadCustomers: Successfully loaded ${customers.size} customers")
                        _customers.value = customers
                        _isLoading.value = false
                    },
                    onFailure = { error ->
                        Log.e(TAG, "loadCustomers: Failed to load customers", error)
                        _errorMessage.value = "Failed to load customers: ${error.message}"
                        _isLoading.value = false
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadCustomers: Error loading customers", e)
                _errorMessage.value = "Error loading customers: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // Sales Report Generation
    fun generateSalesReport() {
        Log.d(TAG, "generateSalesReport: Starting sales report generation")
        if (activeShopId.isNullOrEmpty()) {
            Log.e(TAG, "generateSalesReport: No active shop selected")
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Load all invoices in date range
                val startTimestamp = startDate.value?.time ?: 0
                val endTimestamp = endDate.value?.time ?: System.currentTimeMillis()
                Log.d(TAG, "generateSalesReport: Date range: ${Date(startTimestamp)} to ${Date(endTimestamp)}")

                val allInvoices = withContext(Dispatchers.IO) {
                    Log.d(TAG, "generateSalesReport: Starting invoice fetch")
                    // Get all invoices for this shop
                    val allInvoicesList = mutableListOf<Invoice>()
                    var hasMoreInvoices = true
                    var loadNextPage = false
                    var pageCount = 0

                    while (hasMoreInvoices) {
                        pageCount++
                        Log.d(TAG, "generateSalesReport: Fetching page $pageCount")
                        
                        val result = invoiceRepository.fetchInvoicesPaginated(loadNextPage)

                        result.fold(
                            onSuccess = { invoices ->
                                if (invoices.isEmpty()) {
                                    Log.d(TAG, "generateSalesReport: No more invoices to fetch")
                                    hasMoreInvoices = false
                                } else {
                                    Log.d(TAG, "generateSalesReport: Fetched ${invoices.size} invoices in page $pageCount")
                                    allInvoicesList.addAll(invoices)
                                    loadNextPage = true
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "generateSalesReport: Error fetching invoices", error)
                                hasMoreInvoices = false
                                throw error
                            }
                        )
                    }

                    // Filter by date range
                    val filteredInvoices = allInvoicesList.filter { invoice ->
                        invoice.invoiceDate in startTimestamp..endTimestamp
                    }
                    Log.d(TAG, "generateSalesReport: Filtered to ${filteredInvoices.size} invoices in date range")
                    filteredInvoices
                }

                // Process invoices to generate report data
                Log.d(TAG, "generateSalesReport: Processing invoices for report")
                val salesData = processInvoicesForSalesReport(allInvoices)
                _salesReportData.value = salesData
                _isLoading.value = false
                Log.d(TAG, "generateSalesReport: Report generation completed successfully")

            } catch (e: Exception) {
                Log.e(TAG, "generateSalesReport: Failed to generate sales report", e)
                _errorMessage.value = "Failed to generate sales report: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private suspend fun processInvoicesForSalesReport(invoices: List<Invoice>): SalesReportData {
        Log.d(TAG, "processInvoicesForSalesReport: Processing ${invoices.size} invoices")
        return withContext(Dispatchers.Default) {
            try {
                // Calculate total sales and invoice count
                val totalSales = invoices.sumOf { it.totalAmount }
                val totalPaid = invoices.sumOf { it.paidAmount }
                val invoiceCount = invoices.size
                Log.d(TAG, """
                    processInvoicesForSalesReport: Basic calculations
                    - Total Sales: $totalSales
                    - Total Paid: $totalPaid
                    - Invoice Count: $invoiceCount
                """.trimIndent())

                // Group sales by item type (Gold, Silver, Other)
                val salesByCategory = mutableMapOf<String, Double>()

                // Group sales by customer type (Consumer vs Wholesaler)
                val salesByCustomerType = mutableMapOf<String, Double>()

                // Sales by date for trend analysis
                val salesByDate = mutableMapOf<Long, Double>()

                // Load customers to get customer types
                Log.d(TAG, "processInvoicesForSalesReport: Loading customers for type information")
                val customersResult = customerRepository.fetchCustomersPaginated(
                    loadNextPage = false
                )
                val customers = customersResult.getOrNull() ?: emptyList()
                val customerMap = customers.associateBy { it.id }
                Log.d(TAG, "processInvoicesForSalesReport: Loaded ${customers.size} customers")

                // Process each invoice
                invoices.forEach { invoice ->
                    // Process items for category breakdown
                    invoice.items.forEach { item ->
                        val itemType = item.itemDetails.itemType.uppercase()
                        val itemTotal = item.price * item.quantity
                        salesByCategory[itemType] = (salesByCategory[itemType] ?: 0.0) + itemTotal
                    }

                    // Process customer type breakdown
                    val customerType = customerMap[invoice.customerId]?.customerType ?: "Consumer"
                    salesByCustomerType[customerType] =
                        (salesByCustomerType[customerType] ?: 0.0) + invoice.totalAmount

                    // Process date for trends
                    // Group by day for simplicity
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = invoice.invoiceDate
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val dayTimestamp = calendar.timeInMillis

                    salesByDate[dayTimestamp] = (salesByDate[dayTimestamp] ?: 0.0) + invoice.totalAmount
                }

                Log.d(TAG, """
                    processInvoicesForSalesReport: Category breakdown
                    - Categories: ${salesByCategory.keys.joinToString()}
                    - Customer Types: ${salesByCustomerType.keys.joinToString()}
                    - Date Range: ${salesByDate.keys.size} days
                """.trimIndent())

                // Convert maps to sorted lists for display
                val salesByCategoryList = salesByCategory.map { (category, amount) ->
                    SalesByCategoryItem(category, amount)
                }.sortedByDescending { it.amount }

                val salesByCustomerTypeList = salesByCustomerType.map { (type, amount) ->
                    SalesByCustomerTypeItem(type, amount)
                }.sortedByDescending { it.amount }

                val salesByDateList = salesByDate.map { (date, amount) ->
                    SalesByDateItem(date.toString(), amount)
                }.sortedBy { it.date }

                SalesReportData(
                    totalSales = totalSales,
                    paidAmount = totalPaid,
                    unpaidAmount = totalSales - totalPaid,
                    collectionRate = if (totalSales > 0) (totalPaid / totalSales) * 100 else 0.0,
                    invoiceCount = invoiceCount,
                    salesByCategory = salesByCategoryList,
                    salesByCustomerType = salesByCustomerTypeList,
                    salesByDate = salesByDateList
                )
            } catch (e: Exception) {
                Log.e(TAG, "processInvoicesForSalesReport: Error processing invoices", e)
                throw e
            }
        }
    }

    // Inventory Valuation Report
    fun generateInventoryReport() {
        Log.d(TAG, "generateInventoryReport: Starting inventory report generation")
        if (activeShopId.isNullOrEmpty()) {
            Log.e(TAG, "generateInventoryReport: No active shop selected")
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            val totalStartTime = System.currentTimeMillis() // Add this for overall timing
            try {
                Log.d(TAG, "generateInventoryReport: Fetching inventory items")
                val fetchStartTime = System.currentTimeMillis() // Add this for timing
                // Get all inventory items for this shop
                val result = inventoryRepository.getInventoryItemsForDropdown()
                Log.d(TAG, "generateInventoryReport: Inventory items fetched in ${System.currentTimeMillis() - fetchStartTime}ms")

                result.fold(
                    onSuccess = { items ->
                        Log.d(TAG, "generateInventoryReport: Successfully loaded ${items.size} inventory items")
                        val processStartTime = System.currentTimeMillis() // Add this for timing
                        // Process items and get results
                        val (inventoryItems, totalValue) = processInventoryForReport(items) // Call as suspend function
                        Log.d(TAG, "generateInventoryReport: Inventory items processed in ${System.currentTimeMillis() - processStartTime}ms")

                        withContext(Dispatchers.Main) {
                            _inventoryItems.value = inventoryItems
                            _totalInventoryValue.value = totalValue
                            _errorMessage.value = null // Clear any previous error
                        }
                        Log.d(TAG, "generateInventoryReport: Report generation completed successfully")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "generateInventoryReport: Failed to load inventory", error)
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Failed to load inventory: ${error.message}"
                            _inventoryItems.value = emptyList() // Clear items on error
                            _totalInventoryValue.value = 0.0 // Reset total value on error
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "generateInventoryReport: Error generating inventory report", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error generating inventory report: ${e.message}"
                    _inventoryItems.value = emptyList() // Clear items on error
                    _totalInventoryValue.value = 0.0 // Reset total value on error
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // *** ADDED THIS LOG TO DIAGNOSE PROGRESS BAR ISSUE ***
                    Log.d(TAG, "generateInventoryReport: Setting isLoading to false in finally block.")
                    _isLoading.value = false // Ensure isLoading is set to false in finally
                    Log.d(TAG, "generateInventoryReport: Total execution time: ${System.currentTimeMillis() - totalStartTime}ms")
                }
            }
        }
    }

    private suspend fun processInventoryForReport(items: List<JewelleryItem>): Pair<List<InventoryValueItem>, Double> {
        Log.d(TAG, "processInventoryForReport: Processing ${items.size} inventory items")
        return withContext(Dispatchers.Default) {
            val inventoryItems = items.map { item ->
                // Calculate item value based on its properties
                val metalValue = calculateMetalValue(item)
                val makingValue = calculateMakingValue(item)
                val diamondValue = item.diamondPrice

                val totalItemValue = metalValue + makingValue + diamondValue
                val totalStockValue = totalItemValue * item.stock

                Log.d(TAG, """
                    processInventoryForReport: Item ${item.displayName}
                    - Metal Value: $metalValue
                    - Making Value: $makingValue
                    - Diamond Value: $diamondValue
                    - Total Item Value: $totalItemValue
                    - Total Stock Value: $totalStockValue
                """.trimIndent())

                InventoryValueItem(
                    id = item.id,
                    name = item.displayName,
                    itemType = item.itemType,
                    stock = item.stock,
                    stockUnit = item.stockUnit,
                    metalValue = metalValue,
                    makingValue = makingValue,
                    diamondValue = diamondValue,
                    totalItemValue = totalItemValue,
                    totalStockValue = totalStockValue
                )
            }.sortedBy { it.name }

            val totalValue = inventoryItems.sumOf { it.totalStockValue }
            Log.d(TAG, "processInventoryForReport: Total inventory value: $totalValue")

            Pair(inventoryItems, totalValue) // Return the processed data
        }
    }

    private fun calculateMetalValue(item: JewelleryItem): Double {
        val netWeight = item.netWeight
        val metalRate = item.metalRate
        val wastage = item.wastage / 100 // Convert percentage to decimal
        val metalValue = netWeight * (1 + wastage) * metalRate
        
        Log.d(TAG, """
            calculateMetalValue: Item ${item.displayName}
            - Net Weight: $netWeight
            - Metal Rate: $metalRate
            - Wastage: ${item.wastage}%
            - Metal Value: $metalValue
        """.trimIndent())
        
        return metalValue
    }

    private fun calculateMakingValue(item: JewelleryItem): Double {
        val makingValue = if (item.makingChargesType.equals("Percentage", ignoreCase = true)) {
            // If percentage, calculate based on metal value
            val metalValue = calculateMetalValue(item)
            metalValue * (item.makingCharges / 100)
        } else {
            // If fixed, multiply by weight
            item.netWeight * item.makingCharges
        }
        
        Log.d(TAG, """
            calculateMakingValue: Item ${item.displayName}
            - Making Charges Type: ${item.makingChargesType}
            - Making Charges: ${item.makingCharges}
            - Making Value: $makingValue
        """.trimIndent())
        
        return makingValue
    }

    // GST Report
    fun generateGstReport() {
        synchronized(gstReportLock) {
            if (isGstReportLoading) {
                Log.d(TAG, "generateGstReport: Already generating, skipping")
                return
            }

            Log.d(TAG, "generateGstReport: Starting GST report generation")

            if (activeShopId.isNullOrEmpty()) {
                Log.e(TAG, "generateGstReport: No active shop selected")
                _errorMessage.value = "No active shop selected"
                return
            }

            isGstReportLoading = true
            _isLoading.value = true

            currentReportJob = viewModelScope.launch {
                val totalStartTime = System.currentTimeMillis()
                try {
                    val startTimestamp = startDate.value ?: Date(0) // Use default if null
                    val endTimestamp = endDate.value ?: Date(System.currentTimeMillis()) // Use current time if null
                    Log.d(TAG, "generateGstReport: Date range for query: $startTimestamp to $endTimestamp")

                    val fetchStartTime = System.currentTimeMillis()
                    // --- REPLACED OLD INVOICE FETCHING LOGIC WITH NEW EFFICIENT CALL ---
                    val result = invoiceRepository.getTaxableInvoicesBetweenDates(startTimestamp, endTimestamp)
                    Log.d(TAG, "generateGstReport: Invoice fetch completed in ${System.currentTimeMillis() - fetchStartTime}ms")

                    result.fold(
                        onSuccess = { allInvoices ->
                            if (allInvoices.isEmpty()) {
                                Log.d(TAG, "generateGstReport: No taxable invoices found in date range")
                                withContext(Dispatchers.Main) {
                                    _gstReportItems.value = emptyList()
                                    _isLoading.value = false
                                    _errorMessage.value = "No GST data found for the selected period"
                                }
                                return@launch
                            }

                            Log.d(TAG, "generateGstReport: Processing ${allInvoices.size} invoices for GST report")
                            processInvoicesForGstReport(allInvoices) // Pass the already filtered invoices
                        },
                        onFailure = { error ->
                            Log.e(TAG, "generateGstReport: Failed to fetch taxable invoices", error)
                            withContext(Dispatchers.Main) {
                                _errorMessage.value = "Failed to generate GST report: ${error.message}"
                                _isLoading.value = false
                                _gstReportItems.value = emptyList()
                            }
                        }
                    )

                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "generateGstReport: Report generation cancelled")
                    } else {
                        Log.e(TAG, "generateGstReport: Failed to generate GST report (general catch)", e)
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Failed to generate GST report: ${e.message}"
                            _isLoading.value = false
                            _gstReportItems.value = emptyList()
                        }
                    }
                } finally {
                    isGstReportLoading = false
                    currentReportJob = null
                    // _isLoading.value is set in the success/failure blocks, so we don't need to force it here again.
                    // This ensures isLoading is accurately reflecting if processing is still ongoing via processInvoicesForGstReport
                    Log.d(TAG, "generateGstReport: Total execution time: ${System.currentTimeMillis() - totalStartTime}ms")
                }
            }
        }
    }

    private fun processInvoicesForGstReport(invoices: List<Invoice>) {
        Log.d(TAG, "Processing ${invoices.size} invoices for GST report")
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val taxRateGroups = mutableMapOf<Double, MutableList<Pair<Double, Double>>>()

                invoices.forEach { invoice ->
                    if (!isActive) {
                        Log.d(TAG, "processInvoicesForGstReport: Processing cancelled")
                        return@launch
                    }

                    Log.d(TAG, "processInvoicesForGstReport: Processing invoice ${invoice.invoiceNumber}")
                    invoice.items.forEach { invoiceItem ->
                        val item = invoiceItem.itemDetails
                        val quantity = invoiceItem.quantity
                        val itemPrice = invoiceItem.price // This is either net or gross price
                        val taxRate = item.taxRate // This is the percentage, e.g., 5.0, 12.0

                        // --- CRUCIAL FIX / ADJUSTMENT FOR TAX CALCULATION ---
                        val totalLineAmount = itemPrice * quantity // Total value for this line item

                        val calculatedTaxableAmount: Double
                        val calculatedTaxAmount: Double

                        // Assuming itemPrice is the NET price (price before tax)
                        // If itemPrice is GROSS price (price including tax), the calculation below needs adjustment
                        if (taxRate > 0.0) {
                            calculatedTaxableAmount = totalLineAmount
                            calculatedTaxAmount = totalLineAmount * (taxRate / 100.0)
                        } else {
                            calculatedTaxableAmount = totalLineAmount
                            calculatedTaxAmount = 0.0
                        }

                        Log.d(TAG, """
                            Processing item ${item.displayName}:
                            - Tax Rate: $taxRate%
                            - Quantity: $quantity
                            - Item Price: $itemPrice
                            - Total Line Amount (Price*Qty): $totalLineAmount
                            - Calculated Taxable Amount: $calculatedTaxableAmount
                            - Calculated Tax Amount: $calculatedTaxAmount
                        """.trimIndent())

                        val taxRateList = taxRateGroups.getOrPut(taxRate) { mutableListOf() }
                        taxRateList.add(Pair(calculatedTaxableAmount, calculatedTaxAmount))
                    }
                }

                Log.d(TAG, "Found ${taxRateGroups.size} different tax rates")

                // Convert to report items
                val gstItems = taxRateGroups.map { (taxRate, amounts) ->
                    val totalTaxableAmount = amounts.sumOf { it.first }
                    val totalTaxAmount = amounts.sumOf { it.second }

                    Log.d(TAG, """
                        processInvoicesForGstReport: Tax Rate $taxRate%
                        - Total Taxable Amount: $totalTaxableAmount
                        - Total Tax Amount: $totalTaxAmount
                        - CGST: ${totalTaxAmount / 2}
                        - SGST: ${totalTaxAmount / 2}
                    """.trimIndent())

                    GstReportItem(
                        taxRate = taxRate,
                        taxableAmount = totalTaxableAmount,
                        cgst = totalTaxAmount / 2, // Assuming equal split between CGST and SGST
                        sgst = totalTaxAmount / 2,
                        totalTax = totalTaxAmount
                    )
                }.sortedBy { it.taxRate }

                Log.d(TAG, "processInvoicesForGstReport: Generated ${gstItems.size} GST report items")

                withContext(Dispatchers.Main) {
                    if (gstItems.isEmpty()) {
                        Log.d(TAG, "processInvoicesForGstReport: No GST data found")
                        _errorMessage.value = "No GST data found for the selected period"
                    }
                    _gstReportItems.value = gstItems
                    _isLoading.value = false
                    Log.d(TAG, "processInvoicesForGstReport: GST report generation completed successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing GST data", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error processing GST data: ${e.message}"
                    _isLoading.value = false
                    _gstReportItems.value = emptyList()
                }
            }
        }
    }

    // Low Stock Report
    fun generateLowStockReport() {
        Log.d(TAG, "generateLowStockReport: Starting low stock report generation")
        if (activeShopId.isNullOrEmpty()) {
            Log.e(TAG, "generateLowStockReport: No active shop selected")
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Define low stock thresholds
                val LOW_STOCK_THRESHOLD = 5.0 // For quantity-based inventory
                val LOW_STOCK_WEIGHT_THRESHOLD = 100.0 // For weight-based inventory (grams)
                Log.d(TAG, """
                    generateLowStockReport: Using thresholds
                    - Quantity Threshold: $LOW_STOCK_THRESHOLD
                    - Weight Threshold: $LOW_STOCK_WEIGHT_THRESHOLD g
                """.trimIndent())

                // Get all inventory items for this shop
                Log.d(TAG, "generateLowStockReport: Fetching inventory items")
                val result = inventoryRepository.getInventoryItemsForDropdown()

                result.fold(
                    onSuccess = { items ->
                        Log.d(TAG, "generateLowStockReport: Successfully loaded ${items.size} inventory items")
                        // Filter low stock items based on inventory type
                        val lowStockItems = items
                            .filter { item ->
                                val isLowStock = when (item.inventoryType) {
                                    com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> {
                                        // For weight-based inventory, check totalWeightGrams
                                        item.totalWeightGrams <= LOW_STOCK_WEIGHT_THRESHOLD
                                    }
                                    else -> {
                                        // For quantity-based inventory, check stock
                                        item.stock <= LOW_STOCK_THRESHOLD
                                    }
                                }
                                
                                if (isLowStock) {
                                    Log.d(TAG, """
                                        generateLowStockReport: Low stock item found
                                        - Name: ${item.displayName}
                                        - Type: ${item.inventoryType}
                                        - Current Stock: ${if (item.inventoryType == com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK) item.totalWeightGrams else item.stock}
                                        - Unit: ${if (item.inventoryType == com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK) "g" else item.stockUnit}
                                    """.trimIndent())
                                }
                                
                                isLowStock
                            }
                            .map { item ->
                                // Determine appropriate reorder level based on inventory type
                                val reorderLevel = when (item.inventoryType) {
                                    com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> LOW_STOCK_WEIGHT_THRESHOLD
                                    else -> LOW_STOCK_THRESHOLD
                                }
                                
                                LowStockItem(
                                    id = item.id,
                                    name = item.displayName,
                                    itemType = item.itemType,
                                    currentStock = when (item.inventoryType) {
                                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> item.totalWeightGrams
                                        else -> item.stock
                                    },
                                    stockUnit = when (item.inventoryType) {
                                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> "g"
                                        else -> item.stockUnit
                                    },
                                    reorderLevel = reorderLevel,
                                    lastSoldDate = null // This would require additional query to find
                                )
                            }
                            .sortedBy { it.currentStock }

                        Log.d(TAG, "generateLowStockReport: Found ${lowStockItems.size} low stock items")
                        _lowStockItems.value = lowStockItems
                        _isLoading.value = false
                    },
                    onFailure = { error ->
                        Log.e(TAG, "generateLowStockReport: Failed to load inventory", error)
                        _errorMessage.value = "Failed to load inventory: ${error.message}"
                        _isLoading.value = false
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "generateLowStockReport: Error generating low stock report", e)
                _errorMessage.value = "Error generating low stock report: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // Customer Account Statement
    fun loadCustomerStatement(customer: Customer) {
        Log.d(TAG, "loadCustomerStatement: Starting statement generation for customer: ${customer.firstName} (ID: ${customer.id})")
        if (activeShopId.isNullOrEmpty()) {
            Log.e(TAG, "loadCustomerStatement: No active shop selected")
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Calculate opening balance
                Log.d(TAG, "loadCustomerStatement: Calculating opening balance")
                val openingBalance = calculateOpeningBalance(customer)
                _openingBalance.value = openingBalance
                Log.d(TAG, "loadCustomerStatement: Opening balance: $openingBalance")

                // Get all invoices for this customer directly from the server
                val customerInvoices = withContext(Dispatchers.IO) {
                    Log.d(TAG, "loadCustomerStatement: Fetching customer-specific invoices")
                    val allCustomerInvoicesList = mutableListOf<Invoice>()
                    var hasMoreInvoices = true
                    var loadNextPage = false // Start with loading the first page

                    while (hasMoreInvoices) {
                        Log.d(TAG, "loadCustomerStatement: Fetching customer invoices - page. Customer ID: ${customer.id}")
                        
                        // Use the new dedicated function to fetch invoices for the specific customer
                        val result = invoiceRepository.fetchInvoicesForCustomerPaginated(customer.id, loadNextPage)

                        result.fold(
                            onSuccess = { invoices ->
                                if (invoices.isEmpty()) {
                                    Log.d(TAG, "loadCustomerStatement: No more invoices to fetch for customer ${customer.id}")
                                    hasMoreInvoices = false
                                } else {
                                    Log.d(TAG, "loadCustomerStatement: Fetched ${invoices.size} invoices for customer ${customer.id}")
                                    allCustomerInvoicesList.addAll(invoices)
                                    loadNextPage = true // Set to true to fetch the next page in the subsequent iteration
                                }
                            },
                            onFailure = { error ->
                                Log.e(TAG, "loadCustomerStatement: Error fetching customer invoices", error)
                                hasMoreInvoices = false
                                throw error
                            }
                        )
                    }
                    Log.d(TAG, "loadCustomerStatement: Total ${allCustomerInvoicesList.size} invoices fetched for customer ${customer.id}")
                    allCustomerInvoicesList
                }

                // Sort all transactions by date
                val allTransactions = customerInvoices.sortedBy { it.invoiceDate }
                Log.d(TAG, "loadCustomerStatement: Processing ${allTransactions.size} transactions")

                // Calculate running balance and closing balance
                var runningBalance = openingBalance
                val transactionsWithBalance = allTransactions.map { invoice ->
                    val unpaidAmount = invoice.totalAmount - invoice.paidAmount
                    // Apply change based on customer balance type
                    runningBalance += if (customer.balanceType.equals(
                            "Credit",
                            ignoreCase = true
                        )
                    ) {
                        unpaidAmount  // For Credit customers, unpaid amounts increase the balance
                    } else {
                        -unpaidAmount // For Debit customers, unpaid amounts decrease the balance
                    }

                    Log.d(TAG, """
                        loadCustomerStatement: Processing invoice ${invoice.invoiceNumber}
                        - Total Amount: ${invoice.totalAmount}
                        - Paid Amount: ${invoice.paidAmount}
                        - Unpaid Amount: $unpaidAmount
                        - Running Balance: $runningBalance
                    """.trimIndent())

                    Pair(invoice, runningBalance)
                }

                // Set closing balance
                _closingBalance.value = runningBalance
                Log.d(TAG, "loadCustomerStatement: Closing balance: $runningBalance")

                // Set the transactions with running balance
                _customerTransactions.value = transactionsWithBalance.map { it.first }
                _isLoading.value = false
                Log.d(TAG, "loadCustomerStatement: Statement generation completed successfully")

            } catch (e: Exception) {
                Log.e(TAG, "loadCustomerStatement: Failed to load customer statement", e)
                _errorMessage.value = "Failed to load customer statement: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private suspend fun calculateOpeningBalance(customer: Customer): Double {
        Log.d(TAG, "calculateOpeningBalance: Calculating for customer ${customer.firstName} (ID: ${customer.id})")
        // For simplicity, we'll use the opening balance from the customer record
        // In a real implementation, you would calculate this based on transactions before startDate
        val balance = customer.openingBalance
        Log.d(TAG, "calculateOpeningBalance: Opening balance: $balance")
        return balance
    }

    fun clearErrorMessage() {
        Log.d(TAG, "clearErrorMessage: Clearing error message")
        _errorMessage.value = null
    }
}