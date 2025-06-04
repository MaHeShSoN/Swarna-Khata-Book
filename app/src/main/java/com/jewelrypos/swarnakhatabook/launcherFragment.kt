package com.jewelrypos.swarnakhatabook

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jewelrypos.swarnakhatabook.ViewModle.SplashViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentLauncherBinding
import com.google.firebase.auth.FirebaseAuth
import com.jewelrypos.swarnakhatabook.BottomSheet.PinEntryBottomSheet
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.Utilitys.DataWipeManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinCheckResult
import com.jewelrypos.swarnakhatabook.Utilitys.PinEntryDialogHandler
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.launch
import java.io.File

class launcherFragment : Fragment() {

    private var _binding: FragmentLauncherBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SplashViewModel
    private lateinit var sharedPreferences: SharedPreferences
    private val coroutineJobs = mutableListOf<kotlinx.coroutines.Job>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show loading indicator immediately
        binding.loadingIndicator.visibility = View.VISIBLE

        // Initialize ViewModel using ViewModelProvider.Factory for AndroidViewModel
        val factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[SplashViewModel::class.java]

        // Initialize SharedPreferences
        sharedPreferences = SecurePreferences.getInstance(requireContext())

        // Initialize SessionManager
        SessionManager.initialize(requireContext())

