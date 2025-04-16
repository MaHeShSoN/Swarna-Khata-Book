package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

    private val repository by lazy {
        NotificationRepository(firestore, auth)
    }

    // Modify the doWork() method in NotificationWorker.kt to include payment due/overdue checks
    override suspend fun doWork(): Result {
        try {
            // Get current user information
            val currentUser = auth.currentUser ?: return Result.failure()
            val phoneNumber = currentUser.phoneNumber?.replace("+", "") ?: return Result.failure()

            // Get notification preferences once at the beginning
            val notificationPreferences =
                repository.getNotificationPreferences().getOrNull() ?: NotificationPreferences()
            var anySuccessfulCheck = false
            var anyFailedCheck = false

            // Check for payment due and overdue notifications
            try {
                Log.d(TAG, "Checking payment due and overdue")
                val paymentNotificationsSuccess =
                    checkPaymentDueAndOverdue(phoneNumber, notificationPreferences)
                if (paymentNotificationsSuccess) anySuccessfulCheck = true
            } catch (e: Exception) {
                Log.e(TAG, "Error checking payment due/overdue", e)
                anyFailedCheck = true
                // Continue with other checks
            }

            // Existing check for monthly business overview
            if (isFirstDayOfMonth() && notificationPreferences.businessInsights) {
                try {
                    Log.d(TAG, "Checking monthly business overview")
                    sendMonthlyBusinessOverviewNotification(phoneNumber)
                    anySuccessfulCheck = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending business overview notification", e)
                    anyFailedCheck = true
                    // Continue with other checks
                }
            }

            // Existing check for low stock items
            if (notificationPreferences.lowStock) {
                try {
                    Log.d(TAG, "Checking low stock items")
                    val lowStockSuccess = sendLowStockAlerts(phoneNumber, notificationPreferences)
                    if (lowStockSuccess) anySuccessfulCheck = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending low stock alerts", e)
                    anyFailedCheck = true
                    // Continue with other checks
                }
            }

            // Existing check for customer special dates (birthdays, anniversaries)
            val shouldCheckBirthdays = notificationPreferences.customerBirthday
            val shouldCheckAnniversaries = notificationPreferences.customerAnniversary

            if (shouldCheckBirthdays || shouldCheckAnniversaries) {
                try {
                    Log.d(TAG, "Checking customer special dates")
                    val specialDatesSuccess = checkCustomerSpecialDates(
                        phoneNumber,
                        shouldCheckBirthdays,
                        shouldCheckAnniversaries
                    )
                    if (specialDatesSuccess) anySuccessfulCheck = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking customer special dates", e)
                    anyFailedCheck = true
                    // Continue with other checks
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
     * Check for payment due and overdue notifications
     * @return true if any notifications were created
     */
    private suspend fun checkPaymentDueAndOverdue(
        phoneNumber: String,
        preferences: NotificationPreferences
    ): Boolean {
        // Skip if both notification types are disabled
        if (!preferences.paymentDue && !preferences.paymentOverdue) {
            Log.d(TAG, "Payment due and overdue notifications are disabled")
            return false
        }

        try {
            // Query unpaid invoices with due dates
            val invoicesSnapshot = firestore.collection("users")
                .document(phoneNumber)
                .collection("invoices")
                .whereNotEqualTo("dueDate", null)
                .get()
                .await()

            val unpaidInvoices = invoicesSnapshot.toObjects(Invoice::class.java)
                .filter { it.paidAmount < it.totalAmount } // Only unpaid or partially paid invoices

            if (unpaidInvoices.isEmpty()) {
                Log.d(TAG, "No unpaid invoices with due dates found")
                return false
            }

            Log.d(TAG, "Found ${unpaidInvoices.size} unpaid invoices with due dates")
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
                    phoneNumber, unpaidInvoices, today, preferences.paymentDueReminderDays
                ) || notificationsCreated
            }

            // Check for payment overdue notifications
            if (preferences.paymentOverdue) {
                notificationsCreated = checkPaymentOverdueNotifications(
                    phoneNumber, unpaidInvoices, today, preferences.paymentOverdueAlertDays
                ) || notificationsCreated
            }

            return notificationsCreated
        } catch (e: Exception) {
            Log.e(TAG, "Error checking payment due/overdue", e)
            return false
        }
    }

    /**
     * Check and create payment overdue notifications
     * @return true if any notifications were created
     */
    private suspend fun checkPaymentOverdueNotifications(
        phoneNumber: String,
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
                    phoneNumber,
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

                    repository.createNotification(notification)
                    Log.d(
                        TAG,
                        "Created payment overdue notification for invoice ${invoice.invoiceNumber}"
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
        phoneNumber: String,
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
                    phoneNumber,
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

                    repository.createNotification(notification)
                    Log.d(
                        TAG,
                        "Created payment due notification for invoice ${invoice.invoiceNumber}"
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
        phoneNumber: String,
        checkBirthdays: Boolean,
        checkAnniversaries: Boolean
    ): Boolean {
        // Get today's date in MM-dd format (without year)
        val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())

        // Fetch customers
        val customersSnapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .get()
            .await()

        val customers = customersSnapshot.toObjects(Customer::class.java)
        var notificationsCreated = false

        // Check birthdays
        if (checkBirthdays) {
            for (customer in customers) {
                // Convert stored birthday to MM-dd format
                // *** CORRECTED LINE: Changed date format from "yyyy-MM-dd" to "dd/MM/yyyy" ***
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
                        phoneNumber,
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

                        repository.createNotification(notification)
                        notificationsCreated = true
                    }
                }
            }
        }

        // Similarly check anniversaries
        if (checkAnniversaries) {
            for (customer in customers) {
                // Convert stored anniversary to MM-dd format
                // *** CORRECTED LINE: Changed date format from "yyyy-MM-dd" to "dd/MM/yyyy" ***
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
                        phoneNumber,
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

                        repository.createNotification(notification)
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
        phoneNumber: String,
        type: NotificationType,
        entityId: String
    ): Boolean {
        try {
            // Define a time threshold (24 hours ago)
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, -24)
            val timestamp = com.google.firebase.Timestamp(Date(calendar.timeInMillis))

            // Query for existing notifications
            val querySnapshot = firestore.collection("users")
                .document(phoneNumber)
                .collection("notifications")
                .whereEqualTo("type", type)
                .whereEqualTo("customerId", entityId)
                .whereGreaterThan("createdAt", timestamp)
                .limit(1)
                .get()
                .await()

            return !querySnapshot.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for existing notification", e)
            return false // If we can't check, assume no existing notification to be safe
        }
    }

    /**
     * Generate and send a monthly business overview notification
     */
    private suspend fun sendMonthlyBusinessOverviewNotification(phoneNumber: String) {
        // Fetch business overview data
        val businessOverview = fetchBusinessOverviewData(phoneNumber)

        // Check if a business overview notification was already sent today
        val existingNotification = doesNotificationExist(
            phoneNumber,
            NotificationType.GENERAL,
            "SYSTEM_MONTHLY_OVERVIEW"
        )

        if (existingNotification) {
            Log.d(TAG, "Monthly business overview already sent today, skipping")
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

        repository.createNotification(notification)
    }

    /**
     * Fetch business data for the overview notification
     */
    private suspend fun fetchBusinessOverviewData(phoneNumber: String): BusinessOverview {
        // Fetch invoices for the previous month
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -1)
        val lastMonthStart = calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        // Fetch invoices
        val invoicesSnapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("invoices")
            .whereGreaterThan("invoiceDate", lastMonthStart)
            .get()
            .await()

        val invoices = invoicesSnapshot.toObjects(Invoice::class.java)

        // Fetch inventory items
        val inventorySnapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("inventory")
            .get()
            .await()

        val inventoryItems = inventorySnapshot.toObjects(JewelleryItem::class.java)

        // Fetch customers
        val customersSnapshot = firestore.collection("users")
            .document(phoneNumber)
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
     * Find items with low stock levels
     */
    private fun calculateLowStockItems(items: List<JewelleryItem>): List<String> {
        return items.filter { it.stock <= 5 }
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
        phoneNumber: String,
        preferences: NotificationPreferences
    ): Boolean {
        if (!preferences.lowStock) {
            return false
        }

        // Fetch inventory items with low stock
        val inventorySnapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("inventory")
            .get()
            .await()

        val lowStockItems = inventorySnapshot.toObjects(JewelleryItem::class.java)
            .filter { it.stock <= 5 }

        if (lowStockItems.isEmpty()) return false

        var notificationsCreated = false

        // If there are many low stock items, create a summary notification
        if (lowStockItems.size > 3) {
            // Check if a summary notification already exists for today
            val existingSummary = doesNotificationExist(
                phoneNumber,
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

                repository.createNotification(summaryNotification)
                notificationsCreated = true
            }
        } else {
            // Create individual notifications for each item if they don't already exist
            for (item in lowStockItems) {
                val existingItemAlert = doesNotificationExist(
                    phoneNumber,
                    NotificationType.GENERAL,
                    "ITEM_${item.id}"
                )

                if (!existingItemAlert) {
                    val notification = AppNotification(
                        customerId = "ITEM_${item.id}",
                        customerName = "Inventory Alert",
                        title = "Low Stock: ${item.displayName}",
                        message = "Current stock: ${item.stock} ${item.stockUnit}",
                        type = NotificationType.GENERAL,
                        status = NotificationStatus.UNREAD,
                        priority = NotificationPriority.HIGH,
                        relatedItemId = item.id,
                        stockLevel = item.stock
                    )

                    repository.createNotification(notification)
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