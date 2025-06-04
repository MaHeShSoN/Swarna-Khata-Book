package com.jewelrypos.swarnakhatabook.DataClasses

import androidx.annotation.DrawableRes
import com.google.firebase.firestore.PropertyName
import com.jewelrypos.swarnakhatabook.Enums.TemplateType

/**
 * Data class representing an invoice template style
 */
data class InvoiceTemplate(
    @PropertyName("id")
    val id: String,
    @PropertyName("name")
    val name: String,
    @DrawableRes 
    @PropertyName("previewResId")
    val previewResId: Int,
    @PropertyName("description")
    val description: String,
    @PropertyName("isPremium")
    val isPremium: Boolean = false,
    @PropertyName("templateType")
    val templateType: TemplateType
)