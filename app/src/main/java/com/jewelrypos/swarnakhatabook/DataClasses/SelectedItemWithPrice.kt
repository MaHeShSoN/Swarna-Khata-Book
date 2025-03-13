package com.jewelrypos.swarnakhatabook.DataClasses

data class SelectedItemWithPrice(
    val item: JewelleryItem,
    val quantity: Int,
    val price: Double
)