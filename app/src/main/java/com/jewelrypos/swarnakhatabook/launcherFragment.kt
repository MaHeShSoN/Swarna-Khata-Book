package com.jewelrypos.swarnakhatabook

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jewelrypos.swarnakhatabook.ViewModle.SplashViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentLauncherBinding
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class launcherFragment : Fragment() {

    private var _binding: FragmentLauncherBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SplashViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel using ViewModelProvider.Factory for AndroidViewModel
        val factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[SplashViewModel::class.java]

        // Initialize SharedPreferences
        sharedPreferences = requireActivity().getSharedPreferences(
            "jewelry_pos_settings",
            Context.MODE_PRIVATE
        )

        // Check if user is authenticated, then check subscription
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            checkSubscriptionStatus()
        } else {
            // Not logged in yet, proceed with normal flow
            checkAppLockAndProceed()
        }
    }

    private fun checkSubscriptionStatus() {
        lifecycleScope.launch {
            val subscriptionManager = SwarnaKhataBook.userSubscriptionManager

            // Check if trial has expired for non-premium users
            if (!subscriptionManager.isPremiumUser() && subscriptionManager.hasTrialExpired()) {
                showTrialExpiredDialog()
            } else {
                // Trial still valid or user is premium
                checkAppLockAndProceed()
            }
        }
    }

    private fun showTrialExpiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Trial Period Expired")
            .setMessage("Your 10-day trial period has expired. Please upgrade to continue using Swarna Khata Book.")
            .setCancelable(false)
            .setPositiveButton("Upgrade") { _, _ ->
                // Navigate to upgrade screen
                // For demonstration, we'll just show a toast
                Toast.makeText(requireContext(), "Navigate to upgrade screen", Toast.LENGTH_LONG).show()

                // For testing - simulate an upgrade
                upgradeToPremium()
            }
            .setNegativeButton("Log Out") { _, _ ->
                // Log out the user
                logoutUser()
            }
            .show()
    }

    private fun upgradeToPremium() {
        lifecycleScope.launch {
            val success = SwarnaKhataBook.userSubscriptionManager.updatePremiumStatus(true)
            if (success) {
                Toast.makeText(requireContext(), "Upgraded to Premium!", Toast.LENGTH_SHORT).show()
                // Continue with normal app flow
                checkAppLockAndProceed()
            } else {
                Toast.makeText(requireContext(), "Upgrade failed. Please try again.", Toast.LENGTH_SHORT).show()
                logoutUser()
            }
        }
    }

    private fun logoutUser() {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Navigate to login screen
        findNavController().navigate(R.id.action_launcherFragment_to_getDetailsFragment)
    }

    private fun checkAppLockAndProceed() {
        // Check if app lock is enabled
        val isAppLockEnabled = sharedPreferences.getBoolean("app_lock_enabled", false)

        if (isAppLockEnabled) {
            // Setup biometrics for app lock
            setupBiometrics()
        } else {
            // No app lock, proceed with normal flow
            startNormalFlow()
        }
    }

    private fun startNormalFlow() {
        // Observe navigation events
        viewModel.navigationEvent.observe(viewLifecycleOwner) { event ->
            Handler(Looper.getMainLooper()).postDelayed({
                when (event) {
                    is SplashViewModel.NavigationEvent.NavigateToDashboard -> {
                        findNavController().navigate(R.id.action_launcherFragment_to_mainScreenFragment)
                    }
                    is SplashViewModel.NavigationEvent.NavigateToRegistration -> {
                        findNavController().navigate(R.id.action_launcherFragment_to_getDetailsFragment)
                    }
                    is SplashViewModel.NavigationEvent.NoInternet -> {
                        showNoInternetDialog()
                    }
                }
            }, 3000) // 3 second delay
        }

        // Trigger connectivity and auth check
        viewModel.checkInternetAndAuth()
    }

    private fun setupBiometrics() {
        executor = ContextCompat.getMainExecutor(requireContext())

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    // Authentication successful, proceed with normal flow
                    startNormalFlow()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        // User clicked cancel, close the app
                        requireActivity().finish()
                    } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        // Show error
                        Toast.makeText(
                            requireContext(),
                            "Authentication error: $errString",
                            Toast.LENGTH_SHORT
                        ).show()

                        // For now, let's proceed to the app anyway if there's a hardware error
                        // You might want to implement a fallback PIN mechanism later
                        startNormalFlow()
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
            .setTitle("Authentication Required")
            .setSubtitle("Please authenticate to access the app")
            .setNegativeButtonText("Cancel")
            .build()

        // Check biometric support and prompt authentication if supported
        checkBiometricSupport()
    }

    // Replace the checkBiometricSupport method in launcherFragment
    private fun checkBiometricSupport() {
        val biometricManager = BiometricManager.from(requireContext())
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Device supports biometric authentication, show prompt
                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                // Device doesn't support biometric authentication
                Toast.makeText(
                    requireContext(),
                    "Your device doesn't support biometric authentication",
                    Toast.LENGTH_SHORT
                ).show()

                // Fall back to PIN authentication
                showPinFallbackDialog()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                // Biometric features are currently unavailable
                Toast.makeText(
                    requireContext(),
                    "Biometric authentication is currently unavailable",
                    Toast.LENGTH_SHORT
                ).show()

                // Fall back to PIN authentication
                showPinFallbackDialog()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // User hasn't enrolled any biometrics, offer to set them up
                Toast.makeText(
                    requireContext(),
                    "No biometric credentials enrolled. Using PIN instead.",
                    Toast.LENGTH_LONG
                ).show()

                // Fall back to PIN authentication
                showPinFallbackDialog()
            }
        }
    }

    private fun showNoInternetDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("No Internet Connection")
            .setMessage("Please check your internet connection and try again.")
            .setPositiveButton("Retry") { _, _ ->
                // Retry connectivity and auth check
                viewModel.checkInternetAndAuth()
            }
            .setNegativeButton("Exit") { _, _ ->
                requireActivity().finish()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }


    private fun showPinFallbackDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_pin_input, null)
        val pinEditText = dialogView.findViewById<EditText>(R.id.pinEditText)

        builder.setView(dialogView)
            .setTitle("Enter PIN")
            .setMessage("Please enter your PIN to unlock the app")
            .setPositiveButton("Unlock") { _, _ ->
                // Empty, will be set below to avoid automatic dismissal
            }
            .setNegativeButton("Cancel") { _, _ ->
                // User canceled, close the app
                requireActivity().finish()
            }
            .setCancelable(false)

        val dialog = builder.create()
        dialog.show()

        // Override the positive button click to validate PIN
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            val savedPin = sharedPreferences.getString("app_lock_pin", "1234") // Default PIN is 1234

            if (enteredPin == savedPin) {
                // PIN is correct
                dialog.dismiss()
                startNormalFlow()
            } else {
                // PIN is incorrect
                pinEditText.error = "Incorrect PIN"
                pinEditText.text.clear()
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}