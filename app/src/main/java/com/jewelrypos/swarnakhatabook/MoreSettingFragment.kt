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
import androidx.lifecycle.lifecycleScope // Ensure this is imported
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.SettingsAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.databinding.FragmentMoreSettingBinding
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Import withContext

class MoreSettingFragment : Fragment() {
    private var _binding: FragmentMoreSettingBinding? = null
    private val binding get() = _binding!!

    // Use the optimized adapter if you applied the previous suggestion
    // Make sure the adapter constructor matches the one you are using
    // (e.g., if you adopted the optimized one, it might not need isPremium passed directly)
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

    // --- Fixed setupSettingsList ---
    private fun setupSettingsList() {
        // Launch coroutine on the main thread initially
        viewLifecycleOwner.lifecycleScope.launch {
            // Only access binding if view is attached
            if (_binding == null) return@launch
            binding.settingsRecyclerView.visibility = View.GONE

            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

            // Perform potentially slow operations on the IO dispatcher
            val (isPremium, daysRemaining) = withContext(Dispatchers.IO) {
                // isPremiumUser involves a network call, getDaysRemaining reads prefs (fast)
                val premiumStatus = subscriptionManager.isPremiumUser()
                val remainingDays = subscriptionManager.getDaysRemaining()
                // Return both results as a Pair
                Pair(premiumStatus, remainingDays)
            }

            // Only access binding if view is attached
            if (_binding == null) return@launch
            // Now that background work is done, create the list (fast)
            val subscriptionBadgeText = if (isPremium) {
                getString(R.string.premium)
            } else if (daysRemaining <= 3 && daysRemaining > 0) {
                "$daysRemaining DAYS LEFT"
            } else if (daysRemaining == 0 && !isPremium) {
                getString(R.string.expired)
            } else {
                getString(R.string.trial)
            }

            val settingsItems = mutableListOf(
                SettingsItem(
                    id = "debug_subscription",
                    title = "Debug Subscription",
                    subtitle = "Developer tools for testing subscription features",
                    iconResId = R.drawable.ic_order // Example icon
                ), SettingsItem(
                    id = "subscription_status",
                    title = if (isPremium) getString(R.string.premium_subscription) else getString(R.string.free_trial),
                    subtitle = if (isPremium) getString(R.string.you_have_access_to_all_premium_features)
                    else "Trial ends in $daysRemaining days",
                    iconResId = R.drawable.fluent__premium_24_regular,
                    badgeText = subscriptionBadgeText
                ), SettingsItem(
                    id = "shop_details",
                    title = getString(R.string.shop_details),
                    subtitle = getString(R.string.configure_your_shop_information_for_invoices),
                    iconResId = R.drawable.stash__shop
                ), SettingsItem(
                    id = "invoice_format",
                    title = getString(R.string.invoice_pdf_format),
                    subtitle = getString(R.string.customize_the_appearance_of_your_invoice_pdfs),
                    iconResId = R.drawable.mdi__invoice_text_edit_outline
                ), SettingsItem( // Keep invoice_template separate from invoice_format
                    id = "invoice_template",
                    title = getString(R.string.invoice_template_color), // Updated title
                    subtitle = getString(R.string.choose_template_and_theme_color), // Updated subtitle
                    iconResId = R.drawable.ic_template, // Specific icon for templates/colors
                    badgeText = if (!isPremium) getString(R.string.premium) else null
                ), SettingsItem(
                    id = "reports",
                    title = getString(R.string.reports),
                    subtitle = getString(R.string.view_and_export_business_reports_and_analytics),
                    iconResId = R.drawable.icon_park_outline__sales_report,
                    badgeText = if (!isPremium) getString(R.string.premium) else null
                ), SettingsItem(
                    id = "recycling_bin",
                    title = getString(R.string.recycling_bin),
                    subtitle = getString(R.string.recover_deleted_invoices_customers_and_items),
                    iconResId = R.drawable.solar__trash_bin_trash_line_duotone,
                    badgeText = if (!isPremium) getString(R.string.premium) else null
                ), SettingsItem(
                    id = "account_settings",
                    title = getString(R.string.account_settings),
                    subtitle = getString(R.string.manage_app_lock_security_and_account_options),
                    iconResId = R.drawable.material_symbols__account_circle_outline
                ), SettingsItem(
                    id = "app_updates",
                    title = getString(R.string.app_updates),
                    subtitle = getString(R.string.manage_automatic_updates_and_check_for_new_versions),
                    iconResId = R.drawable.material_symbols__refresh_rounded
                ), SettingsItem(
                    id = "about_language",
                    title = getString(R.string.about_language),
                    subtitle = getString(R.string.choose_language),
                    iconResId = R.drawable.uil__language // You may need to add a language icon drawable
                )
            )

            // Switch back to the Main dispatcher to update the UI
            // No need for runOnUiThread here as we are already back on Main
            // after withContext finishes (unless launch started on a different dispatcher)
            // but explicitly using withContext(Dispatchers.Main) is clearer.

            // Note: If using the optimized SettingsAdapter, you might not need 'isPremium' here.
            // Adjust the adapter instantiation based on the adapter version you are using.
            // Example using the optimized adapter constructor:
            settingsAdapter = SettingsAdapter(
                settingsItems, isPremium
            ) { item -> // Pass isPremium if still needed by adapter or click handler
                handleNavigation(item, isPremium) // Pass isPremium to handler
            }

            // Only access binding if view is attached
            if (_binding == null) return@launch
            binding.settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = settingsAdapter
            }

            // Hide loading indicator and show RecyclerView
            binding.settingsRecyclerView.visibility = View.VISIBLE
        }
    }
    // --- End of Fixed setupSettingsList ---


    // Function to handle navigation based on item clicked
    // (Pass isPremium fetched in the background)
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
                // Still check premium status for template customization
                // but don't block navigation
                mainNavController.navigate(R.id.action_mainScreenFragment_to_templateSelectionFragment)
            }

            "account_settings" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_accountSettingsFragment)
            }

            "app_updates" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_updateSettingsFragment)
            }

            "reports" -> {
                // Always allow navigation to reports
                mainNavController.navigate(R.id.action_mainScreenFragment_to_reportsFragment)
                // Premium status will be checked within the fragment
            }

            "recycling_bin" -> {
                // Always allow navigation to recycling bin
                mainNavController.navigate(R.id.action_mainScreenFragment_to_recyclingBinFragment)
                // Premium status will be checked within the fragment for restore operations
            }

            "about_language" -> {
                showLanguageSelectionDialog()
            }
        }
    }

    private fun showPremiumFeatureDialog(featureName: String) {
        ThemedM3Dialog(requireContext()).setTitle("✨ Unlock Premium ✨")
            .setLayout(R.layout.dialog_confirmation) // Use a layout with a TextView
            .apply {
                // Set the message in the custom layout
                findViewById<TextView>(R.id.confirmationMessage)?.text =
                    getString(
                        R.string.unlock_powerful_features_like_by_upgrading_to_premium_enhance_your_business_management_today,
                        featureName
                    )
            }.setPositiveButton(getString(R.string.upgrade_now)) { dialog, _ ->
                startActivity(Intent(requireContext(), UpgradeActivity::class.java))
                dialog.dismiss()
            }.setNegativeButton(getString(R.string.maybe_later)) { dialog ->
                dialog.dismiss()
            }.show()
    }

    private fun showLanguageSelectionDialog() {
        val languages =
            arrayOf(getString(R.string.language_english), getString(R.string.language_hindi))
        val codes = arrayOf("en", "hi")
        val currentLang = requireContext().resources.configuration.locales[0].language
        var selectedIdx = codes.indexOf(currentLang)
        if (selectedIdx == -1) selectedIdx = 0

        val builder = android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.choose_language))
            .setSingleChoiceItems(languages, selectedIdx) { dialog, which ->
                if (codes[which] != currentLang) {
                    setLocale(codes[which])
                }
                dialog.dismiss()
            }
        builder.show()
    }

    private fun setLocale(lang: String) {
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        requireActivity().baseContext.resources.updateConfiguration(
            config, requireActivity().baseContext.resources.displayMetrics
        )
        requireActivity().recreate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Prevent memory leaks
    }
}
