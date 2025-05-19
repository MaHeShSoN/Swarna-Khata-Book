package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.jewelrypos.swarnakhatabook.BottomSheet.PinEntryBottomSheet
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.AppUpdateHelper
import com.jewelrypos.swarnakhatabook.Utilitys.DataWipeManager
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationChannelManager
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationPermissionHelper
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationScheduler
import com.jewelrypos.swarnakhatabook.Utilitys.PinCheckResult
import com.jewelrypos.swarnakhatabook.Utilitys.PinEntryDialogHandler
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var wasInBackground = false
    private lateinit var appUpdateHelper: AppUpdateHelper

    // Request permission launcher for notifications (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.d("MainActivity", "Notification permission denied")
            Toast.makeText(
                this,
                getString(R.string.notification_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun applyAppLocale(languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved locale before super.onCreate
        val preferences = SecurePreferences.getInstance(this)
        preferences.getString("selected_language", null)?.let { languageCode ->
            applyAppLocale(languageCode)
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences
        sharedPreferences = SecurePreferences.getInstance(this)

        // Initialize SessionManager
        SessionManager.initialize(this)

        // Initialize ShopManager
        ShopManager.initialize(this)

        // Request notification permission if needed (for Android 13+)
        requestNotificationPermission()

        initializeAppUpdateHelper()

        // Check subscription status when app starts
        checkSubscriptionStatus()

        initializeNotificationSystem()

        handleNotificationNavigation(intent)
    }

    /**
     * Initialize the app update helper and check for updates
     */
    private fun initializeAppUpdateHelper() {
        appUpdateHelper = AppUpdateHelper(this)
        appUpdateHelper.attachToActivity(this)

        // Check for updates on app start
        appUpdateHelper.checkForUpdates()
    }

    /**
     * Request notification permission for Android 13+
     */
    private fun requestNotificationPermission() {
        if (!NotificationPermissionHelper.hasNotificationPermission(this)) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationNavigation(intent)
    }

    private fun handleNotificationNavigation(intent: Intent) {
        try {
            val navigateTo = intent.getStringExtra("navigate_to") ?: return

            val navController = findNavController(R.id.nav_host_fragment)

            // Make sure we're on the main screen before attempting deeper navigation
            if (navController.currentDestination?.id != R.id.mainScreenFragment) {
                // Navigate to mainScreenFragment first
                navController.navigate(R.id.mainScreenFragment)
            }

            // Add a listener to detect when main screen is loaded
            navController.addOnDestinationChangedListener(object :
                NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?
                ) {
                    if (destination.id == R.id.mainScreenFragment) {
                        // Now perform the specific navigation based on notification type
                        when (navigateTo) {
                            "invoice_detail" -> {
                                val invoiceId = intent.getStringExtra("invoiceId") ?: return
                                val bundle = Bundle().apply {
                                    putString("invoiceId", invoiceId)
                                }

                                // Check if action is available before navigating
                                if (controller.currentDestination?.getAction(R.id.action_mainScreenFragment_to_invoiceDetailFragment) != null) {
                                    controller.navigate(
                                        R.id.action_mainScreenFragment_to_invoiceDetailFragment,
                                        bundle
                                    )
                                }
                            }

                            "customer_detail" -> {
                                val customerId = intent.getStringExtra("customerId") ?: return
                                val bundle = Bundle().apply {
                                    putString("customerId", customerId)
                                }

                                // Check if action is available before navigating
                                if (controller.currentDestination?.getAction(R.id.action_mainScreenFragment_to_customerDetailFragment) != null) {
                                    controller.navigate(
                                        R.id.action_mainScreenFragment_to_customerDetailFragment,
                                        bundle
                                    )
                                }
                            }

                            "item_detail" -> {
                                val itemId = intent.getStringExtra("itemId") ?: return
                                val bundle = Bundle().apply {
                                    putString("itemId", itemId)
                                }

                                // Check if action is available before navigating
                                if (controller.currentDestination?.getAction(R.id.action_mainScreenFragment_to_itemDetailFragment) != null) {
                                    controller.navigate(
                                        R.id.action_mainScreenFragment_to_itemDetailFragment,
                                        bundle
                                    )
                                }
                            }

                            "notification_list" -> {
                                // Check if action is available before navigating
                                if (controller.currentDestination?.getAction(R.id.action_mainScreenFragment_to_notificationFragment) != null) {
                                    controller.navigate(R.id.action_mainScreenFragment_to_notificationFragment)
                                }
                            }
                        }

                        // Remove the listener after navigation is complete
                        controller.removeOnDestinationChangedListener(this)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling notification navigation", e)
        }
    }

    private fun checkSubscriptionStatus() {
        lifecycleScope.launch {
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

            try {
                // Use the centralized subscription check method
                when (val status = subscriptionManager.checkSubscriptionStatus()) {
                    is UserSubscriptionManager.SubscriptionStatus.ActiveSubscription -> {
                        // User has active subscription, do nothing
                    }
                    is UserSubscriptionManager.SubscriptionStatus.ActiveTrial -> {
                        // Show reminder dialog if less than 3 days remaining
                        if (status.daysRemaining <= 3) {
                            showTrialReminderDialog(status.daysRemaining)
                        }
                    }
                    is UserSubscriptionManager.SubscriptionStatus.GracePeriod -> {
                        // Show grace period dialog if not already notified
                        if (!subscriptionManager.hasGracePeriodBeenNotified()) {
                            showGracePeriodDialog(status.daysRemaining)
                            subscriptionManager.markGracePeriodNotified()
                        }
                    }
                    is UserSubscriptionManager.SubscriptionStatus.Expired -> {
                        // Trial expired and not in grace period
                        showTrialExpiredDialog()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking subscription status: ${e.message}", e)
                // Don't show error to user, fail silently to prevent blocking usage
            }
        }
    }

    private fun showGracePeriodDialog(daysRemaining: Int) {
        val pluralSuffix = if (daysRemaining > 1) "s" else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.grace_period_title))
            .setMessage(getString(R.string.grace_period_message, daysRemaining, pluralSuffix))
            .setPositiveButton(getString(R.string.upgrade_now)) { _, _ ->
                navigateToUpgradeScreen()
            }
            .setNegativeButton(getString(R.string.remind_later)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showTrialExpiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.trial_expired_title))
            .setMessage(getString(R.string.trial_expired_message))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.upgrade)) { _, _ ->
                // Navigate to upgrade screen
                navigateToUpgradeScreen()
            }
            .setNegativeButton(getString(R.string.log_out)) { _, _ ->
                // Log out the user
                logoutUser()
            }
            .show()
    }

    private fun showTrialReminderDialog(daysRemaining: Int) {
        val pluralSuffix = if (daysRemaining > 1) "s" else ""
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.trial_ending_soon_title))
            .setMessage(getString(R.string.trial_ending_soon_message, daysRemaining, pluralSuffix))
            .setPositiveButton(getString(R.string.upgrade)) { _, _ ->
                // Navigate to upgrade screen
                navigateToUpgradeScreen()
            }
            .setNegativeButton(getString(R.string.remind_later)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun navigateToUpgradeScreen() {
        val intent = Intent(this, UpgradeActivity::class.java)
        startActivity(intent)
    }

    private fun logoutUser() {
        // Clear active shop session
        SessionManager.clearSession(this)
        
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Clear any app-specific data
        clearAppSpecificData()

        // Redirect to the launcher or login screen
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Clear app-specific data on logout, but preserve user's cloud data
     * This only clears local caches without deleting Firestore data
     */
    private fun clearAppSpecificData() {
        try {
            // Clear local databases (without affecting cloud data)
            deleteDatabase("jewelry_app_database.db")
            deleteDatabase("jewelry_pos_cache.db")
            deleteDatabase("metal_item_database.db")
            
            // Clear PIN security data if needed
            val securePrefs = SecurePreferences.getInstance(this)
            securePrefs.edit()
                .remove(PinHashUtil.KEY_PIN_HASH)
                .remove(PinHashUtil.KEY_PIN_SALT)
                .apply()
                
            // Clear Firebase cache without deleting cloud data
            try {
                File(cacheDir, "firebase_firestore.db").delete()
                File(cacheDir, "firestore.sqlite").delete()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error clearing Firebase cache: ${e.message}")
            }
            
            // Clear app cache
            try {
                (application as? SwarnaKhataBook)?.let { 
                    FirebaseFirestore.getInstance().clearPersistence()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error clearing Firestore persistence: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error clearing app-specific data: ${e.message}")
        }
    }

    private fun initializeNotificationSystem() {
        // Schedule periodic notification checks - this ensures they run
        // even if the user doesn't visit the Dashboard fragment
        NotificationScheduler.scheduleNotificationCheck(applicationContext)
    }

    override fun onResume() {
        super.onResume()

        val context = this // Get context once

        // *** FIX: Check if app was in background, PIN exists, AND lock is ENABLED ***
        if (wasInBackground && PinSecurityManager.hasPinBeenSetUp(context) && PinSecurityManager.isPinLockEnabled(
                context
            )
        ) {
            // Only show PIN verification if lock is actually enabled
            showPinVerificationDialog()
        }

        // Reset background flag regardless of PIN check outcome
        wasInBackground = false
        
        // Invalidate options menu to update shop switching visibility
        invalidateOptionsMenu()
    }

    override fun onPause() {
        super.onPause()
        // Set flag to indicate app is going to background
        wasInBackground = true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 123) { // App update request code
            if (resultCode != RESULT_OK) {
                // Update flow failed or was cancelled
                Log.e("MainActivity", "Update flow failed or cancelled: $resultCode")

                // You could check if this was a mandatory update and show a message or retry
                // For now, just log it
            }
        }
    }

    /**
     * Shows PIN verification dialog when app is resumed
     */
    private fun showPinVerificationDialog() {
        // Check security status first
        val securityStatus = PinSecurityManager.checkStatus(this)

        if (securityStatus is PinSecurityStatus.Locked) {
            // Show lockout dialog
            val minutes = (securityStatus.remainingLockoutTimeMs / 60000).toInt() + 1
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.too_many_attempts_title))
                .setMessage(getString(R.string.too_many_attempts_message, minutes))
                .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
                .setCancelable(false)
                .show()
            return
        }

        // Show the full-screen PIN entry
        PinEntryBottomSheet.showPinVerification(
            context = this,
            fragmentManager = supportFragmentManager,
            prefs = sharedPreferences,
            title = getString(R.string.verify_pin),
            reason = getString(R.string.please_enter_your_pin),
            onPinCorrect = {
                // PIN correct, continue app
                wasInBackground = false
            },
            onPinIncorrect = { status ->
                // Handle incorrect PIN based on status
                if (status is PinSecurityStatus.Locked) {
                    val minutes = (status.remainingLockoutTimeMs / 60000).toInt() + 1
                    AlertDialog.Builder(this)
                        .setTitle(getString(R.string.too_many_attempts_title))
                        .setMessage(getString(R.string.too_many_attempts_message, minutes))
                        .setPositiveButton(getString(R.string.ok)) { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                }
            },
            onReversePinEntered = {
                // Reverse PIN entered - trigger data wipe
                DataWipeManager.performEmergencyWipe(applicationContext) {
                    // Restart app after wipe is complete
                    DataWipeManager.restartApp(applicationContext)
                }
            },
            onCancelled = {
                // User cancelled, exit app
                finish()
            }
        )
    }
}
