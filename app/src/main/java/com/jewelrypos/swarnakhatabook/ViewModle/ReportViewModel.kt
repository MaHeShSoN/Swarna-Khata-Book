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
        loadCustomers()
        
        // Initialize reports
        viewModelScope.launch {
            loadSalesReport()
            generateGstReport()
        }
        
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
                Log.d(TAG, "Sales report loading already in progress, skipping")
                return
            }

            val start = _startDate.value ?: return
            val end = _endDate.value ?: return
            Log.d(TAG, "Loading sales report for period: $start to $end")

            if (activeShopId.isNullOrEmpty()) {
                Log.e(TAG, "No active shop selected")
                _errorMessage.postValue("No active shop selected")
                return
            }

            isSalesReportLoading = true
            _isLoading.value = true
            
            viewModelScope.launch {
                try {
                    Log.d(TAG, "Fetching invoices between dates")
                    val invoices = invoiceRepository.getInvoicesBetweenDates(start, end)
                    Log.d(TAG, "Retrieved ${invoices?.size ?: 0} invoices")

                    if (invoices.isNullOrEmpty()) {
                        Log.d(TAG, "No sales data found for the selected period")
                        _salesReportData.postValue(null)
                        _topSellingItems.postValue(emptyList())
                        _topCustomers.postValue(emptyList())
                    } else {
                        Log.d(TAG, "Processing sales data")
                        // --- Calculate Sales Report Data ---
                        val totalSales = invoices.sumOf { it.totalAmount }
                        val totalPaid = invoices.sumOf { it.paidAmount }
                        val unpaidAmount = totalSales - totalPaid
                        val collectionRate = if (totalSales > 0) (totalPaid / totalSales) * 100 else 0.0
                        val invoiceCount = invoices.size

                        // Calculate Sales by Category
                        val salesByCategory = invoices.flatMap { it.items }
                            .groupBy {
                                it.itemDetails?.category ?: "Uncategorized"
                            } // Assumes JewelleryItem has category
                            .mapValues { entry -> entry.value.sumOf { item -> item.price * item.quantity } }
                            .map { SalesByCategoryItem(it.key, it.value) }

                        // --- Calculate Sales by Customer Type ---
                        // FIXME: This calculation cannot be done directly as Invoice object lacks customerType.
                        // Requires fetching Customer objects based on customerId or removing this metric.
                        // Commenting out for now:
                        /*
                        val salesByCustomerType = invoices
                            // Needs logic to fetch customer type based on it.customerId
                            .groupBy { fetchedCustomerTypeMap[it.customerId] ?: "Unknown" }
                            .mapValues { entry -> entry.value.sumOf { inv -> inv.totalAmount } }
                            .map { SalesByCustomerTypeItem(it.key, it.value) }
                        */
                        // Assigning empty list temporarily
                        val salesByCustomerType = emptyList<SalesByCustomerTypeItem>()


                        // Calculate Sales by Date
                        // Assuming Invoice has 'invoiceDate: Long'
                        val salesByDate = invoices
                            .groupBy { invoice ->
                                // Format Long timestamp to a date string (e.g., "dd MMM yyyy")
                                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                dateFormat.format(Date(invoice.invoiceDate))
                            }
                            .mapValues { entry -> entry.value.sumOf { inv -> inv.totalAmount } }
                            .map { SalesByDateItem(it.key, it.value) }
                            .sortedBy { it.date } // Sort by date string (basic sort)


                        val reportData = SalesReportData(
                            totalSales = totalSales,
                            paidAmount = totalPaid,
                            unpaidAmount = unpaidAmount,
                            collectionRate = collectionRate,
                            invoiceCount = invoiceCount,
                            salesByCategory = salesByCategory,
                            salesByCustomerType = salesByCustomerType, // Using empty list for now
                            salesByDate = salesByDate
                        )
                        _salesReportData.postValue(reportData)


                        // --- Calculate Top Selling Items ---
                        // Assuming InvoiceItem has 'itemDetails: JewelleryItem' and JewelleryItem has 'displayName: String'
                        val itemSales = invoices.flatMap { it.items }
                            .groupBy {
                                it.itemDetails?.displayName ?: "Unknown Item"
                            } // Group by display name
                            .map { entry ->
                                val name = entry.key
                                val quantity = entry.value.sumOf { item -> item.quantity }
                                val revenue = entry.value.sumOf { item -> item.price * item.quantity }
                                ItemSalesData(name, quantity, revenue)
                            }
                            .sortedByDescending { it.totalRevenue }
                            .take(5)
                        _topSellingItems.postValue(itemSales)


                        // --- Calculate Top Customers (Corrected) ---
                        // Group by customerId first
                        val customerSales = invoices
                            .filter { it.customerId.isNotBlank() } // Ensure customerId exists
                            .groupBy { it.customerId } // Group by customer ID string
                            .map { entry ->
                                val customerId = entry.key
                                val customerInvoices = entry.value // List of invoices for this customer
                                val totalValue = customerInvoices.sumOf { inv -> inv.totalAmount }
                                val count = customerInvoices.size
                                // Get customer name from the first invoice (assuming it's consistent)
                                val name =
                                    customerInvoices.firstOrNull()?.customerName?.trim() ?: customerId

                                CustomerSalesData(
                                    name.ifBlank { customerId },
                                    totalValue,
                                    count
                                ) // Use ID if name is blank
                            }
                            .sortedByDescending { it.totalPurchaseValue } // Sort by purchase value
                            .take(5) // Take top 5
                        _topCustomers.postValue(customerSales)

                        _errorMessage.postValue(null) // Clear previous error
                    }

                    Log.d(TAG, "Sales report data processed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading sales report", e)
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
        if (activeShopId.isNullOrEmpty()) {
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                customerRepository.fetchCustomersPaginated(
                    loadNextPage = false,
                    source = Source.DEFAULT
                ).fold(
                    onSuccess = { customers ->
                        _customers.value = customers
                        _isLoading.value = false
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to load customers: ${error.message}"
                        _isLoading.value = false
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error loading customers: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // Sales Report Generation
    fun generateSalesReport() {
        if (activeShopId.isNullOrEmpty()) {
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Load all invoices in date range
                val startTimestamp = startDate.value?.time ?: 0
                val endTimestamp = endDate.value?.time ?: System.currentTimeMillis()

                val allInvoices = withContext(Dispatchers.IO) {
                    // Get all invoices for this shop
                    val allInvoicesList = mutableListOf<Invoice>()
                    var hasMoreInvoices = true
                    var loadNextPage = false

                    while (hasMoreInvoices) {
                        val result = invoiceRepository.fetchInvoicesPaginated(loadNextPage)

                        result.fold(
                            onSuccess = { invoices ->
                                if (invoices.isEmpty()) {
                                    hasMoreInvoices = false
                                } else {
                                    allInvoicesList.addAll(invoices)
                                    loadNextPage = true
                                }
                            },
                            onFailure = {
                                hasMoreInvoices = false
                                throw it
                            }
                        )
                    }

                    // Filter by date range
                    allInvoicesList.filter { invoice ->
                        invoice.invoiceDate in startTimestamp..endTimestamp
                    }
                }

                // Process invoices to generate report data
                val salesData = processInvoicesForSalesReport(allInvoices)
                _salesReportData.value = salesData
                _isLoading.value = false

            } catch (e: Exception) {
                _errorMessage.value = "Failed to generate sales report: ${e.message}"
                _isLoading.value = false
                Log.e("ReportViewModel", "Error generating sales report", e)
            }
        }
    }

    private suspend fun processInvoicesForSalesReport(invoices: List<Invoice>): SalesReportData {
        return withContext(Dispatchers.Default) {
            // Calculate total sales and invoice count
            val totalSales = invoices.sumOf { it.totalAmount }
            val totalPaid = invoices.sumOf { it.paidAmount }
            val invoiceCount = invoices.size

            // Group sales by item type (Gold, Silver, Other)
            val salesByCategory = mutableMapOf<String, Double>()

            // Group sales by customer type (Consumer vs Wholesaler)
            val salesByCustomerType = mutableMapOf<String, Double>()

            // Sales by date for trend analysis
            val salesByDate = mutableMapOf<Long, Double>()

            // Load customers to get customer types
            val customersResult = customerRepository.fetchCustomersPaginated(
                loadNextPage = false
            )
            val customers = customersResult.getOrNull() ?: emptyList()
            val customerMap = customers.associateBy { it.id }

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
        }
    }

    // Inventory Valuation Report
    fun generateInventoryReport() {
        if (activeShopId.isNullOrEmpty()) {
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Get all inventory items for this shop
                val result = inventoryRepository.getInventoryItemsForDropdown()

                result.fold(
                    onSuccess = { items ->
                        processInventoryForReport(items)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to load inventory: ${error.message}"
                        _isLoading.value = false
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error generating inventory report: ${e.message}"
                _isLoading.value = false
                Log.e("ReportViewModel", "Error generating inventory report", e)
            }
        }
    }

    private fun processInventoryForReport(items: List<JewelleryItem>) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val inventoryItems = items.map { item ->
                    // Calculate item value based on its properties
                    val metalValue = calculateMetalValue(item)
                    val makingValue = calculateMakingValue(item)
                    val diamondValue = item.diamondPrice

                    val totalItemValue = metalValue + makingValue + diamondValue
                    val totalStockValue = totalItemValue * item.stock

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

                withContext(Dispatchers.Main) {
                    _inventoryItems.value = inventoryItems
                    _totalInventoryValue.value = totalValue
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error processing inventory data: ${e.message}"
                    _isLoading.value = false
                }
                Log.e("ReportViewModel", "Error processing inventory data", e)
            }
        }
    }

    private fun calculateMetalValue(item: JewelleryItem): Double {
        // Calculate the value of the metal in the item
        // Base metal value = net weight * metal rate
        // If wastage is specified, add it: netWeight * (1 + wastage/100) * metalRate
        val netWeight = item.netWeight
        val metalRate = item.metalRate
        val wastage = item.wastage / 100 // Convert percentage to decimal

        return netWeight * (1 + wastage) * metalRate
    }

    private fun calculateMakingValue(item: JewelleryItem): Double {
        // Calculate making charges based on type (percentage or fixed)
        return if (item.makingChargesType.equals("Percentage", ignoreCase = true)) {
            // If percentage, calculate based on metal value
            val metalValue = calculateMetalValue(item)
            metalValue * (item.makingCharges / 100)
        } else {
            // If fixed, multiply by weight
            item.netWeight * item.makingCharges
        }
    }

    // GST Report
    fun generateGstReport() {
        synchronized(gstReportLock) {
            if (isGstReportLoading) {
                Log.d(TAG, "GST report generation already in progress, skipping")
                return
            }

            Log.d(TAG, "Starting GST report generation")
            
            if (activeShopId.isNullOrEmpty()) {
                Log.e(TAG, "No active shop selected for GST report")
                _errorMessage.value = "No active shop selected"
                return
            }

            isGstReportLoading = true
            _isLoading.value = true
            
            currentReportJob = viewModelScope.launch {
                try {
                    val startTimestamp = startDate.value?.time ?: 0
                    val endTimestamp = endDate.value?.time ?: System.currentTimeMillis()
                    Log.d(TAG, "GST Report date range: ${Date(startTimestamp)} to ${Date(endTimestamp)}")

                    val allInvoices = withContext(Dispatchers.IO) {
                        Log.d(TAG, "Fetching invoices from Firestore")
                        // Get all invoices for this shop
                        val allInvoicesList = mutableListOf<Invoice>()
                        var hasMoreInvoices = true
                        var loadNextPage = false
                        var pageCount = 0
                        var lastFetchTime = System.currentTimeMillis()

                        while (hasMoreInvoices) {
                            // Check if job was cancelled
                            if (!isActive) {
                                Log.d(TAG, "generateGstReport: Report generation cancelled")
                                return@withContext emptyList()
                            }

                            pageCount++
                            Log.d(TAG, "generateGstReport: Fetching page $pageCount")
                            
                            // Add timeout check
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastFetchTime > 10000) { // 10 second timeout
                                Log.e(TAG, "generateGstReport: Fetch timeout on page $pageCount")
                                throw Exception("Invoice fetch timeout")
                            }
                            
                            val result = invoiceRepository.fetchInvoicesPaginated(loadNextPage)
                            lastFetchTime = System.currentTimeMillis()

                            result.fold(
                                onSuccess = { invoices ->
                                    if (invoices.isEmpty()) {
                                        Log.d(TAG, "generateGstReport: No more invoices to fetch")
                                        hasMoreInvoices = false
                                    } else {
                                        Log.d(TAG, "generateGstReport: Fetched ${invoices.size} invoices in page $pageCount")
                                        allInvoicesList.addAll(invoices)
                                        loadNextPage = true
                                    }
                                },
                                onFailure = { error ->
                                    Log.e(TAG, "generateGstReport: Error fetching invoices", error)
                                    hasMoreInvoices = false
                                    throw error
                                }
                            )
                        }

                        Log.d(TAG, "generateGstReport: Total invoices fetched: ${allInvoicesList.size}")

                        // Filter by date range
                        val filteredInvoices = allInvoicesList.filter { invoice ->
                            invoice.invoiceDate in startTimestamp..endTimestamp
                        }
                        
                        Log.d(TAG, "generateGstReport: Invoices in date range: ${filteredInvoices.size}")
                        filteredInvoices
                    }

                    if (allInvoices.isEmpty()) {
                        Log.d(TAG, "generateGstReport: No invoices found in date range")
                        withContext(Dispatchers.Main) {
                            _gstReportItems.value = emptyList()
                            _isLoading.value = false
                            _errorMessage.value = "No invoices found for the selected period"
                        }
                        return@launch
                    }

                    Log.d(TAG, "Processing ${allInvoices.size} invoices for GST report")
                    processInvoicesForGstReport(allInvoices)

                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "generateGstReport: Report generation cancelled")
                    } else {
                        Log.e(TAG, "Failed to generate GST report", e)
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Failed to generate GST report: ${e.message}"
                            _isLoading.value = false
                            _gstReportItems.value = emptyList()
                        }
                    }
                } finally {
                    isGstReportLoading = false
                    currentReportJob = null
                    Log.d(TAG, "GST report generation completed")
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
                        val itemPrice = invoiceItem.price
                        val taxRate = item.taxRate
                        val totalAmount = itemPrice * quantity
                        val taxableAmount = totalAmount
                        val taxAmount = totalAmount - taxableAmount

                        Log.d(TAG, """
                            Processing item ${item.displayName}:
                            - Tax Rate: $taxRate%
                            - Quantity: $quantity
                            - Total Amount: $totalAmount
                            - Taxable Amount: $taxableAmount
                            - Tax Amount: $taxAmount
                        """.trimIndent())

                        val taxRateList = taxRateGroups.getOrPut(taxRate) { mutableListOf() }
                        taxRateList.add(Pair(taxableAmount, taxAmount))
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
        if (activeShopId.isNullOrEmpty()) {
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Define low stock thresholds
                val LOW_STOCK_THRESHOLD = 5.0 // For quantity-based inventory
                val LOW_STOCK_WEIGHT_THRESHOLD = 100.0 // For weight-based inventory (grams)

                // Get all inventory items for this shop
                val result = inventoryRepository.getInventoryItemsForDropdown()

                result.fold(
                    onSuccess = { items ->
                        // Filter low stock items based on inventory type
                        val lowStockItems = items
                            .filter { item ->
                                when (item.inventoryType) {
                                    com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> {
                                        // For weight-based inventory, check totalWeightGrams
                                        item.totalWeightGrams <= LOW_STOCK_WEIGHT_THRESHOLD
                                    }
                                    else -> {
                                        // For quantity-based inventory, check stock
                                        item.stock <= LOW_STOCK_THRESHOLD
                                    }
                                }
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

                        _lowStockItems.value = lowStockItems
                        _isLoading.value = false
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to load inventory: ${error.message}"
                        _isLoading.value = false
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Error generating low stock report: ${e.message}"
                _isLoading.value = false
                Log.e("ReportViewModel", "Error generating low stock report", e)
            }
        }
    }

    // Customer Account Statement
    fun loadCustomerStatement(customer: Customer) {
        if (activeShopId.isNullOrEmpty()) {
            _errorMessage.value = "No active shop selected"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val startTimestamp = startDate.value?.time ?: 0
                val endTimestamp = endDate.value?.time ?: System.currentTimeMillis()

                // Calculate opening balance
                val openingBalance = calculateOpeningBalance(customer, startTimestamp)
                _openingBalance.value = openingBalance

                // Get all invoices for this customer in this shop
                val customerInvoices = withContext(Dispatchers.IO) {
                    // Get all invoices
                    val allInvoicesList = mutableListOf<Invoice>()
                    var hasMoreInvoices = true
                    var loadNextPage = false

                    while (hasMoreInvoices) {
                        val result = invoiceRepository.fetchInvoicesPaginated(loadNextPage)

                        result.fold(
                            onSuccess = { invoices ->
                                if (invoices.isEmpty()) {
                                    hasMoreInvoices = false
                                } else {
                                    // Add only invoices for this customer
                                    allInvoicesList.addAll(invoices.filter { it.customerId == customer.id })
                                    loadNextPage = true
                                }
                            },
                            onFailure = {
                                hasMoreInvoices = false
                                throw it
                            }
                        )
                    }

                    // Filter by date range
                    allInvoicesList.filter { invoice ->
                        invoice.invoiceDate in startTimestamp..endTimestamp
                    }
                }

                // Sort all transactions by date
                val allTransactions = customerInvoices.sortedBy { it.invoiceDate }

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

                    Pair(invoice, runningBalance)
                }

                // Set closing balance
                _closingBalance.value = runningBalance

                // Set the transactions with running balance
                _customerTransactions.value = transactionsWithBalance.map { it.first }
                _isLoading.value = false

            } catch (e: Exception) {
                _errorMessage.value = "Failed to load customer statement: ${e.message}"
                _isLoading.value = false
                Log.e("ReportViewModel", "Error loading customer statement", e)
            }
        }
    }

    private suspend fun calculateOpeningBalance(customer: Customer, startDate: Long): Double {
        // For simplicity, we'll use the opening balance from the customer record
        // In a real implementation, you would calculate this based on transactions before startDate
        return customer.openingBalance
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}