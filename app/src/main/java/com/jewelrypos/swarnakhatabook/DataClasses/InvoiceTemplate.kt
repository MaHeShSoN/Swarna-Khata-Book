package com.jewelrypos.swarnakhatabook.DataClasses

import androidx.annotation.DrawableRes
import com.jewelrypos.swarnakhatabook.Enums.TemplateType

/**
 * Data class representing an invoice template style
 */
data class InvoiceTemplate(
    val id: String,
    val name: String,
    @DrawableRes val previewResId: Int,
    val description: String,
    val isPremium: Boolean = false,
    val templateType: TemplateType
)