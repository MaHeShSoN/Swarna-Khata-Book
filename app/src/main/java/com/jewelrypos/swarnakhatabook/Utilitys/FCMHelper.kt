package com.jewelrypos.swarnakhatabook.Utilitys

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FCMHelper {
    private const val TAG = "FCMHelper"

    /**
     * Get the current FCM token
     * @return Result containing the token if successful, or exception if failed
     */
    suspend fun getCurrentToken(): Result<String> {
        return try {
            val token = FirebaseMessaging.getInstance().token.await()
            Log.d(TAG, "FCM Token: $token")
            Result.success(token)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token", e)
            Result.failure(e)
        }
    }

    /**
     * Print the FCM token to logcat
     * This is useful for debugging purposes
     */
    fun printTokenToLogcat() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d(TAG, "FCM Token for debugging: $token")
                    Log.d(TAG, "===========================================")
                    Log.d(TAG, "COPY YOUR FCM TOKEN FROM HERE:")
                    Log.d(TAG, token)
                    Log.d(TAG, "===========================================")
                } else {
                    Log.e(TAG, "Failed to get FCM token", task.exception)
                }
            }
    }
} 