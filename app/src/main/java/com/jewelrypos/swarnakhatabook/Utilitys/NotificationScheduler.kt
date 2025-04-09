package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationWorker
import java.util.concurrent.TimeUnit

/**
 * Helper class to schedule periodic notification checks
 */
object NotificationScheduler {

    private const val NOTIFICATION_WORK_NAME = "business_insights_and_low_stock_check"

    /**
     * Schedule periodic notification checks
     * @param context Application context
     */
    fun scheduleNotificationCheck(context: Context) {
        // Define network constraints - we want to run this only when connected
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create periodic work request - run daily
        val notificationWorkRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
            1, // Repeat every 1 day
            TimeUnit.DAYS
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

    /**
     * Manually trigger a notification check
     * @param context Application context
     */
    fun triggerManualNotificationCheck(context: Context) {
        WorkManager.getInstance(context)
            .enqueue(
                PeriodicWorkRequestBuilder<NotificationWorker>(
                    1,
                    TimeUnit.DAYS
                ).build()
            )
    }
}