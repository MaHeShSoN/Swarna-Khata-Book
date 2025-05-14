package com.jewelrypos.swarnakhatabook.Enums

enum class InventoryType {
    IDENTICAL_BATCH, // Multiple identical items (stock > 1)
    BULK_STOCK      // Raw materials tracked by weight (grams) rather than count
} 