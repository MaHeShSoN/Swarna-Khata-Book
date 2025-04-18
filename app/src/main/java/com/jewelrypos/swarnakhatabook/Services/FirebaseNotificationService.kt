package com.jewelrypos.swarnakhatabook.Services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavDeepLinkBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.MainActivity
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationChannelManager
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Service to handle Firebase Cloud Messaging
 * Manages both notification and data messages and handles notification grouping
 */
class FirebaseNotificationService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FirebaseNotService"
        private const val GROUP_KEY_PAYMENTS = "group_payments"
        private const val GROUP_KEY_INVENTORY = "group_inventory"
        private const val GROUP_KEY_CUSTOMERS = "group_customers"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val notificationRepository by lazy { NotificationRepository(firestore, auth) }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            super.onMessageReceived(remoteMessage)

            Log.d(TAG, "From: ${remoteMessage.from}")

            // Check if message contains a notification payload
            if (remoteMessage.notification != null) {
                Log.d(TAG, "Message Notification Body: ${remoteMessage.notification?.body}")
                sendNotification(remoteMessage.notification!!, remoteMessage.data)
            }
            // Check if message contains data payload
            else if (remoteMessage.data.isNotEmpty()) {
                Log.d(TAG, "Message data payload: ${remoteMessage.data}")

                // Handle data message
                handleDataMessage(remoteMessage.data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM message", e)
        }
    }

    /**
     * Send a notification with a RemoteMessage.Notification object
     */
    private fun sendNotification(notification: RemoteMessage.Notification, data: Map<String, String> = mapOf()) {
        try {
            // Choose channel based on notification type
            val type = data["type"] ?: "GENERAL"
            val channelId = NotificationChannelManager.getChannelForType(type)

            // Generate specific notification ID
            val notificationId = generateUniqueNotificationId(data)

            // Create intent using deep linking
            val pendingIntent = createDeepLinkPendingIntent(type, data)

            // Determine if notification belongs to a group
            val groupKey = getGroupKeyForType(type)

            // Build the notification
            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.mingcute__notification_fill)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            // Add style for long messages
            if ((notification.body?.length ?: 0) > 40) {
                notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            }

            // Apply grouping if applicable
            if (groupKey != null) {
                notificationBuilder.setGroup(groupKey)
            }

            // Add any additional actions based on type
            addActionsToNotification(notificationBuilder, type, data)

            // Get notification manager
            val notificationManager = NotificationManagerCompat.from(this)

            // Show the notification with permission check - SAFELY
            showNotificationSafely(notificationManager, notificationId, notificationBuilder.build())

            // If part of a group, check if we need a summary notification
            if (groupKey != null) {
                coroutineScope.launch {
                    updateGroupSummary(groupKey)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification", e)
        }
    }

    /**
     * Handle data message and create appropriate notification
     */
    private fun handleDataMessage(data: Map<String, String>) {
        try {
            // Create notification manager using the compat version
            val notificationManager = NotificationManagerCompat.from(this)

            // Extract notification data from the message
            val notificationType = data["type"] ?: "GENERAL"
            val title = data["title"] ?: "New Notification"
            val message = data["message"] ?: "You have a new notification"
            val shopId = data["shopId"] // Extract shop ID if present

            // Create a notification builder
            val notificationBuilder = createNotificationBuilder(
                title = title,
                message = message,
                type = notificationType,
                data = data
            )

            // Generate a unique ID for this notification
            val notificationId = generateUniqueNotificationId(data)

            // Get group key for potential grouping
            val groupKey = getGroupKeyForType(notificationType)

            // Show the notification with permission check - SAFELY
            showNotificationSafely(notificationManager, notificationId, notificationBuilder.build())

            // If part of a group, check if we need a summary notification
            if (groupKey != null) {
                coroutineScope.launch {
                    updateGroupSummary(groupKey)
                }
            }

            // Store the notification in Firestore for persistence
            storeNotificationInFirestore(title, message, notificationType, data, shopId)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing data message", e)
        }
    }

    /**
     * Store notification in Firestore for persistence and retrieval in the app
     */
    private fun storeNotificationInFirestore(
        title: String,
        message: String,
        type: String,
        data: Map<String, String>,
        shopId: String?
    ) {
        coroutineScope.launch {
            try {
                val notificationType = try {
                    NotificationType.valueOf(type)
                } catch (e: IllegalArgumentException) {
                    NotificationType.GENERAL
                }

                // Create notification object
                val notification = AppNotification(
                    id = "", // Will be set by repository
                    title = title,
                    message = message,
                    type = notificationType,
                    customerId = data["customerId"] ?: "",
                    customerName = data["customerName"] ?: "",
                    relatedItemId = data["itemId"],
                    relatedInvoiceId = data["invoiceId"],
                    amount = data["amount"]?.toDoubleOrNull(),
                    shopId = shopId // Include shop ID in the notification
                )

                // Use repository to create the notification
                notificationRepository.createNotification(notification, shopId)
                    .fold(
                        onSuccess = { id ->
                            Log.d(TAG, "Notification stored in Firestore with ID: $id")
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to store notification in Firestore", e)
                        }
                    )
            } catch (e: Exception) {
                Log.e(TAG, "Error storing notification in Firestore", e)
            }
        }
    }

    /**
     * Create a notification builder for a data message
     */
    private fun createNotificationBuilder(
        title: String,
        message: String,
        type: String,
        data: Map<String, String>
    ): NotificationCompat.Builder {
        // Choose channel based on notification type
        val channelId = NotificationChannelManager.getChannelForType(type)

        // Create deep link pending intent
        val pendingIntent = createDeepLinkPendingIntent(type, data)

        // Determine if notification belongs to a group
        val groupKey = getGroupKeyForType(type)

        // Build the notification
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.mingcute__notification_fill)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Add style for long messages
        if (message.length > 40) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }

        // Apply the group if applicable
        if (groupKey != null) {
            builder.setGroup(groupKey)
        }

        // Add any additional actions based on type
        addActionsToNotification(builder, type, data)

        return builder
    }

    /**
     * Create a pending intent using NavDeepLinkBuilder for proper deep linking
     */
    private fun createDeepLinkPendingIntent(type: String, data: Map<String, String>): PendingIntent {
        // Default pending intent that opens the notification list
        var pendingIntent: PendingIntent = createSimpleIntent()

        try {
            // Create specific deep links based on notification type
            when (type) {
                "PAYMENT_DUE", "PAYMENT_OVERDUE", "payment_action" -> {
                    val invoiceId = data["invoiceId"]
                    if (!invoiceId.isNullOrEmpty()) {
                        pendingIntent = createInvoiceDetailIntent(invoiceId)
                    }
                }

                "CREDIT_LIMIT", "customer_detail" -> {
                    val customerId = data["customerId"]
                    if (!customerId.isNullOrEmpty()) {
                        pendingIntent = createCustomerDetailIntent(customerId)
                    }
                }

                "LOW_STOCK", "item_detail" -> {
                    val itemId = data["itemId"]
                    if (!itemId.isNullOrEmpty()) {
                        pendingIntent = createItemDetailIntent(itemId)
                    }
                }

                "BIRTHDAY", "ANNIVERSARY" -> {
                    val customerId = data["customerId"]
                    if (!customerId.isNullOrEmpty()) {
                        pendingIntent = createCustomerDetailIntent(customerId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating deep link pending intent", e)
        }

        return pendingIntent
    }

    /**
     * Create a simple intent for notification list
     */
    private fun createSimpleIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("navigate_to", "notification_list")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Create intent for invoice detail
     */
    private fun createInvoiceDetailIntent(invoiceId: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("navigate_to", "invoice_detail")
            putExtra("invoiceId", invoiceId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            invoiceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Create intent for customer detail
     */
    private fun createCustomerDetailIntent(customerId: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("navigate_to", "customer_detail")
            putExtra("customerId", customerId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            customerId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Create intent for item detail
     */
    private fun createItemDetailIntent(itemId: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("navigate_to", "item_detail")
            putExtra("itemId", itemId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Create intent for payments screen
     */
    private fun createPaymentsIntent(invoiceId: String?, customerId: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("navigate_to", "payment_screen")
            if (invoiceId != null) putExtra("invoiceId", invoiceId)
            if (customerId != null) putExtra("customerId", customerId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            this,
            (invoiceId ?: customerId ?: "payments").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Add action buttons to a notification based on type
     */
    private fun addActionsToNotification(
        builder: NotificationCompat.Builder,
        type: String,
        data: Map<String, String>
    ) {
        try {
            when (type) {
                "PAYMENT_DUE", "PAYMENT_OVERDUE" -> {
                    // Add a "Pay Now" action
                    val payIntent = createPaymentsIntent(
                        data["invoiceId"],
                        data["customerId"]
                    )

                    builder.addAction(
                        R.drawable.mdi__currency_inr,
                        "Pay Now",
                        payIntent
                    )
                }

                "LOW_STOCK" -> {
                    // Add a "View Item" action
                    val itemId = data["itemId"]
                    if (!itemId.isNullOrEmpty()) {
                        val restockIntent = createItemDetailIntent(itemId)

                        builder.addAction(
                            R.drawable.material_symbols_warning_rounded,
                            "View Item",
                            restockIntent
                        )
                    }
                }

                "CREDIT_LIMIT" -> {
                    // Add a "View Customer" action
                    val customerId = data["customerId"]
                    if (!customerId.isNullOrEmpty()) {
                        val customerIntent = createCustomerDetailIntent(customerId)

                        builder.addAction(
                            R.drawable.material_symbols_warning_rounded,
                            "View Customer",
                            customerIntent
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding actions to notification", e)
        }
    }

    /**
     * Get the appropriate group key for a notification type
     */
    private fun getGroupKeyForType(type: String): String? {
        return when(type) {
            "PAYMENT_DUE", "PAYMENT_OVERDUE" -> GROUP_KEY_PAYMENTS
            "LOW_STOCK" -> GROUP_KEY_INVENTORY
            "BIRTHDAY", "ANNIVERSARY" -> GROUP_KEY_CUSTOMERS
            else -> null
        }
    }

    /**
     * Create or update a summary notification for a group
     */
    private suspend fun updateGroupSummary(groupKey: String) {
        try {
            // Fetch active notifications in this group
            val result = notificationRepository.getActiveNotificationsInGroup(groupKey)

            if (result.isSuccess) {
                val notifications = result.getOrNull()
                val count = notifications?.size ?: 0

                // Only create summary if there are multiple notifications
                if (count > 1) {
                    createGroupSummaryNotification(groupKey, count, notifications)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group summary", e)
        }
    }

    /**
     * Create a summary notification for a group with more informative content
     */
    private fun createGroupSummaryNotification(
        groupKey: String,
        notificationCount: Int,
        notifications: List<com.jewelrypos.swarnakhatabook.DataClasses.AppNotification>? = null
    ) {
        val title: String
        val message: String

        when (groupKey) {
            GROUP_KEY_PAYMENTS -> {
                title = "Payment Notifications"
                message = buildDetailedSummary(
                    "$notificationCount payment notifications",
                    notifications,
                    "payment"
                )
            }
            GROUP_KEY_INVENTORY -> {
                title = "Inventory Alerts"
                message = buildDetailedSummary(
                    "$notificationCount inventory alerts",
                    notifications,
                    "inventory"
                )
            }
            GROUP_KEY_CUSTOMERS -> {
                title = "Customer Events"
                message = buildDetailedSummary(
                    "$notificationCount customer events",
                    notifications,
                    "customer"
                )
            }
            else -> {
                title = "Notifications"
                message = "$notificationCount new notifications"
            }
        }

        // Create intent for notification list
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("navigate_to", "notification_list")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            groupKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Determine channel for summary
        val channelId = when (groupKey) {
            GROUP_KEY_PAYMENTS -> NotificationChannelManager.CHANNEL_ALERTS
            GROUP_KEY_INVENTORY -> NotificationChannelManager.CHANNEL_ALERTS
            GROUP_KEY_CUSTOMERS -> NotificationChannelManager.CHANNEL_REMINDERS
            else -> NotificationChannelManager.CHANNEL_INSIGHTS
        }

        val summaryNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.mingcute__notification_fill)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Get notification manager
        val notificationManager = NotificationManagerCompat.from(this)

        // Show the notification with permission check - SAFELY
        showNotificationSafely(notificationManager, groupKey.hashCode(), summaryNotification)
    }

    /**
     * Safely show a notification with proper permission checking
     */
    private fun showNotificationSafely(
        notificationManager: NotificationManagerCompat,
        notificationId: Int,
        notification: android.app.Notification
    ) {
        // Check permission before showing notification
        if (NotificationPermissionHelper.hasNotificationPermission(this)) {
            try {
                notificationManager.notify(notificationId, notification)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException when showing notification", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error showing notification", e)
            }
        } else {
            Log.w(TAG, "Notification not shown: POST_NOTIFICATIONS permission not granted")
        }
    }

    /**
     * Build a more detailed summary with specific items
     */
    private fun buildDetailedSummary(
        defaultSummary: String,
        notifications: List<com.jewelrypos.swarnakhatabook.DataClasses.AppNotification>?,
        type: String
    ): String {
        if (notifications.isNullOrEmpty() || notifications.size <= 1) {
            return defaultSummary
        }

        val builder = StringBuilder(defaultSummary)
        builder.append("\n\n")

        // Limit to 3 items in the summary
        val limitedList = notifications.take(3)

        when (type) {
            "payment" -> {
                limitedList.forEach { notification ->
                    if (notification.customerName.isNotEmpty()) {
                        builder.append("• ${notification.customerName}: ₹${notification.amount ?: 0}\n")
                    } else {
                        builder.append("• ${notification.title}\n")
                    }
                }
            }
            "inventory" -> {
                limitedList.forEach { notification ->
                    builder.append("• ${notification.title.replace("Low Stock: ", "")}\n")
                }
            }
            "customer" -> {
                limitedList.forEach { notification ->
                    builder.append("• ${notification.customerName}: ${notification.type.name}\n")
                }
            }
        }

        // If there are more items, add an ellipsis
        if (notifications.size > 3) {
            builder.append("• And ${notifications.size - 3} more...")
        }

        return builder.toString()
    }

    /**
     * Generate a truly unique notification ID based on notification data
     * Uses combination of notification type, entity ID, and UUID to avoid collisions
     */
    private fun generateUniqueNotificationId(data: Map<String, String>): Int {
        val baseIdValue = when {
            data.containsKey("invoiceId") -> "invoice-${data["invoiceId"]}"
            data.containsKey("itemId") -> "item-${data["itemId"]}"
            data.containsKey("customerId") -> "customer-${data["customerId"]}"
            else -> "notification-${UUID.randomUUID()}"
        }

        // Include notification type for additional uniqueness
        val type = data["type"] ?: "GENERAL"
        val finalIdValue = "$type-$baseIdValue"

        // Get positive hash code
        return Math.abs(finalIdValue.hashCode())
    }

    /**
     * Handle FCM token refresh
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")

        // Send the token to the server
        sendRegistrationToServer(token)
    }

    /**
     * Send FCM token to server for targeting notifications
     * Supports multiple devices by storing tokens in a map with device IDs as keys
     */
    private fun sendRegistrationToServer(token: String) {
        coroutineScope.launch {
            try {
                // Get the current user
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    Log.d(TAG, "No current user, token not saved")
                    return@launch
                }

                // Format the phone number as required
                val phoneNumber = currentUser.phoneNumber?.replace("+", "")
                if (phoneNumber == null) {
                    Log.d(TAG, "No phone number, token not saved")
                    return@launch
                }

                // Get device ID for multi-device support
                val deviceId = android.provider.Settings.Secure.getString(
                    applicationContext.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                
                Log.d(TAG, "Updating FCM token for device: $deviceId")

                // Reference to the user document
                val userRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(phoneNumber)
                
                // Use a transaction to safely update the tokens map
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(userRef)
                    
                    // Get existing tokens map or create a new one
                    @Suppress("UNCHECKED_CAST")
                    val tokens = snapshot.get("fcmTokens") as? Map<String, String> ?: mapOf()
                    
                    // Update with the new token for this device
                    val updatedTokens = tokens.toMutableMap()
                    updatedTokens[deviceId] = token
                    
                    // Update the document with the new tokens map
                    transaction.update(userRef, "fcmTokens", updatedTokens)
                    
                    // For backward compatibility, also update the single token field
                    transaction.update(userRef, "fcmToken", token)
                }.await()

                Log.d(TAG, "FCM token successfully updated in Firestore for device: $deviceId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating FCM token", e)
            }
        }
    }
}