package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Helper class to schedule periodic notification checks
 */
object NotificationScheduler {

    private const val NOTIFICATION_WORK_NAME = "credit_limit_notification_check"

    /**
     * Schedule periodic credit limit checks
     * @param context Application context
     * @param repeatInterval Interval in hours (default: 12 hours)
     */
    fun scheduleNotificationCheck(context: Context, repeatInterval: Long = 12) {
        // Define network constraints - we want to run this only when connected
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create periodic work request
        val notificationWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            repeatInterval,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        // Enqueue the work - replace if already exists
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NOTIFICATION_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            notificationWorkRequest
        )
    }

    /**
     * Cancel scheduled notification checks
     * @param context Application context
     */
    fun cancelNotificationChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NOTIFICATION_WORK_NAME)
    }
}