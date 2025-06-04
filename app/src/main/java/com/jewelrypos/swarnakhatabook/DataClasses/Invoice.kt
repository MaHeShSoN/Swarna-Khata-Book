package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName

data class Invoice(
    @PropertyName("id")
    var id: String = "",
    @PropertyName("invoiceNumber")
    val invoiceNumber: String = "",
    @PropertyName("customerId")
    val customerId: String = "",
    @PropertyName("customerName")
    val customerName: String = "",
    @PropertyName("customerPhone")
    val customerPhone: String = "",
    @PropertyName("customerAddress")
    val customerAddress: String = "",
    @PropertyName("invoiceDate")
    val invoiceDate: Long = System.currentTimeMillis(),
    @PropertyName("dueDate")
    val dueDate: Long? = null,
    @PropertyName("items")
    val items: List<InvoiceItem> = listOf(),
    @PropertyName("payments")
    val payments: List<Payment> = listOf(),
    @PropertyName("totalAmount")
    val totalAmount: Double = 0.0,
    @PropertyName("paidAmount")
    val paidAmount: Double = 0.0,
    @PropertyName("notes")
    val notes: String = "",
    @PropertyName("paymentStatus")
    val paymentStatus: String = "UNPAID",
    @PropertyName("keywords")
    var keywords: List<String> = emptyList()

)