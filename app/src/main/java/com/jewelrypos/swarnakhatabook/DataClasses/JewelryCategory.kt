package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class JewelryCategory(
    @DocumentId
    val id: String = "",
    @PropertyName("name")
    val name: String = "",
    @PropertyName("metalType")
    val metalType: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    @PropertyName("createdBy")
    val createdBy: String = "",
    @PropertyName("isDefault")
    val isDefault: Boolean = false
) 