package com.jewelrypos.swarnakhatabook.Services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jewelrypos.swarnakhatabook.MainActivity
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "MyFirebaseMessaging"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Handle notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "", it.body ?: "", remoteMessage.data)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        try {
            val title = data["title"] ?: return
            val message = data["message"] ?: return
            val type = data["type"] ?: "GENERAL"
            val priority = data["priority"] ?: "NORMAL"
            val actionUrl = data["actionUrl"]
            val shopId = data["shopId"]

            // Create notification object
            val notification = AppNotification(
                title = title,
                message = message,
                type = NotificationType.valueOf(type),
                priority = NotificationPriority.valueOf(priority),
                actionUrl = actionUrl,
                shopId = shopId
            )

            // Store in Firestore and show notification
            val repository = NotificationRepository(
                FirebaseFirestore.getInstance(),
                FirebaseAuth.getInstance(),
                applicationContext
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.createNotification(notification, shopId).fold(
                        onSuccess = { id ->
                            Log.d(TAG, "Notification stored in Firestore: $id")
                            // Show the notification
                            sendNotification(title, message, data)
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Error storing notification", e)
                            // Still show the notification even if storage fails
                            sendNotification(title, message, data)
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling data message", e)
                    // Ensure notification is shown even if there's an error
                    sendNotification(title, message, data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing data message", e)
        }
    }

    private fun sendNotification(title: String, messageBody: String, data: Map<String, String> = mapOf()) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data?.forEach { (key, value) ->
                putExtra(key, value)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.mingcute__notification_fill)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun sendRegistrationToServer(token: String) {
        // Store FCM token in Firestore for the current user
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        
        auth.currentUser?.let { user ->
            firestore.collection("users")
                .document(user.uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "FCM token updated successfully")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating FCM token", e)
                }
        }
    }

    companion object {
        private const val TAG = "MyFirebaseMessaging"
    }
} 