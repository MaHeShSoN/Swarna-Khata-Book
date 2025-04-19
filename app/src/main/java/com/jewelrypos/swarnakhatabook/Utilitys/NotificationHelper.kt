package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NotificationHelper {
    private const val TAG = "NotificationHelper"

    /**
     * Send an app update notification
     */
    fun sendAppUpdateNotification(
        context: Context,
        title: String,
        message: String,
        playStoreUrl: String? = null
    ) {
        val repository = NotificationRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance(),
            context
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notification = AppNotification(
                    title = title,
                    message = message,
                    type = NotificationType.APP_UPDATE,
                    priority = NotificationPriority.HIGH,
                    actionUrl = playStoreUrl
                )

                repository.createNotification(notification).fold(
                    onSuccess = { id ->
                        Log.d(TAG, "App update notification sent successfully: $id")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error sending app update notification", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendAppUpdateNotification", e)
            }
        }
    }

    /**
     * Send a general customer notification
     */
    fun sendCustomerNotification(
        context: Context,
        title: String,
        message: String,
        priority: NotificationPriority = NotificationPriority.NORMAL,
        actionUrl: String? = null
    ) {
        val repository = NotificationRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance(),
            context
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notification = AppNotification(
                    title = title,
                    message = message,
                    type = NotificationType.GENERAL,
                    priority = priority,
                    actionUrl = actionUrl
                )

                repository.createNotification(notification).fold(
                    onSuccess = { id ->
                        Log.d(TAG, "Customer notification sent successfully: $id")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error sending customer notification", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendCustomerNotification", e)
            }
        }
    }

    /**
     * Send a notification to all users of a specific shop
     */
    fun sendShopNotification(
        context: Context,
        shopId: String,
        title: String,
        message: String,
        type: NotificationType = NotificationType.GENERAL,
        priority: NotificationPriority = NotificationPriority.NORMAL,
        actionUrl: String? = null
    ) {
        val repository = NotificationRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance(),
            context
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notification = AppNotification(
                    title = title,
                    message = message,
                    type = type,
                    priority = priority,
                    actionUrl = actionUrl,
                    shopId = shopId
                )

                repository.createNotification(notification, shopId).fold(
                    onSuccess = { id ->
                        Log.d(TAG, "Shop notification sent successfully: $id")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error sending shop notification", e)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendShopNotification", e)
            }
        }
    }
} 