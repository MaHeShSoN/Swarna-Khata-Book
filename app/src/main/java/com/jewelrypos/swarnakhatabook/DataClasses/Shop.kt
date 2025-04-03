package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.Timestamp

data class Shop(
    val shopName: String = "",
    val phoneNumber: String = "",
    val name: String = "",
    val hasGst: Boolean = false,
    val gstNumber: String = "",
    val address: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val email: String = "",
    var logo: String? = null,
    var signature: String? = null
)