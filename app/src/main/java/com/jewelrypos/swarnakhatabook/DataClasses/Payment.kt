package com.jewelrypos.swarnakhatabook.DataClasses

import java.util.UUID

data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val method: String = "",
    val date: Long = System.currentTimeMillis(),
    val reference: String = "",
    val details: Map<String, Any> = mapOf(), // For storing method-specific details
    val notes: String = "",
    val invoiceNumber: String? = null,
    val customerName: String? = null
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
