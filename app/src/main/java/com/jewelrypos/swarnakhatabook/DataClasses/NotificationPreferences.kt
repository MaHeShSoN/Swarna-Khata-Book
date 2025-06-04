package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName

data class NotificationPreferences(
    @PropertyName("paymentDue")
    val paymentDue: Boolean = true,
    @PropertyName("paymentOverdue")
    val paymentOverdue: Boolean = true,
    @PropertyName("creditLimit")
    val creditLimit: Boolean = true,
    @PropertyName("lowStock")
    val lowStock: Boolean = true,
    @PropertyName("businessInsights")
    val businessInsights: Boolean = true,
    @PropertyName("customerBirthday")
    val customerBirthday: Boolean = true,
    @PropertyName("customerAnniversary")
    val customerAnniversary: Boolean = true,
    @PropertyName("appUpdates")
    val appUpdates: Boolean = true,
    @PropertyName("paymentDueReminderDays")
    val paymentDueReminderDays: Int = 3,
    @PropertyName("paymentOverdueAlertDays")
    val paymentOverdueAlertDays: Int = 1
)