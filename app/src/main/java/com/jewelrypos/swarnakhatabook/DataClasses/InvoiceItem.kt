package com.jewelrypos.swarnakhatabook.DataClasses

import java.util.UUID

data class InvoiceItem(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String = "",
    val quantity: Int = 0,
    val itemDetails: JewelleryItem = JewelleryItem(),
    val price: Double = 0.0,
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