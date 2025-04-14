package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val invoiceRepository: InvoiceRepository
    private val customerRepository: CustomerRepository
    private val inventoryRepository: InventoryRepository

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

    init {
        invoiceRepository = InvoiceRepository(firestore, auth)
        customerRepository = CustomerRepository(firestore, auth)
        inventoryRepository = InventoryRepository(firestore, auth)

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

        // Load customers for selection
        loadCustomers()

        loadSalesReport()
    }

    fun setDateRange(start: Date, end: Date) {
        _startDate.value = start
        _endDate.value = end
        loadSalesReport()
        // Refresh data for currently selected report if any
    }

    fun selectCustomer(customer: Customer) {
        _selectedCustomer.value = customer
        loadCustomerStatement(customer)
    }

    fun clearSelectedCustomer() {
        _selectedCustomer.value = null
        _customerTransactions.value = emptyList()
    }


    fun loadSalesReport() {
        val start = _startDate.value ?: return // Need dates to load
        val end = _endDate.value ?: return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // FIXME: Replace 'getInvoicesBetweenDates' with the actual method from your InvoiceRepository
                val invoices = invoiceRepository.getInvoicesBetweenDates(start, end)

                if (invoices.isNullOrEmpty()) {
                    _salesReportData.postValue(null)
                    _topSellingItems.postValue(emptyList())
                    _topCustomers.postValue(emptyList())
                    // _errorMessage.postValue("No sales data found for the selected period.") // Optional message
                } else {
                    // --- Calculate Sales Report Data ---
                    val totalSales = invoices.sumOf { it.totalAmount }
                    val totalPaid = invoices.sumOf { it.paidAmount }
                    val unpaidAmount = totalSales - totalPaid
                    val collectionRate = if (totalSales > 0) (totalPaid / totalSales) * 100 else 0.0
                    val invoiceCount = invoices.size

                    // Calculate Sales by Category
                    val salesByCategory = invoices.flatMap { it.items }
                        .groupBy { it.itemDetails?.category ?: "Uncategorized" } // Assumes JewelleryItem has category
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
                        .groupBy { it.itemDetails?.displayName ?: "Unknown Item" } // Group by display name
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
                            val name = customerInvoices.firstOrNull()?.customerName?.trim() ?: customerId

                            CustomerSalesData(name.ifBlank { customerId }, totalValue, count) // Use ID if name is blank
                        }
                        .sortedByDescending { it.totalPurchaseValue } // Sort by purchase value
                        .take(5) // Take top 5
                    _topCustomers.postValue(customerSales)

                    _errorMessage.postValue(null) // Clear previous error
                }

            } catch (e: Exception) {
                _errorMessage.postValue("Error loading sales report: ${e.localizedMessage}")
                _salesReportData.postValue(null)
                _topSellingItems.postValue(null)
                _topCustomers.postValue(null)
                Log.e("ReportViewModel", "Error loading sales report", e)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
    // Load customers for customer statement selection
    fun loadCustomers() {
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
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Load all invoices in date range
                val startTimestamp = startDate.value?.time ?: 0
                val endTimestamp = endDate.value?.time ?: System.currentTimeMillis()

                val allInvoices = withContext(Dispatchers.IO) {
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
            val customersResult = customerRepository.fetchCustomersPaginated(loadNextPage = false)
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
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Get all inventory items
                val result = inventoryRepository.getAllInventoryItems()

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
                        code = item.jewelryCode,
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
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Load all invoices in date range
                val startTimestamp = startDate.value?.time ?: 0
                val endTimestamp = endDate.value?.time ?: System.currentTimeMillis()

                val allInvoices = withContext(Dispatchers.IO) {
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

                // Process invoices to extract GST information
                processInvoicesForGstReport(allInvoices)

            } catch (e: Exception) {
                _errorMessage.value = "Failed to generate GST report: ${e.message}"
                _isLoading.value = false
                Log.e("ReportViewModel", "Error generating GST report", e)
            }
        }
    }

    private fun processInvoicesForGstReport(invoices: List<Invoice>) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Group invoice items by tax rate
                val taxRateGroups =
                    mutableMapOf<Double, MutableList<Pair<Double, Double>>>() // taxRate -> List of (taxableAmount, taxAmount)

                invoices.forEach { invoice ->
                    invoice.items.forEach { invoiceItem ->
                        val item = invoiceItem.itemDetails
                        val quantity = invoiceItem.quantity
                        val itemPrice = invoiceItem.price

                        // Calculate taxable amount (price before tax)
                        val taxRate = item.taxRate
                        val taxableAmount = (itemPrice * quantity) / (1 + taxRate / 100)
                        val taxAmount = itemPrice * quantity - taxableAmount

                        val taxRateList = taxRateGroups.getOrPut(taxRate) { mutableListOf() }
                        taxRateList.add(Pair(taxableAmount, taxAmount))
                    }
                }

                // Convert to report items
                val gstItems = taxRateGroups.map { (taxRate, amounts) ->
                    val totalTaxableAmount = amounts.sumOf { it.first }
                    val totalTaxAmount = amounts.sumOf { it.second }

                    GstReportItem(
                        taxRate = taxRate,
                        taxableAmount = totalTaxableAmount,
                        cgst = totalTaxAmount / 2, // Assuming equal split between CGST and SGST
                        sgst = totalTaxAmount / 2,
                        totalTax = totalTaxAmount
                    )
                }.sortedBy { it.taxRate }

                withContext(Dispatchers.Main) {
                    _gstReportItems.value = gstItems
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Error processing GST data: ${e.message}"
                    _isLoading.value = false
                }
                Log.e("ReportViewModel", "Error processing GST data", e)
            }
        }
    }

    // Low Stock Report
    fun generateLowStockReport() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Define low stock threshold
                val LOW_STOCK_THRESHOLD = 5.0

                // Get all inventory items
                val result = inventoryRepository.getAllInventoryItems()

                result.fold(
                    onSuccess = { items ->
                        // Filter low stock items
                        val lowStockItems = items
                            .filter { it.stock <= LOW_STOCK_THRESHOLD }
                            .map { item ->
                                LowStockItem(
                                    id = item.id,
                                    name = item.displayName,
                                    code = item.jewelryCode,
                                    itemType = item.itemType,
                                    currentStock = item.stock,
                                    stockUnit = item.stockUnit,
                                    reorderLevel = LOW_STOCK_THRESHOLD,
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
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val startTimestamp = startDate.value?.time ?: 0
                val endTimestamp = endDate.value?.time ?: System.currentTimeMillis()

                // Calculate opening balance
                val openingBalance = calculateOpeningBalance(customer, startTimestamp)
                _openingBalance.value = openingBalance

                // Get all invoices for this customer
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