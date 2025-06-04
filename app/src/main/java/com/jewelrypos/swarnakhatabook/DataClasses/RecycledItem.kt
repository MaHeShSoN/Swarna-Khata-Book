package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

data class RecycledItem(
    @PropertyName("id")
    val id: String = "", // Original item ID
    @PropertyName("itemId")
    val itemId: String = "", // For reference to the original item (invoice number, etc.)
    @PropertyName("itemType")
    val itemType: String = "", // "INVOICE", "CUSTOMER", "INVENTORY"
    @PropertyName("itemName")
    val itemName: String = "", // Display name (invoice number, customer name, item name)
    @PropertyName("itemData")
    val itemData: Map<String, Any> = mapOf(), // Serialized item data
    @ServerTimestamp 
    @PropertyName("deletedAt")
    val deletedAt: Timestamp? = null, // When it was moved to recycling bin
    @PropertyName("expiresAt")
    val expiresAt: Long = 0, // When it will be permanently deleted (30 days after deletion)
    @PropertyName("userId")
    val userId: String = "" // User ID who deleted it
)