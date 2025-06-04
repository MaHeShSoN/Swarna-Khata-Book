package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class UserProfile(
    @PropertyName("userId")
    val userId: String = "",
    @PropertyName("name")
    val name: String = "",
    @PropertyName("phoneNumber")
    val phoneNumber: String = "",
    @PropertyName("managedShops")
    val managedShops: Map<String, Boolean> = emptyMap(), // Keys are shopIds
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now()
) 