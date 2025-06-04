package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class LowStockItem(
    @PropertyName("id")
    val id: String,
    @PropertyName("name")
    val name: String,
    @PropertyName("itemType")
    val itemType: String,
    @PropertyName("currentStock")
    val currentStock: Double,
    @PropertyName("stockUnit")
    val stockUnit: String,
    @PropertyName("reorderLevel")
    val reorderLevel: Double,
    @PropertyName("lastSoldDate")
    val lastSoldDate: Date?
)