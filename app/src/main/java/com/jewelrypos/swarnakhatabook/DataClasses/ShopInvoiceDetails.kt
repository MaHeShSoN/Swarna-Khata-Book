package com.jewelrypos.swarnakhatabook.DataClasses

data class ShopInvoiceDetails(
    val id: String,
    var shopName: String,
    var address: String,
    var phoneNumber: String,
    var email: String,
    var gstNumber: String,
    var logo: String? = null,
    var signature: String? = null
)