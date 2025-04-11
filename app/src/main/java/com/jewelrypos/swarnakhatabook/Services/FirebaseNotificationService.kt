package com.jewelrypos.swarnakhatabook.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jewelrypos.swarnakhatabook.MainActivity
import com.jewelrypos.swarnakhatabook.R

class FirebaseNotificationService : FirebaseMessagingService() {


    companion object {
        private const val TAG = "FirebaseNotService"
        private const val GROUP_KEY_PAYMENTS = "group_payments"
        private const val GROUP_KEY_INVENTORY = "group_inventory"
        private const val GROUP_KEY_CUSTOMERS = "group_customers"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            super.onMessageReceived(remoteMessage)

            // Check if message contains a notification payload
            remoteMessage.notification?.let {
                sendNotification(it)
            }

            // Check if message contains data payload
            if (remoteMessage.data.isNotEmpty()) {
                handleDataMessage(remoteMessage.data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing FCM message", e)
        }
    }

    internal fun createNotificationChannels() {
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
            val notificationId = when {
                data.containsKey("invoiceId") -> "invoice_${data["invoiceId"]}".hashCode()
                data.containsKey("itemId") -> "item_${data["itemId"]}".hashCode()
                data.containsKey("customerId") -> "customer_${data["customerId"]}".hashCode()
                else -> System.currentTimeMillis().toInt()
            }

            // Create notification manager
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create intent based on notification type
            val type = data["type"] ?: "GENERAL"
            val intent = createIntentForNotification(type, data)

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build the notification
            val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.mingcute__notification_fill)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            // Show the notification
            notificationManager.notify(notificationId, notificationBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification", e)
        }
    }
    private fun handleDataMessage(data: Map<String, String>) {
        try {
            // Determine notification type and create appropriate content
            val notificationType = data["type"] ?: "GENERAL"
            val title = data["title"] ?: "New Notification"
            var message = data["message"] ?: "You have a new notification"

            val notificationBuilder = createNotificationBuilder(
                title = title,
                message = message,
                type = notificationType,
                data = data
            )

            // Generate a unique ID for this notification
            val notificationId = generateNotificationId(data)

            // Show the notification
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notificationBuilder.build())

        } catch (e: Exception) {
            Log.e(TAG, "Error processing data message", e)
        }
    }

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

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Add action buttons for certain notification types
        val actions = mutableListOf<NotificationCompat.Action>()

        when (type) {
            "PAYMENT_DUE", "PAYMENT_OVERDUE" -> {
                // Add a "Pay Now" action
                val payIntent = createIntentForNotification("payment_action", data)
                val payPendingIntent = PendingIntent.getActivity(
                    this, 1, payIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(
                    NotificationCompat.Action(
                        R.drawable.mdi__currency_inr,
                        "Pay Now",
                        payPendingIntent
                    )
                )
            }

            "LOW_STOCK" -> {
                // Add a "Restock" action
                val restockIntent = createIntentForNotification("item_detail", data)
                val restockPendingIntent = PendingIntent.getActivity(
                    this, 2, restockIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(
                    NotificationCompat.Action(
                        R.drawable.material_symbols_warning_rounded,
                        "View Item",
                        restockPendingIntent
                    )
                )
            }
        }

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

        // Add actions
        actions.forEach { action ->
            builder.addAction(action)
        }

        val groupKey = when(type) {
            "PAYMENT_DUE", "PAYMENT_OVERDUE" -> GROUP_KEY_PAYMENTS
            "LOW_STOCK" -> GROUP_KEY_INVENTORY
            "BIRTHDAY", "ANNIVERSARY" -> GROUP_KEY_CUSTOMERS
            else -> null
        }

        // Apply the group if applicable
        if (groupKey != null) {
            builder.setGroup(groupKey)
        }

        return builder
    }

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
            this, 100, intent,
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(groupKey.hashCode(), summaryNotification)
    }

    private fun createIntentForNotification(type: String, data: Map<String, String>): Intent {
        // Create base intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Add navigation based on notification type
        when (type) {
            "PAYMENT_DUE", "PAYMENT_OVERDUE", "payment_action" -> {
                intent.putExtra("navigate_to", "invoice_detail")
                intent.putExtra("invoiceId", data["invoiceId"])
            }

            "CREDIT_LIMIT" -> {
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

    private fun generateNotificationId(data: Map<String, String>): Int {
        return when {
            data.containsKey("invoiceId") -> "invoice_${data["invoiceId"]}".hashCode()
            data.containsKey("itemId") -> "item_${data["itemId"]}".hashCode()
            data.containsKey("customerId") -> "customer_${data["customerId"]}".hashCode()
            else -> System.currentTimeMillis().toInt()
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Here you would typically send the token to your server
        // This is important for sending targeted notifications
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // Implement token registration with your backend
        // This might involve sending the token to your Firestore user document
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            val phoneNumber = user.phoneNumber?.replace("+", "") ?: return

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(phoneNumber)
                .update("fcmToken", token)
        }
    }
}