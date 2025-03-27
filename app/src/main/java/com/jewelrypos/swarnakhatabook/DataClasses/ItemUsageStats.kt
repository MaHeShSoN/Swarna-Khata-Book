package com.jewelrypos.swarnakhatabook.DataClasses

data class ItemUsageStats(
    val totalInvoicesUsed: Int = 0,
    val totalQuantitySold: Int = 0,
    val totalRevenue: Double = 0.0,
    val lastSoldDate: Long = 0,
    val topCustomerName: String = "",
    val topCustomerQuantity: Int = 0
)