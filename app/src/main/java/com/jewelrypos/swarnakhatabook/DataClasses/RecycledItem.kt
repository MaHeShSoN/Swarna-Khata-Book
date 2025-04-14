package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class RecycledItem(
    val id: String = "", // Original item ID
    val itemId: String = "", // For reference to the original item (invoice number, etc.)
    val itemType: String = "", // "INVOICE", "CUSTOMER", "INVENTORY"
    val itemName: String = "", // Display name (invoice number, customer name, item name)
    val itemData: Map<String, Any> = mapOf(), // Serialized item data
    @ServerTimestamp val deletedAt: Timestamp? = null, // When it was moved to recycling bin
    val expiresAt: Long = 0, // When it will be permanently deleted (30 days after deletion)
    val userId: String = "" // User ID who deleted it
)