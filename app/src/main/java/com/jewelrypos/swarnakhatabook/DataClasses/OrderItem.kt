package com.jewelrypos.swarnakhatabook.DataClasses

data class OrderItem(
    val itemId: String,
    val quantity: Int,
    val itemDetails: JewelleryItem,
    val notes: String = ""
)