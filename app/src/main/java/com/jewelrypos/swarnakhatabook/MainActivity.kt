package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationScheduler
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var wasInBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("jewelry_pos_settings", Context.MODE_PRIVATE)

        setupBiometrics()

        initializeNotificationSystem()
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
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_pin_input, null)
        val pinEditText = dialogView.findViewById<EditText>(R.id.pinEditText)

        builder.setView(dialogView)
            .setTitle("Enter PIN")
            .setMessage("Please enter your PIN to continue")
            .setPositiveButton("Unlock") { _, _ ->
                // Empty, will be set below to avoid automatic dismissal
            }
            .setNegativeButton("Cancel") { _, _ ->
                // User canceled, close the app
                finish()
            }
            .setCancelable(false)

        val dialog = builder.create()
        dialog.show()

        // Override the positive button click to validate PIN
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            val savedPin =
                sharedPreferences.getString("app_lock_pin", "1234") // Default PIN is 1234

            if (enteredPin == savedPin) {
                // PIN is correct
                dialog.dismiss()
                wasInBackground = false
            } else {
                // PIN is incorrect
                pinEditText.error = "Incorrect PIN"
                pinEditText.text.clear()
            }
        }
    }
}