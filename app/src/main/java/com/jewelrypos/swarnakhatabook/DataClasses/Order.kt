package com.jewelrypos.swarnakhatabook.DataClasses

data class Order(
    val id: String = "",
    val orderNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val orderDate: Long = System.currentTimeMillis(),
    val deliveryDate: Long = System.currentTimeMillis(),
    val items: List<OrderItem> = listOf(),
    val totalAmount: Double = 0.0,
    val advanceAmount: Double = 0.0,
    val status: String = "Pending",
    val notes: String = ""
)