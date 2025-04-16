package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val managedShops: Map<String, Boolean> = emptyMap(), // Keys are shopIds
    val createdAt: Timestamp = Timestamp.now()
) 