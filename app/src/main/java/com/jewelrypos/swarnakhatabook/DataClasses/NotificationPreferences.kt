package com.jewelrypos.swarnakhatabook.DataClasses

data class NotificationPreferences(
    val paymentDue: Boolean = true,
    val paymentOverdue: Boolean = true,
    val creditLimit: Boolean = true,
    val lowStock: Boolean = true,
    val businessInsights: Boolean = true,
    val customerBirthday: Boolean = true,
    val customerAnniversary: Boolean = true,
    val appUpdates: Boolean = true,
    val paymentDueReminderDays: Int = 3,
    val paymentOverdueAlertDays: Int = 1
)