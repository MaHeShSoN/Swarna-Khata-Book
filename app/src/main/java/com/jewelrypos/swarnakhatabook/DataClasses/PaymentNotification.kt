package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.ServerTimestamp
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import java.util.Date

data class PaymentNotification(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val title: String = "",
    val message: String = "",
    val type: NotificationType = NotificationType.CREDIT_LIMIT,
    val status: NotificationStatus = NotificationStatus.UNREAD,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val amount: Double = 0.0,
    val creditLimit: Double = 0.0,
    val currentBalance: Double = 0.0,
    @ServerTimestamp val createdAt: Date? = null,
    val readAt: Date? = null,
    val actionTaken: Boolean = false
)