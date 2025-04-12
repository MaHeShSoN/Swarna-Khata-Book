package com.jewelrypos.swarnakhatabook

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
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


        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup settings list
        setupSettingsList()
    }

    private fun setupSettingsList() {
        lifecycleScope.launch {
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

            // Check subscription status
            val isPremium = subscriptionManager.isPremiumUser()
            val daysRemaining = subscriptionManager.getDaysRemaining()

            // Prepare subscription badge text
            val subscriptionBadgeText = if (isPremium) {
                "PREMIUM"
            } else if (daysRemaining <= 3) {
                "$daysRemaining DAYS LEFT"
            } else {
                "TRIAL"
            }

            // Create settings items list with subscription status
            val settingsItems = mutableListOf(
                SettingsItem(
                    id = "debug_subscription",
                    title = "Debug Subscription",
                    subtitle = "Developer tools for testing subscription features",
                    iconResId = R.drawable.ic_order
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
                    iconResId = R.drawable.ic_store
                ), SettingsItem(
                    id = "invoice_format",
                    title = "Invoice PDF Format",
                    subtitle = "Customize the appearance of your invoice PDFs",
                    iconResId = R.drawable.ic_invoice
                ), SettingsItem(
                    id = "invoice_template",
                    title = "Invoice Template",
                    subtitle = "Choose from multiple professional templates",
                    iconResId = R.drawable.ic_template,
                    badgeText = if (!isPremium) "PREMIUM" else null
                ), SettingsItem(
                    id = "account_settings",
                    title = "Account Settings",
                    subtitle = "Manage app lock, security and account options",
                    iconResId = R.drawable.ic_account
                ), SettingsItem(
                    id = "app_updates",
                    title = "App Updates",
                    subtitle = "Manage automatic updates and check for new versions",
                    iconResId = R.drawable.material_symbols__refresh_rounded
                )

            )

            // Update the UI on the main thread
            requireActivity().runOnUiThread {
                settingsAdapter = SettingsAdapter(settingsItems) { item ->
                    when (item.id) {
                        "debug_subscription" -> {
                            val mainNavController =
                                requireActivity().findNavController(R.id.nav_host_fragment)
                            mainNavController.navigate(R.id.action_mainScreenFragment_to_subscriptionDebugActivity)
                        }


                        "subscription_status" -> {
                            if (!isPremium) {
                                // Navigate to upgrade screen
                                val mainNavController =
                                    requireActivity().findNavController(R.id.nav_host_fragment)
                                mainNavController.navigate(R.id.action_mainScreenFragment_to_upgradeActivity)
                            } else {
                                Toast.makeText(
                                    context,
                                    "You already have a premium subscription",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        "shop_details" -> {
                            val mainNavController =
                                requireActivity().findNavController(R.id.nav_host_fragment)
                            mainNavController.navigate(R.id.action_mainScreenFragment_to_shopSettingsFragment)
                        }

                        "invoice_format" -> {
                            val mainNavController =
                                requireActivity().findNavController(R.id.nav_host_fragment)
                            mainNavController.navigate(R.id.action_mainScreenFragment_to_invoicePdfSettingsFragment)
                        }

                        "invoice_template" -> {
                            if (isPremium) {
                                val mainNavController =
                                    requireActivity().findNavController(R.id.nav_host_fragment)
                                mainNavController.navigate(R.id.action_mainScreenFragment_to_templateSelectionFragment)
                            } else {
                                // Show premium feature dialog
                                showPremiumFeatureDialog()
                            }
                        }

                        "account_settings" -> {
                            val mainNavController =
                                requireActivity().findNavController(R.id.nav_host_fragment)
                            mainNavController.navigate(R.id.action_mainScreenFragment_to_accountSettingsFragment)
                        }

                        "app_updates" -> {
                            val mainNavController =
                                requireActivity().findNavController(R.id.nav_host_fragment)
                            mainNavController.navigate(R.id.action_mainScreenFragment_to_updateSettingsFragment)
                        }
                    }
                }

                binding.settingsRecyclerView.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = settingsAdapter
                }
            }
        }
    }

    private fun showPremiumFeatureDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Premium Feature")
            .setMessage("Advanced invoice templates are only available with a premium subscription. Upgrade now to access all templates and features.")
            .setPositiveButton("Upgrade") { _, _ ->
                startActivity(Intent(requireContext(), UpgradeActivity::class.java))
            }.setNegativeButton("Not Now", null).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}