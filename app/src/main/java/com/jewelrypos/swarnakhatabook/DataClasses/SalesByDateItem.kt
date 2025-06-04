package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class SalesByDateItem(
    @PropertyName("date")
    val date: String,
    @PropertyName("amount")
    val amount: Double
)