package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName

data class SelectedItemWithPrice(
    @PropertyName("item")
    val item: JewelleryItem,
    @PropertyName("quantity")
    val quantity: Int,
    @PropertyName("price")
    val price: Double,
    @PropertyName("usedWeight")
    val usedWeight: Double = 0.0, // Used weight for weight-based items (in grams)
)