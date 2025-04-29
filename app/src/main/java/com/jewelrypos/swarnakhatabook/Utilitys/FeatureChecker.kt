package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.jewelrypos.swarnakhatabook.DataClasses.SubscriptionFeatures
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.SwarnaKhataBook
import com.jewelrypos.swarnakhatabook.UpgradeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Utility for checking feature access based on subscription plan
 */
object FeatureChecker {

    /**
     * Check if a feature is available and handle appropriately
     * @param context The context for showing dialogs
     * @param feature A lambda that checks the subscription features
     * @param onAvailable Called if the feature is available
     * @param featureName The name of the feature for the dialog (optional)
     */
    fun checkFeatureAccess(
        context: Context,
        feature: (SubscriptionFeatures) -> Boolean,
        onAvailable: () -> Unit,
        featureName: String = "This feature"
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            val isAvailable = withContext(Dispatchers.IO) {
                SwarnaKhataBook.getUserSubscriptionManager().isFeatureAvailable(feature)
            }
            
            if (isAvailable) {
                onAvailable()
            } else {
                showUpgradeDialog(context, featureName)
            }
        }
    }
    
    /**
     * Check feature access for fragments
     */
    fun checkFeatureAccess(
        fragment: Fragment,
        feature: (SubscriptionFeatures) -> Boolean,
        onAvailable: () -> Unit,
        featureName: String = "This feature"
    ) {
        fragment.activity?.let {
            checkFeatureAccess(it, feature, onAvailable, featureName)
        }
    }

    /**
     * Show a dialog prompting the user to upgrade
     */
    private fun showUpgradeDialog(context: Context, featureName: String) {
        AlertDialog.Builder(context)
            .setTitle("Upgrade Required")
            .setMessage("$featureName requires a higher subscription plan. Would you like to upgrade?")
            .setPositiveButton("Upgrade") { _, _ ->
                val intent = Intent(context, UpgradeActivity::class.java)
                context.startActivity(intent)
            }
            .setNegativeButton("Not Now", null)
            .show()
    }
    
    /**
     * Show a dialog prompting the user to upgrade due to reaching a specific feature limit
     */
    fun showUpgradeDialogForLimit(context: Context, featureType: String, limit: Int) {
        AlertDialog.Builder(context)
            .setTitle("$featureType Limit Reached")
            .setMessage("You've reached the maximum number of $featureType ($limit) allowed for your current plan. Upgrade for unlimited $featureType.")
            .setPositiveButton("Upgrade") { _, _ ->
                val intent = Intent(context, UpgradeActivity::class.java)
                context.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Check if user has reached shop count limit
     */
    fun checkShopCountLimit(context: Context, currentCount: Int, onAvailable: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val features = withContext(Dispatchers.IO) {
                SwarnaKhataBook.getUserSubscriptionManager().getActiveSubscriptionFeatures()
            }
            
            if (currentCount < features.maxShopProfiles) {
                onAvailable()
            } else {
                AlertDialog.Builder(context)
                    .setTitle("Shop Limit Reached")
                    .setMessage("You've reached the maximum shop profiles (${features.maxShopProfiles}) allowed for your plan. Upgrade to add more shops.")
                    .setPositiveButton("Upgrade") { _, _ ->
                        val intent = Intent(context, UpgradeActivity::class.java)
                        context.startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    
    /**
     * Check if monthly invoice limit has been reached (Basic plan)
     */
    fun checkMonthlyInvoiceLimit(context: Context, onAvailable: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()
            val features = withContext(Dispatchers.IO) {
                subscriptionManager.getActiveSubscriptionFeatures()
            }
            
            val currentCount = subscriptionManager.getMonthlyInvoiceCount()
            
            if (currentCount < features.maxMonthlyInvoices) {
                onAvailable()
            } else {
                AlertDialog.Builder(context)
                    .setTitle("Invoice Limit Reached")
                    .setMessage("You've reached the maximum number of invoices (${features.maxMonthlyInvoices}) allowed this month on your current plan. Upgrade for unlimited invoices.")
                    .setPositiveButton("Upgrade") { _, _ ->
                        val intent = Intent(context, UpgradeActivity::class.java)
                        context.startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    
    /**
     * Check if customer limit has been reached
     */
    fun checkCustomerLimit(context: Context, currentCount: Int, onAvailable: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val features = withContext(Dispatchers.IO) {
                SwarnaKhataBook.getUserSubscriptionManager().getActiveSubscriptionFeatures()
            }
            
            if (currentCount < features.maxCustomerEntries) {
                onAvailable()
            } else {
                AlertDialog.Builder(context)
                    .setTitle("Customer Limit Reached")
                    .setMessage("You've reached the maximum number of customers (${features.maxCustomerEntries}) allowed for your current plan. Upgrade for unlimited customers.")
                    .setPositiveButton("Upgrade") { _, _ ->
                        val intent = Intent(context, UpgradeActivity::class.java)
                        context.startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    
    /**
     * Check if inventory item limit has been reached
     */
    fun checkInventoryLimit(context: Context, currentCount: Int, onAvailable: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val features = withContext(Dispatchers.IO) {
                SwarnaKhataBook.getUserSubscriptionManager().getActiveSubscriptionFeatures()
            }
            
            if (currentCount < features.maxInventoryItems) {
                onAvailable()
            } else {
                AlertDialog.Builder(context)
                    .setTitle("Inventory Limit Reached")
                    .setMessage("You've reached the maximum number of inventory items (${features.maxInventoryItems}) allowed for your current plan. Upgrade for unlimited inventory.")
                    .setPositiveButton("Upgrade") { _, _ ->
                        val intent = Intent(context, UpgradeActivity::class.java)
                        context.startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    
    /**
     * Suspend function to check if inventory limit is reached without showing dialog
     * Returns pair of (isLimitReached, maxLimit)
     */
    suspend fun isInventoryLimitReached(context: Context, currentCount: Int): Pair<Boolean, Int> {
        val features = withContext(Dispatchers.IO) {
            SwarnaKhataBook.getUserSubscriptionManager().getActiveSubscriptionFeatures()
        }
        val maxLimit = features.maxInventoryItems
        return Pair(currentCount >= maxLimit, maxLimit)
    }
    
    /**
     * Suspend function to check if customer limit is reached without showing dialog
     * Returns pair of (isLimitReached, maxLimit)
     */
    suspend fun isCustomerLimitReached(context: Context, currentCount: Int): Pair<Boolean, Int> {
        val features = withContext(Dispatchers.IO) {
            SwarnaKhataBook.getUserSubscriptionManager().getActiveSubscriptionFeatures()
        }
        val maxLimit = features.maxCustomerEntries
        return Pair(currentCount >= maxLimit, maxLimit)
    }
}