package com.jewelrypos.swarnakhatabook.DataClasses

import androidx.annotation.ColorRes
import com.jewelrypos.swarnakhatabook.Enums.TemplateType
import com.jewelrypos.swarnakhatabook.R

class PdfSettings(
    @ColorRes var primaryColorRes: Int = R.color.black,
    @ColorRes var secondaryColorRes: Int = R.color.black,
    var showLogo: Boolean = true,
    var showWatermark: Boolean = false,
    var showQrCode: Boolean = true,
    var showSignature: Boolean = true,
    var logoUri: String? = null,
    var watermarkUri: String? = null,
    var signatureUri: String? = null,
    var termsAndConditions: String = "1. Goods once sold cannot be returned.\n2. All disputes subject to local jurisdiction.\n3. E&OE: Errors and Omissions Excepted.",
    var invoicePrefix: String = "INV-",
    var upiId: String = "",
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