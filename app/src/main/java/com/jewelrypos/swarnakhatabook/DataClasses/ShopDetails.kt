package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class ShopDetails(
    @PropertyName("shopId")
    val shopId: String = "",
    @PropertyName("ownerUserId")
    val ownerUserId: String = "",
    @PropertyName("shopName")
    val shopName: String = "",
    @PropertyName("address")
    val address: String = "",
    @PropertyName("gstNumber")
    val gstNumber: String? = null,
    @PropertyName("hasGst")
    val hasGst: Boolean = false,
    @PropertyName("logoUrl")
    val logoUrl: String? = null,
    @PropertyName("signatureUrl")
    val signatureUrl: String? = null,
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now()
) 