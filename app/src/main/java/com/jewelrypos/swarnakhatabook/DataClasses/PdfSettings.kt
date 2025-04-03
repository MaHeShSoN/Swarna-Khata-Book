package com.jewelrypos.swarnakhatabook.DataClasses

import com.jewelrypos.swarnakhatabook.R

data class PdfSettings(
    // Branding settings
    var primaryColorRes: Int = R.color.my_light_primary,
    var secondaryColorRes: Int = R.color.my_light_secondary,
    var showLogo: Boolean = true,
    var logoUri: String? = null,
    var showWatermark: Boolean = false,
    var watermarkUri: String? = null,
    var showSignature: Boolean = true,
    var signatureUri: String? = null,

    // Content settings
    var showQrCode: Boolean = true,
    var upiId: String = "",
    var invoicePrefix: String = "INV-",
    var termsAndConditions: String = "1. Goods once sold cannot be returned.\n2. All disputes subject to local jurisdiction.\n3. E&OE: Errors and Omissions Excepted."
)