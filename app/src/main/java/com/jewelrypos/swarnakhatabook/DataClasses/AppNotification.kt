package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import java.util.Date

data class AppNotification(
    @PropertyName("id")
    val id: String = "",
    @PropertyName("customerId")
    val customerId: String = "",
    @PropertyName("customerName")
    val customerName: String = "",
    @PropertyName("title")
    val title: String = "",
    @PropertyName("message")
    val message: String = "",
    @PropertyName("type")
    val type: NotificationType = NotificationType.GENERAL,
    @PropertyName("status")
    val status: NotificationStatus = NotificationStatus.UNREAD,
    @PropertyName("priority")
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    @PropertyName("amount")
    val amount: Double? = null,
    @PropertyName("creditLimit")
    val creditLimit: Double? = null,
    @PropertyName("currentBalance")
    val currentBalance: Double? = null,
    @PropertyName("relatedItemId")
    val relatedItemId: String? = null,
    @PropertyName("relatedInvoiceId")
    val relatedInvoiceId: String? = null,
    @PropertyName("stockLevel")
    val stockLevel: Double? = null,
    @PropertyName("shopId")
    val shopId: String? = null,  // Added shopId field for multi-user support
    @PropertyName("actionUrl")
    val actionUrl: String? = null, // URL for app updates or external actions
    @ServerTimestamp 
    @PropertyName("createdAt")
    val createdAt: Date? = null,
    @PropertyName("readAt")
    val readAt: Date? = null,
    @PropertyName("actionTaken")
    val actionTaken: Boolean = false
)