package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName

data class NotificationPreferences(
    @PropertyName("paymentDue")
    val paymentDue: Boolean = true,
    @PropertyName("paymentOverdue")
    val paymentOverdue: Boolean = false,
    @PropertyName("creditLimit")
    val creditLimit: Boolean = false,
    @PropertyName("lowStock")
    val lowStock: Boolean = false,
    @PropertyName("businessInsights")
    val businessInsights: Boolean = false,
    @PropertyName("customerBirthday")
    val customerBirthday: Boolean = false,
    @PropertyName("customerAnniversary")
    val customerAnniversary: Boolean = false,
    @PropertyName("appUpdates")
    val appUpdates: Boolean = true,
    @PropertyName("paymentDueReminderDays")
    val paymentDueReminderDays: Int = 3,
    @PropertyName("paymentOverdueAlertDays")
    val paymentOverdueAlertDays: Int = 1
)