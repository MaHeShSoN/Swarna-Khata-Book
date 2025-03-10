package com.jewelrypos.swarnakhatabook.TypeConverter

import androidx.room.TypeConverter
import com.jewelrypos.swarnakhatabook.Enums.MetalItemType

class MetalItemTypeConverter {

    @TypeConverter
    fun fromItemType(value: MetalItemType): String {
        return value.name
    }

    @TypeConverter
    fun toItemType(value: String): MetalItemType {
        return MetalItemType.valueOf(value)
    }
}