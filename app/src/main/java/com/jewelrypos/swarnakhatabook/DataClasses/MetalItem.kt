package com.jewelrypos.swarnakhatabook.DataClasses

import com.jewelrypos.swarnakhatabook.Enums.MetalItemType

data class MetalItem(
    val fieldName: String = "",
    val type: MetalItemType = MetalItemType.GOLD // Default to OTHER
)