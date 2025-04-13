package com.jewelrypos.swarnakhatabook.DataClasses

import java.util.Date

data class LowStockItem(
    val id: String,
    val name: String,
    val code: String,
    val itemType: String,
    val currentStock: Double,
    val stockUnit: String,
    val reorderLevel: Double,
    val lastSoldDate: Date?
)