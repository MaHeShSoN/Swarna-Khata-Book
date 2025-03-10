package com.jewelrypos.swarnakhatabook.DataClasses

data class JewelleryItem(
    val displayName: String,
    val jewelryCode: String,
    val itemType: String,
    val category: String,
    val grossWeight: Double,
    val netWeight: Double,
    val wastage: Double,
    val purity: String,
    val makingCharges: Double,
    val makingChargesType: String,
    val stock: Double,
    val stockUnit: String,
    val location: String
)