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
                ),
                InvoiceTemplate(
                    id = "advance_gst",
                    name = "Advance GST",
                    previewResId = R.drawable.template_preview_advance_gst,
                    description = "Design optimized for GST details",
                    templateType = TemplateType.ADVANCE_GST
                ),
                InvoiceTemplate(
                    id = "advance_gst_tally",
                    name = "Advance GST (Tally)",
                    previewResId = R.drawable.template_preview_advance_gst_tally,
                    description = "Similar to Tally software format",
                    templateType = TemplateType.ADVANCE_GST_TALLY,
                    isPremium = true
                ),
                InvoiceTemplate(
                    id = "billbook",
                    name = "Billbook",
                    previewResId = R.drawable.template_preview_billbook,
                    description = "Traditional billbook style layout",
                    templateType = TemplateType.BILLBOOK,
                    isPremium = true
                )
            )
        }
    }
}