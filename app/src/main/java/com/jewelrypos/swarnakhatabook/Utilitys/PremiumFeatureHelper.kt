package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.Enums.SubscriptionPlan
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.SwarnaKhataBook
import com.jewelrypos.swarnakhatabook.UpgradeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for handling premium feature access across the app
 * Ensures consistent behavior for premium feature checks
 */
class PremiumFeatureHelper {
    companion object {
        private const val TAG = "PremiumFeatureHelper"

        /**
         * Checks if the user has access to a feature based on their subscription plan
         * and executes appropriate action.
         *
         * @param fragment The fragment where the check is being performed
         * @param featureName Name of the premium feature being accessed
         * @param minimumPlan Optional. The minimum subscription plan required for the feature. If null,
         *                    the check will pass for any non-NONE plan (backward compatibility).
         * @param showToast Whether to show a toast message for non-premium users
         * @param premiumAction Action to execute if user has premium access
         * @param nonPremiumAction Optional action to execute if user doesn't have premium (defaults to showing upgrade dialog)
         */
        fun checkPremiumAccess(
            fragment: Fragment,
            featureName: String,
            minimumPlan: SubscriptionPlan? = null, // Make minimumPlan optional
            showToast: Boolean = true,
            premiumAction: () -> Unit,
            nonPremiumAction: (() -> Unit)? = null
        ) {
            val context = fragment.requireContext()
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val hasAccess = withContext(Dispatchers.IO) {
                    if (minimumPlan != null) {
                        // If a minimum plan is specified, check against it
                        subscriptionManager.hasMinimumPlan(minimumPlan)
                    } else {
                        // If no minimum plan is specified, check if it's any paid plan (backward compatibility)
                        subscriptionManager.isPremiumUser()
                    }
                }

                if (hasAccess) {
                    // User has access to the feature, execute the premium action
                    premiumAction()
                } else {
                    // User doesn't have access to the feature
                    if (showToast) {
                        val message = if (minimumPlan != null) {
                            "A ${minimumPlan.name} or higher subscription is required for $featureName"
                        } else {
                            "Premium subscription required for $featureName"
                        }
                        Toast.makeText(
                            context,
                            message,
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
         * Asynchronously checks if user has the minimum required plan and returns result via callback
         * Useful for UI initialization that needs to know plan status
         */
        fun hasMinimumPlan(fragment: Fragment, minimumPlan: SubscriptionPlan, callback: (Boolean) -> Unit) {
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val hasAccess = withContext(Dispatchers.IO) {
                    subscriptionManager.hasMinimumPlan(minimumPlan)
                }
                callback(hasAccess)
            }
        }



        /**
         * Shows the premium feature upgrade dialog
         */
        fun showPremiumFeatureDialog(context: Context, featureName: String) {
            Log.d(TAG, "showPremiumFeatureDialog: Showing dialog for feature: $featureName")
            val startTime = System.currentTimeMillis()

            ThemedM3Dialog(context).setTitle("✨ Unlock Premium ✨")
                .setLayout(R.layout.dialog_confirmation)
                .apply {
                    findViewById<TextView>(R.id.confirmationMessage)?.text =
                        context.getString(
                            R.string.unlock_powerful_features_like_by_upgrading_to_premium_enhance_your_business_management_today,
                            featureName
                        )
                }.setPositiveButton(context.getString(R.string.upgrade_now)) { dialog, _ ->
                    Log.d(TAG, "showPremiumFeatureDialog: User clicked upgrade for: $featureName")
                    context.startActivity(Intent(context, UpgradeActivity::class.java))
                    dialog.dismiss()
                }.setNegativeButton(context.getString(R.string.maybe_later)) { dialog ->
                    Log.d(
                        TAG,
                        "showPremiumFeatureDialog: User clicked maybe later for: $featureName"
                    )
                    dialog.dismiss()
                }.show()

            Log.d(
                TAG,
                "showPremiumFeatureDialog: Dialog shown in ${System.currentTimeMillis() - startTime}ms"
            )
        }

        /**
         * Asynchronously checks if user has premium and returns result via callback
         * Useful for UI initialization that needs to know premium status
         * @return The created Job that can be tracked or cancelled by the caller
         */
        fun isPremiumUser(fragment: Fragment, callback: (Boolean) -> Unit): Job {
            Log.d(TAG, "isPremiumUser: Starting premium check")
            val startTime = System.currentTimeMillis()
            return fragment.viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val isPremium = isPremiumUserSync()
                    callback(isPremium)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking premium status: ${e.message}", e)
                    callback(false)
                } finally {
                    Log.d(
                        TAG,
                        "isPremiumUser: Total execution time: ${System.currentTimeMillis() - startTime}ms"
                    )
                }
            }
        }

        suspend fun isPremiumUserSync(): Boolean {
            Log.d(TAG, "isPremiumUserSync: Starting synchronous premium check")
            val startTime = System.currentTimeMillis()
            return try {
                val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()
                Log.d(TAG, "isPremiumUserSync: Using subscriptionManager: $subscriptionManager")
                val isPremium = subscriptionManager.isPremiumUser()
                Log.d(
                    TAG,
                    "isPremiumUserSync: Premium check completed in ${System.currentTimeMillis() - startTime}ms. Result: $isPremium"
                )
                isPremium
            } catch (e: Exception) {
                Log.e(TAG, "Error checking premium status: ${e.message}", e)
                false
            }
        }
    }
} 