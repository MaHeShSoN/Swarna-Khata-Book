package com.jewelrypos.swarnakhatabook.DataClasses

data class SalesReportData(
    val totalSales: Double,
    val paidAmount: Double, // Renamed from totalPaid to match usage
    val unpaidAmount: Double, // Added missing field
    val collectionRate: Double, // Added missing field
    val invoiceCount: Int,
    val salesByCategory: List<SalesByCategoryItem>,
    val salesByCustomerType: List<SalesByCustomerTypeItem>,
    val salesByDate: List<SalesByDateItem>
    // Ensure SalesByCategoryItem, SalesByCustomerTypeItem, SalesByDateItem are defined correctly elsewhere
)