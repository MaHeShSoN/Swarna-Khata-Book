package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository

/**
 * Worker class to check for credit limit notifications in the background
 */
class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "NotificationWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting notification check...")

        try {
            // Get current user
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            // Only proceed if user is logged in
            if (currentUser == null) {
                Log.d(TAG, "No logged in user, skipping notification check")
                return Result.failure()
            }

            // Create repository
            val repository = NotificationRepository(
                FirebaseFirestore.getInstance(),
                auth
            )

            // Check for notifications
            repository.generateCreditLimitNotifications().fold(
                onSuccess = { notificationsCount ->
                    Log.d(TAG, "Notification check completed. Created $notificationsCount notifications")
                    return Result.success()
                },
                onFailure = { exception ->
                    Log.e(TAG, "Error during notification check", exception)
                    return Result.retry()
                }
            )

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during notification check", e)
            return Result.failure()
        }
    }
}