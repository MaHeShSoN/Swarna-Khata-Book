package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import java.util.UUID

// ExtraCharge.java
data class ExtraCharge(
    @PropertyName("id")
    val id: String = UUID.randomUUID().toString(),
    
    @PropertyName("name")
    val name: String = "",
    
    @PropertyName("amount")
    val amount: Double = 0.0
)