        // Check if user is authenticated, then check subscription
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            checkSubscriptionStatus()
        } else {
            // Not logged in yet, proceed with normal flow
            checkPinAndProceed()
        }
    }

    private fun checkSubscriptionStatus() {
        val job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

                // Check if trial has expired for non-premium users
                if (!subscriptionManager.isPremiumUser() && subscriptionManager.hasTrialExpired()) {
                    // Hide loading indicator before showing dialog
                    binding.loadingIndicator.visibility = View.GONE
                    showTrialExpiredDialog()
                } else {
                    // Trial still valid or user is premium
                    checkPinAndProceed()
                }

                // Use the centralized subscription check method
            } catch (e: Exception) {
                Log.e("LauncherFragment", "Error checking subscription: ${e.message}", e)
                // If there's an error, proceed with normal flow to avoid blocking users
                // but log for investigation
                checkPinAndProceed()
            }
        }
        coroutineJobs.add(job)
    }

    private fun showTrialExpiredDialog() {
        MaterialAlertDialogBuilder(requireContext())
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

    private fun navigateToUpgradeScreen() {
        val intent = Intent(requireContext(), UpgradeActivity::class.java)
        startActivity(intent)
    }

    private fun logoutUser() {
        // Clear active shop session
        SessionManager.clearSession(requireContext())

        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Clear app-specific data
        clearAppSpecificData()

        // Navigate to login screen
        findNavController().navigate(R.id.action_launcherFragment_to_getDetailsFragment)
    }

    /**
     * Clear app-specific data on logout
     */
    private fun clearAppSpecificData() {
        try {
            val context = requireContext()

            // Clear local databases
            context.deleteDatabase("jewelry_app_database.db")
            context.deleteDatabase("jewelry_pos_cache.db")
            context.deleteDatabase("metal_item_database.db")

            // Clear Firebase cache
            try {
                File(context.cacheDir, "firebase_firestore.db").delete()
                File(context.cacheDir, "firestore.sqlite").delete()
            } catch (e: Exception) {
                Log.e("LauncherFragment", "Error clearing Firebase cache: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e("LauncherFragment", "Error clearing app data: ${e.message}")
        }
    }

    private fun checkPinAndProceed() {
        val context = requireContext() // Get context once

        // *** FIX: Check BOTH if a PIN exists AND if the lock is ENABLED ***
        if (PinSecurityManager.hasPinBeenSetUp(context) && PinSecurityManager.isPinLockEnabled(context)) {
            // Hide loading indicator before showing PIN dialog
            binding.loadingIndicator.visibility = View.GONE
            // PIN is set AND the lock is enabled, show verification dialog
            showPinVerificationDialog()
        } else {
            // No PIN set OR the lock is disabled, proceed with normal flow
            startNormalFlow()
        }
    }

    private fun startNormalFlow() {
        // Keep loading indicator visible while waiting for navigation events
        binding.loadingIndicator.visibility = View.VISIBLE

        // Observe navigation events
        viewModel.navigationEvent.observe(viewLifecycleOwner) { event ->
            // Hide loading indicator before navigation
            binding.loadingIndicator.visibility = View.GONE

            // Navigate immediately without delay
            when (event) {
                is SplashViewModel.NavigationEvent.NavigateToDashboard -> {
                    findNavController().navigate(R.id.action_launcherFragment_to_mainScreenFragment)
                }
                is SplashViewModel.NavigationEvent.NavigateToRegistration -> {
                    findNavController().navigate(R.id.action_launcherFragment_to_getDetailsFragment)
                }
                is SplashViewModel.NavigationEvent.NavigateToCreateShop -> {
                    findNavController().navigate(R.id.action_launcherFragment_to_createShopFragment)
                }
                is SplashViewModel.NavigationEvent.NavigateToShopSelection -> {
                    val bundle = Bundle().apply {
                        putBoolean("fromLogin", true)
                    }
                    findNavController().navigate(R.id.action_launcherFragment_to_shopSelectionFragment, bundle)
                }
                is SplashViewModel.NavigationEvent.NoInternet -> {
                    showNoInternetDialog()
                }
            }
        }

        // Trigger connectivity and auth check
        viewModel.checkInternetAndAuth()
    }

    private fun showNoInternetDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("No Internet Connection")
            .setMessage("Please check your internet connection and try again.")
            .setPositiveButton("Retry") { _, _ ->
                // Show loading indicator when retrying
                binding.loadingIndicator.visibility = View.VISIBLE
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

    private fun showPinVerificationDialog() {
        // First check PIN security status
        val securityStatus = PinSecurityManager.checkStatus(requireContext())

        if (securityStatus is PinSecurityStatus.Locked) {
            // Show lockout dialog
            val minutes = (securityStatus.remainingLockoutTimeMs / 60000).toInt() + 1
            AlertDialog.Builder(requireContext())
                .setTitle("Too Many Failed Attempts")
                .setMessage("PIN entry has been disabled for $minutes minutes due to multiple failed attempts.")
                .setPositiveButton("OK") { _, _ -> requireActivity().finish() }
                .setCancelable(false)
                .show()
            return
        }

        // Show the full-screen PIN entry
        PinEntryBottomSheet.showPinVerification(
            context = requireContext(),
            fragmentManager = parentFragmentManager,
            prefs = sharedPreferences,
            title = "Enter PIN",
            reason = "Please enter your PIN to unlock the app",
            onPinCorrect = {
                // PIN correct, continue app
                // Show loading indicator before proceeding
                binding.loadingIndicator.visibility = View.VISIBLE
                startNormalFlow()
            },
            onPinIncorrect = { status ->
                // Handle incorrect PIN based on status
                if (status is PinSecurityStatus.Locked) {
                    val minutes = (status.remainingLockoutTimeMs / 60000).toInt() + 1
                    AlertDialog.Builder(requireContext())
                        .setTitle("Too Many Failed Attempts")
                        .setMessage("PIN entry has been disabled for $minutes minutes due to multiple failed attempts.")
                        .setPositiveButton("OK") { _, _ -> requireActivity().finish() }
                        .setCancelable(false)
                        .show()
                }
            },
            onReversePinEntered = {
                // Reverse PIN entered - trigger data wipe
                DataWipeManager.performEmergencyWipe(requireContext()) {
                    // Restart app after wipe is complete
                    DataWipeManager.restartApp(requireContext())
                }
            },
            onCancelled = {
                // User cancelled, exit app
                requireActivity().finish()
            }
        )
    }

    private fun showGracePeriodDialog(daysRemaining: Int) {
        val pluralSuffix = if (daysRemaining > 1) "s" else ""
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.grace_period_title))
            .setMessage(getString(R.string.grace_period_message, daysRemaining, pluralSuffix))
            .setPositiveButton(getString(R.string.upgrade_now)) { _, _ ->
                navigateToUpgradeScreen()
            }
            .setNegativeButton(getString(R.string.continue_using)) { _, _ ->
                // Continue with normal flow
                checkPinAndProceed()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        // Cancel all coroutine jobs to prevent memory leaks
        coroutineJobs.forEach { it.cancel() }
        coroutineJobs.clear()

        super.onDestroyView()
        _binding = null
    }
}