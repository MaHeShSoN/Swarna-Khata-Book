package com.jewelrypos.swarnakhatabook.Utilitys

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.SwarnaKhataBook

/**
 * Helper class to integrate app updates with activity/fragment lifecycle
 */
class AppUpdateHelper(
    private val context: Context,
    private val updateRequestCallback: (() -> Unit)? = null
) : DefaultLifecycleObserver {

    // Create the app update manager (don't store as static field)
    private val appUpdateManager = AppUpdateManager(context)

    // Store reference to activity for update flow
    private var activity: Activity? = null

    /**
     * Attach the helper to an activity
     */
    fun attachToActivity(activity: AppCompatActivity) {
        this.activity = activity
        activity.lifecycle.addObserver(this)

        // Observe update availability
        appUpdateManager.updateAvailabilityStatus.observe(activity) { updateType ->
            handleUpdateAvailability(updateType)
        }
    }

    /**
     * Attach the helper to a fragment
     */
    fun attachToFragment(fragment: Fragment) {
        this.activity = fragment.activity
        fragment.lifecycle.addObserver(this)

        // Observe update availability
        appUpdateManager.updateAvailabilityStatus.observe(fragment.viewLifecycleOwner) { updateType ->
            handleUpdateAvailability(updateType)
        }
    }

    /**
     * Check for app updates
     */
    fun checkForUpdates() {
        appUpdateManager.checkForUpdates()
    }

    /**
     * Handle the update availability status
     */
    private fun handleUpdateAvailability(updateType: Int) {
        when (updateType) {
            AppUpdateManager.UPDATE_TYPE_IMMEDIATE -> {
                showImmediateUpdateDialog()
            }
            AppUpdateManager.UPDATE_TYPE_FLEXIBLE -> {
                showFlexibleUpdateDialog()
            }
            else -> {
                // No update available or already updating
            }
        }

        // Notify callback if provided
        updateRequestCallback?.invoke()
    }

    /**
     * Show dialog for immediate (required) updates
     */
    private fun showImmediateUpdateDialog() {
        activity?.let { activityContext ->
            MaterialAlertDialogBuilder(activityContext)
                .setTitle("Update Required")
                .setMessage(appUpdateManager.getUpdateMessage(true))
                .setCancelable(false)
                .setPositiveButton("Update Now") { dialog, _ ->
                    startImmediateUpdate()
                    dialog.dismiss()
                }
                .show()
        }
    }

    /**
     * Show dialog for flexible (optional) updates
     */
    private fun showFlexibleUpdateDialog() {
        activity?.let { activityContext ->
            MaterialAlertDialogBuilder(activityContext)
                .setTitle("Update Available")
                .setMessage(appUpdateManager.getUpdateMessage(false))
                .setPositiveButton("Update") { dialog, _ ->
                    startFlexibleUpdate()
                    dialog.dismiss()
                }
                .setNegativeButton("Not Now") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    /**
     * Start an immediate update
     */
    private fun startImmediateUpdate() {
        activity?.let { activityContext ->
            appUpdateManager.startUpdate(
                activityContext,
                AppUpdateManager.UPDATE_TYPE_IMMEDIATE
            )
        }
    }

    /**
     * Start a flexible update
     */
    private fun startFlexibleUpdate() {
        activity?.let { activityContext ->
            appUpdateManager.startUpdate(
                activityContext,
                AppUpdateManager.UPDATE_TYPE_FLEXIBLE
            )
        }
    }

    /**
     * Called when the activity/fragment is resumed
     */
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)

        // Complete any flexible update that was downloaded
        activity?.let { activityContext ->
            appUpdateManager.completeFlexibleUpdateIfNeeded(activityContext)
        }
    }

    /**
     * Called when the activity/fragment is destroyed
     */
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        // Clean up resources
        appUpdateManager.cleanup()
        activity = null
    }
}