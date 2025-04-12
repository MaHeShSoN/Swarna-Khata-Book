package com.jewelrypos.swarnakhatabook.Utilitys

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Manages notification channels for the application
 */
object NotificationChannelManager {

    // Channel IDs
    const val CHANNEL_ALERTS = "alerts_channel"
    const val CHANNEL_INSIGHTS = "insights_channel"
    const val CHANNEL_REMINDERS = "reminders_channel"

    /**
     * Creates all notification channels used by the app (for Android O and above)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // High priority alerts
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Important Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical notifications like low stock and payment overdue"
                enableLights(true)
                enableVibration(true)
            }

            // Business insights
            val insightsChannel = NotificationChannel(
                CHANNEL_INSIGHTS,
                "Business Insights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Monthly reports and business analytics"
            }

            // Reminders
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
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
     * Get appropriate channel ID based on notification type
     */
    fun getChannelForType(type: String): String {
        return when (type) {
            "PAYMENT_OVERDUE", "CREDIT_LIMIT", "LOW_STOCK" -> CHANNEL_ALERTS
            "BUSINESS_OVERVIEW", "GENERAL" -> CHANNEL_INSIGHTS
            "BIRTHDAY", "ANNIVERSARY" -> CHANNEL_REMINDERS
            else -> CHANNEL_INSIGHTS
        }
    }
}