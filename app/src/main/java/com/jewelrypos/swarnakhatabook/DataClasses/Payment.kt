package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import java.util.UUID

data class Payment(
    @PropertyName("id")
    val id: String = UUID.randomUUID().toString(),
    @PropertyName("amount")
    val amount: Double = 0.0,
    @PropertyName("method")
    val method: String = "",
    @PropertyName("date")
    val date: Long = System.currentTimeMillis(),
    @PropertyName("reference")
    val reference: String = "",
    @PropertyName("details")
    val details: Map<String, Any> = mapOf(), // For storing method-specific details
    @PropertyName("notes")
    val notes: String = "",
) {
    // Equality based on the unique ID
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Payment

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    // Helper method to check if payments are essentially the same (for business logic)
    fun isSamePayment(other: Payment): Boolean {
        return amount == other.amount &&
                method == other.method &&
                date == other.date
    }
}
