package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName

data class InventoryValueItem(
    @PropertyName("id")
    val id: String,
    @PropertyName("name")
    val name: String,
    @PropertyName("itemType")
    val itemType: String,
    @PropertyName("stock")
    val stock: Double,
    @PropertyName("stockUnit")
    val stockUnit: String,
    @PropertyName("metalValue")
    val metalValue: Double,
    @PropertyName("makingValue")
    val makingValue: Double,
    @PropertyName("diamondValue")
    val diamondValue: Double,
    @PropertyName("totalItemValue")
    val totalItemValue: Double,
    @PropertyName("totalStockValue")
    val totalStockValue: Double
)