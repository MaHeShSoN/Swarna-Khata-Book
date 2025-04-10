package com.jewelrypos.swarnakhatabook.Repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.NotificationPreferences
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Repository for handling notification data and operations
 */
class NotificationRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    // For pagination
    private val pageSize = 20
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false

    // Add a TAG for logging
    companion object {
        private const val TAG = "NotificationRepo"
    }

    /**
     * Gets a paginated list of notifications
     */
    suspend fun getNotifications(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<AppNotification>> {
        return try {
            val phoneNumber = getCurrentUserPhoneNumber()

            // Reset pagination if not loading next page
            if (!loadNextPage) {
                lastDocumentSnapshot = null
                isLastPage = false
            }

            // Return empty list if we've reached the last page
            if (isLastPage) {
                return Result.success(emptyList())
            }

            // Build query with pagination, ordering by creation date descending (newest first)
            var query = firestore.collection("users")
                .document(phoneNumber)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            // Add start after clause if we have a previous page
            if (loadNextPage && lastDocumentSnapshot != null) {
                query = query.startAfter(lastDocumentSnapshot!!)
            }

            // Get data with the specified source
            val snapshot = query.get(source).await()

            // Update pagination state
            if (snapshot.documents.size < pageSize) {
                isLastPage = true
            }

            // Save the last document for next page query
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
            }

            // Handle backward compatibility - try to convert from old PaymentNotification format
            val notifications = snapshot.documents.mapNotNull { doc ->
                try {
                    // First try to convert to the new model directly
                    doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    // If that fails, try the old model and convert
                    try {
                        val oldModel = doc.toObject(PaymentNotification::class.java)
                        if (oldModel != null) {
                            convertToAppNotification(oldModel, doc.id)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting notification", e)
                        null
                    }
                }
            }

            Result.success(notifications)
        } catch (e: Exception) {
            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                return getNotifications(loadNextPage, Source.SERVER)
            }
            Log.e(TAG, "Error getting notifications", e)
            Result.failure(e)
        }
    }

    /**
     * Gets unread notification count
     */
    suspend fun getUnreadNotificationCount(): Result<Int> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        val count = firestore.collection("users")
            .document(phoneNumber)
            .collection("notifications")
            .whereEqualTo("status", NotificationStatus.UNREAD.name)
            .get()
            .await()
            .size()

        Result.success(count)
    } catch (e: Exception) {
        Log.e(TAG, "Error getting unread count", e)
        Result.failure(e)
    }

    /**
     * Marks a notification as read
     */
    suspend fun markAsRead(notificationId: String): Result<Unit> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        firestore.collection("users")
            .document(phoneNumber)
            .collection("notifications")
            .document(notificationId)
            .update(
                mapOf(
                    "status" to NotificationStatus.READ.name,
                    "readAt" to com.google.firebase.Timestamp.now()
                )
            )
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error marking notification as read", e)
        Result.failure(e)
    }

    /**
     * Records that action was taken on a notification
     */
    suspend fun markActionTaken(notificationId: String): Result<Unit> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        firestore.collection("users")
            .document(phoneNumber)
            .collection("notifications")
            .document(notificationId)
            .update("actionTaken", true)
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error marking action taken", e)
        Result.failure(e)
    }

    /**
     * Deletes a notification
     */
    suspend fun deleteNotification(notificationId: String): Result<Unit> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        firestore.collection("users")
            .document(phoneNumber)
            .collection("notifications")
            .document(notificationId)
            .delete()
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting notification", e)
        Result.failure(e)
    }

    /**
     * Creates a new notification
     */
    suspend fun createNotification(notification: AppNotification): Result<String> {
        return try {
            val phoneNumber = getCurrentUserPhoneNumber()

            // Check if we should send this notification based on user preferences
            if (!shouldSendNotification(notification.type)) {
                return Result.success("Notification disabled by user preferences")
            }

            val notificationRef = firestore.collection("users")
                .document(phoneNumber)
                .collection("notifications")
                .document()

            // Always use the Firestore document ID for the notification ID
            val notificationToSave = notification.copy(id = notificationRef.id)

            notificationRef.set(notificationToSave).await()
            Result.success(notificationRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification", e)
            Result.failure(e)
        }
    }

    /**
     * Gets notification preferences for the current user
     */
    suspend fun getNotificationPreferences(): Result<NotificationPreferences> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        val prefsDoc = firestore.collection("users")
            .document(phoneNumber)
            .collection("settings")
            .document("notifications")
            .get()
            .await()

        if (prefsDoc.exists()) {
            val prefs = prefsDoc.toObject(NotificationPreferences::class.java)
                ?: NotificationPreferences()
            Result.success(prefs)
        } else {
            // Default to all enabled if not set
            Result.success(NotificationPreferences())
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error getting notification preferences", e)
        Result.failure(e)
    }

    /**
     * Updates notification preferences for the current user
     */
    suspend fun updateNotificationPreferences(preferences: NotificationPreferences): Result<Unit> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        firestore.collection("users")
            .document(phoneNumber)
            .collection("settings")
            .document("notifications")
            .set(preferences)
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error updating notification preferences", e)
        Result.failure(e)
    }

    /**
     * Check if a notification of specific type should be sent based on user preferences
     */
    suspend fun shouldSendNotification(type: NotificationType): Boolean {
        return try {
            val prefs = getNotificationPreferences().getOrNull() ?: return true

            when (type) {
                NotificationType.PAYMENT_DUE -> prefs.paymentDue
                NotificationType.PAYMENT_OVERDUE -> prefs.paymentOverdue
                NotificationType.CREDIT_LIMIT -> prefs.creditLimit
                NotificationType.BIRTHDAY -> prefs.customerBirthday
                NotificationType.ANNIVERSARY -> prefs.customerAnniversary
                NotificationType.GENERAL -> {
                    // For GENERAL type, we need to check the context
                    // Default to business insights preference
                    prefs.businessInsights
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification preferences", e)
            true // Default to sending if there's an error
        }
    }

    /**
     * Helper to convert from the old notification model to the new one
     * Safely handles null fields
     */
    private fun convertToAppNotification(
        notification: PaymentNotification,
        id: String
    ): AppNotification {
        return AppNotification(
            id = notification.id.ifEmpty { id },
            customerId = notification.customerId,
            customerName = notification.customerName,
            title = notification.title,
            message = notification.message,
            type = notification.type,
            status = notification.status,
            priority = notification.priority,
            amount = notification.amount,
            creditLimit = notification.creditLimit,
            currentBalance = notification.currentBalance,
            createdAt = notification.createdAt,
            readAt = notification.readAt,
            actionTaken = notification.actionTaken
        )
    }

    /**
     * Gets a list of active notifications for a group (used for grouping summary)
     */
    suspend fun getActiveNotificationsInGroup(groupKey: String): Result<List<AppNotification>> {
        return try {
            val phoneNumber = getCurrentUserPhoneNumber()

            val query = when (groupKey) {
                "group_payments" -> {
                    firestore.collection("users")
                        .document(phoneNumber)
                        .collection("notifications")
                        .whereIn("type", listOf(NotificationType.PAYMENT_DUE.name, NotificationType.PAYMENT_OVERDUE.name))
                        .whereEqualTo("status", NotificationStatus.UNREAD.name)
                }
                "group_inventory" -> {
                    // For inventory notifications (assuming some have a special field/value)
                    firestore.collection("users")
                        .document(phoneNumber)
                        .collection("notifications")
                        .whereEqualTo("type", NotificationType.GENERAL.name)
                        .whereNotEqualTo("relatedItemId", null)
                        .whereEqualTo("status", NotificationStatus.UNREAD.name)
                }
                "group_customers" -> {
                    firestore.collection("users")
                        .document(phoneNumber)
                        .collection("notifications")
                        .whereIn("type", listOf(NotificationType.BIRTHDAY.name, NotificationType.ANNIVERSARY.name))
                        .whereEqualTo("status", NotificationStatus.UNREAD.name)
                }
                else -> {
                    // Default query for any other group
                    firestore.collection("users")
                        .document(phoneNumber)
                        .collection("notifications")
                        .whereEqualTo("status", NotificationStatus.UNREAD.name)
                }
            }

            val snapshot = query.get().await()
            val notifications = snapshot.toObjects(AppNotification::class.java)

            Result.success(notifications)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active notifications in group", e)
            Result.failure(e)
        }
    }

    // Helper to get current user's phone number for document path
    private fun getCurrentUserPhoneNumber(): String {
        val currentUser = auth.currentUser ?: throw UserNotAuthenticatedException("User not authenticated.")
        return currentUser.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")
    }

    // Exception classes
    class UserNotAuthenticatedException(message: String) : Exception(message)
    class PhoneNumberInvalidException(message: String) : Exception(message)
}