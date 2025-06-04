package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import com.jewelrypos.swarnakhatabook.Enums.SubscriptionPlan

/**
 * Defines the features and limits for each subscription plan
 */
data class SubscriptionFeatures(
    @PropertyName("maxShopProfiles")
    val maxShopProfiles: Int,
    @PropertyName("maxCustomerEntries")
    val maxCustomerEntries: Int,
    @PropertyName("maxInventoryItems")
    val maxInventoryItems: Int,
    @PropertyName("maxMonthlyInvoices")
    val maxMonthlyInvoices: Int,
    @PropertyName("hasAdvancedReports")
    val hasAdvancedReports: Boolean,
    @PropertyName("hasMultipleInvoiceTemplates")
    val hasMultipleInvoiceTemplates: Boolean,
    @PropertyName("hasInvoiceCustomization")
    val hasInvoiceCustomization: Boolean,
    @PropertyName("hasFullInvoiceCustomization")
    val hasFullInvoiceCustomization: Boolean,
    @PropertyName("hasPinSecurity")
    val hasPinSecurity: Boolean,
    @PropertyName("hasLowStockNotifications")
    val hasLowStockNotifications: Boolean,
    @PropertyName("hasDataExport")
    val hasDataExport: Boolean,
    @PropertyName("hasRecyclingBin")
    val hasRecyclingBin: Boolean,
    @PropertyName("hasMultiUserAccess")
    val hasMultiUserAccess: Boolean,
    @PropertyName("hasAdvancedNotifications")
    val hasAdvancedNotifications: Boolean,
    @PropertyName("hasDataBackupRestore")
    val hasDataBackupRestore: Boolean,
    @PropertyName("hasPrioritySupport")
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
