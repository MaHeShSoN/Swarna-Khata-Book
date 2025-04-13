package com.jewelrypos.swarnakhatabook.DataClasses

data class GstReportItem(
    val taxRate: Double,
    val taxableAmount: Double,
    val cgst: Double,
    val sgst: Double,
    val totalTax: Double
)