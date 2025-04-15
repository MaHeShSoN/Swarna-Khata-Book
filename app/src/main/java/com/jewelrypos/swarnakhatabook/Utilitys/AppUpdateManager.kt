package com.jewelrypos.swarnakhatabook.Utilitys

import android.app.Activity
import android.content.Context
import android.content.IntentSender.SendIntentException
import android.os.Build // Import Build
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.jewelrypos.swarnakhatabook.R

/**
 * Utility class to handle app updates using Google Play In-App Update API
 * and Firebase Remote Config for version control (Modified)
 */
class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val REQUEST_CODE_UPDATE = 123

        // Remote config keys
        private const val KEY_LATEST_VERSION_CODE = "latest_version_code" // Still useful for info/logging
        private const val KEY_UPDATE_REQUIRED = "update_required" // Main flag from Remote Config
        private const val KEY_UPDATE_MESSAGE = "update_message"
        private const val KEY_FLEXIBLE_UPDATE_MESSAGE = "flexible_update_message"

        // Update types
        const val UPDATE_TYPE_NONE = 0
        const val UPDATE_TYPE_FLEXIBLE = 1
        const val UPDATE_TYPE_IMMEDIATE = 2
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val firebaseRemoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    private val _updateAvailabilityStatus = MutableLiveData<Int>()
    val updateAvailabilityStatus: LiveData<Int> = _updateAvailabilityStatus

    private var installStateUpdatedListener: InstallStateUpdatedListener? = null

    init {
        // Initialize Firebase Remote Config
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            // *** CHANGE FOR TESTING: Set to 0 or 60 for faster fetches during testing ***
            // *** REMEMBER TO CHANGE BACK to 3600 or higher for PRODUCTION! ***
            .setMinimumFetchIntervalInSeconds(60) // Reduced interval for testing
            .build()

        firebaseRemoteConfig.setConfigSettingsAsync(configSettings)

        // Get current version code from the package info
        val currentVersionCode = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current version code", e)
            -1 // Indicate error
        }


        // Set defaults with the retrieved version code
        firebaseRemoteConfig.setDefaultsAsync(
            mapOf(
                KEY_LATEST_VERSION_CODE to currentVersionCode.toLong(), // Store as Long
                KEY_UPDATE_REQUIRED to false,
                KEY_UPDATE_MESSAGE to "Please update the app to continue using all features.",
                KEY_FLEXIBLE_UPDATE_MESSAGE to "A new version is available. Would you like to update now?"
            )
        )
    }

    /**
     * Check for updates using both Firebase Remote Config (for flags) and Play Store (for availability)
     */
    fun checkForUpdates() {
        // First fetch the latest config to get flags like 'update_required'
        firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            var forceUpdate = false // Default to not forcing update
            if (task.isSuccessful) {
                Log.d(TAG, "Remote config fetch successful")
                // Get the update required flag from remote config
                forceUpdate = firebaseRemoteConfig.getBoolean(KEY_UPDATE_REQUIRED)
                Log.d(TAG, "Remote Config: update_required = $forceUpdate")
            } else {
                Log.e(TAG, "Remote config fetch failed, proceeding with Play Store check without force flag.", task.exception)
                // Proceed without the forceUpdate flag if fetch fails
            }

            // *** CHANGE: Always check Play Store after fetching config ***
            checkPlayStoreUpdate(forceUpdate)

        }
    }

    /**
     * Check Play Store for updates
     * @param forceUpdate If true (usually from Remote Config), will suggest immediate update if possible
     */
    private fun checkPlayStoreUpdate(forceUpdate: Boolean) {
        // Create the update task
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        // Register a listener for update state changes if using flexible update
        registerInstallStateListener()

        // Check for update availability
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            val updateAvailability = appUpdateInfo.updateAvailability()
            Log.d(TAG, "Play Store Update Availability Status: $updateAvailability")

            // *** CHANGE: Handle DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS for testing ***
            if (updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ||
                updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // Update is available or developer triggered (treat as available for testing)

                // Determine update type based on forceUpdate flag and stalenessDays
                // Prioritize forceUpdate flag from Remote Config
                val updateType = if (forceUpdate && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    UPDATE_TYPE_IMMEDIATE
                } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    // If immediate is not forced or not allowed, try flexible
                    // Optionally, you could still force immediate based on staleness here if needed:
                    // else if (isUpdateHighPriority(appUpdateInfo) && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) { UPDATE_TYPE_IMMEDIATE }
                    UPDATE_TYPE_FLEXIBLE
                } else {
                    // Neither type allowed, though availability was reported. Log this edge case.
                    Log.w(TAG, "Update available but neither IMMEDIATE nor FLEXIBLE type is allowed.")
                    UPDATE_TYPE_NONE
                }

                // Update LiveData with status if an update type is determined
                if (updateType != UPDATE_TYPE_NONE) {
                    _updateAvailabilityStatus.value = updateType
                    Log.d(TAG, "Update available, determined type: $updateType")
                } else {
                    _updateAvailabilityStatus.value = UPDATE_TYPE_NONE
                }

            } else {
                // No update or other state (UNKNOWN, UPDATE_NOT_AVAILABLE)
                _updateAvailabilityStatus.value = UPDATE_TYPE_NONE
                Log.d(TAG, "No update available or other status: $updateAvailability")
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to check Play Store for updates", exception)
            _updateAvailabilityStatus.value = UPDATE_TYPE_NONE
        }
    }

    /**
     * Determines if an update should be considered high priority (Example logic)
     * NOTE: This is less critical now as 'forceUpdate' from Remote Config takes precedence.
     */
    private fun isUpdateHighPriority(appUpdateInfo: AppUpdateInfo): Boolean {
        // Example logic - consider updates more than 7 days old as high priority
        return (appUpdateInfo.clientVersionStalenessDays() ?: 0) >= 7
    }

    /**
     * Start the update process
     * @param activity The current activity
     * @param updateType The type of update (AppUpdateManager.UPDATE_TYPE_FLEXIBLE or AppUpdateManager.UPDATE_TYPE_IMMEDIATE)
     */
    fun startUpdate(activity: Activity, updateType: Int) {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            // Double-check availability before starting the flow
            val updateAvailability = appUpdateInfo.updateAvailability()
            if (updateAvailability == UpdateAvailability.UPDATE_AVAILABLE ||
                updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {

                val actualUpdateType = when (updateType) {
                    UPDATE_TYPE_IMMEDIATE -> AppUpdateType.IMMEDIATE
                    else -> AppUpdateType.FLEXIBLE
                }

                // Check if this specific update type is allowed *now*
                if (appUpdateInfo.isUpdateTypeAllowed(actualUpdateType)) {
                    try {
                        // Start update flow
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            actualUpdateType,
                            activity,
                            REQUEST_CODE_UPDATE
                        )
                        Log.d(TAG, "Update flow started, type: $actualUpdateType")
                    } catch (e: SendIntentException) {
                        Log.e(TAG, "Error launching update flow for type $actualUpdateType", e)
                    }
                } else {
                    Log.d(TAG, "Requested update type $actualUpdateType not allowed at this moment.")

                    // Optional Fallback: If IMMEDIATE was requested but not allowed, try FLEXIBLE if it is allowed
                    if (actualUpdateType == AppUpdateType.IMMEDIATE &&
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                AppUpdateType.FLEXIBLE, // Fallback type
                                activity,
                                REQUEST_CODE_UPDATE
                            )
                            Log.d(TAG, "Falling back to flexible update flow")
                        } catch (e: SendIntentException) {
                            Log.e(TAG, "Error launching fallback flexible update flow", e)
                        }
                    }
                }
            } else {
                Log.w(TAG, "Attempted to start update, but current availability is $updateAvailability")
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to get AppUpdateInfo before starting update flow", exception)
        }
    }

    /**
     * Complete a flexible update if needed
     * Should be called from onResume() of activities
     */
    fun completeFlexibleUpdateIfNeeded(activity: Activity) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                Log.d(TAG, "Update downloaded, showing completion notification")
                showFlexibleUpdateCompletionNotification(activity)
            }
        }.addOnFailureListener { exception ->
            // Log error but don't crash the app
            Log.e(TAG, "Failed to check for downloaded update in onResume", exception)
        }
    }

    /**
     * Show notification that update has been downloaded and is ready to install
     */
    private fun showFlexibleUpdateCompletionNotification(activity: Activity) {
        // Ensure the activity is still valid and view is accessible
        try {
            val contentView = activity.findViewById<View>(android.R.id.content)
            if (contentView != null) {
                Snackbar.make(
                    contentView, // Use the root content view
                    "Update downloaded. Install now?",
                    Snackbar.LENGTH_INDEFINITE
                ).apply {
                    setAction("INSTALL") { appUpdateManager.completeUpdate() }
                    // Ensure color resource exists and context is valid
                    try {
                        setActionTextColor(ContextCompat.getColor(activity, R.color.my_light_primary))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting Snackbar action text color", e)
                    }
                    show()
                }
            } else {
                Log.e(TAG, "Cannot show Snackbar, content view is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing Snackbar", e)
        }
    }


    /**
     * Register a listener for install state updates
     */
    private fun registerInstallStateListener() {
        // Remove any existing listener first
        unregisterInstallStateListener()

        // Create and register new listener
        installStateUpdatedListener = InstallStateUpdatedListener { state: InstallState ->
            Log.d(TAG, "Install state updated: ${state.installStatus()}") // Log all state changes
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> {
                    // Log that the update has been downloaded
                    Log.d(TAG, "Update download completed, ready for completion.")
                    // The completion prompt is typically shown in onResume via completeFlexibleUpdateIfNeeded
                }
                InstallStatus.INSTALLED -> {
                    // Log that the update has been installed
                    Log.d(TAG, "Update installed successfully")
                    unregisterInstallStateListener() // Clean up listener once installed
                }
                InstallStatus.FAILED -> {
                    Log.e(TAG, "Update failed with error code: ${state.installErrorCode()}")
                    unregisterInstallStateListener() // Clean up listener on failure
                }
                InstallStatus.CANCELED -> {
                    Log.d(TAG, "Update flow cancelled.")
                    // No need to unregister here, user might retry
                }
                InstallStatus.DOWNLOADING -> {
                    val bytesDownloaded = state.bytesDownloaded()
                    val totalBytesToDownload = state.totalBytesToDownload()
                    Log.d(TAG, "Update downloading: $bytesDownloaded / $totalBytesToDownload")
                    // Optionally update UI with progress here
                }
                InstallStatus.INSTALLING -> {
                    Log.d(TAG, "Update installing...")
                }
                InstallStatus.PENDING -> {
                    Log.d(TAG, "Update pending...")
                }
                InstallStatus.UNKNOWN -> {
                    Log.d(TAG, "Update status unknown.")
                }
            }
        }

        // Register the listener
        installStateUpdatedListener?.let {
            appUpdateManager.registerListener(it)
            Log.d(TAG, "InstallStateUpdatedListener registered.")
        }
    }

    /**
     * Unregister the install state listener
     */
    private fun unregisterInstallStateListener() {
        installStateUpdatedListener?.let {
            appUpdateManager.unregisterListener(it)
            installStateUpdatedListener = null
            Log.d(TAG, "InstallStateUpdatedListener unregistered.")
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        unregisterInstallStateListener()
    }

    /**
     * Get update message from remote config
     */
    fun getUpdateMessage(isImmediate: Boolean): String {
        return if (isImmediate) {
            firebaseRemoteConfig.getString(KEY_UPDATE_MESSAGE)
        } else {
            firebaseRemoteConfig.getString(KEY_FLEXIBLE_UPDATE_MESSAGE)
        }
    }
}
