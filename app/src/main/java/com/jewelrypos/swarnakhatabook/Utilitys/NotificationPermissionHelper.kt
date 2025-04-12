package com.jewelrypos.swarnakhatabook.Utilitys

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Helper class for handling notification permissions on Android 13+ (API 33+)
 */
class NotificationPermissionHelper {

    companion object {
        /**
         * Checks if notification permission is granted
         * @param context Application context
         * @return true if permission is granted or not required (Android < 13), false otherwise
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // Permission is automatically granted on SDK < 33
                true
            }
        }

        /**
         * Request notification permission if needed
         * @param activity Activity to request permission from
         * @return true if permission is already granted or not required, false if request was triggered
         */
        fun requestNotificationPermissionIfNeeded(activity: Activity): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        100 // Request code
                    )
                    return false
                }
            }
            return true
        }

        /**
         * Setup permission request launcher for Activity/Fragment
         * @param onPermissionResult Callback with permission result
         * @return ActivityResultLauncher to register in Activity/Fragment
         */
        fun createPermissionLauncher(
            fragment: Fragment,
            onPermissionResult: (Boolean) -> Unit
        ): ActivityResultLauncher<String> {
            return fragment.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                onPermissionResult(isGranted)
            }
        }
    }
}