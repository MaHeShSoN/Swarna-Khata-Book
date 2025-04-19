package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.ServerTimestamp
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import java.util.Date

data class AppNotification(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val title: String = "",
    val message: String = "",
    val type: NotificationType = NotificationType.GENERAL,
    val status: NotificationStatus = NotificationStatus.UNREAD,
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val amount: Double? = null,
    val creditLimit: Double? = null,
    val currentBalance: Double? = null,
    val relatedItemId: String? = null,
    val relatedInvoiceId: String? = null,
    val stockLevel: Double? = null,
    val shopId: String? = null,  // Added shopId field for multi-user support
    val actionUrl: String? = null, // URL for app updates or external actions
    @ServerTimestamp val createdAt: Date? = null,
    val readAt: Date? = null,
    val actionTaken: Boolean = false
)