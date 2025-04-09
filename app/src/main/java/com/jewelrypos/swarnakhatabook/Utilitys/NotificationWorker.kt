package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        return try {
            val currentUser = auth.currentUser ?: return Result.failure()
            val phoneNumber = currentUser.phoneNumber?.replace("+", "") ?: return Result.failure()

            val calendar = Calendar.getInstance()
            val today = calendar.time

            // Check if it's the first day of the month
            if (calendar.get(Calendar.DAY_OF_MONTH) == 1) {
                sendMonthlyBusinessOverviewNotification(phoneNumber)
            }

            // Send low stock alerts
            sendLowStockAlerts(phoneNumber)

            Result.success()
        } catch (e: Exception) {
            Log.e("NotificationWorker", "Error in background work", e)
            Result.failure()
        }
    }

    private suspend fun sendMonthlyBusinessOverviewNotification(phoneNumber: String) {
        // Fetch business overview data
        val businessOverview = fetchBusinessOverviewData(phoneNumber)

        // Create comprehensive notification
        val notification = PaymentNotification(
            customerId = "SYSTEM",
            customerName = "Business Overview",
            title = "Monthly Business Insights",
            message = createBusinessOverviewMessage(businessOverview),
            type = NotificationType.GENERAL,
            status = NotificationStatus.UNREAD,
            priority = NotificationPriority.NORMAL
        )

        // Save to Firestore
        saveNotificationToFirestore(phoneNumber, notification)
    }

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

    private fun calculateTopCustomer(invoices: List<Invoice>): String {
        return invoices.groupBy { it.customerName }
            .maxByOrNull { it.value.sumOf { inv -> inv.totalAmount } }
            ?.key ?: "No top customer"
    }

    private fun calculateLowStockItems(items: List<JewelleryItem>): List<String> {
        return items.filter { it.stock <= 5 }
            .map { it.displayName }
    }

    private fun createBusinessOverviewMessage(overview: BusinessOverview): String {
        return """
        Last Month's Business Overview:
        📊 Total Invoices: ${overview.totalInvoices}
        💰 Total Sales: ₹${String.format("%.2f", overview.totalSales)}
        🏆 Top Customer: ${overview.topCustomer}
        📉 Pending Invoices: ${overview.pendingInvoices.size}
        🚨 Low Stock Items: ${overview.lowStockItems.joinToString(", ")}
        """.trimIndent()
    }

    private suspend fun sendLowStockAlerts(phoneNumber: String) {
        // Fetch inventory items
        val inventorySnapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("inventory")
            .get()
            .await()

        val lowStockItems = inventorySnapshot.toObjects(JewelleryItem::class.java)
            .filter { it.stock <= 5 }

        if (lowStockItems.isNotEmpty()) {
            val lowStockNotification = PaymentNotification(
                customerId = "SYSTEM",
                customerName = "Inventory Alert",
                title = "Low Stock Warning",
                message = "Low stock alert for: ${lowStockItems.map { it.displayName }.joinToString(", ")}",
                type = NotificationType.GENERAL,
                status = NotificationStatus.UNREAD,
                priority = NotificationPriority.HIGH
            )

            saveNotificationToFirestore(phoneNumber, lowStockNotification)
        }
    }

    private suspend fun saveNotificationToFirestore(phoneNumber: String, notification: PaymentNotification) {
        firestore.collection("users")
            .document(phoneNumber)
            .collection("notifications")
            .add(notification)
            .await()
    }

    // Data classes for business overview
    data class BusinessOverview(
        val totalInvoices: Int,
        val totalSales: Double,
        val topCustomer: String,
        val lowStockItems: List<String>,
        val pendingInvoices: List<Invoice>
    )
}