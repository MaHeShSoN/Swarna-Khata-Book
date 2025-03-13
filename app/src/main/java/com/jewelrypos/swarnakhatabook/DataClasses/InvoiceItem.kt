package com.jewelrypos.swarnakhatabook.DataClasses

data class InvoiceItem(
    val itemId: String,
    val quantity: Int,
    val itemDetails: JewelleryItem,
    val price: Double,
    val discount: Double = 0.0,
    val notes: String = ""
)