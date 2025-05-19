package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.NotificationPreferences
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Worker class that generates notifications based on business logic
 * Scheduled to run periodically to check for notification conditions
 */
class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val TAG = "NotificationWorker"
    private val SHOP_ID_KEY = "shop_id"

    private val repository by lazy {
        NotificationRepository(firestore, auth, applicationContext)
    }

    // Modified doWork() method to handle the new database structure
    override suspend fun doWork(): Result {
        try {
            // Get current user information
            val currentUser = auth.currentUser ?: return Result.failure()
            val userId = currentUser.uid ?: return Result.failure()
            
            // Try to get shop IDs from input data
            val shopIdsFromInput = inputData.getStringArray(SHOP_ID_KEY)
            
            // Get shop IDs - either from input data or by querying Firestore
            val shopIds = if (!shopIdsFromInput.isNullOrEmpty()) {
                Log.d(TAG, "Using shop IDs from input data: ${shopIdsFromInput.toList()}")
                shopIdsFromInput.toList()
            } else {
                Log.d(TAG, "No shop IDs in input data, querying Firestore")
                getShopIdsForUser(userId)
            }
            
            if (shopIds.isEmpty()) {
                Log.w(TAG, "No shops found for user $userId")
                return Result.failure()
            }
            
            Log.d(TAG, "Found ${shopIds.size} shops for user $userId: $shopIds")
            
            // Get notification preferences once at the beginning
            val notificationPreferences =
                repository.getNotificationPreferences().getOrNull() ?: NotificationPreferences()
            var anySuccessfulCheck = false
            var anyFailedCheck = false
            
            // Process each shop
            for (shopId in shopIds) {
                Log.d(TAG, "Processing notifications for shop: $shopId")
                
                // Check for payment due and overdue notifications
                try {
                    Log.d(TAG, "Checking payment due and overdue for shop $shopId")
                    val paymentNotificationsSuccess =
                        checkPaymentDueAndOverdue(shopId, notificationPreferences)
                    if (paymentNotificationsSuccess) anySuccessfulCheck = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking payment due/overdue for shop $shopId", e)
                    anyFailedCheck = true
                    // Continue with other checks
                }

                // Existing check for monthly business overview
                if (isFirstDayOfMonth() && notificationPreferences.businessInsights) {
                    try {
                        Log.d(TAG, "Checking monthly business overview for shop $shopId")
                        sendMonthlyBusinessOverviewNotification(shopId)
                        anySuccessfulCheck = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending business overview notification for shop $shopId", e)
                        anyFailedCheck = true
                        // Continue with other checks
                    }
                }

                // Existing check for low stock items
                if (notificationPreferences.lowStock) {
                    try {
                        Log.d(TAG, "Checking low stock items for shop $shopId")
                        val lowStockSuccess = sendLowStockAlerts(shopId, notificationPreferences)
                        if (lowStockSuccess) anySuccessfulCheck = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending low stock alerts for shop $shopId", e)
                        anyFailedCheck = true
                        // Continue with other checks
                    }
                }

                // Existing check for customer special dates (birthdays, anniversaries)
                val shouldCheckBirthdays = notificationPreferences.customerBirthday
                val shouldCheckAnniversaries = notificationPreferences.customerAnniversary

                if (shouldCheckBirthdays || shouldCheckAnniversaries) {
                    try {
                        Log.d(TAG, "Checking customer special dates for shop $shopId")
                        val specialDatesSuccess = checkCustomerSpecialDates(
                            shopId,
                            shouldCheckBirthdays,
                            shouldCheckAnniversaries
                        )
                        if (specialDatesSuccess) anySuccessfulCheck = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking customer special dates for shop $shopId", e)
                        anyFailedCheck = true
                        // Continue with other checks
                    }
                }
            }

            // Return appropriate result based on checks
            return when {
                anySuccessfulCheck && !anyFailedCheck -> Result.success()
                anySuccessfulCheck -> Result.success() // Consider partial success as success
                else -> Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in notification worker", e)
            return Result.failure()
        }
    }
    
    /**
     * Get all shop IDs associated with the current user
     */
    private suspend fun getShopIdsForUser(userId: String): List<String> {
        try {
            // Try to get shop IDs from userShops collection
            val userShopsSnapshot = firestore.collection("userShops")
                .document(userId)
                .collection("shops")
                .get()
                .await()
                
            val shopIds = userShopsSnapshot.documents.mapNotNull { it.id }
            
            if (shopIds.isNotEmpty()) {
                return shopIds
            }
            
            // If no shops found in userShops collection, try to find shops where user is an owner
            val shopsSnapshot = firestore.collection("shopData")
                .whereEqualTo("ownerId", userId)
                .get()
                .await()
                
            return shopsSnapshot.documents.mapNotNull { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting shop IDs for user", e)
            return emptyList()
        }
    }

    /**
     * Check for payment due and overdue notifications
     * @return true if any notifications were created
     */
    private suspend fun checkPaymentDueAndOverdue(
        shopId: String,
        preferences: NotificationPreferences
    ): Boolean {
        // Skip if both notification types are disabled
        if (!preferences.paymentDue && !preferences.paymentOverdue) {
            Log.d(TAG, "Payment due and overdue notifications are disabled")
            return false
        }

        try {
            // Query unpaid invoices with due dates using new path structure
            val invoicesSnapshot = firestore.collection("shopData")
                .document(shopId)
                .collection("invoices")
                .whereNotEqualTo("dueDate", null)
                .get()
                .await()

            val unpaidInvoices = invoicesSnapshot.toObjects(Invoice::class.java)
                .filter { it.paidAmount < it.totalAmount } // Only unpaid or partially paid invoices

            if (unpaidInvoices.isEmpty()) {
                Log.d(TAG, "No unpaid invoices with due dates found for shop $shopId")
                return false
            }

            Log.d(TAG, "Found ${unpaidInvoices.size} unpaid invoices with due dates for shop $shopId")
            var notificationsCreated = false

            // Get current date at the start of day (midnight)
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val today = calendar.timeInMillis

            // Check for payment due notifications
            if (preferences.paymentDue) {
                notificationsCreated = checkPaymentDueNotifications(
                    shopId, unpaidInvoices, today, preferences.paymentDueReminderDays
                ) || notificationsCreated
            }

            // Check for payment overdue notifications
            if (preferences.paymentOverdue) {
                notificationsCreated = checkPaymentOverdueNotifications(
                    shopId, unpaidInvoices, today, preferences.paymentOverdueAlertDays
                ) || notificationsCreated
            }

            return notificationsCreated
        } catch (e: Exception) {
            Log.e(TAG, "Error checking payment due/overdue for shop $shopId", e)
            return false
        }
    }

    /**
     * Check and create payment overdue notifications
     * @return true if any notifications were created
     */
    private suspend fun checkPaymentOverdueNotifications(
        shopId: String,
        unpaidInvoices: List<Invoice>,
        today: Long,
        alertDays: Int
    ): Boolean {
        var notificationsCreated = false

        for (invoice in unpaidInvoices) {
            val dueDate = invoice.dueDate ?: continue

            // Only consider if today is past the due date
            if (today <= dueDate) continue

            // Calculate target overdue alert date (dueDate + alertDays)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dueDate
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, alertDays)
            val overdueAlertDate = calendar.timeInMillis

            // Check if today is the overdue alert date
            if (today == overdueAlertDate) {
                // Check if notification already exists
                val notificationExists = doesNotificationExist(
                    shopId,
                    NotificationType.PAYMENT_OVERDUE,
                    invoice.id // Use invoice ID as entity ID for checking
                )

                if (!notificationExists) {
                    // Format due date for display
                    val formattedDueDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        .format(Date(dueDate))

                    // Create notification
                    val notification = AppNotification(
                        customerId = invoice.customerId,
                        customerName = invoice.customerName,
                        title = "Payment Overdue",
                        message = "Payment for Invoice ${invoice.invoiceNumber} to ${invoice.customerName} was due on $formattedDueDate.",
                        type = NotificationType.PAYMENT_OVERDUE,
                        status = NotificationStatus.UNREAD,
                        priority = NotificationPriority.HIGH,
                        amount = invoice.totalAmount - invoice.paidAmount,
                        relatedInvoiceId = invoice.id
                    )

                    repository.createNotification(notification, shopId)
                    Log.d(
                        TAG,
                        "Created payment overdue notification for invoice ${invoice.invoiceNumber} in shop $shopId"
                    )
                    notificationsCreated = true
                }
            }
        }

        return notificationsCreated
    }

    /**
     * Check and create payment due notifications
     * @return true if any notifications were created
     */
    private suspend fun checkPaymentDueNotifications(
        shopId: String,
        unpaidInvoices: List<Invoice>,
        today: Long,
        reminderDays: Int
    ): Boolean {
        var notificationsCreated = false

        for (invoice in unpaidInvoices) {
            val dueDate = invoice.dueDate ?: continue

            // Calculate target reminder date (dueDate - reminderDays)
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dueDate
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.add(Calendar.DAY_OF_YEAR, -reminderDays)
            val reminderDate = calendar.timeInMillis

            // Check if today is the reminder date
            if (today == reminderDate) {
                // Check if notification already exists
                val notificationExists = doesNotificationExist(
                    shopId,
                    NotificationType.PAYMENT_DUE,
                    invoice.id // Use invoice ID as entity ID for checking
                )

                if (!notificationExists) {
                    // Format due date for display
                    val formattedDueDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        .format(Date(dueDate))

                    // Create notification
                    val notification = AppNotification(
                        customerId = invoice.customerId,
                        customerName = invoice.customerName,
                        title = "Payment Due Soon",
                        message = "Payment for Invoice ${invoice.invoiceNumber} to ${invoice.customerName} is due on $formattedDueDate.",
                        type = NotificationType.PAYMENT_DUE,
                        status = NotificationStatus.UNREAD,
                        priority = NotificationPriority.NORMAL,
                        amount = invoice.totalAmount - invoice.paidAmount,
                        relatedInvoiceId = invoice.id
                    )

                    repository.createNotification(notification, shopId)
                    Log.d(
                        TAG,
                        "Created payment due notification for invoice ${invoice.invoiceNumber} in shop $shopId"
                    )
                    notificationsCreated = true
                }
            }
        }

        return notificationsCreated
    }


    /**
     * Check if today is the first day of the month
     */
    private fun isFirstDayOfMonth(): Boolean {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.DAY_OF_MONTH) == 1
    }

    /**
     * Check for customer birthdays and anniversaries
     * @return true if any notifications were created
     */
    private suspend fun checkCustomerSpecialDates(
        shopId: String,
        checkBirthdays: Boolean,
        checkAnniversaries: Boolean
    ): Boolean {
        // Get today's date in MM-dd format (without year)
        val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())

        // Fetch customers using new path structure
        val customersSnapshot = firestore.collection("shopData")
            .document(shopId)
            .collection("customers")
            .get()
            .await()

        val customers = customersSnapshot.toObjects(Customer::class.java)
        var notificationsCreated = false

        // Check birthdays
        if (checkBirthdays) {
            for (customer in customers) {
                // Convert stored birthday to MM-dd format
                val birthdayDate = try {
                    // Ensure customer.birthday is not null or empty before parsing
                    if (customer.birthday.isNullOrEmpty()) {
                        null
                    } else {
                        val date = SimpleDateFormat(
                            "dd/MM/yyyy",
                            Locale.getDefault()
                        ).parse(customer.birthday)
                        SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
                    }
                } catch (e: Exception) {
                    Log.e(
                        "NotificationWorker",
                        "Error parsing birthday ${customer.birthday} for ${customer.id}: ${e.message}"
                    )
                    null // Handle parsing errors gracefully
                }

                // If today is the customer's birthday, create a notification
                if (birthdayDate == today) {
                    // Check if a birthday notification already exists for this customer today
                    val existingNotification = doesNotificationExist(
                        shopId,
                        NotificationType.BIRTHDAY,
                        customer.id
                    )

                    if (!existingNotification) {
                        val notification = AppNotification(
                            customerId = customer.id,
                            customerName = "${customer.firstName} ${customer.lastName}",
                            title = "Customer Birthday Today",
                            message = "${customer.firstName} ${customer.lastName} is celebrating their birthday today!",
                            type = NotificationType.BIRTHDAY,
                            status = NotificationStatus.UNREAD,
                            priority = NotificationPriority.NORMAL
                        )

                        repository.createNotification(notification, shopId)
                        notificationsCreated = true
                    }
                }
            }
        }

        // Similarly check anniversaries
        if (checkAnniversaries) {
            for (customer in customers) {
                // Convert stored anniversary to MM-dd format
                val anniversaryDate = try {
                    // Ensure customer.anniversary is not null or empty before parsing
                    if (customer.anniversary.isNullOrEmpty()) {
                        null
                    } else {
                        val date = SimpleDateFormat(
                            "dd/MM/yyyy",
                            Locale.getDefault()
                        ).parse(customer.anniversary)
                        SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
                    }
                } catch (e: Exception) {
                    Log.e(
                        "NotificationWorker",
                        "Error parsing anniversary ${customer.anniversary} for ${customer.id}: ${e.message}"
                    )
                    null // Handle parsing errors gracefully
                }

                if (anniversaryDate == today) {
                    // Check if an anniversary notification already exists for this customer today
                    val existingNotification = doesNotificationExist(
                        shopId,
                        NotificationType.ANNIVERSARY,
                        customer.id
                    )

                    if (!existingNotification) {
                        val notification = AppNotification(
                            customerId = customer.id,
                            customerName = "${customer.firstName} ${customer.lastName}",
                            title = "Customer Anniversary Today",
                            message = "${customer.firstName} ${customer.lastName} is celebrating their anniversary today!",
                            type = NotificationType.ANNIVERSARY,
                            status = NotificationStatus.UNREAD,
                            priority = NotificationPriority.NORMAL
                        )

                        repository.createNotification(notification, shopId)
                        notificationsCreated = true
                    }
                }
            }
        }

        return notificationsCreated
    }

    /**
     * Check if a notification already exists for the given type and entity id
     * Helps avoid duplicate notifications
     */
    private suspend fun doesNotificationExist(
        shopId: String,
        type: NotificationType,
        entityId: String
    ): Boolean {
        try {
            // Define a time threshold (24 hours ago)
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, -24)
            val timestamp = com.google.firebase.Timestamp(Date(calendar.timeInMillis))

            // Query for existing notifications using new path structure
            val querySnapshot = firestore.collection("shopData")
                .document(shopId)
                .collection("notifications")
                .whereEqualTo("type", type)
                .whereEqualTo("customerId", entityId)
                .whereGreaterThan("createdAt", timestamp)
                .limit(1)
                .get()
                .await()

            return !querySnapshot.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for existing notification in shop $shopId", e)
            return false // If we can't check, assume no existing notification to be safe
        }
    }

    /**
     * Generate and send a monthly business overview notification
     */
    private suspend fun sendMonthlyBusinessOverviewNotification(shopId: String) {
        // Fetch business overview data
        val businessOverview = fetchBusinessOverviewData(shopId)

        // Check if a business overview notification was already sent today
        val existingNotification = doesNotificationExist(
            shopId,
            NotificationType.GENERAL,
            "SYSTEM_MONTHLY_OVERVIEW"
        )

        if (existingNotification) {
            Log.d(TAG, "Monthly business overview already sent today for shop $shopId, skipping")
            return
        }

        // Create comprehensive notification
        val notification = AppNotification(
            customerId = "SYSTEM_MONTHLY_OVERVIEW",
            customerName = "Business Overview",
            title = "Monthly Business Insights",
            message = createBusinessOverviewMessage(businessOverview),
            type = NotificationType.GENERAL,
            status = NotificationStatus.UNREAD,
            priority = NotificationPriority.NORMAL
        )

        repository.createNotification(notification, shopId)
    }

    /**
     * Fetch business data for the overview notification
     */
    private suspend fun fetchBusinessOverviewData(shopId: String): BusinessOverview {
        // Fetch invoices for the previous month
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val lastMonthStart = calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        // Fetch invoices using new path structure
        val invoicesSnapshot = firestore.collection("shopData")
            .document(shopId)
            .collection("invoices")
            .whereGreaterThan("invoiceDate", lastMonthStart)
            .get()
            .await()

        val invoices = invoicesSnapshot.toObjects(Invoice::class.java)

        // Fetch inventory items using new path structure
        val inventorySnapshot = firestore.collection("shopData")
            .document(shopId)
            .collection("inventory")
            .get()
            .await()

        val inventoryItems = inventorySnapshot.toObjects(JewelleryItem::class.java)

        // Fetch customers using new path structure
        val customersSnapshot = firestore.collection("shopData")
            .document(shopId)
            .collection("customers")
            .get()
            .await()

        val customers = customersSnapshot.toObjects(Customer::class.java)

        // Calculate metrics
        return BusinessOverview(
            totalInvoices = invoices.size,
            totalSales = invoices.sumOf { it.totalAmount },
            topCustomer = calculateTopCustomer(invoices),
            lowStockItems = calculateLowStockItems(inventoryItems),
            pendingInvoices = invoices.filter { it.totalAmount > it.paidAmount }
        )
    }

    /**
     * Determine the top customer based on sales amount
     */
    private fun calculateTopCustomer(invoices: List<Invoice>): String {
        return invoices.groupBy { it.customerName }
            .mapValues { it.value.sumOf { inv -> inv.totalAmount } }
            .maxByOrNull { it.value }
            ?.key ?: "No top customer"
    }

    /**
     * Calculate items with low stock for business overview
     */
    private fun calculateLowStockItems(items: List<JewelleryItem>): List<String> {
        // Define low stock thresholds
        val LOW_STOCK_THRESHOLD = 5.0 // For quantity-based inventory
        val LOW_STOCK_WEIGHT_THRESHOLD = 100.0 // For weight-based inventory (grams)
        
        return items.filter { item ->
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
        .map { it.displayName }
    }

    /**
     * Format business overview data into readable message
     */
    private fun createBusinessOverviewMessage(overview: BusinessOverview): String {
        val formatter = java.text.DecimalFormat("#,##,##0.00")

        return """
        Last Month's Business Overview:
        📊 Total Invoices: ${overview.totalInvoices}
        💰 Total Sales: ₹${formatter.format(overview.totalSales)}
        🏆 Top Customer: ${overview.topCustomer}
        📉 Pending Invoices: ${overview.pendingInvoices.size}
        🚨 Low Stock Items: ${
            if (overview.lowStockItems.isNotEmpty()) overview.lowStockItems.joinToString(
                ", "
            ) else "None"
        }
        """.trimIndent()
    }

    /**
     * Check for and send low stock alerts
     * @return true if any low stock notifications were created
     */
    private suspend fun sendLowStockAlerts(
        shopId: String,
        preferences: NotificationPreferences
    ): Boolean {
        if (!preferences.lowStock) {
            return false
        }

        // Define low stock thresholds
        val LOW_STOCK_THRESHOLD = 5.0 // For quantity-based inventory
        val LOW_STOCK_WEIGHT_THRESHOLD = 100.0 // For weight-based inventory (grams)

        // Fetch inventory items with low stock using new path structure
        val inventorySnapshot = firestore.collection("shopData")
            .document(shopId)
            .collection("inventory")
            .get()
            .await()

        val lowStockItems = inventorySnapshot.toObjects(JewelleryItem::class.java)
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

        if (lowStockItems.isEmpty()) return false

        var notificationsCreated = false

        // If there are many low stock items, create a summary notification
        if (lowStockItems.size > 3) {
            // Check if a summary notification already exists for today
            val existingSummary = doesNotificationExist(
                shopId,
                NotificationType.GENERAL,
                "LOW_STOCK_SUMMARY"
            )

            if (!existingSummary) {
                val summaryNotification = AppNotification(
                    customerId = "LOW_STOCK_SUMMARY",
                    customerName = "Inventory Alert",
                    title = "Multiple Items Low in Stock",
                    message = "${lowStockItems.size} items are running low on stock",
                    type = NotificationType.GENERAL,
                    status = NotificationStatus.UNREAD,
                    priority = NotificationPriority.HIGH,
                    relatedItemId = null // No specific item for summary
                )

                repository.createNotification(summaryNotification, shopId)
                notificationsCreated = true
            }
        } else {
            // Create individual notifications for each item if they don't already exist
            for (item in lowStockItems) {
                val existingItemAlert = doesNotificationExist(
                    shopId,
                    NotificationType.GENERAL,
                    "ITEM_${item.id}"
                )

                if (!existingItemAlert) {
                    // Get appropriate stock level and unit based on inventory type
                    val stockLevel = when (item.inventoryType) {
                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> item.totalWeightGrams
                        else -> item.stock
                    }
                    
                    val stockUnit = when (item.inventoryType) {
                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> "g"
                        else -> item.stockUnit
                    }

                    val notification = AppNotification(
                        customerId = "ITEM_${item.id}",
                        customerName = "Inventory Alert",
                        title = "Low Stock: ${item.displayName}",
                        message = "Current stock: $stockLevel $stockUnit",
                        type = NotificationType.GENERAL,
                        status = NotificationStatus.UNREAD,
                        priority = NotificationPriority.HIGH,
                        relatedItemId = item.id,
                        stockLevel = stockLevel
                    )

                    repository.createNotification(notification, shopId)
                    notificationsCreated = true
                }
            }
        }

        return notificationsCreated
    }

    // Data class for business overview
    data class BusinessOverview(
        val totalInvoices: Int,
        val totalSales: Double,
        val topCustomer: String,
        val lowStockItems: List<String>,
        val pendingInvoices: List<Invoice>
    )
}