package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class JewelryCategory(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val metalType: String = "",
    @ServerTimestamp
    val createdAt: Date? = null,
    val createdBy: String = "",
    val isDefault: Boolean = false
) 