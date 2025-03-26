package com.jewelrypos.swarnakhatabook.Repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.roundToInt

class NotificationRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    // For pagination
    private val pageSize = 20
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false

    // Get current user's phone number for document path
    private fun getCurrentUserPhoneNumber(): String {
        val currentUser = auth.currentUser ?: throw UserNotAuthenticatedException("User not authenticated.")
        return currentUser.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")
    }


    /**
     * Gets a paginated list of notifications
     */
    suspend fun getNotifications(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<PaymentNotification>> {
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

            val notifications = snapshot.toObjects(PaymentNotification::class.java)
            Result.success(notifications)
        } catch (e: Exception) {
            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                return getNotifications(loadNextPage, Source.SERVER)
            }
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
        Result.failure(e)
    }
}