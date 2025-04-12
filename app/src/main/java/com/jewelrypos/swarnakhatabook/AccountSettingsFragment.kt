package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentAccountSettingsBinding
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

        biometricPrompt = BiometricPrompt(this, executor,
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
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
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

        // Check if PIN exists
        val hasPinSet = sharedPreferences.contains("app_lock_pin")

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
                val savedPin = sharedPreferences.getString("app_lock_pin", "")
                if (enteredCurrentPin != savedPin) {
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
                // Save the new PIN
                sharedPreferences.edit().putString("app_lock_pin", newPin).apply()

                dialog.dismiss()
                Toast.makeText(requireContext(), "PIN updated successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSecurityQuestionsDialog() {
        // Show dialog to set up security questions
        // This is a placeholder for actual security questions setup
        Toast.makeText(requireContext(), "Security questions to be implemented", Toast.LENGTH_SHORT).show()
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
            val savedPin = sharedPreferences.getString("app_lock_pin", "1234") // Default is 1234

            if (enteredPin == savedPin) {
                // PIN is correct, proceed with data deletion
                dialog.dismiss()
                deleteAllData()
            } else {
                // PIN is incorrect
                pinEditText.error = "Incorrect PIN"
                pinEditText.text?.clear()
            }
        }
    }

    private fun deleteAllData() {
        // Show loading dialog
        val loadingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Deleting Data")
            .setMessage("Please wait while we delete your data...")
            .setCancelable(false)
            .create()

        loadingDialog.show()

        // Start deleting data
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val userId = auth.currentUser?.phoneNumber?.replace("+", "") ?: ""

        if (userId.isNotEmpty()) {
            // Delete Firestore data
            deleteFirestoreData(userId, loadingDialog)
        } else {
            // No user ID, just clear local data and redirect
            clearLocalDataAndRedirect(loadingDialog)
        }
    }

    private fun deleteFirestoreData(userId: String, loadingDialog: AlertDialog) {
        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(userId)

        // Define collections to delete
        val collections = listOf(
            "customers",
            "invoices",
            "inventory",
            "notifications",
            "payments",
            "settings"
        )

        // Delete each collection
        var completedCollections = 0
        var hasError = false

        for (collection in collections) {
            userDoc.collection(collection)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    // Create batch to delete documents
                    val batch = firestore.batch()
                    for (document in querySnapshot.documents) {
                        batch.delete(document.reference)
                    }

                    // Commit the batch
                    if (querySnapshot.documents.isNotEmpty()) {
                        batch.commit()
                            .addOnSuccessListener {
                                completedCollections++
                                checkDeletionProgress(completedCollections, collections.size, loadingDialog, hasError, userDoc)
                            }
                            .addOnFailureListener {
                                hasError = true
                                completedCollections++
                                checkDeletionProgress(completedCollections, collections.size, loadingDialog, hasError, userDoc)
                            }
                    } else {
                        // No documents in this collection
                        completedCollections++
                        checkDeletionProgress(completedCollections, collections.size, loadingDialog, hasError, userDoc)
                    }
                }
                .addOnFailureListener {
                    hasError = true
                    completedCollections++
                    checkDeletionProgress(completedCollections, collections.size, loadingDialog, hasError, userDoc)
                }
        }
    }

    private fun checkDeletionProgress(completed: Int, total: Int, loadingDialog: AlertDialog, hasError: Boolean, userDoc: DocumentReference) {
        if (completed >= total) {
            // All collections processed, now delete the user document itself
            userDoc.delete()
                .addOnSuccessListener {
                    // Successfully deleted user document
                    clearLocalDataAndRedirect(loadingDialog, hasError)
                }
                .addOnFailureListener { e ->
                    Log.e("AccountSettings", "Error deleting user document", e)
                    clearLocalDataAndRedirect(loadingDialog, true)
                }
        }
    }

    private fun clearLocalDataAndRedirect(loadingDialog: AlertDialog, hasError: Boolean = false) {
        // Clear SharedPreferences
        val allPrefs = listOf(
            "jewelry_pos_settings",
            "shop_preferences",
            "jewelry_pos_pdf_settings",
            "secure_jewelry_pos_settings" // Add the encrypted preferences
        )

        for (prefName in allPrefs) {
            val prefs = requireContext().getSharedPreferences(prefName, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }

        // Clear specific databases used by the app
        try {
            // Replace these with your actual database names
            requireContext().deleteDatabase("jewelry_app_database.db")
            requireContext().deleteDatabase("jewelry_pos_cache.db")
        } catch (e: Exception) {
            Log.e("AccountSettings", "Error deleting databases", e)
        }

        // Clear shop data
        ShopManager.clearLocalShop(requireContext())

        // Dismiss loading dialog
        loadingDialog.dismiss()

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
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
            }
            .setCancelable(false)
            .show()
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