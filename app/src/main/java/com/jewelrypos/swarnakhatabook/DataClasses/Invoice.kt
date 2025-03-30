package com.jewelrypos.swarnakhatabook.DataClasses

data class Invoice(
    val id: String = "",
    val invoiceNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",        // Add this
    val customerAddress: String = "",      // Add this
    val invoiceDate: Long = System.currentTimeMillis(),
    val items: List<InvoiceItem> = listOf(),
    val payments: List<Payment> = listOf(),
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val notes: String = "",

    val fineGoldAmount: Double = 0.0,
    val fineSilverAmount: Double = 0.0,
    val originalTotalBeforeFine: Double = 0.0,
    val isMetalExchangeApplied: Boolean = false
)