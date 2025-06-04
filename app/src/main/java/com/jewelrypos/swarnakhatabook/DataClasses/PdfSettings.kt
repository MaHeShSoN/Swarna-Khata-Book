package com.jewelrypos.swarnakhatabook.DataClasses

import androidx.annotation.ColorRes
import com.google.firebase.firestore.PropertyName
import com.jewelrypos.swarnakhatabook.Enums.TemplateType
import com.jewelrypos.swarnakhatabook.R

class PdfSettings(
    @PropertyName("primaryColorRes")
    @ColorRes var primaryColorRes: Int = R.color.black,
    @PropertyName("secondaryColorRes")
    @ColorRes var secondaryColorRes: Int = R.color.black,
    @PropertyName("showLogo")
    var showLogo: Boolean = true,
    @PropertyName("showWatermark")
    var showWatermark: Boolean = false,
    @PropertyName("showQrCode")
    var showQrCode: Boolean = true,
    @PropertyName("showSignature")
    var showSignature: Boolean = true,
    @PropertyName("logoUri")
    var logoUri: String? = null,
    @PropertyName("watermarkUri")
    var watermarkUri: String? = null,
    @PropertyName("signatureUri")
    var signatureUri: String? = null,
    @PropertyName("termsAndConditions")
    var termsAndConditions: String = "1. Goods once sold cannot be returned.\n2. All disputes subject to local jurisdiction.\n3. E&OE: Errors and Omissions Excepted.",
    @PropertyName("invoicePrefix")
    var invoicePrefix: String = "INV-",
    @PropertyName("upiId")
    var upiId: String = "",
    @PropertyName("templateType")
    var templateType: TemplateType = TemplateType.SIMPLE
) {
    companion object {
        /**
         * Get a list of available templates
         */
        fun getAvailableTemplates(): List<InvoiceTemplate> {
            return listOf(
                InvoiceTemplate(
                    id = "simple",
                    name = "Simple",
                    previewResId = R.drawable.template_preview_simple,
                    description = "Clean and basic design for all businesses",
                    templateType = TemplateType.SIMPLE
                ),
                InvoiceTemplate(
                    id = "stylish",
                    name = "Stylish",
                    previewResId = R.drawable.template_preview_stylish,
                    description = "Modern design with elegant layout",
                    templateType = TemplateType.STYLISH
                )
            )
        }
    }
}