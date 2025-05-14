package com.jewelrypos.swarnakhatabook.DataClasses

data class SelectedItemWithPrice(
    val item: JewelleryItem,
    val quantity: Int,
    val price: Double,
    val usedWeight: Double = 0.0, // Used weight for weight-based items (in grams)
)