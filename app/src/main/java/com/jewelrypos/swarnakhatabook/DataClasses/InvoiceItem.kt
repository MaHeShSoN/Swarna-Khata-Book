package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import java.util.UUID

data class InvoiceItem(
    @PropertyName("id")
    val id: String = UUID.randomUUID().toString(),
    @PropertyName("itemId")
    val itemId: String = "",
    @PropertyName("quantity")
    val quantity: Int = 0,
    @PropertyName("itemDetails")
    val itemDetails: JewelleryItem = JewelleryItem(),
    @PropertyName("price")
    val price: Double = 0.0,
    @PropertyName("usedWeight")
    val usedWeight: Double = 0.0
) {
    // Equality based on the unique ID, not just item details
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InvoiceItem

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    // Helper method to check if items are essentially the same (for business logic)
    fun isSameItem(other: InvoiceItem): Boolean {
        return itemId == other.itemId &&
                itemDetails == other.itemDetails &&
                price == other.price
    }
}