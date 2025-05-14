package com.jewelrypos.swarnakhatabook.DataClasses

import com.jewelrypos.swarnakhatabook.Enums.InventoryType

data class JewelleryItem(
    val id: String = "",
    val displayName: String = "",
    val itemType: String = "",
    val category: String = "",
    val grossWeight: Double = 0.0,
    val netWeight: Double = 0.0,
    val wastage: Double = 0.0,
    val wastageType: String = "",
    val purity: String = "",
    val makingCharges: Double = 0.0,
    val makingChargesType: String = "",
    val stock: Double = 0.0,
    val stockUnit: String = "",
    val location: String = "",
    val diamondPrice: Double = 0.0,
    val metalRate: Double = 0.0,
    val metalRateOn: String = "",
    val taxRate: Double = 0.0,
    val totalTax: Double = 0.0,
    val listOfExtraCharges: List<ExtraCharge> = emptyList(),
    val inventoryType: InventoryType = InventoryType.IDENTICAL_BATCH,
    val totalWeightGrams: Double = 0.0,
    val imageUrl: String = ""
)