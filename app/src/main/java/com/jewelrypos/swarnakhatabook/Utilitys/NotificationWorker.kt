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
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

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

    override suspend fun doWork(): Result {
        try {
            // Get current user information
            val currentUser = auth.currentUser ?: return Result.failure()
            val phoneNumber = currentUser.phoneNumber?.replace("+", "") ?: return Result.failure()

            // Handle monthly business overview
            try {
                if (isFirstDayOfMonth()) {
                    sendMonthlyBusinessOverviewNotification(phoneNumber)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending business overview notification", e)
                // Continue with other checks
            }

            // Check for low stock items
            try {
                sendLowStockAlerts(phoneNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending low stock alerts", e)
                // Continue with other checks
            }

            // Check for customer special dates
            try {
                checkCustomerSpecialDates(phoneNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking customer special dates", e)
                // Continue with other checks
            }

            // Return success even if some checks failed
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in notification worker", e)
            return Result.failure()
        }
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
     */
    private suspend fun checkCustomerSpecialDates(phoneNumber: String) {
        // Get today's date in MM-dd format (without year)
        val today = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date())

        // Fetch customers
        val customersSnapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .get()
            .await()

        val customers = customersSnapshot.toObjects(Customer::class.java)

        // Check birthdays
        if (repository.shouldSendNotification(NotificationType.BIRTHDAY)) {
            customers.forEach { customer ->
                // Convert stored birthday to MM-dd format
                val birthdayDate = try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(customer.birthday)
                    SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
                } catch (e: Exception) {
                    null
                }

                // If today is the customer's birthday, create a notification
                if (birthdayDate == today) {
                    val notification = AppNotification(
                        customerId = customer.id,
                        customerName = "${customer.firstName} ${customer.lastName}",
                        title = "Customer Birthday Today",
                        message = "${customer.firstName} ${customer.lastName} is celebrating their birthday today!",
                        type = NotificationType.BIRTHDAY,
                        status = NotificationStatus.UNREAD,
                        priority = NotificationPriority.NORMAL
                    )

                    // Double-check preference before creating notification
                    if (repository.shouldSendNotification(NotificationType.BIRTHDAY)) {
                        repository.createNotification(notification)
                    }
                }
            }
        }

        // Similarly check anniversaries
        if (repository.shouldSendNotification(NotificationType.ANNIVERSARY)) {
            customers.forEach { customer ->
                val anniversaryDate = try {
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(customer.anniversary)
                    SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
                } catch (e: Exception) {
                    null
                }

                if (anniversaryDate == today) {
                    val notification = AppNotification(
                        customerId = customer.id,
                        customerName = "${customer.firstName} ${customer.lastName}",
                        title = "Customer Anniversary Today",
                        message = "${customer.firstName} ${customer.lastName} is celebrating their anniversary today!",
                        type = NotificationType.ANNIVERSARY,
                        status = NotificationStatus.UNREAD,
                        priority = NotificationPriority.NORMAL
                    )

                    // Double-check preference before creating notification
                    if (repository.shouldSendNotification(NotificationType.ANNIVERSARY)) {
                        repository.createNotification(notification)
                    }
                }
            }
        }
    }

    /**
     * Generate and send a monthly business overview notification
     */
    private suspend fun sendMonthlyBusinessOverviewNotification(phoneNumber: String) {
        // Double-check preference before fetching data
        if (!repository.shouldSendNotification(NotificationType.GENERAL)) {
            return
        }

        // Fetch business overview data
        val businessOverview = fetchBusinessOverviewData(phoneNumber)

        // Create comprehensive notification
        val notification = AppNotification(
            customerId = "SYSTEM",
            customerName = "Business Overview",
            title = "Monthly Business Insights",
            message = createBusinessOverviewMessage(businessOverview),
            type = NotificationType.GENERAL,
            status = NotificationStatus.UNREAD,
            priority = NotificationPriority.NORMAL
        )

        // Final check before creating notification
        if (repository.shouldSendNotification(NotificationType.GENERAL)) {
            repository.createNotification(notification)
        }
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
        🚨 Low Stock Items: ${if (overview.lowStockItems.isNotEmpty()) overview.lowStockItems.joinToString(", ") else "None"}
        """.trimIndent()
    }

    /**
     * Check for and send low stock alerts
     */
    private suspend fun sendLowStockAlerts(phoneNumber: String) {
        // Double-check if low stock alerts are enabled
        if (!repository.shouldSendNotification(NotificationType.GENERAL)) {
            return
        }

        // Fetch inventory items with low stock
        val inventorySnapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("inventory")
            .get()
            .await()

        val lowStockItems = inventorySnapshot.toObjects(JewelleryItem::class.java)
            .filter { it.stock <= 5 }

        if (lowStockItems.isEmpty()) return

        // If there are many low stock items, create a summary notification
        if (lowStockItems.size > 3) {
            val summaryNotification = AppNotification(
                customerId = "SYSTEM",
                customerName = "Inventory Alert",
                title = "Multiple Items Low in Stock",
                message = "${lowStockItems.size} items are running low on stock",
                type = NotificationType.GENERAL,
                status = NotificationStatus.UNREAD,
                priority = NotificationPriority.HIGH,
                relatedItemId = null // No specific item for summary
            )

            // Final check before creating notification
            if (repository.shouldSendNotification(NotificationType.GENERAL)) {
                repository.createNotification(summaryNotification)
            }
        } else {
            // Create individual notifications for each item
            lowStockItems.forEach { item ->
                val notification = AppNotification(
                    customerId = "SYSTEM",
                    customerName = "Inventory Alert",
                    title = "Low Stock: ${item.displayName}",
                    message = "Current stock: ${item.stock} ${item.stockUnit}",
                    type = NotificationType.GENERAL,
                    status = NotificationStatus.UNREAD,
                    priority = NotificationPriority.HIGH,
                    relatedItemId = item.id,
                    stockLevel = item.stock
                )

                // Final check before creating notification
                if (repository.shouldSendNotification(NotificationType.GENERAL)) {
                    repository.createNotification(notification)
                }
            }
        }
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