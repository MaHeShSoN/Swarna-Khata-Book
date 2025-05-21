package com.jewelrypos.swarnakhatabook.Enums

import com.google.firebase.firestore.PropertyName

enum class InventoryType {
    @PropertyName("IDENTICAL_BATCH")
    IDENTICAL_BATCH, // Multiple identical items (stock > 1)
    
    @PropertyName("BULK_STOCK")
    BULK_STOCK      // Raw materials tracked by weight (grams) rather than count
} 