package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.DataWipeManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
import com.jewelrypos.swarnakhatabook.databinding.FragmentAccountSettingsBinding
import kotlinx.coroutines.launch

class AccountSettingsFragment : Fragment() {
    private var _binding: FragmentAccountSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences

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
        sharedPreferences = SecurePreferences.getInstance(requireContext())

        // Setup UI elements with current settings
        setupUI()

        // Setup click listeners for settings options
        setupClickListeners()
    }

    private fun setupUI() {
        // Set the app lock switch to current setting
        val isPinSet = PinSecurityManager.isPinSet(requireContext())
        binding.switchAppLock.isChecked = isPinSet
        updateAppLockUI(isPinSet)

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

        // Update the description text to include reverse PIN information when enabled
        binding.textAppLockDesc.text = if (enabled) {
            "PIN protection is enabled. You'll need to enter your 4-digit PIN when opening the app.\n\n" +
                    "Security feature: If you're ever forced to unlock your app against your will, " +
                    "enter your PIN in reverse to safely wipe all data."
        } else {
            "Enable PIN protection to secure your data. You can set a 4-digit PIN that will be required to access the app."
        }
    }

    private fun setupClickListeners() {
        // App Lock switch
        binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // If PIN not set, show setup dialog
                if (!PinSecurityManager.isPinSet(requireContext())) {
                    showPinSetupDialog()
                }
                // If already enabled, do nothing
            } else {
                // Disable PIN protection - show confirmation dialog first
                showDisablePinConfirmationDialog()
            }
        }

        // Change PIN option
        binding.cardChangePIN.setOnClickListener {
            showChangePINDialog()
        }

        // Delete All Data button
        binding.buttonDeleteAllData.setOnClickListener {
            showDeleteDataConfirmationDialog()
        }

        // Logout button
        binding.buttonLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showDisablePinConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Disable PIN Protection")
            .setMessage("Are you sure you want to disable PIN protection? Your data will no longer be secured.")
            .setPositiveButton("Disable") { _, _ ->
                // First verify current PIN before disabling
                showPinVerificationForDisable()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Reset switch state since operation was cancelled
                binding.switchAppLock.isChecked = true
            }
            .show()
    }

    private fun showPinVerificationForDisable() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_pin_input, null)
        val pinEditText = dialogView.findViewById<TextInputEditText>(R.id.pinEditText)

        builder.setView(dialogView)
            .setTitle("Verify PIN")
            .setMessage("Enter your current PIN to disable PIN protection")
            .setPositiveButton("Submit", null) // Set below
            .setNegativeButton("Cancel") { _, _ ->
                // Reset switch state since operation was cancelled
                binding.switchAppLock.isChecked = true
            }

        val dialog = builder.create()
        dialog.show()

        // Override the positive button click to validate PIN
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = pinEditText.text.toString()

            if (PinHashUtil.verifyPin(enteredPin, sharedPreferences)) {
                // PIN is correct, proceed to disable
                dialog.dismiss()
                disablePinProtection()
            } else {
                // PIN is incorrect
                pinEditText.error = "Incorrect PIN"
                pinEditText.text?.clear()
            }
        }
    }

    private fun disablePinProtection() {
        // Remove PIN hash and salt from preferences
        sharedPreferences.edit()
            .remove("pin_hash")
            .remove("pin_salt")
            .apply()

        // Update UI
        binding.switchAppLock.isChecked = false
        updateAppLockUI(false)

        Toast.makeText(requireContext(), "PIN protection disabled", Toast.LENGTH_SHORT).show()
    }

    private fun showPinSetupDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_pin_setup, null)

        // Hide current PIN field since no PIN is set yet
        val currentPinEditText = dialogView.findViewById<TextInputEditText>(R.id.currentPinEditText)
        val newPinEditText = dialogView.findViewById<TextInputEditText>(R.id.newPinEditText)
        val confirmPinEditText = dialogView.findViewById<TextInputEditText>(R.id.confirmPinEditText)
        val currentPinLayout = dialogView.findViewById<TextInputLayout>(R.id.currentPinLayout)
        currentPinLayout.visibility = View.GONE

        // Add information about reverse PIN
        val infoTextView = TextView(requireContext())
        infoTextView.text = "Important security feature: If you're ever forced to unlock your app against your will, entering your PIN in reverse will securely wipe all app data."
        infoTextView.setPadding(32, 32, 32, 32)
        infoTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_primary))

        val container = dialogView.findViewById<ViewGroup>(R.id.pin_dialog_container)
        container.addView(infoTextView, container.childCount - 1) // Add above buttons but below other fields

        builder.setView(dialogView)
            .setTitle("Set PIN")
            .setMessage("Create a 4-digit PIN to protect your app")
            .setPositiveButton("Save", null) // Will be set below
            .setNegativeButton("Cancel") { _, _ ->
                // Reset switch state since operation was cancelled
                binding.switchAppLock.isChecked = false
            }

        val dialog = builder.create()
        dialog.show()

        // Override the positive button click to validate inputs
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newPin = newPinEditText.text.toString()
            val confirmPin = confirmPinEditText.text.toString()

            var isValid = true

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
                // Store PIN securely
                PinHashUtil.storePin(newPin, sharedPreferences)

                dialog.dismiss()
                updateAppLockUI(true)
                Toast.makeText(
                    requireContext(),
                    "PIN protection enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

        // Add information about reverse PIN
        val infoTextView = TextView(requireContext())
        infoTextView.text = "Remember: In case of emergency, entering your PIN in reverse will securely wipe all app data."
        infoTextView.setPadding(32, 32, 32, 32)
        infoTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_primary))

        val container = dialogView.findViewById<ViewGroup>(R.id.pin_dialog_container)
        container.addView(infoTextView, container.childCount - 1) // Add above buttons but below other fields

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
                lifecycleScope.launch {
                    // Store PIN securely
                    PinHashUtil.storePin(newPin, securePrefs)

                    // Update UI
                    binding.switchAppLock.isChecked = true
                    updateAppLockUI(true)

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

                // Perform complete data wipe
                DataWipeManager.performEmergencyWipe(requireContext()) {
                    // Restart app after wipe is complete
                    DataWipeManager.restartApp(requireContext())
                }
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