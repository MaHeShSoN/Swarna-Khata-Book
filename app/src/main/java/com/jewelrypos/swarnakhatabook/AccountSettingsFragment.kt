package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
import com.jewelrypos.swarnakhatabook.databinding.FragmentAccountSettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

class AccountSettingsFragment : Fragment() {
    private var _binding: FragmentAccountSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAccountSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup back navigation
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Initialize SharedPreferences for app settings
        sharedPreferences = requireActivity().getSharedPreferences(
            "jewelry_pos_settings",
            Context.MODE_PRIVATE
        )

        // Initialize biometric components
        setupBiometrics()

        // Setup UI elements with current settings
        setupUI()

        // Setup click listeners for settings options
        setupClickListeners()
    }

    private fun setupBiometrics() {
        executor = ContextCompat.getMainExecutor(requireContext())

        biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Successful authentication, enable app lock
                    enableAppLock(true)
                    updateAppLockUI(true)
                    Toast.makeText(
                        requireContext(),
                        "Biometric authentication successful",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        Toast.makeText(
                            requireContext(),
                            "Authentication error: $errString",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        requireContext(),
                        "Authentication failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Log in using your biometric credential")
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun setupUI() {
        // Set the app lock switch to current setting
        val isAppLockEnabled = sharedPreferences.getBoolean("app_lock_enabled", false)
        binding.switchAppLock.isChecked = isAppLockEnabled
        updateAppLockUI(isAppLockEnabled)

        // Show user information
        val currentUser = FirebaseAuth.getInstance().currentUser
        binding.textUsername.text = currentUser?.phoneNumber ?: "Not signed in"

        // Get shop name from ShopManager to display in UI
        ShopManager.getShop(requireContext()) { shop ->
            if (shop != null) {
                binding.textShopName.text = shop.shopName
            }
        }
    }

    private fun updateAppLockUI(enabled: Boolean) {
        // Update additional UI elements based on app lock state
        binding.textAppLockStatus.text = if (enabled) "Enabled" else "Disabled"
        binding.textAppLockDesc.text = if (enabled) {
            "App lock is currently enabled. You'll need to authenticate each time you open the app."
        } else {
            "Enable app lock to protect your data with biometric authentication."
        }
    }

    private fun setupClickListeners() {
        // App Lock switch
        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // First check if device supports biometric authentication
                checkBiometricSupport()
            } else {
                // Disable app lock
                enableAppLock(false)
                updateAppLockUI(false)
            }
        }

        // Change PIN option
        binding.cardChangePIN.setOnClickListener {
            showChangePINDialog()
        }

        // Change Security Questions
//        binding.cardSecurityQuestions.setOnClickListener {
//            showSecurityQuestionsDialog()
//        }

        // Delete All Data button
        binding.buttonDeleteAllData.setOnClickListener {
            showDeleteDataConfirmationDialog()
        }

        // Logout button
        binding.buttonLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun checkBiometricSupport() {
        val biometricManager = BiometricManager.from(requireContext())
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Device supports biometric authentication, show prompt
                biometricPrompt.authenticate(promptInfo)
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                // Device doesn't support biometric authentication
                binding.switchAppLock.isChecked = false
                Toast.makeText(
                    requireContext(),
                    "Device doesn't support biometric authentication",
                    Toast.LENGTH_LONG
                ).show()
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                // Biometric features are currently unavailable
                binding.switchAppLock.isChecked = false
                Toast.makeText(
                    requireContext(),
                    "Biometric features are currently unavailable",
                    Toast.LENGTH_LONG
                ).show()
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // User hasn't enrolled any biometrics
                binding.switchAppLock.isChecked = false
                Toast.makeText(
                    requireContext(),
                    "No biometric credentials are enrolled. Please set up in system settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun enableAppLock(enable: Boolean) {
        // Save app lock setting to SharedPreferences
        sharedPreferences.edit().putBoolean("app_lock_enabled", enable).apply()
    }

    private fun showChangePINDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_pin_setup, null)

        val currentPinEditText = dialogView.findViewById<TextInputEditText>(R.id.currentPinEditText)
        val newPinEditText = dialogView.findViewById<TextInputEditText>(R.id.newPinEditText)
        val confirmPinEditText = dialogView.findViewById<TextInputEditText>(R.id.confirmPinEditText)
        val currentPinLayout = dialogView.findViewById<TextInputLayout>(R.id.currentPinLayout)

        // Get secure preferences
        val securePrefs = SecurePreferences.getInstance(requireContext())

        // Check if PIN exists
        val hasPinSet = securePrefs.contains("pin_hash") && securePrefs.contains("pin_salt")

        // Hide current PIN field if no PIN is set yet
        if (!hasPinSet) {
            currentPinLayout.visibility = View.GONE
            builder.setTitle("Set PIN")
            builder.setMessage("Please create a PIN for app security")
        } else {
            builder.setTitle("Change PIN")
            builder.setMessage("Enter your current PIN and then set a new one")
        }

        builder.setView(dialogView)
            .setPositiveButton("Save", null) // Will be set below
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        val dialog = builder.create()
        dialog.show()

        // Override the positive button click to validate inputs
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredCurrentPin = currentPinEditText.text.toString()
            val newPin = newPinEditText.text.toString()
            val confirmPin = confirmPinEditText.text.toString()

            var isValid = true

            // Validate current PIN if required
            if (hasPinSet && currentPinLayout.visibility == View.VISIBLE) {
                if (!PinHashUtil.verifyPin(enteredCurrentPin, securePrefs)) {
                    currentPinEditText.error = "Current PIN is incorrect"
                    isValid = false
                }
            }

            // Validate new PIN
            if (newPin.length < 4) {
                newPinEditText.error = "PIN must be at least 4 digits"
                isValid = false
            }

            // Validate confirmation PIN
            if (newPin != confirmPin) {
                confirmPinEditText.error = "PINs do not match"
                isValid = false
            }

            if (isValid) {
                // Start background operation
                lifecycleScope.launch(Dispatchers.IO) {
                    // Store PIN securely
                    PinHashUtil.storePin(newPin, securePrefs)

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(
                            requireContext(),
                            "PIN updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }


    private fun showDeleteDataConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All Data")
            .setMessage("Are you sure you want to delete all your data? This action cannot be undone and all your business data will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                showPinConfirmationForDataDeletion()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPinConfirmationForDataDeletion() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_pin_input, null)
        val pinEditText = dialogView.findViewById<TextInputEditText>(R.id.pinEditText)

        // Add warning text
        val messageTextView = dialogView.findViewById<TextView>(R.id.pin_fallback_reason)
        if (messageTextView != null) {
            messageTextView.text = "This operation will permanently delete all your data"
            messageTextView.setTextColor(requireContext().getColor(R.color.status_unpaid))
            messageTextView.visibility = View.VISIBLE
        }

        builder.setView(dialogView)
            .setTitle("Confirm with PIN")
            .setMessage("Please enter your PIN to confirm data deletion")
            .setPositiveButton("Confirm", null) // Will be set below
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.show()

        // Override the positive button click to validate PIN
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            val securePrefs = SecurePreferences.getInstance(requireContext())

            // Use secure PIN verification
            if (PinHashUtil.verifyPin(enteredPin, securePrefs)) {
                // PIN is correct, proceed with data deletion
                dialog.dismiss()
                deleteAllData()
            } else {
                // Track failed attempt
                val securityStatus = PinSecurityManager.recordFailedAttempt(requireContext())

                when (securityStatus) {
                    is PinSecurityStatus.Locked -> {
                        dialog.dismiss()
                        // Show lockout dialog
                        val minutes = (securityStatus.remainingLockoutTimeMs / 60000).toInt() + 1
                        AlertDialog.Builder(requireContext())
                            .setTitle("Too Many Failed Attempts")
                            .setMessage("PIN entry has been disabled for $minutes minutes.")
                            .setPositiveButton("OK", null)
                            .setCancelable(true)
                            .show()
                    }

                    is PinSecurityStatus.Limited -> {
                        pinEditText.error =
                            "Incorrect PIN (${securityStatus.remainingAttempts} attempts remaining)"
                        pinEditText.text?.clear()
                    }

                    else -> {
                        pinEditText.error = "Incorrect PIN"
                        pinEditText.text?.clear()
                    }
                }
            }
        }
    }

    private fun deleteAllData() {
        // Create and show a detailed progress dialog
        val loadingDialog = showDeletionProgressDialog()

        // Start deleting data
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val userId = auth.currentUser?.phoneNumber?.replace("+", "") ?: ""

        if (userId.isNotEmpty()) {
            // Delete Firestore data
            deleteFirestoreData(userId, loadingDialog)
        } else {
            // No user ID, just clear local data and redirect
            updateDeletionProgress(loadingDialog, "User data", false)
            clearLocalDataAndRedirect(loadingDialog)
        }
    }

    private fun showDeletionProgressDialog(): AlertDialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_deletion_progress, null)

        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val statusTextView = dialogView.findViewById<TextView>(R.id.statusTextView)
        val progressSteps = dialogView.findViewById<LinearLayout>(R.id.progressSteps)

        // Setup progress steps
        val collections =
            listOf("Customers", "Invoices", "Inventory", "Notifications", "Payments", "Settings")
        collections.forEach { collection ->
            val stepView = inflater.inflate(R.layout.item_progress_step, null)
            val stepText = stepView.findViewById<TextView>(R.id.stepText)
            val stepStatus = stepView.findViewById<ImageView>(R.id.stepStatus)

            stepText.text = "Deleting $collection"
            stepStatus.visibility = View.INVISIBLE

            progressSteps.addView(stepView)
        }

        builder.setView(dialogView)
            .setTitle("Deleting Data")
            .setCancelable(false)

        return builder.create().apply { show() }
    }

    private fun updateDeletionProgress(
        dialog: AlertDialog,
        collection: String,
        isSuccess: Boolean
    ) {
        requireActivity().runOnUiThread {
            val progressSteps = dialog.findViewById<LinearLayout>(R.id.progressSteps)
            val statusTextView = dialog.findViewById<TextView>(R.id.statusTextView)

            for (i in 0 until progressSteps!!.childCount) {
                val stepView = progressSteps.getChildAt(i)
                val stepText = stepView.findViewById<TextView>(R.id.stepText)
                val stepStatus = stepView.findViewById<ImageView>(R.id.stepStatus)

                if (stepText.text.toString() == "Deleting $collection") {
                    stepStatus.visibility = View.VISIBLE
                    stepStatus.setImageResource(
                        if (isSuccess) R.drawable.ic_check
                        else R.drawable.tdesign__notification_error_filled
                    )
                    break
                }
            }

            statusTextView!!.text = "Processing $collection..."
        }

    }

    private fun deleteFirestoreData(userId: String, loadingDialog: AlertDialog) {
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(userId)

        // Define collections to delete with verification
        val collections = listOf("customers", "invoices", "inventory", "notifications", "payments", "settings")
        val deletionStatus = mutableMapOf<String, Boolean>()
        collections.forEach { deletionStatus[it] = false }

        // Create a coroutine scope for async operations
        lifecycleScope.launch {
            collections.forEach { collection ->
                try {
                    // Update progress in UI
                    updateDeletionProgress(loadingDialog, collection.capitalize(), false)

                    // Get collection documents
                    val querySnapshot = withContext(Dispatchers.IO) {
                        userDoc.collection(collection).get().await()
                    }

                    if (querySnapshot.isEmpty) {
                        // No documents to delete
                        deletionStatus[collection] = true
                        updateDeletionProgress(loadingDialog, collection.capitalize(), true)
                        checkDeletionComplete(deletionStatus, userDoc, loadingDialog)
                        return@forEach
                    }

                    // Use batches for efficient deletion (Firestore limits: 500 operations per batch)
                    val chunks = querySnapshot.documents.chunked(450)

                    withContext(Dispatchers.IO) {
                        chunks.forEachIndexed { index, chunk ->
                            val batch = firestore.batch()
                            chunk.forEach { document -> batch.delete(document.reference) }

                            try {
                                batch.commit().await()
                                withContext(Dispatchers.Main) {
                                    val statusTextView = loadingDialog.findViewById<TextView>(R.id.statusTextView)
                                    statusTextView?.text = "Deleting $collection (${index+1}/${chunks.size})"
                                }
                                Log.d("DataDeletion", "Deleted ${chunk.size} documents from $collection (batch ${index+1}/${chunks.size})")
                            } catch (e: Exception) {
                                Log.e("DataDeletion", "Error deleting $collection batch: ${e.message}")
                                throw e
                            }
                        }
                    }

                    // Mark collection as successfully deleted
                    deletionStatus[collection] = true
                    updateDeletionProgress(loadingDialog, collection.capitalize(), true)

                } catch (e: Exception) {
                    Log.e("DataDeletion", "Error deleting collection $collection: ${e.message}")
                    // Mark as failed in UI
                    updateDeletionProgress(loadingDialog, collection.capitalize(), false)
                } finally {
                    // Check if all collections are processed
                    checkDeletionComplete(deletionStatus, userDoc, loadingDialog)
                }
            }
        }
    }
    private fun checkDeletionComplete(
        status: Map<String, Boolean>,
        userDoc: DocumentReference,
        loadingDialog: AlertDialog
    ) {
        // Check if all collections are processed
        if (status.values.all { it != null }) { // Allow both true and false, just need a decision made

            // Count success and failures
            val successCount = status.values.count { it }
            val totalCount = status.size

            // Update final status
            requireActivity().runOnUiThread {
                val statusTextView = loadingDialog.findViewById<TextView>(R.id.statusTextView)
                statusTextView?.text = "Completed: $successCount/$totalCount collections processed"
            }

            // All collections processed, now delete the user document
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Update UI
                    withContext(Dispatchers.Main) {
                        val statusTextView = loadingDialog.findViewById<TextView>(R.id.statusTextView)
                        statusTextView?.text = "Deleting user account data..."
                    }

                    // Delete document
                    userDoc.delete().await()

                    Log.d("DataDeletion", "User document deleted successfully")

                    // Final cleanup
                    clearLocalDataAndRedirect(loadingDialog, status.values.any { !it })

                } catch (e: Exception) {
                    Log.e("DataDeletion", "Error in final user document deletion: ${e.message}")
                    clearLocalDataAndRedirect(loadingDialog, true)
                }
            }
        }
    }
    private fun clearLocalDataAndRedirect(loadingDialog: AlertDialog, hasError: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Log start of cleanup
                Log.d("AccountSettings", "Starting local data cleanup")

                // Clear SharedPreferences
                val allPrefs = listOf(
                    "jewelry_pos_settings",
                    "shop_preferences",
                    "jewelry_pos_pdf_settings",
                    "secure_jewelry_pos_settings"
                )

                for (prefName in allPrefs) {
                    try {
                        val prefs =
                            requireContext().getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        Log.d("AccountSettings", "Cleared preferences: $prefName")
                    } catch (e: Exception) {
                        Log.e(
                            "AccountSettings",
                            "Error clearing preferences $prefName: ${e.message}"
                        )
                    }
                }

                // Clear specific databases
                try {
                    requireContext().deleteDatabase("jewelry_app_database.db")
                    Log.d("AccountSettings", "Deleted database: jewelry_app_database.db")
                } catch (e: Exception) {
                    Log.e(
                        "AccountSettings",
                        "Error deleting database jewelry_app_database.db: ${e.message}"
                    )
                }

                try {
                    requireContext().deleteDatabase("jewelry_pos_cache.db")
                    Log.d("AccountSettings", "Deleted database: jewelry_pos_cache.db")
                } catch (e: Exception) {
                    Log.e(
                        "AccountSettings",
                        "Error deleting database jewelry_pos_cache.db: ${e.message}"
                    )
                }

                // Clear shop data
                try {
                    ShopManager.clearLocalShop(requireContext())
                    Log.d("AccountSettings", "Cleared shop manager data")
                } catch (e: Exception) {
                    Log.e("AccountSettings", "Error clearing shop manager data: ${e.message}")
                }

                // Switch to main thread for UI operations
                withContext(Dispatchers.Main) {
                    // Dismiss loading dialog
                    if (loadingDialog.isShowing) {
                        loadingDialog.dismiss()
                    }

                    // Show completion message
                    val message = if (hasError) {
                        "Some data may not have been completely deleted due to errors. Local data has been cleared."
                    } else {
                        "All your data has been successfully deleted."
                    }

                    AlertDialog.Builder(requireContext())
                        .setTitle("Data Deletion Complete")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ ->
                            // Redirect to launcher screen
                            val intent = Intent(requireContext(), MainActivity::class.java)
                            intent.flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            requireActivity().finish()
                        }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                Log.e("AccountSettings", "Unexpected error in clearLocalDataAndRedirect", e)

                // Ensure UI operations run on main thread
                withContext(Dispatchers.Main) {
                    if (loadingDialog.isShowing) {
                        loadingDialog.dismiss()
                    }

                    // Show error dialog
                    AlertDialog.Builder(requireContext())
                        .setTitle("Error During Cleanup")
                        .setMessage("An unexpected error occurred while cleaning up: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout? You will need to login again to access your account.")
            .setPositiveButton("Logout") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Clear local shop data
        ShopManager.clearLocalShop(requireContext())

        // Clear any app-specific data you want to remove on logout
        // ...

        // Redirect to the launcher or login screen
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}