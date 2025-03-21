package com.jewelrypos.swarnakhatabook.DataClasses

data class InvoiceItem(
    val itemId: String = "",
    val quantity: Int = 0,
    val itemDetails: JewelleryItem = JewelleryItem(),
    val price: Double = 0.0
)