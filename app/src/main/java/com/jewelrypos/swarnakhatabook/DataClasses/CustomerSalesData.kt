package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName

data class CustomerSalesData(
    @PropertyName("customerName")
    val customerName: String,
    @PropertyName("totalPurchaseValue")
    val totalPurchaseValue: Double,
    @PropertyName("invoiceCount")
    val invoiceCount: Int
)