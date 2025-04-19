package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.Intent
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.SwarnaKhataBook
import com.jewelrypos.swarnakhatabook.UpgradeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for handling premium feature access across the app
 * Ensures consistent behavior for premium feature checks
 */
class PremiumFeatureHelper {
    companion object {
        /**
         * Checks if the user has premium access and executes appropriate action
         * 
         * @param fragment The fragment where the check is being performed
         * @param featureName Name of the premium feature being accessed
         * @param showToast Whether to show a toast message for non-premium users
         * @param premiumAction Action to execute if user has premium access
         * @param nonPremiumAction Optional action to execute if user doesn't have premium (defaults to showing upgrade dialog)
         */
        fun checkPremiumAccess(
            fragment: Fragment,
            featureName: String,
            showToast: Boolean = true,
            premiumAction: () -> Unit,
            nonPremiumAction: (() -> Unit)? = null
        ) {
            val context = fragment.requireContext()
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()
            
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val isPremium = withContext(Dispatchers.IO) {
                    subscriptionManager.isPremiumUser()
                }
                
                if (isPremium) {
                    // User has premium access, execute the premium action
                    premiumAction()
                } else {
                    // User doesn't have premium access
                    if (showToast) {
                        Toast.makeText(
                            context,
                            "Premium subscription required for $featureName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    // Execute non-premium action if provided, otherwise show upgrade dialog
                    if (nonPremiumAction != null) {
                        nonPremiumAction()
                    } else {
                        showPremiumFeatureDialog(context, featureName)
                    }
                }
            }
        }
        
        /**
         * Shows the premium feature upgrade dialog
         */
        fun showPremiumFeatureDialog(context: Context, featureName: String) {
            ThemedM3Dialog(context).setTitle("✨ Unlock Premium ✨")
                .setLayout(R.layout.dialog_confirmation)
                .apply {
                    findViewById<TextView>(R.id.confirmationMessage)?.text =
                        context.getString(
                            R.string.unlock_powerful_features_like_by_upgrading_to_premium_enhance_your_business_management_today,
                            featureName
                        )
                }.setPositiveButton(context.getString(R.string.upgrade_now)) { dialog, _ ->
                    context.startActivity(Intent(context, UpgradeActivity::class.java))
                    dialog.dismiss()
                }.setNegativeButton(context.getString(R.string.maybe_later)) { dialog ->
                    dialog.dismiss()
                }.show()
        }
        
        /**
         * Asynchronously checks if user has premium and returns result via callback
         * Useful for UI initialization that needs to know premium status
         */
        fun isPremiumUser(fragment: Fragment, callback: (Boolean) -> Unit) {
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()
            
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val isPremium = withContext(Dispatchers.IO) {
                    subscriptionManager.isPremiumUser()
                }
                callback(isPremium)
            }
        }
    }
} 