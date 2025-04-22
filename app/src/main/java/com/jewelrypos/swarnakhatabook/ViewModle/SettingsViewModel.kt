package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jewelrypos.swarnakhatabook.DataClasses.BadgeType
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the settings screen that manages subscription state and settings items list
 */
class SettingsViewModel(
    application: Application,
    private val subscriptionManager: UserSubscriptionManager
) : AndroidViewModel(application) {

    // LiveData for subscription status
    private val _isPremium = MutableLiveData<Boolean>()
    val isPremium: LiveData<Boolean> = _isPremium

    // LiveData for remaining days in trial
    private val _daysRemaining = MutableLiveData<Int>()
    val daysRemaining: LiveData<Int> = _daysRemaining

    // LiveData for settings items
    private val _settingsItems = MutableLiveData<List<SettingsItem>>()
    val settingsItems: LiveData<List<SettingsItem>> = _settingsItems

    // LiveData for badge type for subscription status
    private val _subscriptionBadgeType = MutableLiveData<BadgeType>()
    val subscriptionBadgeType: LiveData<BadgeType> = _subscriptionBadgeType

    // LiveData for badge text resource ID
    private val _subscriptionBadgeTextResId = MutableLiveData<Int?>(null)
    val subscriptionBadgeTextResId: LiveData<Int?> = _subscriptionBadgeTextResId
    
    // LiveData for badge text args
    private val _subscriptionBadgeTextArgs = MutableLiveData<List<Any>?>(null)
    val subscriptionBadgeTextArgs: LiveData<List<Any>?> = _subscriptionBadgeTextArgs

    // LiveData for loading state
    private val _isLoading = MutableLiveData<Boolean>(true)
    val isLoading: LiveData<Boolean> = _isLoading

    // Initialize by loading subscription data
    init {
        loadSubscriptionData()
    }

    /**
     * Load subscription data from the subscription manager on a background thread
     */
    private fun loadSubscriptionData() {
        _isLoading.value = true
        
        viewModelScope.launch {
            // Perform slow operations on IO dispatcher
            val (isPremiumStatus, remainingDays) = withContext(Dispatchers.IO) {
                val premium = subscriptionManager.isPremiumUser()
                val days = subscriptionManager.getDaysRemaining()
                Pair(premium, days)
            }

            // Update LiveData values
            _isPremium.value = isPremiumStatus
            _daysRemaining.value = remainingDays
            
            // Update badge info based on subscription status
            updateSubscriptionBadge(isPremiumStatus, remainingDays)
            
            // Create and set the settings items list
            _settingsItems.value = createSettingsItemsList(isPremiumStatus, remainingDays)
            
            // Complete loading
            _isLoading.value = false
        }
    }

    /**
     * Updates the subscription badge info based on subscription status
     */
    private fun updateSubscriptionBadge(isPremium: Boolean, daysRemaining: Int) {
        // Determine badge type
        val badgeType = when {
            isPremium -> BadgeType.PREMIUM
            daysRemaining <= 3 && daysRemaining > 0 -> BadgeType.DAYS_LEFT
            daysRemaining == 0 && !isPremium -> BadgeType.EXPIRED
            else -> BadgeType.TRIAL
        }
        
        // Set badge type
        _subscriptionBadgeType.value = badgeType
        
        // Set appropriate badge text resource ID and args based on type
        val (badgeTextResId, badgeTextArgs) = when (badgeType) {
            BadgeType.PREMIUM -> Pair(R.string.premium, null)
            BadgeType.DAYS_LEFT -> Pair(R.string.days_left_format, listOf(daysRemaining))
            BadgeType.EXPIRED -> Pair(R.string.expired, null)
            BadgeType.TRIAL -> Pair(R.string.trial, null)
            else -> Pair(null, null)
        }
        
        _subscriptionBadgeTextResId.value = badgeTextResId
        _subscriptionBadgeTextArgs.value = badgeTextArgs
    }

    /**
     * Create the list of settings items based on subscription status
     */
    private fun createSettingsItemsList(isPremium: Boolean, daysRemaining: Int): List<SettingsItem> {
        return mutableListOf(
//            SettingsItem(
//                id = "debug_subscription",
//                titleResId = R.string.debug_subscription,
//                subtitleResId = R.string.developer_tools_for_testing,
//                iconResId = R.drawable.ic_order
//            ),
            SettingsItem(
                id = "subscription_status",
                titleResId = if (isPremium) R.string.premium_subscription else R.string.free_trial,
                subtitleResId = if (isPremium) R.string.you_have_access_to_all_premium_features 
                               else R.string.trial_ends_in_days,
                subtitleResIdArgs = if (isPremium) null else listOf(daysRemaining),
                iconResId = R.drawable.fluent__premium_24_regular,
                badgeTextResId = _subscriptionBadgeTextResId.value,
                badgeTextResIdArgs = _subscriptionBadgeTextArgs.value,
                badgeType = _subscriptionBadgeType.value ?: BadgeType.NONE
            ),
            SettingsItem(
                id = "shop_details",
                titleResId = R.string.shop_details,
                subtitleResId = R.string.configure_your_shop_information_for_invoices,
                iconResId = R.drawable.stash__shop
            ),
            SettingsItem(
                id = "invoice_format",
                titleResId = R.string.invoice_pdf_format,
                subtitleResId = R.string.customize_the_appearance_of_your_invoice_pdfs,
                iconResId = R.drawable.mdi__invoice_text_edit_outline
            ),
            SettingsItem(
                id = "invoice_template",
                titleResId = R.string.invoice_template_color,
                subtitleResId = R.string.choose_template_and_theme_color,
                iconResId = R.drawable.ic_template,
                badgeTextResId = if (!isPremium) R.string.premium else null,
                badgeType = if (!isPremium) BadgeType.PREMIUM else BadgeType.NONE
            ),
            SettingsItem(
                id = "reports",
                titleResId = R.string.reports,
                subtitleResId = R.string.view_and_export_business_reports_and_analytics,
                iconResId = R.drawable.icon_park_outline__sales_report,
                badgeTextResId = if (!isPremium) R.string.premium else null,
                badgeType = if (!isPremium) BadgeType.PREMIUM else BadgeType.NONE
            ),
            SettingsItem(
                id = "recycling_bin",
                titleResId = R.string.recycling_bin,
                subtitleResId = R.string.recover_deleted_invoices_customers_and_items,
                iconResId = R.drawable.solar__trash_bin_trash_line_duotone,
                badgeTextResId = if (!isPremium) R.string.premium else null,
                badgeType = if (!isPremium) BadgeType.PREMIUM else BadgeType.NONE
            ),
            SettingsItem(
                id = "account_settings",
                titleResId = R.string.account_settings,
                subtitleResId = R.string.manage_app_lock_security_and_account_options,
                iconResId = R.drawable.material_symbols__account_circle_outline
            ),
            SettingsItem(
                id = "app_updates",
                titleResId = R.string.app_updates,
                subtitleResId = R.string.manage_automatic_updates_and_check_for_new_versions,
                iconResId = R.drawable.material_symbols__refresh_rounded
            ),
            SettingsItem(
                id = "about_language",
                titleResId = R.string.about_language,
                subtitleResId = R.string.choose_language,
                iconResId = R.drawable.uil__language
            )
        )
    }

    /**
     * Force refresh of subscription data (can be called after subscription changes)
     */
    fun refreshData() {
        loadSubscriptionData()
    }
} 