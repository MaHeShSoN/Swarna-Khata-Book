package com.jewelrypos.swarnakhatabook.Utilitys

import android.app.Activity
import android.content.Context
import android.content.IntentSender.SendIntentException
import android.util.Log
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
 * and Firebase Remote Config for version control
 */
class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val REQUEST_CODE_UPDATE = 123

        // Remote config keys
        private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
        private const val KEY_UPDATE_REQUIRED = "update_required"
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
            .setMinimumFetchIntervalInSeconds(3600) // 1 hour, set to lower for testing
            .build()

        firebaseRemoteConfig.setConfigSettingsAsync(configSettings)
        // Get current version code from the package info
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        }

        // Set defaults with the retrieved version code
        firebaseRemoteConfig.setDefaultsAsync(
            mapOf(
                KEY_LATEST_VERSION_CODE to currentVersionCode,
                KEY_UPDATE_REQUIRED to false,
                KEY_UPDATE_MESSAGE to "Please update the app to continue using all features.",
                KEY_FLEXIBLE_UPDATE_MESSAGE to "A new version is available. Would you like to update now?"
            )
        )
    }

    /**
     * Check for updates using both Firebase Remote Config and Play Store
     */
    fun checkForUpdates() {
        // First fetch the latest config
        firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Remote config fetch successful")

                // Get update info from remote config
                val latestVersionCode = firebaseRemoteConfig.getLong(KEY_LATEST_VERSION_CODE).toInt()
                val updateRequired = firebaseRemoteConfig.getBoolean(KEY_UPDATE_REQUIRED)

                // Check current app version against remote config
                // Get current version code
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }

                if (latestVersionCode > currentVersionCode) {
                    Log.d(TAG, "New version available: $latestVersionCode vs current: $currentVersionCode")

                    // Check Play Store for update availability
                    checkPlayStoreUpdate(updateRequired)
                } else {
                    Log.d(TAG, "App is up to date")
                    _updateAvailabilityStatus.value = UPDATE_TYPE_NONE
                }
            } else {
                Log.e(TAG, "Remote config fetch failed", task.exception)

                // Fallback to play store check only
                checkPlayStoreUpdate(false)
            }
        }
    }

    /**
     * Check Play Store for updates
     * @param forceUpdate If true, will suggest immediate update
     */
    private fun checkPlayStoreUpdate(forceUpdate: Boolean) {
        // Create the update task
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        // Register a listener for update state changes if using flexible update
        registerInstallStateListener()

        // Check for update availability
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                // Update is available

                // Determine update type based on stalenessDays and forceUpdate flag
                val updateType = if (forceUpdate || isUpdateHighPriority(appUpdateInfo)) {
                    // Critical update - make it immediate
                    UPDATE_TYPE_IMMEDIATE
                } else {
                    // Non-critical update - make it flexible
                    UPDATE_TYPE_FLEXIBLE
                }

                // Update LiveData with status
                _updateAvailabilityStatus.value = updateType

                Log.d(TAG, "Update available, type: $updateType")
            } else {
                // No update or other state
                _updateAvailabilityStatus.value = UPDATE_TYPE_NONE
                Log.d(TAG, "No update available or already updating: ${appUpdateInfo.updateAvailability()}")
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to check for updates", exception)
            _updateAvailabilityStatus.value = UPDATE_TYPE_NONE
        }
    }

    /**
     * Determines if an update should be considered high priority
     */
    private fun isUpdateHighPriority(appUpdateInfo: AppUpdateInfo): Boolean {
        // Example logic - consider updates more than 7 days old as high priority
        return appUpdateInfo.clientVersionStalenessDays() ?: 0 >= 7
    }

    /**
     * Start the update process
     * @param activity The current activity
     * @param updateType The type of update (AppUpdateType.FLEXIBLE or AppUpdateType.IMMEDIATE)
     */
    fun startUpdate(activity: Activity, updateType: Int) {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                try {
                    val actualUpdateType = when (updateType) {
                        UPDATE_TYPE_IMMEDIATE -> AppUpdateType.IMMEDIATE
                        else -> AppUpdateType.FLEXIBLE
                    }

                    // Check if this update type is allowed
                    if (appUpdateInfo.isUpdateTypeAllowed(actualUpdateType)) {
                        // Start update flow
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            actualUpdateType,
                            activity,
                            REQUEST_CODE_UPDATE
                        )

                        Log.d(TAG, "Update flow started, type: $actualUpdateType")
                    } else {
                        Log.d(TAG, "Update type not allowed: $actualUpdateType")

                        // Try another update type if immediate is not allowed
                        if (actualUpdateType == AppUpdateType.IMMEDIATE &&
                            appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                            appUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                AppUpdateType.FLEXIBLE,
                                activity,
                                REQUEST_CODE_UPDATE
                            )
                            Log.d(TAG, "Falling back to flexible update")
                        }
                    }
                } catch (e: SendIntentException) {
                    Log.e(TAG, "Error launching update flow", e)
                }
            }
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
        }
    }

    /**
     * Show notification that update has been downloaded and is ready to install
     */
    private fun showFlexibleUpdateCompletionNotification(activity: Activity) {
        Snackbar.make(
            activity.findViewById(android.R.id.content),
            "Update downloaded. Install now?",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("INSTALL") { appUpdateManager.completeUpdate() }
            setActionTextColor(activity.getColor(R.color.my_light_primary))
            show()
        }
    }

    /**
     * Register a listener for install state updates
     */
    private fun registerInstallStateListener() {
        // Remove any existing listener
        unregisterInstallStateListener()

        // Create and register new listener
        installStateUpdatedListener = InstallStateUpdatedListener { state: InstallState ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> {
                    // Log that the update has been downloaded
                    Log.d(TAG, "Update download completed")
                }
                InstallStatus.INSTALLED -> {
                    // Log that the update has been installed
                    Log.d(TAG, "Update installed successfully")
                    unregisterInstallStateListener()
                }
                InstallStatus.FAILED -> {
                    Log.e(TAG, "Update failed: ${state.installErrorCode()}")
                    unregisterInstallStateListener()
                }
            }
        }

        // Register the listener
        installStateUpdatedListener?.let {
            appUpdateManager.registerListener(it)
        }
    }

    /**
     * Unregister the install state listener
     */
    private fun unregisterInstallStateListener() {
        installStateUpdatedListener?.let {
            appUpdateManager.unregisterListener(it)
            installStateUpdatedListener = null
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