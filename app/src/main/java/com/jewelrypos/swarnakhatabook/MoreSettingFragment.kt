package com.jewelrypos.swarnakhatabook

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.widget.TextView
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.BadgeType
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.Factorys.SettingsViewModelFactory
import com.jewelrypos.swarnakhatabook.ViewModle.SettingsViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentMoreSettingBinding
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences

class MoreSettingFragment : Fragment() {
    private var _binding: FragmentMoreSettingBinding? = null
    private val binding get() = _binding!!

    // Reference to ViewModel
    private lateinit var viewModel: SettingsViewModel

    // Cache ColorStateLists and other resources for reuse
    private lateinit var colorCache: SettingsColorCache

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoreSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ColorCache for efficient resource loading
        colorCache = SettingsColorCache(requireContext())

        // Initialize ViewModel through factory
        val factory = SettingsViewModelFactory(
            requireActivity().application,
            SwarnaKhataBook.getUserSubscriptionManager()
        )
        viewModel = ViewModelProvider(this, factory)[SettingsViewModel::class.java]

        // Set up observers for the ViewModel data
        setupObservers()
    }

    private fun setupObservers() {
        // Combined observer pattern for efficient updates
        viewModel.settingsItems.observe(viewLifecycleOwner) { items ->
            if (items.isNotEmpty()) {
                // Get premium status
                val isPremium = viewModel.isPremium.value ?: false

                // Populate the settings
                populateSettings(items, isPremium)
            }
        }

        // Observe premium status changes separately
        viewModel.isPremium.observe(viewLifecycleOwner) { isPremium ->
            viewModel.settingsItems.value?.let { items ->
                populateSettings(items, isPremium)
            }
        }
    }

    private fun populateSettings(items: List<SettingsItem>, isPremium: Boolean) {
        // Clear all existing settings views first
        binding.settingsContainer.removeAllViews()

        // Create and add each settings item directly
        items.forEach { item ->
            val settingView = createSettingItemView(item, isPremium)
            binding.settingsContainer.addView(settingView)
        }
    }

    private fun createSettingItemView(item: SettingsItem, isPremium: Boolean): View {
        val inflater = LayoutInflater.from(requireContext())
        val itemView = inflater.inflate(R.layout.item_setting, binding.settingsContainer, false)

        // Get references to views
        val cardView = itemView as MaterialCardView
        val iconView: ImageView = itemView.findViewById(R.id.settingIconView)
        val titleView: TextView = itemView.findViewById(R.id.settingTitle)
        val subtitleView: TextView = itemView.findViewById(R.id.settingSubtitle)
        val badgeView: TextView = itemView.findViewById(R.id.newBadge)

        // Set content
        iconView.setImageResource(item.iconResId)
        titleView.text = getString(item.titleResId)

        // Handle subtitle with potential format args
        subtitleView.text = if (item.subtitleResIdArgs != null) {
            getString(item.subtitleResId, *item.subtitleResIdArgs.toTypedArray())
        } else {
            getString(item.subtitleResId)
        }

        // Badge logic
        if (item.badgeTextResId != null) {
            badgeView.visibility = View.VISIBLE

            // Resolve badge text with potential format args
            badgeView.text = if (item.badgeTextResIdArgs != null) {
                getString(item.badgeTextResId, *item.badgeTextResIdArgs.toTypedArray())
            } else {
                getString(item.badgeTextResId)
            }

            // Apply badge styling from cache
            val (badgeBackground, badgeTextColor) = colorCache.getBadgeStyling(item.badgeType)
            badgeView.backgroundTintList = badgeBackground
            badgeView.setTextColor(badgeTextColor)
        } else {
            badgeView.visibility = View.GONE
        }

        // Apply icon tint and background
        iconView.imageTintList = colorCache.whiteIconTint
        iconView.backgroundTintList = colorCache.getIconBackgroundTint(item.id)

        // Set click listener
        cardView.setOnClickListener {
            handleNavigation(item, isPremium)
        }

        return cardView
    }

    // Color cache class to efficiently manage resources
    inner class SettingsColorCache(context: android.content.Context) {
        // Icon tint
        val whiteIconTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.white)

        // Badge colors
        private val premiumBadgeBackground = androidx.core.content.ContextCompat.getColorStateList(context, R.color.premium_color)
        private val newBadgeBackground = androidx.core.content.ContextCompat.getColorStateList(context, R.color.status_paid)
        private val daysLeftBadgeBackground = androidx.core.content.ContextCompat.getColorStateList(context, R.color.status_unpaid)
        private val defaultBadgeBackground = androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_light_secondary)

        // Text colors
        private val blackTextColor = androidx.core.content.ContextCompat.getColor(context, R.color.black)
        private val whiteTextColor = androidx.core.content.ContextCompat.getColor(context, R.color.white)

        // Default background
        private val defaultIconBackgroundTint = androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_grey_color)

        // Icon background tints
        private val iconBackgroundTints: Map<String, android.content.res.ColorStateList?> = mapOf(
            "debug_subscription" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_pink_color),
            "subscription_status" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_gold_color),
            "shop_details" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_green_color),
            "invoice_format" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_purple_color),
            "invoice_template" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_blue_color),
            "reports" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_yellow_color),
            "recycling_bin" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_red_color),
            "account_settings" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_orange_color),
            "app_updates" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_teal_color),
            "about_language" to androidx.core.content.ContextCompat.getColorStateList(context, R.color.my_background_light_pink_color)
        )

        fun getBadgeStyling(badgeType: BadgeType): Pair<android.content.res.ColorStateList?, Int> {
            return when (badgeType) {
                BadgeType.PREMIUM -> premiumBadgeBackground to whiteTextColor
                BadgeType.NEW -> newBadgeBackground to whiteTextColor
                BadgeType.DAYS_LEFT, BadgeType.EXPIRED -> daysLeftBadgeBackground to whiteTextColor
                BadgeType.TRIAL -> defaultBadgeBackground to whiteTextColor
                BadgeType.NONE -> defaultBadgeBackground to whiteTextColor
            }
        }

        fun getIconBackgroundTint(itemId: String): android.content.res.ColorStateList? {
            return iconBackgroundTints[itemId] ?: defaultIconBackgroundTint
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
                if (!isPremium) {
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_upgradeActivity)
                } else {
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_subscriptionStatusFragment)
                }
            }

            "shop_details" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_shopSettingsFragment)
            }

            "invoice_format" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_invoicePdfSettingsFragment)
            }

            "invoice_template" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_templateSelectionFragment)
            }

            "account_settings" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_accountSettingsFragment)
            }

            "app_updates" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_updateSettingsFragment)
            }

            "reports" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_reportsActivity)
            }

            "recycling_bin" -> {
                mainNavController.navigate(R.id.action_mainScreenFragment_to_recyclingBinFragment)
            }

            "about_language" -> {
                showLanguageSelectionDialog()
            }
        }
    }

    private fun showPremiumFeatureDialog(featureName: String) {
        ThemedM3Dialog(requireContext()).setTitle("✨ Unlock Premium ✨")
            .setLayout(R.layout.dialog_confirmation)
            .apply {
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
        // Save the selected language to SecurePreferences
        val preferences = SecurePreferences.getInstance(requireContext())
        preferences.edit().putString("selected_language", lang).apply()

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
        _binding = null
    }

}