package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.Timestamp

data class ShopDetails(
    val shopId: String = "",
    val ownerUserId: String = "",
    val shopName: String = "",
    val address: String = "",
    val gstNumber: String? = null,
    val hasGst: Boolean = false,
    val logoUrl: String? = null,
    val signatureUrl: String? = null,
    val createdAt: Timestamp = Timestamp.now()
) 