package com.jewelrypos.swarnakhatabook.DataClasses

data class Payment(
    val id: String = "",
    val amount: Double = 0.0,
    val method: String = "",
    val date: Long = System.currentTimeMillis(),
    val reference: String = "",
    val details: Map<String, Any> = mapOf(), // For storing method-specific details
    val notes: String = ""
)