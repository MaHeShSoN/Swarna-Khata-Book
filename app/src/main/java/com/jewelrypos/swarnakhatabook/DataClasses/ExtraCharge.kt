package com.jewelrypos.swarnakhatabook.DataClasses

import java.util.UUID

// ExtraCharge.java
data class ExtraCharge(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double
)