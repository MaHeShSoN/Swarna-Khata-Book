package com.jewelrypos.swarnakhatabook.DataClasses

data class CustomerSalesData(
    val customerName: String,
    val totalPurchaseValue: Double,
    val invoiceCount: Int
)