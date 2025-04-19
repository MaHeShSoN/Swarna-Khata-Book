package com.jewelrypos.swarnakhatabook.Enums

/**
 * Represents the different subscription plans available in the app
 */
enum class SubscriptionPlan {
    /**
     * No active subscription
     */
    NONE,
    
    /**
     * Basic plan (₹99/month)
     * - 1 Shop Profile
     * - 100 Customer Entries
     * - 100 Inventory Items
     * - 100 Invoices per month
     * - Basic Sales Reports
     * - Standard Invoice Template
     * - Community Support
     */
    BASIC,
    
    /**
     * Standard plan (₹199/month)
     * - 2 Shop Profiles
     * - Unlimited Customer Entries
     * - Unlimited Inventory Items
     * - Unlimited Invoices
     * - All Standard Reports
     * - Multiple Invoice Templates
     * - Basic Customization
     * - PIN Security
     * - Low Stock Notifications
     * - Data Export
     * - Recycling Bin
     * - Standard Support
     */
    STANDARD,
    
    /**
     * Premium plan (₹299/month)
     * - 3 Shop Profiles
     * - All Standard Plan features
     * - Multi-User Access
     * - Full Invoice Customization
     * - Advanced Notifications
     * - Business Insight Reports
     * - Data Backup & Restore
     * - Priority Support
     */
    PREMIUM
}
