package com.jewelrypos.swarnakhatabook.Entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jewelrypos.swarnakhatabook.Enums.MetalItemType

@Entity(tableName = "items")
data class MetalItemEntity(
    @PrimaryKey val fieldName: String,
    val type: MetalItemType
)