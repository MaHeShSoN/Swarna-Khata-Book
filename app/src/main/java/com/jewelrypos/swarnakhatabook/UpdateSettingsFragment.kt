package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.jewelrypos.swarnakhatabook.Utilitys.AppUpdateManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentUpdateSettingsBinding

/**
 * Fragment for managing app update settings
 */
class UpdateSettingsFragment : Fragment() {

    private var _binding: FragmentUpdateSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpdateSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupUpdatePreferences()
        setupButtons()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = binding.topAppBar
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupUpdatePreferences() {
        // Load current preferences from SharedPreferences
        val sharedPreferences = requireActivity().getSharedPreferences(
            "app_update_preferences",
            android.content.Context.MODE_PRIVATE
        )

        // Auto-check for updates
        val autoCheckEnabled = sharedPreferences.getBoolean("auto_check_updates", true)
        binding.switchAutoCheckUpdates.isChecked = autoCheckEnabled

        // Save preference when changed
        binding.switchAutoCheckUpdates.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("auto_check_updates", isChecked).apply()

            Toast.makeText(
                requireContext(),
                if (isChecked) "Automatic updates enabled" else "Automatic updates disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Wifi-only updates
        val wifiOnlyEnabled = sharedPreferences.getBoolean("wifi_only_updates", true)
        binding.switchWifiOnly.isChecked = wifiOnlyEnabled

        // Save preference when changed
        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("wifi_only_updates", isChecked).apply()

            Toast.makeText(
                requireContext(),
                if (isChecked) "Updates will download on Wi-Fi only" else "Updates may use mobile data",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupButtons() {
        // Check for updates button
        binding.btnCheckUpdates.setOnClickListener {
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        // Show a loading indicator
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCheckUpdates.isEnabled = false
        binding.btnCheckUpdates.text = "Checking..."

        // Use the app update manager to check for updates
        SwarnaKhataBook.getAppUpdateManager().checkForUpdates()

        // Observe the update status
        SwarnaKhataBook.getAppUpdateManager().updateAvailabilityStatus.observe(viewLifecycleOwner) { updateType ->
            // Hide loading indicator
            binding.progressBar.visibility = View.GONE
            binding.btnCheckUpdates.isEnabled = true
            binding.btnCheckUpdates.text = "Check for Updates"

            // Handle update status
            when (updateType) {
                AppUpdateManager.UPDATE_TYPE_IMMEDIATE -> {
                    showUpdateDialog(true)
                }
                AppUpdateManager.UPDATE_TYPE_FLEXIBLE -> {
                    showUpdateDialog(false)
                }
                AppUpdateManager.UPDATE_TYPE_NONE -> {
                    Toast.makeText(
                        requireContext(),
                        "Your app is up to date!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showUpdateDialog(isImmediate: Boolean) {
        val updateMessage = SwarnaKhataBook.getAppUpdateManager().getUpdateMessage(isImmediate)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (isImmediate) "Update Required" else "Update Available")
            .setMessage(updateMessage)
            .setPositiveButton("Update Now") { dialog, _ ->
                // Start the update
                SwarnaKhataBook.getAppUpdateManager().startUpdate(
                    requireActivity(),
                    if (isImmediate) AppUpdateManager.UPDATE_TYPE_IMMEDIATE else AppUpdateManager.UPDATE_TYPE_FLEXIBLE
                )
                dialog.dismiss()
            }

        // Add a negative button for flexible updates
        if (!isImmediate) {
            dialog.setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}