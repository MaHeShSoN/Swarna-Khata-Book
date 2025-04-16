// main/java/com/jewelrypos/swarnakhatabook/MoreSettingFragment.kt
package com.jewelrypos.swarnakhatabook

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.widget.TextView
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog // Import your custom dialog
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope // Ensure this is imported
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.SettingsAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.databinding.FragmentMoreSettingBinding
import kotlinx.coroutines.launch


class MoreSettingFragment : Fragment() {
    private var _binding: FragmentMoreSettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsAdapter: SettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMoreSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSettingsList() // Call setup function
    }

    private fun setupSettingsList() {
        // Launch coroutine to check premium status asynchronously
        lifecycleScope.launch {
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()
            val isPremium = subscriptionManager.isPremiumUser() // Fetch premium status
            val daysRemaining = subscriptionManager.getDaysRemaining()

            val subscriptionBadgeText = if (isPremium) {
                "PREMIUM"
            } else if (daysRemaining <= 3 && daysRemaining > 0) { // Show days left only if > 0
                "$daysRemaining DAYS LEFT"
            } else if (daysRemaining == 0 && !isPremium) {
                "EXPIRED" // Show expired if 0 days left and not premium
            } else {
                "TRIAL" // Otherwise, show TRIAL
            }


            // Create settings items list
            val settingsItems = mutableListOf(
                SettingsItem(
                    id = "debug_subscription",
                    title = "Debug Subscription",
                    subtitle = "Developer tools for testing subscription features",
                    iconResId = R.drawable.ic_order // Example icon
                ), SettingsItem(
                    id = "subscription_status",
                    title = if (isPremium) "Premium Subscription" else "Free Trial",
                    subtitle = if (isPremium) "You have access to all premium features"
                    else "Trial ends in $daysRemaining days",
                    iconResId = R.drawable.fluent__premium_24_regular,
                    badgeText = subscriptionBadgeText
                ), SettingsItem(
                    id = "shop_details",
                    title = "Shop Details",
                    subtitle = "Configure your shop information for invoices",
                    iconResId = R.drawable.stash__shop
                ), SettingsItem(
                    id = "invoice_format",
                    title = "Invoice PDF Format",
                    subtitle = "Customize the appearance of your invoice PDFs",
                    iconResId = R.drawable.mdi__invoice_text_edit_outline
                ), SettingsItem( // Keep invoice_template separate from invoice_format
                    id = "invoice_template", title = "Invoice Template & Color", // Updated title
                    subtitle = "Choose template and theme color", // Updated subtitle
                    iconResId = R.drawable.ic_template, // Specific icon for templates/colors
                    badgeText = if (!isPremium) "PREMIUM" else null
                ), SettingsItem(
                    id = "reports",
                    title = "Reports",
                    subtitle = "View and export business reports and analytics",
                    iconResId = R.drawable.icon_park_outline__sales_report,
                    badgeText = if (!isPremium) "PREMIUM" else null
                ), SettingsItem(
                    id = "recycling_bin",
                    title = "Recycling Bin",
                    subtitle = "Recover deleted invoices, customers, and items",
                    iconResId = R.drawable.solar__trash_bin_trash_line_duotone,
                    badgeText = if (!isPremium) "PREMIUM" else null
                ), SettingsItem(
                    id = "account_settings",
                    title = "Account Settings",
                    subtitle = "Manage app lock, security and account options",
                    iconResId = R.drawable.material_symbols__account_circle_outline
                ), SettingsItem(
                    id = "app_updates",
                    title = "App Updates",
                    subtitle = "Manage automatic updates and check for new versions",
                    iconResId = R.drawable.material_symbols__refresh_rounded
                )
            )

            // Update the UI on the main thread
            requireActivity().runOnUiThread {
                // Pass the fetched premium status to the adapter
                settingsAdapter =
                    SettingsAdapter(settingsItems, isPremium) { item -> // Pass isPremium here
                        handleNavigation(item, isPremium) // Pass isPremium to handler
                    }

                binding.settingsRecyclerView.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = settingsAdapter
                }
            }
        }
    }

    // Function to handle navigation based on item clicked
    fun handleNavigation(item: SettingsItem, isPremium: Boolean) {
        val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
        when (item.id) {
            "debug_subscription" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_subscriptionDebugActivity)
            }

            "subscription_status" -> {
                // Navigate based on current status, not just the badge text
                if (!isPremium) {
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_upgradeActivity)
                } else {
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_subscriptionStatusFragment) // Navigate to status screen if premium
                }
            }

            "shop_details" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_shopSettingsFragment)
            }

            "invoice_format" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_invoicePdfSettingsFragment)
            }

            "invoice_template" -> {
                // Check premium status directly
                if (isPremium) {
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_templateSelectionFragment)
                } else {
                    showPremiumFeatureDialog("Advanced invoice templates & colors")
                }
            }

            "account_settings" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_accountSettingsFragment)
            }

            "app_updates" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_updateSettingsFragment)
            }

            "reports" -> {
                if (isPremium) {
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_reportsFragment)
                } else {
                    showPremiumFeatureDialog("Business reports")
                }
            }

            "recycling_bin" -> {
                if (isPremium) {
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_recyclingBinFragment)
                } else {
                    showPremiumFeatureDialog("Recycling Bin")
                }
            }
        }
    }

    private fun showPremiumFeatureDialog(featureName: String) {
        ThemedM3Dialog(requireContext()).setTitle("✨ Unlock Premium ✨")
            .setLayout(R.layout.dialog_confirmation) // Use a layout with a TextView
            .apply {
                // Set the message in the custom layout
                findViewById<TextView>(R.id.confirmationMessage)?.text =
                    "Unlock powerful features like '$featureName' by upgrading to Premium! Enhance your business management today."
            }.setPositiveButton("Upgrade Now") { dialog, _ ->
                startActivity(Intent(requireContext(), UpgradeActivity::class.java))
                dialog.dismiss()
            }.setNegativeButton("Maybe Later") { dialog ->
                dialog.dismiss()
            }.show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}