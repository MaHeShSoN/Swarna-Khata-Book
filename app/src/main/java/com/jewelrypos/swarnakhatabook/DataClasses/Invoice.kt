package com.jewelrypos.swarnakhatabook.DataClasses

data class Invoice(
    var id: String = "",
    val invoiceNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",        // Add this
    val customerAddress: String = "",      // Add this
    val invoiceDate: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val items: List<InvoiceItem> = listOf(),
    val payments: List<Payment> = listOf(),
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val notes: String = "",
    val paymentStatus: String = "UNPAID"  // Add this field with default value
)