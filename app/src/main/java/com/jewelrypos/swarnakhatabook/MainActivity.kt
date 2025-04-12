package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.jewelrypos.swarnakhatabook.Utilitys.AppUpdateHelper
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationChannelManager
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationPermissionHelper
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationScheduler
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
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
                "Notification permission denied. You may miss important alerts.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences
//        sharedPreferences = getSharedPreferences("jewelry_pos_settings", Context.MODE_PRIVATE)
        sharedPreferences = SecurePreferences.getInstance(this)
        // Request notification permission if needed (for Android 13+)
        requestNotificationPermission()


        initializeAppUpdateHelper()
        // Check subscription status when app starts
        checkSubscriptionStatus()

        setupBiometrics()

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

            // Check if the user is premium
            val isPremium = subscriptionManager.isPremiumUser()

            if (!isPremium) {
                // Check if trial has expired
                if (subscriptionManager.hasTrialExpired()) {
                    // Trial expired - show alert and log out
                    showTrialExpiredDialog()
                } else {
                    // Trial still active - show remaining days if less than 3
                    val daysRemaining = subscriptionManager.getDaysRemaining()
                    if (daysRemaining <= 3) {
                        showTrialReminderDialog(daysRemaining)
                    }
                }
            }
        }
    }

    private fun showTrialExpiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Trial Period Expired")
            .setMessage("Your 10-day trial period has expired. Please upgrade to continue using Swarna Khata Book.")
            .setCancelable(false)
            .setPositiveButton("Upgrade") { _, _ ->
                // Navigate to upgrade screen
                navigateToUpgradeScreen()
            }
            .setNegativeButton("Log Out") { _, _ ->
                // Log out the user
                logoutUser()
            }
            .show()
    }

    private fun showTrialReminderDialog(daysRemaining: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Trial Period Ending Soon")
            .setMessage("Your trial period will expire in $daysRemaining day${if (daysRemaining > 1) "s" else ""}. Upgrade now to continue using all features.")
            .setPositiveButton("Upgrade") { _, _ ->
                // Navigate to upgrade screen
                navigateToUpgradeScreen()
            }
            .setNegativeButton("Remind Later") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun navigateToUpgradeScreen() {
        // In a real app, this would navigate to your subscription/payment screen
        Toast.makeText(this, "Navigate to upgrade screen", Toast.LENGTH_SHORT).show()

        // For testing purposes only - this would be replaced with actual purchase flow
        lifecycleScope.launch {
            val success = SwarnaKhataBook.getUserSubscriptionManager().updatePremiumStatus(true)
            if (success) {
                Toast.makeText(this@MainActivity, "Upgraded to Premium!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logoutUser() {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Clear any app-specific data

        // Redirect to the launcher or login screen
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun initializeNotificationSystem() {
        // Schedule periodic notification checks - this ensures they run
        // even if the user doesn't visit the Dashboard fragment
        NotificationScheduler.scheduleNotificationCheck(applicationContext)
    }

    override fun onResume() {
        super.onResume()

        // Check if app was in background and app lock is enabled
        if (wasInBackground) {
            val isAppLockEnabled = sharedPreferences.getBoolean("app_lock_enabled", false)
            if (isAppLockEnabled) {
                checkBiometricSupport()
            }
        }

        // Reset flag
        wasInBackground = false
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

    private fun setupBiometrics() {
        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Authentication successful, proceed with app
                    wasInBackground = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        // User clicked cancel, close the app
                        finish()
                    } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        // Show error and fall back to PIN
                        Toast.makeText(
                            this@MainActivity,
                            "Authentication error: $errString",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Show PIN fallback
                        showPinFallbackDialog()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        this@MainActivity,
                        "Authentication failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentication Required")
            .setSubtitle("Please authenticate to continue using the app")
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun checkBiometricSupport() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Device supports biometric authentication, show prompt
                biometricPrompt.authenticate(promptInfo)
            }

            else -> {
                // Biometric authentication not available, use PIN fallback
                showPinFallbackDialog()
            }
        }
    }

    private fun showPinFallbackDialog() {
        // First check PIN security status
        val securityStatus = PinSecurityManager.checkStatus(this)

        when (securityStatus) {
            is PinSecurityStatus.Locked -> {
                // Show lockout dialog
                val minutes = (securityStatus.remainingLockoutTimeMs / 60000).toInt() + 1
                AlertDialog.Builder(this)
                    .setTitle("Too Many Failed Attempts")
                    .setMessage("PIN entry has been disabled for $minutes minutes due to multiple failed attempts.")
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
                return
            }

            else -> {
                // Continue with PIN dialog
            }
        }

        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_pin_input, null)
        val pinEditText = dialogView.findViewById<EditText>(R.id.pinEditText)

        // Add warning for limited attempts
        if (securityStatus is PinSecurityStatus.Limited) {
            val messageTextView = dialogView.findViewById<TextInputLayout>(R.id.pin)
            messageTextView.helperText  = "You have ${securityStatus.remainingAttempts} attempts remaining"
            messageTextView.setHelperTextColor(getColorStateList(R.color.red))
            messageTextView.visibility = View.VISIBLE
        }

        builder.setView(dialogView)
            .setTitle("Enter PIN")
            .setMessage("Please enter your PIN to continue")
            .setPositiveButton("Unlock", null) // Set below
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            val savedPin = sharedPreferences.getString("app_lock_pin", null)

            if (savedPin != null && enteredPin == savedPin) {
                // PIN is correct
                dialog.dismiss()
                PinSecurityManager.resetAttempts(this)
                wasInBackground = false
            } else {
                // PIN is incorrect
                val updatedSecurityStatus = PinSecurityManager.recordFailedAttempt(this)

                if (updatedSecurityStatus is PinSecurityStatus.Locked) {
                    dialog.dismiss()
                    // Show lockout dialog
                    val minutes = (updatedSecurityStatus.remainingLockoutTimeMs / 60000).toInt() + 1
                    AlertDialog.Builder(this)
                        .setTitle("Too Many Failed Attempts")
                        .setMessage("PIN entry has been disabled for $minutes minutes due to multiple failed attempts.")
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                } else if (updatedSecurityStatus is PinSecurityStatus.Limited) {
                    pinEditText.error =
                        "Incorrect PIN (${updatedSecurityStatus.remainingAttempts} attempts remaining)"
                    pinEditText.text.clear()
                }
            }
        }
    }
}