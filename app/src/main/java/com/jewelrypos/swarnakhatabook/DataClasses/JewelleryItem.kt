package com.jewelrypos.swarnakhatabook.DataClasses

data class JewelleryItem(
    val id: String = "",
    val displayName: String = "",
    val jewelryCode: String = "",
    val itemType: String = "",
    val category: String = "",
    val grossWeight: Double = 0.0,
    val netWeight: Double = 0.0,
    val wastage: Double = 0.0,
    val purity: String = "",
    val makingCharges: Double = 0.0,
    val makingChargesType: String = "",
    val stock: Double = 0.0,
    val stockUnit: String = "",
    val location: String = ""
)