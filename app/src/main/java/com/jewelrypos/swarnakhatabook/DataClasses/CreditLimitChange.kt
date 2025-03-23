package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Data class to track changes to a customer's credit limit
 */
data class CreditLimitChange(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val previousLimit: Double = 0.0,
    val newLimit: Double = 0.0,
    val reason: String = "",
    val changedBy: String = "", // User ID or name
    @ServerTimestamp val changeDate: Date? = null
)