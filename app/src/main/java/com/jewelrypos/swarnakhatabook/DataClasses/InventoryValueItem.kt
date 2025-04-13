package com.jewelrypos.swarnakhatabook.DataClasses

data class InventoryValueItem(
    val id: String,
    val name: String,
    val itemType: String,
    val code: String,
    val stock: Double,
    val stockUnit: String,
    val metalValue: Double,
    val makingValue: Double,
    val diamondValue: Double,
    val totalItemValue: Double,
    val totalStockValue: Double
)