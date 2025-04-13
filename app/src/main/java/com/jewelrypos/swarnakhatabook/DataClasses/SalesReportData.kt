package com.jewelrypos.swarnakhatabook.DataClasses

data class SalesReportData(
    val totalSales: Double,
    val totalPaid: Double,
    val invoiceCount: Int,
    val salesByCategory: List<SalesByCategoryItem>,
    val salesByCustomerType: List<SalesByCustomerTypeItem>,
    val salesByDate: List<SalesByDateItem>
)