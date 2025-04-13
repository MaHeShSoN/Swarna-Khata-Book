package com.jewelrypos.swarnakhatabook.DataClasses

import java.util.Date

data class CustomerTransactionItem(
    val id: String,
    val date: Date,
    val description: String,
    val invoiceNumber: String?,
    val debit: Double,
    val credit: Double,
    val balance: Double,
    val isInvoice: Boolean
)