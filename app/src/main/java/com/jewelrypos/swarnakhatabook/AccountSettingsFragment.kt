package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
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
    ): View {
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
        binding.cardSecurityQuestions.setOnClickListener {
            showSecurityQuestionsDialog()
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
        // Show dialog to change PIN
        // This is a placeholder for actual PIN change functionality
        Toast.makeText(requireContext(), "PIN change functionality to be implemented", Toast.LENGTH_SHORT).show()
    }

    private fun showSecurityQuestionsDialog() {
        // Show dialog to set up security questions
        // This is a placeholder for actual security questions setup
        Toast.makeText(requireContext(), "Security questions to be implemented", Toast.LENGTH_SHORT).show()
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