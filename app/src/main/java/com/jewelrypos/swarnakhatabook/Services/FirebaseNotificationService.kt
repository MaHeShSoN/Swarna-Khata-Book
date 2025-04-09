package com.jewelrypos.swarnakhatabook.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jewelrypos.swarnakhatabook.MainActivity
import com.jewelrypos.swarnakhatabook.R

class FirebaseNotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check if message contains a notification payload
        remoteMessage.notification?.let {
            sendNotification(it)
        }

        // Check if message contains data payload
        remoteMessage.data.isNotEmpty().let {
            handleDataMessage(remoteMessage.data)
        }
    }

    private fun sendNotification(notification: RemoteMessage.Notification) {
        val channelId = "SwarnaKhataBookChannel"
        val channelName = "Business Insights"

        // Create notification manager
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android Oreo and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for business insights and low stock alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create an intent that will be fired when the notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
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
        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notificationBuilder.build()
        )
    }

    private fun handleDataMessage(data: Map<String, String>) {
        // Handle any additional data payload if needed
        // This could include custom processing or additional actions
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