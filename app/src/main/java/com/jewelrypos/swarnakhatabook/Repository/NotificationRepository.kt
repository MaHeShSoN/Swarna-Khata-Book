package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.DataClasses.NotificationPreferences
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/**
 * Repository for handling notification data and operations
 */
class NotificationRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context? = null
) {
    // For pagination
    private val pageSize = 20
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false

    // Add a TAG for logging
    companion object {
        private const val TAG = "NotificationRepo"

        // Define a time threshold for duplicate notification prevention (24 hours)
        private const val DUPLICATE_CHECK_HOURS = 24
    }

    /**
     * Gets a paginated list of notifications for the active shop
     */
    suspend fun getNotifications(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT,
        PAGE_SIZE: Int
    ): Result<List<AppNotification>> {
        return try {
            val activeShopId = getActiveShopId()
            if (activeShopId.isNullOrEmpty()) {
                Log.w(TAG, "No active shop ID available")
                return Result.success(emptyList())
            }

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
            var query = firestore.collection("shopData")
                .document(activeShopId)
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
                return getNotifications(loadNextPage, Source.SERVER, PAGE_SIZE)
            }
            Log.e(TAG, "Error getting notifications", e)
            Result.failure(e)
        }
    }

    /**
     * Gets unread notification count for the active shop
     */
    suspend fun getUnreadNotificationCount(): Result<Int> {
        return try {
            val activeShopId = getActiveShopId()
            if (activeShopId.isNullOrEmpty()) {
                Log.w(TAG, "No active shop ID available")
                return Result.success(0)
            }

            val count = firestore.collection("shopData")
                .document(activeShopId)
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
    }

    suspend fun getUnreadNotificationCountSafe(): Int {
        return try {
            val activeShopId = getActiveShopId()
            if (activeShopId.isNullOrEmpty()) {
                Log.w(TAG, "No active shop ID available")
                return 0
            }

            val count = firestore.collection("shopData")
                .document(activeShopId)
                .collection("notifications")
                .whereEqualTo("status", NotificationStatus.UNREAD.name)
                .get()
                .await()
                .size()

            count
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Just re-throw cancellation exceptions
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting unread count", e)
            // Return -1 to indicate error
            -1
        }
    }

    /**
     * Marks a notification as read
     */
    suspend fun markAsRead(notificationId: String, shopId: String? = null): Result<Unit> {
        return try {
            val targetShopId = shopId ?: getActiveShopId()
            if (targetShopId.isNullOrEmpty()) {
                Log.w(TAG, "No shop ID available")
                return Result.failure(Exception("No shop ID available"))
            }

            firestore.collection("shopData")
                .document(targetShopId)
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
    }

    /**
     * Records that action was taken on a notification
     */
    suspend fun markActionTaken(notificationId: String, shopId: String? = null): Result<Unit> {
        return try {
            val targetShopId = shopId ?: getActiveShopId()
            if (targetShopId.isNullOrEmpty()) {
                Log.w(TAG, "No shop ID available")
                return Result.failure(Exception("No shop ID available"))
            }

            firestore.collection("shopData")
                .document(targetShopId)
                .collection("notifications")
                .document(notificationId)
                .update("actionTaken", true)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking action taken", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a notification
     */
    suspend fun deleteNotification(notificationId: String, shopId: String? = null): Result<Unit> {
        return try {
            val targetShopId = shopId ?: getActiveShopId()
            if (targetShopId.isNullOrEmpty()) {
                Log.w(TAG, "No shop ID available")
                return Result.failure(Exception("No shop ID available"))
            }

            firestore.collection("shopData")
                .document(targetShopId)
                .collection("notifications")
                .document(notificationId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting notification", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a new notification with duplicate prevention
     * @return Result with the notification ID if successful, or a message if the notification was skipped
     */
    suspend fun createNotification(notification: AppNotification, shopId: String? = null): Result<String> {
        return try {
            val targetShopId = shopId ?: getActiveShopId()
            if (targetShopId.isNullOrEmpty()) {
                Log.w(TAG, "No shop ID available")
                return Result.failure(Exception("No shop ID available"))
            }
            
            Log.d(TAG, "Creating notification for shop: $targetShopId")

            // Check if we should send this notification based on user preferences
            if (!shouldSendNotification(notification.type)) {
                return Result.success("Notification disabled by user preferences")
            }

            // Check for existing similar notification to prevent duplicates
            if (isDuplicateNotification(targetShopId, notification)) {
                Log.d(TAG, "Skipping duplicate notification: ${notification.title}")
                return Result.success("Duplicate notification skipped")
            }

            val notificationRef = firestore.collection("shopData")
                .document(targetShopId)
                .collection("notifications")
                .document()

            // Always use the Firestore document ID for the notification ID
            val notificationToSave = notification.copy(
                id = notificationRef.id, 
                shopId = targetShopId
            )

            notificationRef.set(notificationToSave).await()
            Result.success(notificationRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a similar notification already exists to prevent duplicates
     */
    private suspend fun isDuplicateNotification(shopId: String, notification: AppNotification): Boolean {
        try {
            // Calculate the time threshold for duplicate checking
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR, -DUPLICATE_CHECK_HOURS)
            val timestamp = com.google.firebase.Timestamp(Date(calendar.timeInMillis))

            // Base query checking type and recency
            var query = firestore.collection("shopData")
                .document(shopId)
                .collection("notifications")
                .whereEqualTo("type", notification.type.name)
                .whereGreaterThan("createdAt", timestamp)

            // Add more specific filters based on notification type
            when (notification.type) {
                NotificationType.PAYMENT_DUE, NotificationType.PAYMENT_OVERDUE -> {
                    // For payment notifications, check specific invoice
                    if (notification.relatedInvoiceId != null) {
                        query = query.whereEqualTo("relatedInvoiceId", notification.relatedInvoiceId)
                    } else if (notification.customerId.isNotEmpty()) {
                        // Or at least the same customer
                        query = query.whereEqualTo("customerId", notification.customerId)
                    }
                }
                NotificationType.CREDIT_LIMIT -> {
                    // For credit limit warnings, check the same customer
                    if (notification.customerId.isNotEmpty()) {
                        query = query.whereEqualTo("customerId", notification.customerId)
                    }
                }
                NotificationType.BIRTHDAY, NotificationType.ANNIVERSARY -> {
                    // For birthdays/anniversaries, check the same customer
                    if (notification.customerId.isNotEmpty()) {
                        query = query.whereEqualTo("customerId", notification.customerId)
                    }
                }
                NotificationType.GENERAL -> {
                    // For general notifications, check by relatedItemId if it exists
                    if (notification.relatedItemId != null) {
                        query = query.whereEqualTo("relatedItemId", notification.relatedItemId)
                    } else if (notification.customerId.isNotEmpty() && notification.customerId != "SYSTEM") {
                        // Or custom ID for system notifications
                        query = query.whereEqualTo("customerId", notification.customerId)
                    }
                }
            }

            // Execute the query
            val querySnapshot = query.get().await()
            return !querySnapshot.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for duplicate notification", e)
            return false // If we can't check, assume it's not a duplicate to be safe
        }
    }

    /**
     * Gets notification preferences for the current user
     */
    suspend fun getNotificationPreferences(): Result<NotificationPreferences> {
        return try {
            val activeShopId = getActiveShopId()
            if (activeShopId.isNullOrEmpty()) {
                Log.w(TAG, "No active shop ID available, using default preferences")
                return Result.success(NotificationPreferences())
            }

            val prefsDoc = firestore.collection("shopData")
                .document(activeShopId)
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
    }

    /**
     * Updates notification preferences for the current user
     */
    suspend fun updateNotificationPreferences(preferences: NotificationPreferences): Result<Unit> {
        return try {
            val activeShopId = getActiveShopId()
            if (activeShopId.isNullOrEmpty()) {
                Log.w(TAG, "No active shop ID available")
                return Result.failure(Exception("No active shop ID available"))
            }

            firestore.collection("shopData")
                .document(activeShopId)
                .collection("settings")
                .document("notifications")
                .set(preferences)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification preferences", e)
            Result.failure(e)
        }
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
    suspend fun getActiveNotificationsInGroup(groupKey: String, shopId: String? = null): Result<List<AppNotification>> {
        return try {
            val targetShopId = shopId ?: getActiveShopId()
            if (targetShopId.isNullOrEmpty()) {
                Log.w(TAG, "No shop ID available")
                return Result.success(emptyList())
            }

            val query = when (groupKey) {
                "group_payments" -> {
                    firestore.collection("shopData")
                        .document(targetShopId)
                        .collection("notifications")
                        .whereIn("type", listOf(NotificationType.PAYMENT_DUE.name, NotificationType.PAYMENT_OVERDUE.name))
                        .whereEqualTo("status", NotificationStatus.UNREAD.name)
                }
                "group_inventory" -> {
                    // For inventory notifications (assuming some have a special field/value)
                    firestore.collection("shopData")
                        .document(targetShopId)
                        .collection("notifications")
                        .whereEqualTo("type", NotificationType.GENERAL.name)
                        .whereNotEqualTo("relatedItemId", null)
                        .whereEqualTo("status", NotificationStatus.UNREAD.name)
                }
                "group_customers" -> {
                    firestore.collection("shopData")
                        .document(targetShopId)
                        .collection("notifications")
                        .whereIn("type", listOf(NotificationType.BIRTHDAY.name, NotificationType.ANNIVERSARY.name))
                        .whereEqualTo("status", NotificationStatus.UNREAD.name)
                }
                else -> {
                    // Default query for any other group
                    firestore.collection("shopData")
                        .document(targetShopId)
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

    /**
     * Gets notifications for a specific shop
     */
    suspend fun getNotificationsForShop(
        shopId: String,
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT,
        PAGE_SIZE: Int
    ): Result<List<AppNotification>> {
        return try {
            Log.d(TAG, "Getting notifications for shop: $shopId")

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
            var query = firestore.collection("shopData")
                .document(shopId)
                .collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())

            // Add start after clause if we have a previous page
            if (loadNextPage && lastDocumentSnapshot != null) {
                query = query.startAfter(lastDocumentSnapshot!!)
            }

            // Get data with the specified source
            val snapshot = query.get(source).await()

            // Update pagination state
            if (snapshot.documents.size < PAGE_SIZE) {
                isLastPage = true
            }

            // Save the last document for next page query
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
            }

            val notifications = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(AppNotification::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting notification", e)
                    null
                }
            }

            Log.d(TAG, "Retrieved ${notifications.size} notifications for shop $shopId")
            Result.success(notifications)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting notifications for shop", e)
            Result.failure(e)
        }
    }

    /**
     * Gets unread notification count for a specific shop
     */
    suspend fun getUnreadNotificationCountForShop(shopId: String): Result<Int> = try {
        val snapshot = firestore.collection("shopData")
            .document(shopId)
            .collection("notifications")
            .whereEqualTo("status", NotificationStatus.UNREAD.name)
            .get()
            .await()

        Result.success(snapshot.size())
    } catch (e: Exception) {
        Log.e(TAG, "Error getting unread count for shop", e)
        Result.failure(e)
    }

    /**
     * Helper to get the active shop ID
     */
    private fun getActiveShopId(): String? {
        if (context == null) {
            Log.w(TAG, "Context is null, cannot get active shop ID")
            return null
        }
        
        return SessionManager.getActiveShopId(context)
    }

    // Exception classes
    class UserNotAuthenticatedException(message: String) : Exception(message)
    class PhoneNumberInvalidException(message: String) : Exception(message)
    class ShopNotSelectedException(message: String) : Exception(message)
}