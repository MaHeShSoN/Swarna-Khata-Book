package com.jewelrypos.swarnakhatabook

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.widget.TextView
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.SettingsAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.Factorys.SettingsViewModelFactory
import com.jewelrypos.swarnakhatabook.ViewModle.SettingsViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentMoreSettingBinding
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences

class MoreSettingFragment : Fragment() {
    private var _binding: FragmentMoreSettingBinding? = null
    private val binding get() = _binding!!

    // Reference to ViewModel (will be initialized in onViewCreated)
    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMoreSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize RecyclerView with layout manager
        binding.settingsRecyclerView.layoutManager = LinearLayoutManager(context)
        
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
        // Observer for loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show/hide recycler view based on loading state
            binding.settingsRecyclerView.visibility = if (isLoading) View.GONE else View.VISIBLE
        }

        // Observer for settings items list
        viewModel.settingsItems.observe(viewLifecycleOwner) { items ->
            // When items are available, ensure we also have premium status
            viewModel.isPremium.value?.let { isPremium ->
                setupAdapter(items, isPremium)
            }
        }
    }

    private fun setupAdapter(items: List<SettingsItem>, isPremium: Boolean) {
        // Create adapter with items and premium status
        val adapter = SettingsAdapter(items, isPremium) { item ->
            handleNavigation(item, isPremium)
        }
        
        // Set adapter to recycler view
        binding.settingsRecyclerView.adapter = adapter
        binding.settingsRecyclerView.visibility = View.VISIBLE
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
                mainNavController.navigate(R.id.action_mainScreenFragment_to_reportsFragment)
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
        _binding = null // Prevent memory leaks
    }
}