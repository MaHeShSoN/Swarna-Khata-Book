package com.jewelrypos.swarnakhatabook.DataClasses

data class Invoice(
    val id: String = "",
    val invoiceNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val invoiceDate: Long = System.currentTimeMillis(),
    val items: List<InvoiceItem> = listOf(),
    val payments: List<Payment> = listOf(),
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val relatedOrderId: String = "", // If converted from an order
    val notes: String = ""
)