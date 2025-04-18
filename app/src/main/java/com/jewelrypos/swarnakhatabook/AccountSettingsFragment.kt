package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton // Import CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Keep for potential future use if needed
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope // Import lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder // Use Material 3 dialogs
import com.google.android.material.textfield.TextInputEditText // Keep if used elsewhere
import com.google.android.material.textfield.TextInputLayout // Keep if used elsewhere
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.BottomSheet.PinChangeBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.PinEntryBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.PinSetupBottomSheet
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
// Import UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.Utilitys.DataWipeManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager // Assuming this is where the new methods are
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentAccountSettingsBinding
import kotlinx.coroutines.launch // Import launch

class AccountSettingsFragment : Fragment() {
    private var _binding: FragmentAccountSettingsBinding? = null
    private val binding get() = _binding!! // Use non-null assertion carefully

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var userSubscriptionManager: UserSubscriptionManager // Declare manager instance

    // *** Define the listener as a property to reuse it ***
    private val appLockSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        // Check if the fragment is added before proceeding
        if (!isAdded) return@OnCheckedChangeListener

        // Get current actual PIN setup status *before* potentially changing it
        val pinIsAlreadySetUp = PinSecurityManager.hasPinBeenSetUp(requireContext())

        if (isChecked) {
            // User wants to ENABLE PIN Lock
            if (pinIsAlreadySetUp) {
                // PIN exists, just enable the lock
                PinSecurityManager.setPinLockEnabled(requireContext(), true)
                updateAppLockUI(true, true) // Update UI immediately
            } else {
                // PIN does not exist, need to run setup flow
                PinSetupBottomSheet.showPinSetup(
                    context = requireContext(),
                    fragmentManager = childFragmentManager,
                    onCompleted = { success ->
                        if (!isAdded || _binding == null) return@showPinSetup // Prevent crash
                        if (success) {
                            PinSecurityManager.setPinLockEnabled(requireContext(), true)
                            updateAppLockUI(true, true)
                            binding.switchAppLock.isChecked = true // Ensure switch stays checked
                            Toast.makeText(
                                requireContext(),
                                "PIN protection enabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            binding.switchAppLock.isChecked = false // Reset switch
                            updateAppLockUI(false, false)
                        }
                    }
                )
            }
        } else {
            // User wants to DISABLE PIN Lock
            if (pinIsAlreadySetUp) {
                // PIN exists, need verification to disable the lock
                showDisablePinConfirmationDialog()
            } else {
                // PIN doesn't exist, just ensure lock is marked disabled
                PinSecurityManager.setPinLockEnabled(requireContext(), false)
                updateAppLockUI(false, false)
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View { // Return non-nullable View
        _binding = FragmentAccountSettingsBinding.inflate(inflater, container, false)
        // Initialize UserSubscriptionManager here
        userSubscriptionManager = UserSubscriptionManager(requireContext())
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

        // Setup click listeners for settings options
        setupClickListeners() // Listener for switch is now attached in setupUI via onResume
    }

    override fun onResume() {
        super.onResume()
        // Setup/Refresh UI elements with current settings when fragment is resumed
        if (_binding != null) {
            setupUI()
        }
    }

    private fun setupUI() {
        binding?.let { b ->
            val pinIsSetUp = PinSecurityManager.hasPinBeenSetUp(requireContext())
            val pinLockIsEnabled = PinSecurityManager.isPinLockEnabled(requireContext())

            // *** FIX: Temporarily remove listener, set state, re-attach listener ***
            b.switchAppLock.setOnCheckedChangeListener(null) // Remove listener
            b.switchAppLock.isChecked = pinLockIsEnabled    // Set state without triggering listener
            b.switchAppLock.setOnCheckedChangeListener(appLockSwitchListener) // Re-attach listener

            updateAppLockUI(pinLockIsEnabled, pinIsSetUp)

            // --- Update Account Info Card ---
            val currentUser = FirebaseAuth.getInstance().currentUser
            b.textUsername.text =
                getString(R.string.account_phone_label, currentUser?.phoneNumber ?: "Not available")

            ShopManager.getShop(requireContext()) { shop ->
                if (isAdded && _binding != null) {
                    if (shop != null) {
                        b.textOwnerName.text = getString(
                            R.string.account_owner_label,
                            shop.name.takeIf { it.isNotEmpty() } ?: "Not Set")
                        b.textShopName.text = getString(
                            R.string.account_shop_label,
                            shop.shopName.takeIf { it.isNotEmpty() } ?: "Not Set")
                        b.textGstNumber.text = getString(
                            R.string.account_gst_label,
                            shop.gstNumber.takeIf { it.isNotEmpty() } ?: "Not Set")
                    } else {
                        b.textOwnerName.text =
                            getString(R.string.account_owner_label, "Not available")
                        b.textShopName.text =
                            getString(R.string.account_shop_label, "Not available")
                        b.textGstNumber.text =
                            getString(R.string.account_gst_label, "Not available")
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val isPremium = userSubscriptionManager.isPremiumUser()
                    val isTrialExpired = userSubscriptionManager.hasTrialExpired()
                    val isEffectivelyActive = isPremium || !isTrialExpired

                    if (isAdded && _binding != null) {
                        b.textSubscriptionStatus.text = getString(
                            R.string.account_subscription_label,
                            if (isEffectivelyActive) {
                                if (isPremium) "Premium" else "Trial (${userSubscriptionManager.getDaysRemaining()} days left)"
                            } else {
                                "Expired"
                            }
                        )
                        b.iconSubscriptionStatus.imageTintList = ContextCompat.getColorStateList(
                            requireContext(),
                            if (isPremium) R.color.premium_color else R.color.my_light_secondary
                        )
                        b.iconSubscriptionStatus.visibility =
                            if (isPremium) View.VISIBLE else View.GONE
                    }

                } catch (e: Exception) {
                    Log.e("AccountSettingsFragment", "Error checking subscription status", e)
                    if (isAdded && _binding != null) {
                        b.textSubscriptionStatus.text =
                            getString(R.string.account_subscription_label, "Error")
                        b.iconSubscriptionStatus.visibility = View.GONE
                    }
                }
            }
            // --- End Update Account Info Card ---
        }
    }


    private fun updateAppLockUI(lockIsEnabled: Boolean, pinIsSetUp: Boolean) {
        binding?.let { b ->
            b.textAppLockStatus.text = if (lockIsEnabled) "Enabled" else "Disabled"
            b.textAppLockDesc.text = if (pinIsSetUp) {
                getString(R.string.pin_enabled_description)
            } else {
                getString(R.string.pin_disabled_description)
            }
            b.cardChangePIN.visibility = if (pinIsSetUp) View.VISIBLE else View.GONE
        }
    }

    private fun setupClickListeners() {
        binding?.let { b -> // Use safe call
            // *** App Lock switch listener is now attached in setupUI ***
            // b.switchAppLock.setOnCheckedChangeListener { ... } // REMOVED FROM HERE

            // Change PIN option
            b.cardChangePIN.setOnClickListener {
                showChangePINDialog()
            }

            // Delete All Data button
            b.buttonDeleteAllData.setOnClickListener {
                if (PinSecurityManager.hasPinBeenSetUp(requireContext())) {
                    showDeleteDataConfirmationDialog()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete All Data")
                        .setMessage("Are you sure you want to delete ALL your data? This action cannot be undone. (No PIN required as App Lock is not set up)")
                        .setPositiveButton("Delete All") { _, _ ->
                            // Show loading indicator


                            // Get the current user's phone number
                            val phoneNumber = SessionManager.getPhoneNumber(requireContext())

                            if (phoneNumber != null) {
                                // Use lifecycleScope to launch a coroutine
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        // Get all shops for this user
                                        val shopsByPhoneResult =
                                            ShopManager.getShopsByPhoneNumber(phoneNumber)

                                        if (shopsByPhoneResult.isSuccess) {
                                            val shopsList =
                                                shopsByPhoneResult.getOrNull() ?: emptyList()

                                            if (shopsList.size > 1) {
                                                // Multi-shop user - delete only current shop
                                                val currentShopId =
                                                    SessionManager.getActiveShopId(requireContext())

                                                if (currentShopId != null) {
                                                    // Delete only the current shop's data
                                                    val result =
                                                        DataWipeManager.deleteSingleShopData(
                                                            requireContext(),
                                                            currentShopId
                                                        )

                                                    if (result.isSuccess) {
                                                        // Hide loading indicator


                                                        // Show success message
                                                        Toast.makeText(
                                                            requireContext(),
                                                            "Current shop data deleted successfully",
                                                            Toast.LENGTH_SHORT
                                                        ).show()

                                                        // Navigate to ShopSelectionFragment
                                                        findNavController().navigate(
                                                            R.id.action_accountSettingsFragment_to_shopSelectionFragment,
                                                            null,
                                                            androidx.navigation.navOptions {
                                                                popUpTo(R.id.mainScreenFragment) {
                                                                    inclusive = true
                                                                }
                                                            }
                                                        )
                                                    } else {
                                                        // Hide loading indicator


                                                        // Show error message
                                                        Toast.makeText(
                                                            requireContext(),
                                                            "Error deleting shop data: ${result.exceptionOrNull()?.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } else {
                                                    // No active shop ID, fall back to full wipe

                                                    performFullDataWipe()
                                                }
                                            } else {
                                                // Single shop user - perform full wipe

                                                performFullDataWipe()
                                            }
                                        } else {
                                            // Error getting shops, fall back to full wipe

                                            performFullDataWipe()
                                        }
                                    } catch (e: Exception) {
                                        // Error in coroutine, fall back to full wipe

                                        performFullDataWipe()
                                    }
                                }
                            } else {
                                // No phone number, fall back to full wipe

                                performFullDataWipe()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            // Logout button
            b.buttonLogout.setOnClickListener {
                showLogoutConfirmationDialog()
            }
        }
    }

    private fun showDisablePinConfirmationDialog() {
        if (!isAdded) return // Prevent crash

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Disable PIN Lock")
            .setMessage("Are you sure you want to disable the PIN lock? You can re-enable it later without setting up a new PIN.")
            .setPositiveButton("Disable Lock") { _, _ ->
                showPinVerificationForDisable()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                if (isAdded && _binding != null) {
                    // *** FIX: Reset switch visually without triggering listener ***
                    binding.switchAppLock.setOnCheckedChangeListener(null)
                    binding.switchAppLock.isChecked = true // User cancelled, reset switch state
                    binding.switchAppLock.setOnCheckedChangeListener(appLockSwitchListener)

                    // Update UI elements accordingly (lock enabled, pin IS set up)
                    updateAppLockUI(true, PinSecurityManager.hasPinBeenSetUp(requireContext()))
                }
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPinVerificationForDisable() {
        if (!isAdded) return // Prevent crash

        PinEntryBottomSheet.showPinVerification(
            context = requireContext(),
            fragmentManager = childFragmentManager,
            prefs = sharedPreferences,
            title = "Verify PIN",
            reason = "Enter your PIN to disable the lock",
            onPinCorrect = {
                if (isAdded) {
                    disablePinLock() // Call the function to disable the lock
                }
            },
            onPinIncorrect = { status ->
                if (!isAdded || _binding == null) return@showPinVerification
                if (status is PinSecurityStatus.Locked) {
                    val minutes = (status.remainingLockoutTimeMs / 60000).toInt() + 1
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Too Many Failed Attempts")
                        .setMessage("PIN entry has been disabled for $minutes minutes.")
                        .setPositiveButton("OK", null)
                        .setCancelable(true)
                        .show()
                }
                // *** FIX: Reset switch visually without triggering listener ***
                binding.switchAppLock.setOnCheckedChangeListener(null)
                binding.switchAppLock.isChecked = true // Reset switch state
                binding.switchAppLock.setOnCheckedChangeListener(appLockSwitchListener)

                updateAppLockUI(true, true) // Pin is still set up, lock should be shown as enabled
            },
            onReversePinEntered = {
                if (isAdded) {
                    DataWipeManager.performEmergencyWipe(requireContext()) {
                        DataWipeManager.restartApp(requireContext())
                    }
                }
            },
            onCancelled = {
                if (isAdded && _binding != null) {
                    // *** FIX: Reset switch visually without triggering listener ***
                    binding.switchAppLock.setOnCheckedChangeListener(null)
                    binding.switchAppLock.isChecked = true // User cancelled, reset switch state
                    binding.switchAppLock.setOnCheckedChangeListener(appLockSwitchListener)

                    updateAppLockUI(true, true) // Pin is still set up
                }
            }
        )
    }

    private fun disablePinLock() {
        if (!isAdded || _binding == null) return

        // Only set the enabled flag to false
        PinSecurityManager.setPinLockEnabled(requireContext(), false)

        // Update UI (lock disabled, but PIN is still set up)
        // *** FIX: Set switch visually without triggering listener ***
        binding.switchAppLock.setOnCheckedChangeListener(null)
        binding.switchAppLock.isChecked = false // Ensure switch is off
        binding.switchAppLock.setOnCheckedChangeListener(appLockSwitchListener)

        updateAppLockUI(false, true) // Update text and card visibility

        Toast.makeText(requireContext(), "PIN lock disabled", Toast.LENGTH_SHORT).show()
    }


    private fun showChangePINDialog() {
        if (!isAdded) return // Prevent crash

        PinChangeBottomSheet.showPinChange(
            context = requireContext(),
            fragmentManager = childFragmentManager,
            onCompleted = { success ->
                if (!isAdded || _binding == null) return@showPinChange
                if (success) {
                    Toast.makeText(requireContext(), "PIN updated successfully", Toast.LENGTH_SHORT)
                        .show()
                    // *** FIX: Set switch visually without triggering listener ***
                    binding.switchAppLock.setOnCheckedChangeListener(null)
                    binding.switchAppLock.isChecked = true // Assume lock should be enabled
                    binding.switchAppLock.setOnCheckedChangeListener(appLockSwitchListener)

                    PinSecurityManager.setPinLockEnabled(requireContext(), true) // Mark as enabled
                    updateAppLockUI(true, true)
                }
            }
        )
    }

    // Dialog shown when user clicks delete and PIN *is* set up
    private fun showDeleteDataConfirmationDialog() {
        if (!isAdded) return // Prevent crash

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete All Data")
            .setMessage("Are you sure you want to delete ALL your data? This action requires PIN confirmation and cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                showPinConfirmationForDataDeletion()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPinConfirmationForDataDeletion() {
        if (!isAdded) return // Prevent crash

        val securityStatus = PinSecurityManager.checkStatus(requireContext())

        if (securityStatus is PinSecurityStatus.Locked) {
            val minutes = (securityStatus.remainingLockoutTimeMs / 60000).toInt() + 1
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Too Many Failed Attempts")
                .setMessage("PIN entry has been disabled for $minutes minutes.")
                .setPositiveButton("OK", null)
                .setCancelable(true)
                .show()
            return
        }

        PinEntryBottomSheet.showPinVerification(
            context = requireContext(),
            fragmentManager = childFragmentManager,
            prefs = sharedPreferences,
            title = "Confirm Delete",
            reason = "Enter your PIN to confirm data deletion",
            onPinCorrect = {
                if (isAdded) {
                    // Show loading indicator


                    // Get the current user's phone number
                    val phoneNumber = SessionManager.getPhoneNumber(requireContext())

                    if (phoneNumber != null) {
                        // Use lifecycleScope to launch a coroutine
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                // Get all shops for this user
                                val shopsByPhoneResult =
                                    ShopManager.getShopsByPhoneNumber(phoneNumber)

                                if (shopsByPhoneResult.isSuccess) {
                                    val shopsList = shopsByPhoneResult.getOrNull() ?: emptyList()

                                    if (shopsList.size > 1) {
                                        // Multi-shop user - delete only current shop
                                        val currentShopId =
                                            SessionManager.getActiveShopId(requireContext())

                                        if (currentShopId != null) {
                                            // Delete only the current shop's data
                                            val result = DataWipeManager.deleteSingleShopData(
                                                requireContext(),
                                                currentShopId
                                            )

                                            if (result.isSuccess) {
                                                // Hide loading indicator


                                                // Show success message
                                                Toast.makeText(
                                                    requireContext(),
                                                    "Current shop data deleted successfully",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                // Navigate to ShopSelectionFragment
                                                findNavController().navigate(
                                                    R.id.action_accountSettingsFragment_to_shopSelectionFragment,
                                                    null,
                                                    androidx.navigation.navOptions {
                                                        popUpTo(R.id.mainScreenFragment) {
                                                            inclusive = true
                                                        }
                                                    }
                                                )
                                            } else {
                                                // Hide loading indicator


                                                // Show error message
                                                Toast.makeText(
                                                    requireContext(),
                                                    "Error deleting shop data: ${result.exceptionOrNull()?.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            // No active shop ID, fall back to full wipe

                                            performFullDataWipe()
                                        }
                                    } else {
                                        // Single shop user - perform full wipe

                                        performFullDataWipe()
                                    }
                                } else {
                                    // Error getting shops, fall back to full wipe

                                    performFullDataWipe()
                                }
                            } catch (e: Exception) {
                                // Error in coroutine, fall back to full wipe

                                performFullDataWipe()
                            }
                        }
                    } else {
                        // No phone number, fall back to full wipe

                        performFullDataWipe()
                    }
                }
            },
            onPinIncorrect = { status ->
                if (!isAdded) return@showPinVerification
                if (status is PinSecurityStatus.Locked) {
                    val minutes = (status.remainingLockoutTimeMs / 60000).toInt() + 1
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Too Many Failed Attempts")
                        .setMessage("PIN entry has been disabled for $minutes minutes.")
                        .setPositiveButton("OK", null)
                        .setCancelable(true)
                        .show()
                }
            },
            onReversePinEntered = {
                if (isAdded) {
                    performFullDataWipe()
                }
            },
            onCancelled = {
                // Check isAdded before showing Toast
                if (isAdded) {
                    Toast.makeText(requireContext(), "Data deletion cancelled", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        )
    }

    // Helper method to perform full data wipe
    private fun performFullDataWipe() {
        DataWipeManager.performEmergencyWipe(requireContext()) {
            DataWipeManager.restartApp(requireContext())
        }
    }

    private fun showLogoutConfirmationDialog() {
        if (!isAdded) return // Prevent crash

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        // Check if fragment is still attached before proceeding
        if (!isAdded) return

        FirebaseAuth.getInstance().signOut()
        ShopManager.clearLocalShop(requireContext())

        // Clear PIN data on logout
        sharedPreferences.edit()
            .remove(PinHashUtil.KEY_PIN_HASH)
            .remove(PinHashUtil.KEY_PIN_SALT)
            .remove(PinSecurityManager.KEY_PIN_LOCK_ENABLED)
            .apply()

        // Use requireActivity() safely
        val activity = activity ?: return // Exit if activity is null
        val intent = Intent(activity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity.finishAffinity()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Important to null out binding
    }
}