package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName

data class SalesReportData(
    @PropertyName("totalSales")
    val totalSales: Double,
    @PropertyName("paidAmount")
    val paidAmount: Double, // Renamed from totalPaid to match usage
    @PropertyName("unpaidAmount")
    val unpaidAmount: Double, // Added missing field
    @PropertyName("collectionRate")
    val collectionRate: Double, // Added missing field
    @PropertyName("invoiceCount")
    val invoiceCount: Int,
    @PropertyName("salesByCategory")
    val salesByCategory: List<SalesByCategoryItem>,
    @PropertyName("salesByCustomerType")
    val salesByCustomerType: List<SalesByCustomerTypeItem>,
    @PropertyName("salesByDate")
    val salesByDate: List<SalesByDateItem>
    // Ensure SalesByCategoryItem, SalesByCustomerTypeItem, SalesByDateItem are defined correctly elsewhere
)