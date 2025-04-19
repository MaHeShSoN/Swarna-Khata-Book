package com.jewelrypos.swarnakhatabook.DataClasses

import com.jewelrypos.swarnakhatabook.Enums.SubscriptionPlan

/**
 * Defines the features and limits for each subscription plan
 */
data class SubscriptionFeatures(
    val maxShopProfiles: Int,
    val maxCustomerEntries: Int,
    val maxInventoryItems: Int,
    val maxMonthlyInvoices: Int,
    val hasAdvancedReports: Boolean,
    val hasMultipleInvoiceTemplates: Boolean,
    val hasInvoiceCustomization: Boolean,
    val hasFullInvoiceCustomization: Boolean,
    val hasPinSecurity: Boolean,
    val hasLowStockNotifications: Boolean,
    val hasDataExport: Boolean,
    val hasRecyclingBin: Boolean,
    val hasMultiUserAccess: Boolean,
    val hasAdvancedNotifications: Boolean,
    val hasDataBackupRestore: Boolean,
    val hasPrioritySupport: Boolean
) {
    companion object {
        /**
         * Get features for a subscription plan
         */
        fun forPlan(plan: SubscriptionPlan): SubscriptionFeatures {
            return when (plan) {
                SubscriptionPlan.NONE -> {
                    // Very limited features when no subscription is active
                    SubscriptionFeatures(
                        maxShopProfiles = 1,
                        maxCustomerEntries = 10,
                        maxInventoryItems = 10,
                        maxMonthlyInvoices = 10,
                        hasAdvancedReports = false,
                        hasMultipleInvoiceTemplates = false,
                        hasInvoiceCustomization = false,
                        hasFullInvoiceCustomization = false,
                        hasPinSecurity = false,
                        hasLowStockNotifications = false,
                        hasDataExport = false,
                        hasRecyclingBin = false,
                        hasMultiUserAccess = false,
                        hasAdvancedNotifications = false,
                        hasDataBackupRestore = false,
                        hasPrioritySupport = false
                    )
                }
                SubscriptionPlan.BASIC -> {
                    // Basic plan features
                    SubscriptionFeatures(
                        maxShopProfiles = 1,
                        maxCustomerEntries = 100,
                        maxInventoryItems = 100,
                        maxMonthlyInvoices = 100,
                        hasAdvancedReports = false,
                        hasMultipleInvoiceTemplates = false,
                        hasInvoiceCustomization = false,
                        hasFullInvoiceCustomization = false,
                        hasPinSecurity = false,
                        hasLowStockNotifications = false,
                        hasDataExport = false,
                        hasRecyclingBin = false,
                        hasMultiUserAccess = false,
                        hasAdvancedNotifications = false,
                        hasDataBackupRestore = false,
                        hasPrioritySupport = false
                    )
                }
                SubscriptionPlan.STANDARD -> {
                    // Standard plan features
                    SubscriptionFeatures(
                        maxShopProfiles = 2,
                        maxCustomerEntries = Int.MAX_VALUE,
                        maxInventoryItems = Int.MAX_VALUE,
                        maxMonthlyInvoices = Int.MAX_VALUE,
                        hasAdvancedReports = true,
                        hasMultipleInvoiceTemplates = true,
                        hasInvoiceCustomization = true,
                        hasFullInvoiceCustomization = false,
                        hasPinSecurity = true,
                        hasLowStockNotifications = true,
                        hasDataExport = true,
                        hasRecyclingBin = true,
                        hasMultiUserAccess = false,
                        hasAdvancedNotifications = false,
                        hasDataBackupRestore = false,
                        hasPrioritySupport = false
                    )
                }
                SubscriptionPlan.PREMIUM -> {
                    // Premium plan features
                    SubscriptionFeatures(
                        maxShopProfiles = 3,
                        maxCustomerEntries = Int.MAX_VALUE,
                        maxInventoryItems = Int.MAX_VALUE,
                        maxMonthlyInvoices = Int.MAX_VALUE,
                        hasAdvancedReports = true,
                        hasMultipleInvoiceTemplates = true,
                        hasInvoiceCustomization = true,
                        hasFullInvoiceCustomization = true,
                        hasPinSecurity = true,
                        hasLowStockNotifications = true,
                        hasDataExport = true,
                        hasRecyclingBin = true,
                        hasMultiUserAccess = true,
                        hasAdvancedNotifications = true,
                        hasDataBackupRestore = true,
                        hasPrioritySupport = true
                    )
                }
            }
        }
    }
}
