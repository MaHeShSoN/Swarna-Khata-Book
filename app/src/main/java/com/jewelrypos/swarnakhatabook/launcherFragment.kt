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
import com.jewelrypos.swarnakhatabook.Utilitys.DataWipeManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinCheckResult
import com.jewelrypos.swarnakhatabook.Utilitys.PinEntryDialogHandler
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.launch

class launcherFragment : Fragment() {

    private var _binding: FragmentLauncherBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SplashViewModel
    private lateinit var sharedPreferences: SharedPreferences

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
        lifecycleScope.launch {
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
        // Show loading indicator while upgrading
        binding.loadingIndicator.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val success = SwarnaKhataBook.getUserSubscriptionManager().updatePremiumStatus(true)
            if (success) {
                Toast.makeText(requireContext(), "Upgraded to Premium!", Toast.LENGTH_SHORT).show()
                // Continue with normal app flow
                checkPinAndProceed()
            } else {
                // Hide loading indicator on failure
                binding.loadingIndicator.visibility = View.GONE
                Toast.makeText(requireContext(), "Upgrade failed. Please try again.", Toast.LENGTH_SHORT).show()
                logoutUser()
            }
        }
    }

    private fun logoutUser() {
        // Clear active shop
        SessionManager.clearSession(requireContext())
        
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Navigate to login screen
        findNavController().navigate(R.id.action_launcherFragment_to_getDetailsFragment)
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
                    findNavController().navigate(R.id.action_launcherFragment_to_shopSelectionFragment)
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}