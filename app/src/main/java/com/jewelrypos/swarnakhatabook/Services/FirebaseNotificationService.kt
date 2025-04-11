package com.jewelrypos.swarnakhatabook.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jewelrypos.swarnakhatabook.MainActivity
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Service to handle Firebase Cloud Messaging
 * Manages both notification and data messages, creates notification channels,
 * and handles notification grouping
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
     * Create notification channels for Android O and above
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // High priority alerts
            val alertsChannel = NotificationChannel(
                "alerts_channel",
                "Important Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical notifications like low stock and payment overdue"
                enableLights(true)
                enableVibration(true)
            }

            // Business insights
            val insightsChannel = NotificationChannel(
                "insights_channel",
                "Business Insights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Monthly reports and business analytics"
            }

            // Reminders
            val remindersChannel = NotificationChannel(
                "reminders_channel",
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Customer birthdays and other reminders"
            }

            notificationManager.createNotificationChannels(
                listOf(
                    alertsChannel, insightsChannel, remindersChannel
                )
            )
        }
    }

    /**
     * Send a notification with a RemoteMessage.Notification object
     */
    private fun sendNotification(notification: RemoteMessage.Notification, data: Map<String, String> = mapOf()) {
        try {
            // Choose channel based on notification type
            val channelId = when(data["type"]) {
                "PAYMENT_OVERDUE", "CREDIT_LIMIT", "LOW_STOCK" -> "alerts_channel"
                "BUSINESS_OVERVIEW" -> "insights_channel"
                "BIRTHDAY", "ANNIVERSARY" -> "reminders_channel"
                else -> "insights_channel"
            }

            // Generate specific notification ID
            val notificationId = generateNotificationId(data)



            // Create intent based on notification type
            val type = data["type"] ?: "GENERAL"
            val intent = createIntentForNotification(type, data)

            val pendingIntent = PendingIntent.getActivity(
                this, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

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

            // Show the notification with permission check
            if (checkNotificationPermission()) {
                notificationManager.notify(notificationId, notificationBuilder.build())
            } else {
                Log.w(TAG, "Notification not shown: POST_NOTIFICATIONS permission not granted")
            }

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

            // Create a notification builder
            val notificationBuilder = createNotificationBuilder(
                title = title,
                message = message,
                type = notificationType,
                data = data
            )

            // Generate a unique ID for this notification
            val notificationId = generateNotificationId(data)

            // Get group key for potential grouping
            val groupKey = getGroupKeyForType(notificationType)

            // Show the notification with permission check
            if (checkNotificationPermission()) {
                notificationManager.notify(notificationId, notificationBuilder.build())
            } else {
                Log.w(TAG, "Notification not shown: POST_NOTIFICATIONS permission not granted")
            }

            // If part of a group, check if we need a summary notification
            if (groupKey != null) {
                coroutineScope.launch {
                    updateGroupSummary(groupKey)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing data message", e)
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
        val channelId = when (type) {
            "PAYMENT_OVERDUE", "CREDIT_LIMIT", "LOW_STOCK" -> "alerts_channel"
            "BUSINESS_OVERVIEW" -> "insights_channel"
            "BIRTHDAY", "ANNIVERSARY" -> "reminders_channel"
            else -> "insights_channel"
        }

        // Create intent based on notification type
        val intent = createIntentForNotification(type, data)
        val notificationId = generateNotificationId(data)

        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
     * Add action buttons to a notification based on type
     */
    private fun addActionsToNotification(
        builder: NotificationCompat.Builder,
        type: String,
        data: Map<String, String>
    ) {
        when (type) {
            "PAYMENT_DUE", "PAYMENT_OVERDUE" -> {
                // Add a "Pay Now" action
                val payIntent = createIntentForNotification("payment_action", data)
                val payPendingIntent = PendingIntent.getActivity(
                    this, 1, payIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    R.drawable.mdi__currency_inr,
                    "Pay Now",
                    payPendingIntent
                )
            }

            "LOW_STOCK" -> {
                // Add a "View Item" action
                val restockIntent = createIntentForNotification("item_detail", data)
                val restockPendingIntent = PendingIntent.getActivity(
                    this, 2, restockIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    R.drawable.material_symbols_warning_rounded,
                    "View Item",
                    restockPendingIntent
                )
            }

            "CREDIT_LIMIT" -> {
                // Add a "View Customer" action
                val customerIntent = createIntentForNotification("customer_detail", data)
                val customerPendingIntent = PendingIntent.getActivity(
                    this, 3, customerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    R.drawable.material_symbols_warning_rounded,
                    "View Customer",
                    customerPendingIntent
                )
            }
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
                    createGroupSummaryNotification(groupKey, count)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating group summary", e)
        }
    }

    /**
     * Create a summary notification for a group
     */
    private fun createGroupSummaryNotification(groupKey: String, notificationCount: Int) {
        val title: String
        val message: String

        when (groupKey) {
            GROUP_KEY_PAYMENTS -> {
                title = "Payment Notifications"
                message = "$notificationCount payment notifications"
            }
            GROUP_KEY_INVENTORY -> {
                title = "Inventory Alerts"
                message = "$notificationCount inventory alerts"
            }
            GROUP_KEY_CUSTOMERS -> {
                title = "Customer Events"
                message = "$notificationCount customer events"
            }
            else -> {
                title = "Notifications"
                message = "$notificationCount new notifications"
            }
        }

        // Create the summary notification
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "notification_list")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 100 + groupKey.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Determine channel for summary
        val channelId = when (groupKey) {
            GROUP_KEY_PAYMENTS -> "alerts_channel"
            GROUP_KEY_INVENTORY -> "alerts_channel"
            GROUP_KEY_CUSTOMERS -> "reminders_channel"
            else -> "insights_channel"
        }

        val summaryNotification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.mingcute__notification_fill)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Get notification manager
        val notificationManager = NotificationManagerCompat.from(this)

        // Show the notification with permission check
        if (checkNotificationPermission()) {
            notificationManager.notify(groupKey.hashCode(), summaryNotification)
        } else {
            Log.w(TAG, "Summary notification not shown: POST_NOTIFICATIONS permission not granted")
        }
    }

    /**
     * Create an intent with appropriate navigation for a notification type
     */
    private fun createIntentForNotification(type: String, data: Map<String, String>): Intent {
        // Create base intent with flags for proper navigation
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Add navigation based on notification type
        when (type) {
            "PAYMENT_DUE", "PAYMENT_OVERDUE", "payment_action" -> {
                intent.putExtra("navigate_to", "invoice_detail")
                intent.putExtra("invoiceId", data["invoiceId"])
            }

            "CREDIT_LIMIT", "customer_detail" -> {
                intent.putExtra("navigate_to", "customer_detail")
                intent.putExtra("customerId", data["customerId"])
            }

            "LOW_STOCK", "item_detail" -> {
                intent.putExtra("navigate_to", "item_detail")
                intent.putExtra("itemId", data["itemId"])
            }

            "BIRTHDAY", "ANNIVERSARY" -> {
                intent.putExtra("navigate_to", "customer_detail")
                intent.putExtra("customerId", data["customerId"])
            }

            else -> {
                intent.putExtra("navigate_to", "notification_list")
            }
        }

        return intent
    }

    /**
     * Generate a unique notification ID based on notification data
     */
    private fun generateNotificationId(data: Map<String, String>): Int {
        return when {
            data.containsKey("invoiceId") -> "invoice_${data["invoiceId"]}".hashCode()
            data.containsKey("itemId") -> "item_${data["itemId"]}".hashCode()
            data.containsKey("customerId") -> "customer_${data["customerId"]}".hashCode()
            else -> System.currentTimeMillis().toInt()
        }
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

                // Update the token in Firestore
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(phoneNumber)
                    .update("fcmToken", token)
                    .await()

                Log.d(TAG, "FCM token successfully updated in Firestore")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating FCM token", e)
            }
        }
    }
    /**
     * Check if we have notification permission
     */
    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission is automatically granted on SDK < 33
            true
        }
    }
}