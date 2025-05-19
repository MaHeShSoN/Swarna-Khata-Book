package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings
import com.jewelrypos.swarnakhatabook.DataClasses.ShopInvoiceDetails
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.jewelrypos.swarnakhatabook.DataClasses.Shop
import com.jewelrypos.swarnakhatabook.Enums.TemplateType
import com.jewelrypos.swarnakhatabook.R
import com.tom_roush.harmony.awt.AWTColor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvoicePdfGenerator(private val context: Context) {
    // Font objects - loaded once and reused
    private lateinit var regularFont: PDType0Font
    private lateinit var boldFont: PDType0Font

    // Formatting constants
    private val formatter = DecimalFormat("#,##,###.##")
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // Settings for customization
    private var settings: PdfSettings = PdfSettings()
    private var primaryColor: AWTColor = AWTColor(33, 33, 33) // Default black
    private var secondaryColor: AWTColor = AWTColor(117, 117, 117) // Default gray

    init {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Apply custom settings for PDF generation
     */
    fun applySettings(pdfSettings: PdfSettings) {
        this.settings = pdfSettings

        // Convert color resources to AWTColor
        try {
            val primaryColorInt = ContextCompat.getColor(context, pdfSettings.primaryColorRes)
            primaryColor = AWTColor(
                Color.red(primaryColorInt),
                Color.green(primaryColorInt),
                Color.blue(primaryColorInt)
            )

            val secondaryColorInt = ContextCompat.getColor(context, pdfSettings.secondaryColorRes)
            secondaryColor = AWTColor(
                Color.red(secondaryColorInt),
                Color.green(secondaryColorInt),
                Color.blue(secondaryColorInt)
            )
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error converting colors: ${e.message}")
        }
    }

    /**
     * Main function to generate PDF from Invoice data
     */
    fun generateInvoicePdf(invoice: Invoice, shop: ShopInvoiceDetails, fileName: String): File {
        val document = PDDocument()
        try {
            // Create page with A4 size
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            // Load fonts
            loadFonts(document)

            // Get page dimensions
            val pageWidth = page.mediaBox.width
            val pageHeight = page.mediaBox.height

            // Create content stream
            PDPageContentStream(document, page).use { contentStream ->
                // Apply watermark if enabled
                if (settings.showWatermark && settings.watermarkUri != null) {
                    applyWatermark(document, page, settings.watermarkUri!!)
                }

                // Choose template based on settings
                when (settings.templateType) {
                    TemplateType.SIMPLE -> generateSimpleTemplate(
                        contentStream, document, page, invoice, shop, pageWidth, pageHeight
                    )

                    TemplateType.STYLISH -> generateStylishTemplate(
                        contentStream, document, page, invoice, shop, pageWidth, pageHeight
                    )

                    TemplateType.GST_TALLY -> generateGstTallyTemplate(
                        contentStream, document, page, invoice, shop, pageWidth, pageHeight
                    )

                    TemplateType.MODERN_MINIMAL -> generateMordenMinimalTemplate(
                        contentStream, document, page, invoice, shop, pageWidth, pageHeight
                    )

                    TemplateType.LUXURY_BOUTIQUE -> generateLuxuryBoutiqueTemplate(
                        contentStream, document, page, invoice, shop, pageWidth, pageHeight
                    )
                }
            }

            // Save the PDF
            return savePdfToFile(document, fileName)

        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error generating PDF", e)
            throw e
        } finally {
            document.close()
        }
    }


    private fun generateMordenMinimalTemplate(
        stream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        details: ShopInvoiceDetails,
        w: Float,
        h: Float
    ) {
        // ---------- HEADER SECTION ----------
        // Draw shop logo if available (left side)

        stream.setNonStrokingColor(AWTColor(255, 254, 250)) // FFFEFA color (very light off-white)
        stream.addRect(0f, 0f, w, h)
        stream.fill()


        var shopDetailsStartX = 25f

        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(document, 100f, h - 70, settings.logoUri!!, stream)
                // If logo is available, move shop details to the right of logo
                shopDetailsStartX = 113f
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Shop details (left side)
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(stream, details.shopName, shopDetailsStartX, h - 47f, 10f, true)
        drawText(stream, details.address, shopDetailsStartX, h - 60f, 9f, false)
        drawText(stream, details.phoneNumber, shopDetailsStartX, h - 73f, 9f, false)
        drawText(stream, "GST: ${details.gstNumber}", shopDetailsStartX, h - 86f, 9f, false)

        // TAX INVOICE title (right side) - adjusted to match image
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(stream, "TAX INVOICE", w - 220f, h - 47f, 14f, true)

        // Add "ORIGINAL FOR RECEIPT" text with rectangle - now positioned to the right with some space
        val receiptText = "ORIGINAL FOR RECEIPT"
        val textWidth1 = regularFont.getStringWidth(receiptText) / 1000 * 8f // Convert to actual width
        val textHeight = 8f
        val padding = 3f // Padding around the text

        // Define gray color
        val grayColor = AWTColor(120, 120, 120) // Medium gray

        // Draw rectangle around text with gray border
        stream.setStrokingColor(grayColor)
        stream.setLineWidth(0.5f) // Thin border

        // Rectangle coordinates - positioned to the right of "TAX INVOICE" with space
        stream.addRect(
            w - 110f, // Moved to the right
            h - 50f,  // Aligned with TAX INVOICE height
            textWidth1 + (padding * 2),
            textHeight + (padding * 2)
        )
        stream.stroke() // Draw the rectangle outline

        // Draw the text in gray
        stream.setNonStrokingColor(grayColor) // Gray text
        drawText(stream, receiptText, w - 110f + padding, h - 45f, 8f, false)

        // ---------- CUSTOMER AND INVOICE DETAILS SECTION ----------
        // "BILL TO" section
        val billToBoxY = h - 120f
        stream.setNonStrokingColor(primaryColor) // Light green
        stream.addRect(20f, billToBoxY, 100f, 18f)
        stream.fill()

        stream.setNonStrokingColor(AWTColor.WHITE)
        drawText(stream, "BILL TO", 27f, billToBoxY + 6f, 10f, true)

        // Customer details
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(stream, invoice.customerName, 25f, billToBoxY - 15f, 10f, true)
        drawText(stream, invoice.customerAddress, 25f, billToBoxY - 30f, 9f, false)
        drawText(stream, invoice.customerPhone, 25f, billToBoxY - 45f, 9f, false)

        // "INVOICE DETAILS" section - adjusted position to match image
        stream.setNonStrokingColor(primaryColor) // Light green
        stream.addRect(w - 220f, billToBoxY, 100f, 18f)
        stream.fill()

        stream.setNonStrokingColor(AWTColor.WHITE)
        drawText(stream, "INVOICE DETAILS", w - 210f, billToBoxY + 6f, 10f, true)

        // Invoice details - adjusted to match image
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(stream, "Invoice No", w - 220f, billToBoxY - 15f, 10f, false)
        val invoiceDisplayNumber = if (invoice.invoiceNumber.startsWith(settings.invoicePrefix)) {
            invoice.invoiceNumber
        } else {
            "${settings.invoicePrefix}${invoice.invoiceNumber}"
        }
        drawText(
            stream,
            "        :         $invoiceDisplayNumber",
            w - 160f,
            billToBoxY - 15f,
            10f,
            false
        )

        drawText(stream, "Invoice Date", w - 220f, billToBoxY - 30f, 10f, false)
        drawText(
            stream,
            "        :         ${dateFormatter.format(Date(invoice.invoiceDate))}",
            w - 160f,
            billToBoxY - 30f,
            10f,
            false
        )

        // ---------- ITEMS TABLE SECTION ----------
        // Fix #1: Reduce the gap between heading and items
        var currentY = billToBoxY - 75f  // Adjusted to reduce the gap

        // Table header row
        stream.setNonStrokingColor(primaryColor) // Light green
        stream.addRect(20f, currentY, w - 40f, 18f)
        stream.fill()

        // Define table columns
        val columns = listOf(
            "Product Name",
            "QTY",
            "Gr.WT",
            "Stone Wt",
            "Net.Wt",
            "Stone Value",
            "Gold Value",
            "Labour",
            "Total Value"
        )
        val columnWidths =
            floatArrayOf(0.22f, 0.06f, 0.08f, 0.08f, 0.08f, 0.12f, 0.12f, 0.09f, 0.15f)
        var columnX = 25f

        // Draw table headers
        stream.setNonStrokingColor(AWTColor.WHITE)
        for (i in columns.indices) {
            drawText(stream, columns[i], columnX, currentY + 6f, 9f, true)
            columnX += columnWidths[i] * (w - 40f)
        }

        // Draw item rows - Fix #1: Reduce space between header and first item
        currentY -= 5f
        val rowHeight = 25f

        // Track totals
        var totalQuantity = 0
        var totalNetWeight = 0.0
        var totalStoneValue = 0.0
        var totalGoldValue = 0.0
        var totalLabour = 0.0
        var totalValue = 0.0

        // Draw each item row
        for (i in invoice.items.indices) {
            val item = invoice.items[i]

            // Alternating row background
            if (i % 2 == 1) {
                // Light gray for alternating rows
                stream.setNonStrokingColor(AWTColor(240, 240, 240))
                stream.addRect(20f, currentY - rowHeight, w - 40f, rowHeight)
                stream.fill()
            }

            // Draw item data
            stream.setNonStrokingColor(AWTColor(0, 0, 0))
            columnX = 25f

            drawText(
                stream,
                item.itemDetails.displayName,
                columnX,
                currentY - rowHeight / 4,
                9f,
                false
            )
            val purityRateText =
                "${item.itemDetails.purity} @ ${formatter.format(item.itemDetails.metalRate)}"
            drawText(stream, purityRateText, columnX, currentY - rowHeight * 3 / 4, 9f, false)

            columnX += columnWidths[0] * (w - 40f)

            // Quantity
            drawText(stream, "${item.quantity}", columnX, currentY - rowHeight / 2, 9f, false)
            columnX += columnWidths[1] * (w - 40f)
            totalQuantity += item.quantity

            // Gross Weight
            drawText(
                stream,
                formatter.format(item.itemDetails.grossWeight),
                columnX,
                currentY - rowHeight / 2,
                9f,
                false
            )
            columnX += columnWidths[2] * (w - 40f)

            // Stone Weight (calculated as Gross Weight - Net Weight)
            val stoneWeight = item.itemDetails.grossWeight - item.itemDetails.netWeight
            drawText(
                stream,
                formatter.format(stoneWeight),
                columnX,
                currentY - rowHeight / 2,
                9f,
                false
            )
            columnX += columnWidths[3] * (w - 40f)

            // Net Weight
            drawText(
                stream,
                formatter.format(item.itemDetails.netWeight),
                columnX,
                currentY - rowHeight / 2,
                9f,
                false
            )
            columnX += columnWidths[4] * (w - 40f)
            totalNetWeight += item.itemDetails.netWeight * item.quantity

            // Stone Value (use diamondPrice if available, otherwise calculate)
            val stoneValue = if (item.itemDetails.diamondPrice > 0) {
                item.itemDetails.diamondPrice * item.quantity
            } else {
                // If no diamondPrice is set, calculate based on stone weight
                val stoneRate = 1000.0 // default rate per gram if not specified
                stoneWeight * stoneRate * item.quantity
            }
            drawText(
                stream,
                formatter.format(stoneValue),
                columnX,
                currentY - rowHeight / 2,
                9f,
                false
            )
            columnX += columnWidths[5] * (w - 40f)
            totalStoneValue += stoneValue

            // Gold Value - calculate based on metal rate and weight
            val goldValue = when (item.itemDetails.metalRateOn.uppercase()) {
                "NET WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.netWeight * item.quantity
                "GROSS WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.grossWeight * item.quantity
                else -> item.itemDetails.metalRate * item.itemDetails.netWeight * item.quantity
            }
            drawText(
                stream,
                formatter.format(goldValue),
                columnX,
                currentY - rowHeight / 2,
                9f,
                false
            )
            columnX += columnWidths[6] * (w - 40f)
            totalGoldValue += goldValue

            // Labour
            val labour = when (item.itemDetails.makingChargesType.uppercase()) {
                "PER GRAM" -> item.itemDetails.makingCharges * item.itemDetails.netWeight * item.quantity
                "FIX" -> item.itemDetails.makingCharges * item.quantity
                "PERCENTAGE" -> {
                    val metalValue = when (item.itemDetails.metalRateOn.uppercase()) {
                        "NET WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.netWeight
                        "GROSS WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.grossWeight
                        else -> item.itemDetails.metalRate * item.itemDetails.netWeight
                    }
                    (metalValue * item.itemDetails.makingCharges / 100.0) * item.quantity
                }

                else -> 0.0
            }
            drawText(stream, formatter.format(labour), columnX, currentY - rowHeight / 2, 9f, false)
            columnX += columnWidths[7] * (w - 40f)
            totalLabour += labour

            // Total Value
            val itemTotal = item.price * item.quantity
            drawText(
                stream,
                formatter.format(itemTotal),
                columnX,
                currentY - rowHeight / 2,
                9f,
                false
            )
            totalValue += itemTotal

            currentY -= rowHeight
        }

        // Subtotal row (light green background)
        stream.setNonStrokingColor(primaryColor) // Light green
        stream.addRect(20f, currentY - rowHeight + 9f, w - 40f, 18f)
        stream.fill()

        // Draw subtotal data - fixed to center text properly
        stream.setNonStrokingColor(AWTColor.WHITE)
        columnX = 25f

        // First column - SUBTOTAL
        drawText(stream, "SUBTOTAL", columnX, currentY - rowHeight / 2 + 2.5f, 9f, true)
        columnX += columnWidths[0] * (w - 40f)

        // Quantity
        drawText(stream, "$totalQuantity", columnX, currentY - rowHeight / 2 + 2.5f, 9f, true)
        columnX += columnWidths[1] * (w - 40f)

        // Skip some columns - jump to net weight
        columnX += (columnWidths[2] + columnWidths[3]) * (w - 40f)

        // Net Weight
        drawText(
            stream,
            formatter.format(totalNetWeight),
            columnX,
            currentY - rowHeight / 2 + 2.5f,
            9f,
            true
        )
        columnX += columnWidths[4] * (w - 40f)

        // Stone Value
        drawText(
            stream,
            formatter.format(totalStoneValue),
            columnX,
            currentY - rowHeight / 2 + 2.5f,
            9f,
            true
        )
        columnX += columnWidths[5] * (w - 40f)

        // Gold Value
        drawText(
            stream,
            formatter.format(totalGoldValue),
            columnX,
            currentY - rowHeight / 2 + 2.5f,
            9f,
            true
        )
        columnX += columnWidths[6] * (w - 40f)

        // Labour
        drawText(
            stream,
            formatter.format(totalLabour),
            columnX,
            currentY - rowHeight / 2 + 2.5f,
            9f,
            true
        )
        columnX += columnWidths[7] * (w - 40f)

        // Total Value
        drawText(
            stream,
            formatter.format(totalValue),
            columnX,
            currentY - rowHeight / 2 + 2.5f,
            9f,
            true
        )

        currentY -= rowHeight

        // ---------- CALCULATION SECTION ----------
        // Tax and charges on the right side
        val taxInfoX = w - 200f
        var taxInfoY = currentY - 20f

        // Calculate tax amounts
        val taxRate = invoice.items.firstOrNull()?.itemDetails?.taxRate ?: 0.0
        val halfTaxRate = taxRate / 2.0
        val totalTax = invoice.items.sumOf { item ->
            val itemTotal = item.price * item.quantity
            val extraChargesTotal =
                item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
            val taxableAmount = itemTotal + extraChargesTotal
            taxableAmount * (item.itemDetails.taxRate / 100.0)
        }
        val halfTax = totalTax / 2.0

        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        // CGST
        drawText(stream, "CGST(${halfTaxRate.toDouble()}%)", taxInfoX, taxInfoY, 9f, false)
        drawText(stream, formatter.format(halfTax), w - 80f, taxInfoY, 9f, false)
        taxInfoY -= 15f

        // SGST
        drawText(stream, "SGST(${halfTaxRate.toDouble()}%)", taxInfoX, taxInfoY, 9f, false)
        drawText(stream, formatter.format(halfTax), w - 80f, taxInfoY, 9f, false)
        taxInfoY -= 15f

        // Extra charges
        val extraChargeMap = mutableMapOf<String, Double>()
        invoice.items.forEach { item ->
            item.itemDetails.listOfExtraCharges.forEach { charge ->
                val amount = charge.amount * item.quantity
                extraChargeMap[charge.name] = extraChargeMap.getOrDefault(charge.name, 0.0) + amount
            }
        }

        // Draw each extra charge
        extraChargeMap.forEach { (name, amount) ->
            drawText(stream, name.uppercase(), taxInfoX, taxInfoY, 9f, false)
            drawText(stream, formatter.format(amount), w - 80f, taxInfoY, 9f, false)
            taxInfoY -= 15f
        }

        // Fix #2: Fix the spacing around the TOTAL AMOUNT
        taxInfoY -= 5f // Add some space before total

        // Draw line before total - fixed position
        drawLine(stream, taxInfoX, taxInfoY + 15f, w - 30f, taxInfoY + 15f, 0.5f, AWTColor(0, 0, 0))

        // TOTAL AMOUNT
        drawText(stream, "TOTAL AMOUNT", taxInfoX, taxInfoY + 2f, 10f, true)
        drawText(stream, formatter.format(invoice.totalAmount), w - 80f, taxInfoY + 2f, 10f, true)

        // Fix #2: Fix the spacing below TOTAL AMOUNT
        taxInfoY -= 5f

        // Draw line after total with fixed position
        drawLine(stream, taxInfoX, taxInfoY, w - 30f, taxInfoY, 0.5f, AWTColor(0, 0, 0))
        taxInfoY -= 10f

        // Payment details
        if (invoice.payments.isNotEmpty()) {
            for (payment in invoice.payments) {
                drawText(stream, payment.method, taxInfoX, taxInfoY, 9f, false)
                drawText(stream, formatter.format(payment.amount), w - 80f, taxInfoY, 9f, false)
                taxInfoY -= 15f
            }
        }

        // Received Amount
        drawText(stream, "Received Amount", taxInfoX, taxInfoY, 9f, true)
        drawText(stream, formatter.format(invoice.paidAmount), w - 80f, taxInfoY, 9f, true)
        taxInfoY -= 15f

        // Balance
        val balanceDue = invoice.totalAmount - invoice.paidAmount
        drawText(stream, "Balance", taxInfoX, taxInfoY, 9f, true)
        stream.setNonStrokingColor(
            if (balanceDue <= 0) AWTColor(0, 128, 0) else AWTColor(
                192,
                0,
                0
            )
        )
        drawText(stream, formatter.format(balanceDue), w - 80f, taxInfoY, 9f, true)

        // Amount in words (Requested feature #2)
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(stream, "Amount in words : ", 25f, taxInfoY, 9f, true)
        drawText(
            stream,
            numberToWords(balanceDue.toInt()) + " Rupees Only",
            25f,
            taxInfoY - 15f,
            9f,
            false
        )
        taxInfoY -= 30f

        // Notes section (Requested feature #1)
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(stream, "Term & Conditions : ", 25f, taxInfoY, 9f, true)
        // If there are specific notes in the invoice object, you would use those here
        // For now, adding a placeholder for notes
        val notes = settings.termsAndConditions.split("\n").take(2).joinToString("\n")
        val noteLines = notes.split("\n")
        for (i in noteLines.indices) {
            drawText(stream, noteLines[i], 25f, taxInfoY - 15f - (i * 15f), 9f, false)
        }

        // ---------- FOOTER SECTION ----------
        // Fix #4: Position QR code more to the left
        if (settings.showQrCode && settings.upiId.isNotEmpty()) {
            drawUpiQrCode(
                document,
                page,
                settings.upiId,
                25f,
                80f
            ) // Moved more to the left (was 60f)
        }

        // Reduced size for app logo
        val appLogoBitmap = vectorToBitmap(context, R.drawable.swarna_khata_book_03, 25, 25) // Reduced from 60x60
        val appLogoStream = ByteArrayOutputStream()
        appLogoBitmap.compress(Bitmap.CompressFormat.PNG, 100, appLogoStream)
        val appLogoBytes = appLogoStream.toByteArray()
        val appLogo = PDImageXObject.createFromByteArray(document, appLogoBytes, "app_logo")

// Reduced size for Play Store logo
        val playStoreLogoBitmap = vectorToBitmap(context, R.drawable.ion__logo_google_playstore, 25, 25) // Reduced from 60x60
        val playStoreLogoStream = ByteArrayOutputStream()
        playStoreLogoBitmap.compress(Bitmap.CompressFormat.PNG, 100, playStoreLogoStream)
        val playStoreLogoBytes = playStoreLogoStream.toByteArray()
        val playStoreLogo = PDImageXObject.createFromByteArray(document, playStoreLogoBytes, "playstore_logo")

// Draw enclosing rectangle for footer
        stream.setStrokingColor(secondaryColor)
        stream.setLineWidth(0.5f)
        stream.addRect(w/2 - 130f, 30f, 260f, 15f)
//        contentStream.addRect( pageWidth / 2 - 105f, 30f, 260f, 15f)
        stream.stroke()

// Draw logos and text
// Scale the images to appropriate size for the PDF
        val logoWidth = 15f  // Reduced from 20f
        val logoHeight = 15f // Reduced from 20f

        //reduse 120 tp 90
//        contentStream.drawImage(appLogo, pageWidth / 2 - 100f, 30f, logoWidth, logoHeight)
//        contentStream.drawImage(playStoreLogo, pageWidth / 2 + 135f, 30f, logoWidth, logoHeight)
        // Position the app logo on the left side of centered text
        stream.drawImage(appLogo, w/2 - 125f, 30f, logoWidth, logoHeight)
// Position the play store logo on the right side of centered text
        stream.drawImage(playStoreLogo, w/2 + 110f, 30f, logoWidth, logoHeight)

// Draw text centered between the logos
        stream.setFont(regularFont, 7f)
        stream.setNonStrokingColor(secondaryColor)
        stream.beginText()
//        stream.newLineAtOffset(pageWidth / 2 - 80f, 35f)
        val footerText = "Invoice Created From SwarnaKhataBook, Available On Play Store"
        val textWidth = regularFont.getStringWidth(footerText) / 1000 * 7f
        stream.newLineAtOffset(w/2 - textWidth/2, 35f)
        stream.showText(footerText)
        stream.endText()

        // Fix #3: Add signature implementation

        // Fix #3: Add signature implementation
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                // Draw uploaded signature image
                try {
                    // First draw a rectangle for signature (new code)
                    stream.setStrokingColor(AWTColor(0, 0, 0))  // Black outline
                    stream.setLineWidth(0.5f)  // Thin border
                      stream.addRect(w - 150f, 90f, 120f, 30f)
                  
                    // Signature box
                    stream.stroke()  // Draw the rectangle outline

                    // Then draw uploaded signature image inside the box
                    drawSignatureImage(document, w - 80f, 100f, settings.signatureUri!!, stream)
                    stream.setNonStrokingColor(AWTColor(0, 0, 0))
                    drawText(stream, "AUTHORIZED SIGNATURE", w - 120f, 80f, 9f, true)
                    drawText(stream, details.shopName, w - 120f, 65f, 8f, false)
                } catch (e: Exception) {
                    Log.e("InvoicePdfGenerator", "Error drawing signature: ${e.message}")
                    // Fall back to text-only signature line with box
                    stream.setStrokingColor(AWTColor(0, 0, 0))
                    stream.setLineWidth(0.5f)
                      stream.addRect(w - 150f, 90f, 120f, 30f)  // Signature box
                    stream.stroke()

                    stream.setNonStrokingColor(AWTColor(0, 0, 0))
                    drawText(stream, "AUTHORIZED SIGNATURE", w - 120f, 80f, 10f, true)
                    drawText(stream, details.shopName, w - 120f, 65f, 9f, false)
                }
            } else {
                // Draw signature box with line
                stream.setStrokingColor(AWTColor(0, 0, 0))
                stream.setLineWidth(0.5f)
                  stream.addRect(w - 150f, 90f, 120f, 30f)  // Signature box
                stream.stroke()

                stream.setNonStrokingColor(AWTColor(0, 0, 0))
                drawText(stream, "AUTHORIZED SIGNATURE", w - 120f, 80f, 9f, true)
                drawText(stream, details.shopName, w - 120f, 65f, 8f, false)
            }
        } else {
            // Shop name at the bottom right even if signature is not shown
            stream.setNonStrokingColor(AWTColor(0, 0, 0))
            drawText(stream, details.shopName, w - 120f, 65f, 10f, true)
        }
    }

    private fun generateLuxuryBoutiqueTemplate(
        stream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        details: ShopInvoiceDetails,
        w: Float,
        h: Float
    ) {
        // Set luxury gold accent color
        val luxuryGold = AWTColor(212, 175, 55)
        val luxuryDarkGold = AWTColor(165, 124, 0)
        val luxuryBackgroundColor = AWTColor(250, 250, 250)

        // Background
        stream.setNonStrokingColor(luxuryBackgroundColor)
        stream.addRect(0f, 0f, w, h)
        stream.fill()

        // Draw elegant border with gold accent
        stream.setStrokingColor(luxuryGold)
        stream.setLineWidth(2.0f)
        stream.addRect(20f, 20f, w - 40f, h - 40f)
        stream.stroke()

        // Add decorative corner elements
        drawDecorativeCorner(stream, 20f, h - 20f, 15f, 15f, luxuryGold) // Top-left
        drawDecorativeCorner(stream, w - 20f, h - 20f, -15f, 15f, luxuryGold) // Top-right
        drawDecorativeCorner(stream, 20f, 20f, 15f, -15f, luxuryGold) // Bottom-left
        drawDecorativeCorner(stream, w - 20f, 20f, -15f, -15f, luxuryGold) // Bottom-right

        // Header section with gold accent bar
        stream.setNonStrokingColor(luxuryGold)
        stream.addRect(20f, h - 130f, w - 40f, 3f)
        stream.fill()

        // Draw elegant "TAX INVOICE" title
        stream.setNonStrokingColor(luxuryDarkGold)
        drawCenteredText(stream, "TAX INVOICE", w / 2, h - 50f, 18f, true)

        // Add shop logo if available
        var shopDetailsStartX = 40f
        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(document, 80f, h - 70f, settings.logoUri!!, stream)
                // If logo is available, move shop details to the right
                shopDetailsStartX = 130f
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Shop details with elegant styling
        stream.setNonStrokingColor(luxuryDarkGold)
        drawText(stream, details.shopName, shopDetailsStartX, h - 80f, 14f, true)
        stream.setNonStrokingColor(AWTColor(60, 60, 60))
        drawText(stream, details.address, shopDetailsStartX, h - 95f, 10f, false)
        drawText(stream, "Tel: ${details.phoneNumber}", shopDetailsStartX, h - 110f, 10f, false)
        drawText(stream, "GSTIN: ${details.gstNumber}", shopDetailsStartX, h - 125f, 10f, false)

        // Customer and Invoice details section
        stream.setNonStrokingColor(luxuryGold)
        stream.addRect(40f, h - 155f, w / 2 - 60f, 25f)
        stream.fill()
        stream.addRect(w / 2 + 20f, h - 155f, w / 2 - 60f, 25f)
        stream.fill()

        // Customer details heading
        stream.setNonStrokingColor(AWTColor(40, 40, 40))
        drawCenteredText(stream, "CLIENT DETAILS", 40f + (w / 2 - 60f) / 2, h - 140f, 12f, true)

        // Invoice details heading
        drawCenteredText(
            stream,
            "INVOICE DETAILS",
            w / 2 + 20f + (w / 2 - 60f) / 2,
            h - 140f,
            12f,
            true
        )

        // Customer details content
        stream.setNonStrokingColor(AWTColor(60, 60, 60))
        drawText(stream, invoice.customerName, 50f, h - 175f, 12f, true)
        drawText(stream, invoice.customerAddress, 50f, h - 190f, 10f, false)
        drawText(stream, "Tel: ${invoice.customerPhone}", 50f, h - 205f, 10f, false)

        // Invoice details content
        val invoiceDisplayNumber = if (invoice.invoiceNumber.startsWith(settings.invoicePrefix)) {
            invoice.invoiceNumber
        } else {
            "${settings.invoicePrefix}${invoice.invoiceNumber}"
        }

        stream.setNonStrokingColor(AWTColor(60, 60, 60))
        drawText(stream, "Invoice No:", w / 2 + 30f, h - 175f, 10f, true)
        drawText(stream, invoiceDisplayNumber, w / 2 + 120f, h - 175f, 10f, false)

        drawText(stream, "Date:", w / 2 + 30f, h - 190f, 10f, true)
        drawText(
            stream,
            dateFormatter.format(Date(invoice.invoiceDate)),
            w / 2 + 120f,
            h - 190f,
            10f,
            false
        )

        // Items Table Header with gold background
        val tableY = h - 230f
        stream.setNonStrokingColor(luxuryGold)
        stream.addRect(40f, tableY, w - 80f, 25f)
        stream.fill()

        // Define table columns
        val columns = listOf(
            "Description",
            "Qty",
            "Weight",
            "Stone Value",
            "Labor",
            "Gold Value",
            "Amount"
        )
        val columnWidths = floatArrayOf(0.30f, 0.08f, 0.12f, 0.12f, 0.12f, 0.12f, 0.14f)
        var columnX = 45f

        // Draw column headers
        stream.setNonStrokingColor(AWTColor(40, 40, 40))
        for (i in columns.indices) {
            val width = columnWidths[i] * (w - 80f)
            drawCenteredText(stream, columns[i], columnX + width / 2, tableY + 10f, 10f, true)
            columnX += width
        }

        // Draw Items
        var currentY = tableY - 5f
        val rowHeight = 30f

        // Track totals
        var totalQuantity = 0
        var totalWeight = 0.0
        var totalStoneValue = 0.0
        var totalLabour = 0.0
        var totalGoldValue = 0.0
        var totalAmount = 0.0

        // Draw alternating rows for items
        for (i in invoice.items.indices) {
            val item = invoice.items[i]

            // Alternating row background
            if (i % 2 == 1) {
                stream.setNonStrokingColor(AWTColor(245, 245, 245))
                stream.addRect(40f, currentY - rowHeight, w - 80f, rowHeight)
                stream.fill()
            }

            columnX = 45f
            stream.setNonStrokingColor(AWTColor(60, 60, 60))

            // Description (2 lines)
            val itemName = item.itemDetails.displayName
            drawText(stream, itemName, columnX + 5f, currentY - rowHeight / 3, 10f, true)

            // Add purity info on second line
            val purityText =
                "${item.itemDetails.purity} @ ${formatter.format(item.itemDetails.metalRate)}"
            drawText(stream, purityText, columnX + 5f, currentY - rowHeight * 2 / 3, 9f, false)
            columnX += columnWidths[0] * (w - 80f)

            // Quantity
            drawCenteredText(
                stream,
                "${item.quantity}",
                columnX + (columnWidths[1] * (w - 80f)) / 2,
                currentY - rowHeight / 2,
                10f,
                false
            )
            columnX += columnWidths[1] * (w - 80f)
            totalQuantity += item.quantity

            // Weight (Net Weight)
            val netWeight = item.itemDetails.netWeight * item.quantity
            drawCenteredText(
                stream,
                formatter.format(netWeight),
                columnX + (columnWidths[2] * (w - 80f)) / 2,
                currentY - rowHeight / 2,
                10f,
                false
            )
            columnX += columnWidths[2] * (w - 80f)
            totalWeight += netWeight

            // Stone Value
            val stoneValue = item.itemDetails.diamondPrice * item.quantity
            drawCenteredText(
                stream,
                formatter.format(stoneValue),
                columnX + (columnWidths[3] * (w - 80f)) / 2,
                currentY - rowHeight / 2,
                10f,
                false
            )
            columnX += columnWidths[3] * (w - 80f)
            totalStoneValue += stoneValue

            // Labor Value
            val labour = when (item.itemDetails.makingChargesType.uppercase()) {
                "PER GRAM" -> item.itemDetails.makingCharges * item.itemDetails.netWeight * item.quantity
                "FIX" -> item.itemDetails.makingCharges * item.quantity
                "PERCENTAGE" -> {
                    val metalValue = when (item.itemDetails.metalRateOn.uppercase()) {
                        "NET WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.netWeight
                        "GROSS WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.grossWeight
                        else -> item.itemDetails.metalRate * item.itemDetails.netWeight
                    }
                    (metalValue * item.itemDetails.makingCharges / 100.0) * item.quantity
                }

                else -> 0.0
            }
            drawCenteredText(
                stream,
                formatter.format(labour),
                columnX + (columnWidths[4] * (w - 80f)) / 2,
                currentY - rowHeight / 2,
                10f,
                false
            )
            columnX += columnWidths[4] * (w - 80f)
            totalLabour += labour

            // Gold Value
            val goldValue = when (item.itemDetails.metalRateOn.uppercase()) {
                "NET WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.netWeight * item.quantity
                "GROSS WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.grossWeight * item.quantity
                else -> item.itemDetails.metalRate * item.itemDetails.netWeight * item.quantity
            }
            drawCenteredText(
                stream,
                formatter.format(goldValue),
                columnX + (columnWidths[5] * (w - 80f)) / 2,
                currentY - rowHeight / 2,
                10f,
                false
            )
            columnX += columnWidths[5] * (w - 80f)
            totalGoldValue += goldValue

            // Total Amount
            val itemTotal = item.price * item.quantity
            drawCenteredText(
                stream,
                formatter.format(itemTotal),
                columnX + (columnWidths[6] * (w - 80f)) / 2,
                currentY - rowHeight / 2,
                10f,
                true
            )
            totalAmount += itemTotal

            currentY -= rowHeight
        }

        // Subtotal row with gold background
        stream.setNonStrokingColor(luxuryGold)
        stream.addRect(40f, currentY - rowHeight, w - 80f, rowHeight)
        stream.fill()

        // Draw subtotal labels and values
        stream.setNonStrokingColor(AWTColor(40, 40, 40))
        columnX = 45f

        // Subtotal label
        drawText(stream, "SUBTOTAL", columnX + 5f, currentY - rowHeight / 2, 10f, true)
        columnX += columnWidths[0] * (w - 80f)

        // Quantity total
        drawCenteredText(
            stream,
            "$totalQuantity",
            columnX + (columnWidths[1] * (w - 80f)) / 2,
            currentY - rowHeight / 2,
            10f,
            true
        )
        columnX += columnWidths[1] * (w - 80f)

        // Weight total
        drawCenteredText(
            stream,
            formatter.format(totalWeight),
            columnX + (columnWidths[2] * (w - 80f)) / 2,
            currentY - rowHeight / 2,
            10f,
            true
        )
        columnX += columnWidths[2] * (w - 80f)

        // Stone Value total
        drawCenteredText(
            stream,
            formatter.format(totalStoneValue),
            columnX + (columnWidths[3] * (w - 80f)) / 2,
            currentY - rowHeight / 2,
            10f,
            true
        )
        columnX += columnWidths[3] * (w - 80f)

        // Labour total
        drawCenteredText(
            stream,
            formatter.format(totalLabour),
            columnX + (columnWidths[4] * (w - 80f)) / 2,
            currentY - rowHeight / 2,
            10f,
            true
        )
        columnX += columnWidths[4] * (w - 80f)

        // Gold Value total
        drawCenteredText(
            stream,
            formatter.format(totalGoldValue),
            columnX + (columnWidths[5] * (w - 80f)) / 2,
            currentY - rowHeight / 2,
            10f,
            true
        )
        columnX += columnWidths[5] * (w - 80f)

        // Amount total
        drawCenteredText(
            stream,
            formatter.format(totalAmount),
            columnX + (columnWidths[6] * (w - 80f)) / 2,
            currentY - rowHeight / 2,
            10f,
            true
        )

        // Payment section on right side
        val summaryX = w - 250f
        var summaryY = currentY - rowHeight - 30f

        // Calculate tax amounts
        val taxRate = invoice.items.firstOrNull()?.itemDetails?.taxRate ?: 0.0
        val halfTaxRate = taxRate / 2.0
        val totalTax = invoice.items.sumOf { item ->
            val itemTotal = item.price * item.quantity
            val extraChargesTotal =
                item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
            val taxableAmount = itemTotal + extraChargesTotal
            taxableAmount * (item.itemDetails.taxRate / 100.0)
        }
        val halfTax = totalTax / 2.0

        // Extra charges
        val extraChargesTotal = invoice.items.sumOf { item ->
            item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
        }

        // Draw payment summary with elegant layout
        drawPaymentSummary(
            stream,
            luxuryGold,
            summaryX,
            summaryY,
            w - summaryX - 40f,
            invoice,
            halfTaxRate,
            halfTax,
            extraChargesTotal
        )

        // Amount in words section
        stream.setNonStrokingColor(luxuryDarkGold)
        drawText(stream, "Amount in Words:", 45f, 150f, 10f, true)
        stream.setNonStrokingColor(AWTColor(60, 60, 60))
        drawText(
            stream,
            numberToWords(invoice.totalAmount.toInt()) + " Rupees Only",
            45f,
            135f,
            10f,
            false
        )

        // Terms and conditions
        stream.setNonStrokingColor(luxuryDarkGold)
        drawText(stream, "Terms & Conditions:", 45f, 115f, 10f, true)
        stream.setNonStrokingColor(AWTColor(60, 60, 60))

        // Split terms into lines
        val terms = settings.termsAndConditions.split("\n")
        var yPos = 100f
        for (term in terms) {
            drawText(stream, term, 45f, yPos, 9f, false)
            yPos -= 15f
        }

        // Draw signature section
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                try {
                    drawSignatureImage(document, w - 100f, 100f, settings.signatureUri!!, stream)
                    stream.setNonStrokingColor(luxuryDarkGold)
                    drawText(stream, "Authorized Signatory", w - 140f, 70f, 10f, true)
                    stream.setNonStrokingColor(AWTColor(60, 60, 60))
                    drawText(stream, details.shopName, w - 140f, 55f, 9f, false)
                } catch (e: Exception) {
                    Log.e("InvoicePdfGenerator", "Error drawing signature: ${e.message}")
                    // Fallback to signature line
                    stream.setNonStrokingColor(luxuryDarkGold)
                    drawLine(stream, w - 180f, 80f, w - 60f, 80f, 0.5f, luxuryDarkGold)
                    drawText(stream, "Authorized Signatory", w - 140f, 70f, 10f, true)
                    stream.setNonStrokingColor(AWTColor(60, 60, 60))
                    drawText(stream, details.shopName, w - 140f, 55f, 9f, false)
                }
            } else {
                // Draw signature line
                stream.setNonStrokingColor(luxuryDarkGold)
                drawLine(stream, w - 180f, 80f, w - 60f, 80f, 0.5f, luxuryDarkGold)
                drawText(stream, "Authorized Signatory", w - 140f, 70f, 10f, true)
                stream.setNonStrokingColor(AWTColor(60, 60, 60))
                drawText(stream, details.shopName, w - 140f, 55f, 9f, false)
            }
        }

        // Draw QR code if enabled
        if (settings.showQrCode && settings.upiId.isNotEmpty()) {
            drawUpiQrCode(document, page, settings.upiId, 45f, 80f)
            stream.setNonStrokingColor(luxuryDarkGold)
            drawText(stream, "Scan to Pay", 65f, 35f, 10f, true)
        }

        // Footer
        stream.setNonStrokingColor(AWTColor(120, 120, 120))
        drawCenteredText(stream, "Invoice generated using Swarna Khata Book", w / 2, 25f, 8f, false)


    }


    private fun drawDecorativeCorner(
        stream: PDPageContentStream,
        x: Float,
        y: Float,
        offsetX: Float,
        offsetY: Float,
        color: AWTColor
    ) {
        stream.setStrokingColor(color)
        stream.setLineWidth(1.5f)

        // First line
        stream.moveTo(x, y)
        stream.lineTo(x + offsetX, y)
        stream.stroke()

        // Second line
        stream.moveTo(x, y)
        stream.lineTo(x, y + offsetY)
        stream.stroke()
    }

    // Helper method to draw payment summary for luxury template
    private fun drawPaymentSummary(
        stream: PDPageContentStream,
        luxuryGold: AWTColor,
        x: Float,
        y: Float,
        width: Float,
        invoice: Invoice,
        halfTaxRate: Double,
        halfTax: Double,
        extraChargesTotal: Double
    ) {
        // Draw elegant payment summary box with gold border
        stream.setStrokingColor(luxuryGold)
        stream.setLineWidth(1.0f)
        stream.addRect(x, y - 150f, width, 150f)
        stream.stroke()

        // Gold accent bar at top
        stream.setNonStrokingColor(luxuryGold)
        stream.addRect(x, y, width, 25f)
        stream.fill()

        // Payment summary heading
        stream.setNonStrokingColor(AWTColor(40, 40, 40))
        drawCenteredText(stream, "PAYMENT SUMMARY", x + width / 2, y + 10f, 10f, true)

        var currentY = y - 20f
        val labelX = x + 10f
        val valueX = x + width - 10f

        // Draw each payment detail item
        stream.setNonStrokingColor(AWTColor(60, 60, 60))

        // CGST
        drawText(stream, "CGST (${halfTaxRate.toInt()}%)", labelX, currentY, 9f, false)
        drawRightAlignedText(stream, formatter.format(halfTax), valueX, currentY, 9f, false)
        currentY -= 15f

        // SGST
        drawText(stream, "SGST (${halfTaxRate.toInt()}%)", labelX, currentY, 9f, false)
        drawRightAlignedText(stream, formatter.format(halfTax), valueX, currentY, 9f, false)
        currentY -= 15f

        // Extra charges if any
        if (extraChargesTotal > 0) {
            drawText(stream, "Extra Charges", labelX, currentY, 9f, false)
            drawRightAlignedText(
                stream,
                formatter.format(extraChargesTotal),
                valueX,
                currentY,
                9f,
                false
            )
            currentY -= 15f
        }

        // Separator line before total
        drawLine(stream, labelX, currentY + 5f, x + width - 10f, currentY + 5f, 0.5f, luxuryGold)
        currentY -= 15f

        // Total Amount
        drawText(stream, "Total Amount", labelX, currentY, 10f, true)
        drawRightAlignedText(
            stream,
            formatter.format(invoice.totalAmount),
            valueX,
            currentY,
            10f,
            true
        )
        currentY -= 20f

        // Payment method(s)
        if (invoice.payments.isNotEmpty()) {
            for (payment in invoice.payments) {
                drawText(stream, payment.method, labelX, currentY, 9f, false)
                drawRightAlignedText(
                    stream,
                    formatter.format(payment.amount),
                    valueX,
                    currentY,
                    9f,
                    false
                )
                currentY -= 15f
            }
        }

        // Separator line before balance
        drawLine(stream, labelX, currentY + 5f, x + width - 10f, currentY + 5f, 0.5f, luxuryGold)
        currentY -= 15f

        // Balance
        val balanceDue = invoice.totalAmount - invoice.paidAmount
        drawText(stream, "Balance", labelX, currentY, 10f, true)
        stream.setNonStrokingColor(
            if (balanceDue <= 0) AWTColor(0, 128, 0) else AWTColor(
                192,
                0,
                0
            )
        )
        drawRightAlignedText(stream, formatter.format(balanceDue), valueX, currentY, 10f, true)
    }

    // Helper method for right-aligned text
    private fun drawRightAlignedText(
        contentStream: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float,
        isBold: Boolean
    ) {
        val font = if (isBold) boldFont else regularFont
        val textWidth = font.getStringWidth(text) / 1000 * fontSize
        drawText(contentStream, text, x - textWidth, y, fontSize, isBold)
    }

    private fun generateGstTallyTemplate(
        stream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        details: ShopInvoiceDetails,
        w: Float,
        h: Float
    ) {
        // Draw the full page border
        drawRectangle(stream, 20f, 20f, w - 40f, h - 40f, AWTColor(0, 0, 0))

        // ---------- HEADER SECTION ----------

        // Draw horizontal line to separate header from content
        drawLine(stream, 20f, h - 120f, w - 20f, h - 120f, 0.5f, AWTColor(0, 0, 0))

        // Draw vertical line to divide header into two sections
        drawLine(stream, w / 2, h - 20f, w / 2, h - 120f, 0.5f, AWTColor(0, 0, 0))

        // Shop details on the right side of the header
        stream.setNonStrokingColor(primaryColor) // Light pink color for shop name
        drawText(stream, details.shopName, 30f, h - 60f, 12f, true)

        stream.setNonStrokingColor(AWTColor(0, 0, 0)) // Black for the rest
        drawText(stream, details.address, 30f, h - 75f, 10f, false)
        drawText(stream, "Mobile No: ${details.phoneNumber}", 30f, h - 90f, 10f, false)
        drawText(stream, "GST number: ${details.gstNumber}", 30f, h - 105f, 10f, false)

        // ---------- CUSTOMER AND INVOICE DETAILS SECTION ----------

        // Draw line to separate customer section from item table
        drawLine(stream, 20f, h - 200f, w - 20f, h - 200f, 0.5f, AWTColor(0, 0, 0))

        // Draw vertical line to divide customer and invoice details
        drawLine(stream, w / 2, h - 120f, w / 2, h - 200f, 0.5f, AWTColor(0, 0, 0))

        // Customer details (left side)
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(stream, "BILL TO", 30f, h - 140f, 11f, true)
        drawText(stream, invoice.customerName, 30f, h - 155f, 11f, true)
        drawText(stream, invoice.customerAddress, 30f, h - 170f, 10f, false)
        drawText(stream, "MOBILE NUMBER: ${invoice.customerPhone}", 30f, h - 185f, 10f, false)

        // Invoice details (right side)
        drawText(stream, "Invoice Number", w / 2 + 20f, h - 155f, 11f, false)
        drawText(stream, ":", w / 2 + 120f, h - 155f, 11f, false)

        val invoiceDisplayNumber = if (invoice.invoiceNumber.startsWith(settings.invoicePrefix)) {
            invoice.invoiceNumber
        } else {
            "${settings.invoicePrefix}${invoice.invoiceNumber}"
        }
        drawText(stream, invoiceDisplayNumber, w / 2 + 140f, h - 155f, 11f, false)

        drawText(stream, "Invoice Date", w / 2 + 20f, h - 170f, 11f, false)
        drawText(stream, ":", w / 2 + 120f, h - 170f, 11f, false)
        drawText(
            stream,
            dateFormatter.format(Date(invoice.invoiceDate)),
            w / 2 + 140f,
            h - 170f,
            11f,
            false
        )

        // ---------- ITEMS TABLE SECTION ----------
        var currentY = h - 200f

        // Define table columns for the main items table
        val columns = listOf(
            "Description",
            "QTY",
            "Gr.WT",
            "Stone WT",
            "Net Wt",
            "Stone Value",
            "Labour",
            "Gold Value",
            "Total Value"
        )
        val columnWidths =
            floatArrayOf(0.20f, 0.08f, 0.08f, 0.08f, 0.08f, 0.12f, 0.12f, 0.12f, 0.12f)
        var columnX = 25f

        // Draw table header background
        stream.setNonStrokingColor(primaryColor) // Light pink background for header
        stream.addRect(20f, currentY - 10f, w - 40f, 18f)
        stream.fill()

        // Draw table header text
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        for (i in columns.indices) {
            val textWidth = boldFont.getStringWidth(columns[i]) / 1000 * 10f
            val columnWidth = columnWidths[i] * (w - 40f)
            val textX = columnX + (columnWidth - textWidth) / 2 // Center text in column

            stream.setNonStrokingColor(AWTColor.WHITE)
            drawText(stream, columns[i], textX, currentY - 5f, 10f, true)

            // Draw vertical line for column
            if (i < columns.size - 1) {
                val lineX = columnX + columnWidth
                drawLine(
                    stream,
                    lineX,
                    currentY + 8f,
                    lineX,
                    currentY - 450f,
                    0.5f,
                    AWTColor(0, 0, 0)
                )
            }

            columnX += columnWidth
        }

        currentY -= 20f // Move to first data row

        // Track totals
        var totalQuantity = 0
        var totalGrossWeight = 0.0
        var totalStoneWeight = 0.0
        var totalNetWeight = 0.0
        var totalStoneValue = 0.0
        var totalLabour = 0.0
        var totalGoldValue = 0.0
        var totalValue = 0.0

        // Draw each item row
        for (item in invoice.items) {
            columnX = 25f

            // Calculate values
            val quantity = item.quantity
            val grossWeight = item.itemDetails.grossWeight * quantity
            val netWeight = item.itemDetails.netWeight * quantity
            val stoneWeight = (item.itemDetails.grossWeight - item.itemDetails.netWeight) * quantity

            // Calculate labor
            val labour = when (item.itemDetails.makingChargesType.uppercase()) {
                "PER GRAM" -> item.itemDetails.makingCharges * netWeight
                "FIX" -> item.itemDetails.makingCharges * quantity
                "PERCENTAGE" -> {
                    val metalValue = when (item.itemDetails.metalRateOn.uppercase()) {
                        "NET WEIGHT" -> item.itemDetails.metalRate * netWeight
                        "GROSS WEIGHT" -> item.itemDetails.metalRate * grossWeight
                        else -> item.itemDetails.metalRate * netWeight
                    }
                    (metalValue * item.itemDetails.makingCharges / 100.0)
                }

                else -> 0.0
            }

            // Calculate stone value
            val stoneValue = item.itemDetails.diamondPrice * quantity

            // Calculate gold value
            val goldValue = when (item.itemDetails.metalRateOn.uppercase()) {
                "NET WEIGHT" -> item.itemDetails.metalRate * netWeight
                "GROSS WEIGHT" -> item.itemDetails.metalRate * grossWeight
                else -> item.itemDetails.metalRate * netWeight
            }

            val itemTotal = item.price * quantity

            // Update totals
            totalQuantity += quantity
            totalGrossWeight += grossWeight
            totalStoneWeight += stoneWeight
            totalNetWeight += netWeight
            totalStoneValue += stoneValue
            totalLabour += labour
            totalGoldValue += goldValue
            totalValue += itemTotal

            stream.setNonStrokingColor(AWTColor(0, 0, 0))

            // Description
            drawText(stream, item.itemDetails.displayName, columnX + 2f, currentY, 9f, false)
            // Second line - purity and rate
            val purityRateText =
                "${item.itemDetails.purity} @ ${formatter.format(item.itemDetails.metalRate)}"
            drawText(stream, purityRateText, columnX + 2f, currentY - 12f, 8f, false)
            columnX += columnWidths[0] * (w - 40f)

            // Quantity
            val qtyX = columnX + (columnWidths[1] * (w - 40f)) / 2 - 5f
            drawText(stream, "$quantity", qtyX, currentY, 9f, false)
            columnX += columnWidths[1] * (w - 40f)

            // Gross Weight
            val gwX = columnX + (columnWidths[2] * (w - 40f)) / 2 - 15f
            drawText(stream, formatter.format(grossWeight), gwX, currentY, 9f, false)
            columnX += columnWidths[2] * (w - 40f)

            // Stone Weight
            val swX = columnX + (columnWidths[3] * (w - 40f)) / 2 - 10f
            drawText(stream, formatter.format(stoneWeight), swX, currentY, 9f, false)
            columnX += columnWidths[3] * (w - 40f)

            // Net Weight
            val nwX = columnX + (columnWidths[4] * (w - 40f)) / 2 - 15f
            drawText(stream, formatter.format(netWeight), nwX, currentY, 9f, false)
            columnX += columnWidths[4] * (w - 40f)

            // Stone Value
            val svX = columnX + (columnWidths[5] * (w - 40f)) / 2 - 10f
            drawText(stream, formatter.format(stoneValue), svX, currentY, 9f, false)
            columnX += columnWidths[5] * (w - 40f)

            // Labour
            val lbX = columnX + (columnWidths[6] * (w - 40f)) / 2 - 20f
            drawText(stream, formatter.format(labour), lbX, currentY, 9f, false)
            columnX += columnWidths[6] * (w - 40f)

            // Gold Value
            val gvX = columnX + (columnWidths[7] * (w - 40f)) / 2 - 20f
            drawText(stream, formatter.format(goldValue), gvX, currentY, 9f, false)
            columnX += columnWidths[7] * (w - 40f)

            // Total Value
            val tvX = columnX + (columnWidths[8] * (w - 40f)) / 2 - 20f
            drawText(stream, formatter.format(itemTotal), tvX, currentY, 9f, false)

            currentY -= 25f
        }

        // Calculate a reasonable Y position for subtotal that will always be visible
        // This makes sure the subtotal row shows regardless of number of items
        var subtotalY = 187f

        // Draw horizontal line before subtotal
        drawLine(stream, 20f, subtotalY + 10f, w - 20f, subtotalY + 10f, 0.5f, AWTColor(0, 0, 0))

        // Draw subtotal row with pink background
        stream.setNonStrokingColor(primaryColor)
        stream.addRect(20f, subtotalY, w - 40f, 18f)
        stream.fill()

        // Draw subtotal text
        stream.setNonStrokingColor(AWTColor.WHITE)
        columnX = 25f

        // Sub Total text
        drawText(stream, "Sub Total", columnX + 2f, subtotalY + 5f, 10f, true)
        columnX += columnWidths[0] * (w - 40f)

        // Quantity
        val qtyX = columnX + (columnWidths[1] * (w - 40f)) / 2 - 5f
        drawText(stream, "$totalQuantity", qtyX, subtotalY + 5f, 10f, true)
        columnX += columnWidths[1] * (w - 40f)

        // Skip gross weight in subtotal
        columnX += columnWidths[2] * (w - 40f)

        // Skip stone weight in subtotal
        columnX += columnWidths[3] * (w - 40f)

        // Net Weight
        val nwX = columnX + (columnWidths[4] * (w - 40f)) / 2 - 10f
        drawText(stream, formatter.format(totalNetWeight), nwX, subtotalY + 5f, 10f, true)
        columnX += columnWidths[4] * (w - 40f)

        // Stone Value
        val svX = columnX + (columnWidths[5] * (w - 40f)) / 2 - 10f
        drawText(stream, formatter.format(totalStoneValue), svX, subtotalY + 5f, 10f, true)
        columnX += columnWidths[5] * (w - 40f)

        // Labour
        val lbX = columnX + (columnWidths[6] * (w - 40f)) / 2 - 10f
        drawText(stream, formatter.format(totalLabour), lbX, subtotalY + 5f, 10f, true)
        columnX += columnWidths[6] * (w - 40f)

        // Gold Value
        val gvX = columnX + (columnWidths[7] * (w - 40f)) / 2 - 10f
        drawText(stream, formatter.format(totalGoldValue), gvX, subtotalY + 5f, 10f, true)
        columnX += columnWidths[7] * (w - 40f)

        // Total Value
        val tvX = columnX + (columnWidths[8] * (w - 40f)) / 2 - 10f
        drawText(stream, formatter.format(totalValue), tvX, subtotalY + 5f, 10f, true)

        // Move to the tax and charges section
        subtotalY -= 30f

        // ---------- TAX AND PAYMENT SECTION ----------

        // Define tax section columns
        val taxColumns =
            listOf("CGST", "SGST", "Total Extra Charges", "Total Received Amount", "Total Amount")
        val taxColumnWidths = floatArrayOf(0.15f, 0.15f, 0.3f, 0.2f, 0.2f)

        // Draw tax section headers with pink background
        stream.setNonStrokingColor(primaryColor)
        stream.addRect(20f, subtotalY, w - 40f, 18f)
        stream.fill()

        // Draw tax header text
        stream.setNonStrokingColor(AWTColor.WHITE)
        columnX = 25f
        for (i in taxColumns.indices) {
            val taxColumnWidth = taxColumnWidths[i] * (w - 40f)
            val textWidth = boldFont.getStringWidth(taxColumns[i]) / 1000 * 10f
            val textX = columnX + (taxColumnWidth - textWidth) / 2 // Center text

            drawText(stream, taxColumns[i], textX, subtotalY + 5f, 10f, true)

            // Draw vertical lines between tax columns
            if (i < taxColumns.size - 1) {
                val lineX = columnX + taxColumnWidth
                drawLine(
                    stream,
                    lineX,
                    subtotalY + 18f,
                    lineX,
                    subtotalY - 35f,
                    0.5f,
                    AWTColor(0, 0, 0)
                )
            }

            columnX += taxColumnWidth
        }

        // Draw horizontal line below tax headers
        drawLine(stream, 20f, subtotalY, w - 20f, subtotalY, 0.5f, AWTColor(0, 0, 0))

        subtotalY -= 20f

        // Calculate tax amounts
        val taxRate = invoice.items.firstOrNull()?.itemDetails?.taxRate ?: 0.0
        val halfTaxRate = taxRate / 2.0
        val totalTax = invoice.items.sumOf { item ->
            val itemTotal = item.price * item.quantity
            val extraChargesTotal =
                item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
            val taxableAmount = itemTotal + extraChargesTotal
            taxableAmount * (item.itemDetails.taxRate / 100.0)
        }
        val halfTax = totalTax / 2.0

        // Calculate extra charges
        val extraChargesTotal = invoice.items.sumOf { item ->
            item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
        }

        // Draw tax values
        columnX = 25f

        stream.setNonStrokingColor(AWTColor(0, 0, 0))

        // CGST
        val cgstX = columnX + (taxColumnWidths[0] * (w - 40f)) / 2 - 5f
        drawText(stream, formatter.format(halfTax), cgstX, subtotalY + 5f, 10f, false)
        columnX += taxColumnWidths[0] * (w - 40f)

        // SGST
        val sgstX = columnX + (taxColumnWidths[1] * (w - 40f)) / 2 - 5f
        drawText(stream, formatter.format(halfTax), sgstX, subtotalY + 5f, 10f, false)
        columnX += taxColumnWidths[1] * (w - 40f)

        // Total Extra Charges
        val ecX = columnX + (taxColumnWidths[2] * (w - 40f)) / 2 - 5f
        drawText(stream, formatter.format(extraChargesTotal), ecX, subtotalY + 5f, 10f, false)
        columnX += taxColumnWidths[2] * (w - 40f)

        // Total Received Amount
        val raX = columnX + (taxColumnWidths[3] * (w - 40f)) / 2 - 5f
        drawText(stream, formatter.format(invoice.paidAmount), raX, subtotalY + 5f, 10f, false)
        columnX += taxColumnWidths[3] * (w - 40f)


        val balanceDue = invoice.totalAmount - invoice.paidAmount

        // Total Amount
        val taX = columnX + (taxColumnWidths[4] * (w - 40f)) / 2 - 5f
        drawText(stream, formatter.format(balanceDue), taX, subtotalY + 5f, 10f, true)

        // Draw horizontal line after tax values
        drawLine(stream, 20f, subtotalY - 15f, w - 20f, subtotalY - 15f, 0.5f, AWTColor(0, 0, 0))

        // ---------- AMOUNT IN WORDS SECTION ----------
        // Move Amount in Words section above QR code section


//        numberToWords(invoice.totalAmount.toInt()) + " Rupees Only"
        // Draw "Amount in Words" label and value
        stream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(
            stream,
            "AMOUNT IN WORDS       :        ${numberToWords(balanceDue.toInt())}" + " Rupees Only",
            25.5f,
            110f,
            10f,
            true
        )


        // ---------- FOOTER SECTION ----------
        // Keep your existing footer implementation as is
        val footerHeight = 85f
        drawRectangle(stream, 20f, 20f, (w - 40f), footerHeight, AWTColor(0, 0, 0))

        // Draw QR code if enabled
        if (settings.showQrCode && settings.upiId.isNotEmpty()) {
            drawUpiQrCode2(document, page, settings.upiId, 20.5f, 20.5f)
        }

        drawLine(stream, 105f, 20f, 105f, 105f, 0.5f, AWTColor(0, 0, 0))

        // Draw Terms and Conditions
        drawText(stream, "Term And Condition", 125f, 90f, 10f, true)

        // Split terms into lines and draw each line
        val terms = settings.termsAndConditions.split("\n")
        for (i in terms.indices) {
            drawText(stream, terms[i], 125f, 70f - (i * 15f), 9f, false)
        }

        drawLine(
            stream,
            160f + (w - 40f) / 2,
            20f,
            160f + (w - 40f) / 2,
            105f,
            0.5f,
            AWTColor(0, 0, 0)
        )

        // Draw signature line
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                // Draw uploaded signature image
                try {
                    drawSignatureImage2(document, w - 90f, 55f, settings.signatureUri!!, stream)
                    drawText(stream, "AUTHORISED SIGNATURE ", w - 140, 30f, 10f, true)
                } catch (e: Exception) {
                    Log.e("InvoicePdfGenerator", "Error drawing signature: ${e.message}")
                    // Fall back to text-only signature line
                    drawText(stream, "AUTHORISED SIGNATURE ", w - 140, 30f, 10f, true)
                }
            } else {
                // Draw signature line
                drawText(stream, "AUTHORISED SIGNATURE ", w - 140, 30f, 10f, true)
            }
        } else {
            // Draw signature text only
            drawText(stream, "AUTHORISED SIGNATURE ", w - 140, 30f, 10f, true)
        }
    }

    // Original template (now called Simple template)
    private fun generateSimpleTemplate(
        contentStream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        shop: ShopInvoiceDetails,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Draw page structure with border
        drawRectangle(contentStream, 20f, 20f, pageWidth - 40f, pageHeight - 40f, secondaryColor)

        // Header section line
        drawLine(
            contentStream,
            20f,
            pageHeight - 100f,
            pageWidth - 20f,
            pageHeight - 100f,
            0.5f,
            primaryColor
        )

        // TAX INVOICE title - larger, bold and centered
        contentStream.setFont(boldFont, 22f)
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.beginText()
        val titleText = "TAX INVOICE"
        val titleWidth = boldFont.getStringWidth(titleText) / 1000 * 22f
        contentStream.newLineAtOffset(pageWidth / 2 - titleWidth / 2, pageHeight - 40f)
        contentStream.showText(titleText)
        contentStream.endText()

        // Invoice number with prefix
        val invoiceDisplayNumber = if (invoice.invoiceNumber.startsWith(settings.invoicePrefix)) {
            invoice.invoiceNumber
        } else {
            "${settings.invoicePrefix}${invoice.invoiceNumber}"
        }

        drawText(
            contentStream, "Invoice No: $invoiceDisplayNumber", 25f, pageHeight - 70f, 12f, true
        )
        drawText(
            contentStream,
            "Date: ${dateFormatter.format(Date(invoice.invoiceDate))}",
            pageWidth - 200f,
            pageHeight - 70f,
            12f,
            true
        )

        // Customer section title and details
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(contentStream, "Customer:", 25f, pageHeight - 120f, 12f, true)
        drawText(contentStream, invoice.customerName, 25f, pageHeight - 135f, 10f, false)
        drawText(
            contentStream, "Phone: ${invoice.customerPhone}", 25f, pageHeight - 150f, 10f, false
        )
        drawText(contentStream, invoice.customerAddress, 25f, pageHeight - 165f, 10f, false)

        // Shop section title and details
        drawText(contentStream, "Shop:", pageWidth / 2 + 20f, pageHeight - 120f, 12f, true)
        drawText(contentStream, shop.shopName, pageWidth / 2 + 20f, pageHeight - 135f, 10f, false)
        drawText(contentStream, shop.address, pageWidth / 2 + 20f, pageHeight - 150f, 10f, false)
        drawText(
            contentStream,
            "GSTIN: ${shop.gstNumber}",
            pageWidth / 2 + 20f,
            pageHeight - 165f,
            10f,
            false
        )

        // Start Y position for items table
        var currentY = pageHeight - 170f

        // Draw a single comprehensive items table
        currentY = drawItemsTable(contentStream, invoice.items, 25f, currentY, pageWidth - 50f)

        // Draw tax details with extra charges section
        currentY = drawTaxAndExtraCharges(contentStream, invoice, 25f, currentY, pageWidth - 50f)

        // Draw payment details
        currentY = drawPaymentDetails(contentStream, invoice, 25f, currentY, pageWidth - 50f)

        // Draw totals and balance
        currentY = drawTotalsSection(contentStream, invoice, 25f, currentY, pageWidth - 50f)

        // Draw QR code if enabled
        if (settings.showQrCode && settings.upiId.isNotEmpty()) {
            drawUpiQrCode(document, page, settings.upiId, 25f, currentY - 80f)
        }

        // Draw footer with terms and conditions
        drawFooter(document, contentStream, shop, pageWidth, pageHeight)
    }

    private fun drawItemsTable(
        contentStream: PDPageContentStream,
        items: List<InvoiceItem>,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        // Define headers
        val headers = listOf(
            "Description",
            "Gr.Wt",
            "Net.Wt",
            "PCS",
            "Labour",
            "Stone Val",
            "Purity",
            "Rate",
            "Total"
        )

        // Define column widths proportions
        val columnWidths =
            floatArrayOf(0.25f, 0.08f, 0.08f, 0.06f, 0.10f, 0.10f, 0.08f, 0.10f, 0.15f)

        // Calculate actual widths
        val actualWidths = columnWidths.map { it * width }.toFloatArray()

        // Define which columns should be right-aligned (typically numeric columns)
        val isRightAligned = arrayOf(false, true, true, true, true, true, true, true, true)

        // Define which columns should be bold
        val isBold = arrayOf(false, false, false, false, false, false, false, false, false)

        // Prepare data rows
        val dataRows = mutableListOf<List<String>>()

        // Track totals
        var totalNetWeight = 0.0
        var totalPieces = 0
        var totalLabour = 0.0
        var totalStoneValue = 0.0
        var totalAmount = 0.0

        // Build data rows
        for (item in items) {
            val netWeight = item.itemDetails.netWeight
            val pieces = item.quantity

            // Calculate labor correctly
            val labour = when (item.itemDetails.makingChargesType.uppercase()) {
                "PER GRAM" -> item.itemDetails.makingCharges * netWeight * pieces
                "FIX" -> item.itemDetails.makingCharges * pieces
                "PERCENTAGE" -> {
                    val metalValue = when (item.itemDetails.metalRateOn.uppercase()) {
                        "NET WEIGHT" -> item.itemDetails.metalRate * netWeight
                        "GROSS WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.grossWeight
                        else -> item.itemDetails.metalRate * netWeight
                    }
                    (metalValue * item.itemDetails.makingCharges / 100.0) * pieces
                }

                else -> 0.0
            }

            val stoneValue = item.itemDetails.diamondPrice * item.quantity
            val itemTotal = item.price * item.quantity

            // Update totals
            totalNetWeight += netWeight * pieces
            totalPieces += pieces
            totalLabour += labour
            totalStoneValue += stoneValue
            totalAmount += itemTotal

            // Add row data
            dataRows.add(
                listOf(
                    item.itemDetails.displayName,
                    formatter.format(item.itemDetails.grossWeight),
                    formatter.format(netWeight),
                    pieces.toString(),
                    formatter.format(labour),
                    formatter.format(stoneValue),
                    item.itemDetails.purity,
                    formatter.format(item.itemDetails.metalRate),
                    formatter.format(itemTotal)
                )
            )
        }

        // Add totals row with proper formatting
        dataRows.add(
            listOf(
                "Totals",
                "",  // Skip gross weight total
                formatter.format(totalNetWeight),
                totalPieces.toString(),
                formatter.format(totalLabour),
                formatter.format(totalStoneValue),
                "",  // Skip purity
                "",  // Skip rate
                formatter.format(totalAmount)
            )
        )

        // Define which total row values should be bold
        val totalRowBold = arrayOf(true, false, true, true, true, true, false, false, true)
        isBold[dataRows.size - 1] = true

        // Draw the table with all elements
        return drawTableWithGrid(
            contentStream,
            headers,
            dataRows,
            x,
            y,
            width,
            actualWidths,
            isRightAligned,
            isBold
        )
    }


    private fun generateStylishTemplate(
        contentStream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        shop: ShopInvoiceDetails,
        pageWidth: Float,
        pageHeight: Float
    ) {

        // Draw header accent bar
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.addRect(20f, pageHeight - 115f, pageWidth - 40f, 2f)
        contentStream.fill()

        // TAX INVOICE title at the top left
        contentStream.setFont(boldFont, 10f)
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        contentStream.beginText()
        contentStream.newLineAtOffset(25f, pageHeight - 40f)
        contentStream.showText("TAX INVOICE")
        contentStream.endText()

        contentStream.setFont(regularFont, 8f)

        // Calculate text dimensions
        val recipientText = "ORIGINAL FOR RECIPIENT"
        val textWidth1 = regularFont.getStringWidth(recipientText) / 1000 * 8f // Convert to actual width
        val textHeight = 8f
        val padding = 3f // Padding around the text

        // Define gray color
        val grayColor = AWTColor(120, 120, 120) // Medium gray

        // Draw rectangle around text with gray border
        contentStream.setStrokingColor(grayColor) // Gray border
        contentStream.setLineWidth(0.5f) // Thin border

        // Rectangle coordinates: x, y, width, height
        // Note: y is the bottom-left corner of the rectangle
        contentStream.addRect(
            100f - padding,
            pageHeight - 40f - textHeight + 3f,
            textWidth1 + (padding * 2),
            textHeight + (padding * 2)+3f
        )
        contentStream.stroke() // Draw the outlined rectangle

        // Draw the text in gray
        contentStream.setNonStrokingColor(grayColor) // Gray text
        contentStream.beginText()
        contentStream.newLineAtOffset(100f, pageHeight - 40f)
        contentStream.showText(recipientText)
        contentStream.endText()

        // Shop name and details
        contentStream.setNonStrokingColor(primaryColor)
        drawText(contentStream, shop.shopName, 25f, pageHeight - 70f, 14f, true)
        contentStream.setNonStrokingColor(secondaryColor)
        drawText(contentStream, shop.address, 25f, pageHeight - 85f, 9f, false)
        drawText(
            contentStream,
            "Mobile: ${shop.phoneNumber}," + " " + "GSTIN: ${shop.gstNumber}",
            25f,
            pageHeight - 100f,
            9f,
            false
        )

        // Draw shop logo if enabled (top right, aligned with shop name)
        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(
                    document, pageWidth - 60f, pageHeight - 70f, settings.logoUri!!, contentStream
                )
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Customer details section
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(contentStream, "BILL TO", 25f, pageHeight - 135f, 10f, true)
        drawText(
            contentStream,
            "INVOICE DETAILS",
            pageWidth / 2 + 10f,
            pageHeight - 135f,
            10f,
            true
        )

        // Customer details
        drawText(contentStream, invoice.customerName, 25f, pageHeight - 150f, 10f, true)
        drawText(contentStream, invoice.customerAddress, 25f, pageHeight - 165f, 9f, false)
        drawText(
            contentStream,
            "Mobile: ${invoice.customerPhone}",
            25f,
            pageHeight - 180f,
            9f,
            false
        )

        // Invoice details on right
        drawText(contentStream, "Invoice No.:", pageWidth / 2 + 10f, pageHeight - 150f, 9f, true)
        drawText(
            contentStream,
            settings.invoicePrefix + invoice.invoiceNumber,
            pageWidth / 2 + 80f,
            pageHeight - 150f,
            9f,
            false
        )

        // Invoice date
        drawText(contentStream, "Invoice Date:", pageWidth / 2 + 10f, pageHeight - 165f, 9f, true)
        drawText(
            contentStream,
            dateFormatter.format(Date(invoice.invoiceDate)),
            pageWidth / 2 + 80f,
            pageHeight - 165f,
            9f,
            false
        )

        // Draw items table with clean borders
        var currentY = pageHeight - 210f

        // Draw items with stylish headers - modified for better formatting
        currentY = drawImprovedStylishItemsTable(
            contentStream,
            invoice.items,
            25f,
            currentY,
            pageWidth - 50f
        )

        // Draw terms and conditions heading
        drawText(contentStream, "TERMS AND CONDITIONS", 25f, currentY - 40f, 10f, true)
        // Set color to black explicitly for terms and conditions
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        val terms = settings.termsAndConditions.split("\n")
        var yPos = currentY - 55f
        for (term in terms) {
            drawText(contentStream, term, 25f, yPos, 9f, false)
            yPos -= 15f
        }

        drawText(contentStream, "Amount in words    :", 25f, yPos - 15f, 9f, true)
        drawText(
            contentStream,
            numberToWords(invoice.totalAmount.toInt() - invoice.paidAmount.toInt()) + " Rupees Only",
            25f,
            yPos - 30f,
            9f,
            false
        )

        // Right side financial summary section
        currentY = drawCompactFinancialSummary(
            contentStream,
            invoice,
            pageWidth / 2 + 100f,
            currentY,
            (pageWidth / 2) - 125f
        )

        // Define signature and QR code Y position (aligned with each other)
        val signatureY = 120f

        // Draw QR code on the left side (if enabled)
        if (settings.showQrCode && settings.upiId.isNotEmpty()) {
            drawUpiQrCode(document, page, settings.upiId, 25f, signatureY - 50f)
        }

        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))

        // Draw signature at right bottom
// Fix #3: Add signature implementation
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                // Draw uploaded signature image
                try {
                    // First draw a rectangle for signature (new code)
                    contentStream.setStrokingColor(AWTColor(0, 0, 0))  // Black outline
                    contentStream.setLineWidth(0.5f)  // Thin border
                    contentStream.addRect(pageWidth - 150f, 90f, 120f, 30f)  // Signature box
                      // Signature box
                    contentStream.stroke()  // Draw the rectangle outline

                    // Then draw uploaded signature image inside the box
                    drawSignatureImage(document, pageWidth - 80f, 100f, settings.signatureUri!!, contentStream)
                    contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
                    drawText(contentStream, "AUTHORIZED SIGNATURE", pageWidth - 120f, 80f, 9f, true)
                    drawText(contentStream, shop.shopName, pageWidth - 120f, 65f, 8f, false)
                } catch (e: Exception) {
                    Log.e("InvoicePdfGenerator", "Error drawing signature: ${e.message}")
                    // Fall back to text-only signature line with box
                    contentStream.setStrokingColor(AWTColor(0, 0, 0))
                    contentStream.setLineWidth(0.5f)
                    contentStream.addRect(pageWidth - 150f, 90f, 120f, 30f)  // Signature box
                    contentStream.stroke()

                    contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
                    drawText(contentStream, "AUTHORIZED SIGNATURE", pageWidth - 120f, 80f, 10f, true)
                    drawText(contentStream, shop.shopName, pageWidth - 120f, 65f, 9f, false)
                }
            } else {
                // Draw signature box with line
                contentStream.setStrokingColor(AWTColor(0, 0, 0))
                contentStream.setLineWidth(0.5f)
                contentStream.addRect(pageWidth - 150f, 90f, 120f, 30f)  // Signature box
                contentStream.stroke()

                contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
                drawText(contentStream, "AUTHORIZED SIGNATURE", pageWidth - 120f, 80f, 9f, true)
                drawText(contentStream, shop.shopName, pageWidth - 120f, 65f, 8f, false)
            }
        } else {
            // Shop name at the bottom right even if signature is not shown
            contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
            drawText(contentStream, shop.shopName, pageWidth - 120f, 65f, 10f, true)
        }



        // Draw footer with vector drawable logos at bottom and enclose in rectangle
// Function to convert vector drawable to bitmap


// Reduced size for app logo
        val appLogoBitmap = vectorToBitmap(context, R.drawable.swarna_khata_book_03, 25, 25) // Reduced from 60x60
        val appLogoStream = ByteArrayOutputStream()
        appLogoBitmap.compress(Bitmap.CompressFormat.PNG, 100, appLogoStream)
        val appLogoBytes = appLogoStream.toByteArray()
        val appLogo = PDImageXObject.createFromByteArray(document, appLogoBytes, "app_logo")

// Reduced size for Play Store logo
        val playStoreLogoBitmap = vectorToBitmap(context, R.drawable.ion__logo_google_playstore, 25, 25) // Reduced from 60x60
        val playStoreLogoStream = ByteArrayOutputStream()
        playStoreLogoBitmap.compress(Bitmap.CompressFormat.PNG, 100, playStoreLogoStream)
        val playStoreLogoBytes = playStoreLogoStream.toByteArray()
        val playStoreLogo = PDImageXObject.createFromByteArray(document, playStoreLogoBytes, "playstore_logo")

// Draw enclosing rectangle for footer
        contentStream.setStrokingColor(secondaryColor)
        contentStream.setLineWidth(0.5f)
        contentStream.addRect(pageWidth/2 - 130f, 30f, 260f, 15f)
//        contentStream.addRect( pageWidth / 2 - 105f, 30f, 260f, 15f)
        contentStream.stroke()

// Draw logos and text
// Scale the images to appropriate size for the PDF
        val logoWidth = 15f  // Reduced from 20f
        val logoHeight = 15f // Reduced from 20f

        //reduse 120 tp 90
//        contentStream.drawImage(appLogo, pageWidth / 2 - 100f, 30f, logoWidth, logoHeight)
//        contentStream.drawImage(playStoreLogo, pageWidth / 2 + 135f, 30f, logoWidth, logoHeight)
        // Position the app logo on the left side of centered text
        contentStream.drawImage(appLogo, pageWidth/2 - 125f, 30f, logoWidth, logoHeight)
// Position the play store logo on the right side of centered text
        contentStream.drawImage(playStoreLogo, pageWidth/2 + 110f, 30f, logoWidth, logoHeight)

// Draw text centered between the logos
        contentStream.setFont(regularFont, 7f)
        contentStream.setNonStrokingColor(secondaryColor)
        contentStream.beginText()
//        contentStream.newLineAtOffset(pageWidth / 2 - 80f, 35f)
        val footerText = "Invoice Created From SwarnaKhataBook, Available On Play Store"
        val textWidth = regularFont.getStringWidth(footerText) / 1000 * 7f
        contentStream.newLineAtOffset(pageWidth/2 - textWidth/2, 35f)
        contentStream.showText(footerText)
        contentStream.endText()

    }

    fun vectorToBitmap(context: Context, drawableId: Int, width: Int, height: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable?.setBounds(0, 0, canvas.width, canvas.height)
        drawable?.draw(canvas)
        return bitmap
    }

    private fun drawImprovedStylishItemsTable(
        contentStream: PDPageContentStream,
        items: List<InvoiceItem>,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        // Define headers - reduced columns by removing purity and rate (now part of description)
        val headers = listOf(
            "Description",
            "Gr.Wt",
            "Net.Wt",
            "PCS",
            "Labour",
            "Stone Val",
            "Total"
        )

        // Define column widths proportions - adjusted to account for removed columns
        val columnWidths = floatArrayOf(0.35f, 0.08f, 0.08f, 0.06f, 0.12f, 0.12f, 0.19f)

        // Calculate actual widths
        val actualWidths = columnWidths.map { it * width }.toFloatArray()

        // Define which columns should be right-aligned
        val isRightAligned = arrayOf(false, true, true, true, true, true, true)

        // Draw header row
        var xPos = x
        for (i in headers.indices) {
            // Calculate text position based on alignment
            val textX =
                if (isRightAligned[i]) xPos + actualWidths[i] - 5f - (boldFont.getStringWidth(
                    headers[i]
                ) / 1000 * 10f)
                else xPos + 5f

            // Draw header text
            contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
            drawText(contentStream, headers[i], textX, y, 10f, true)

            // Move to next column position
            xPos += actualWidths[i]
        }

        // Draw line above headers
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.addRect(x, y + 15f, width, 1.5f)
        contentStream.fill()

        // Draw line below headers
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.addRect(x, y - 10f, width, 1.5f)
        contentStream.fill()

        // Reset text color
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))

        // Prepare data rows
        val rowHeight = 15f
        var currentY = y - 10f - rowHeight

        // Track totals
        var totalNetWeight = 0.0
        var totalPieces = 0
        var totalLabour = 0.0
        var totalStoneValue = 0.0
        var totalAmount = 0.0

        // Build data rows
        for (item in items) {
            val netWeight = item.itemDetails.netWeight
            val grossWeight = item.itemDetails.grossWeight
            val pieces = item.quantity

            // Calculate labor correctly
            val labour = when (item.itemDetails.makingChargesType.uppercase()) {
                "PER GRAM" -> item.itemDetails.makingCharges * netWeight * pieces
                "FIX" -> item.itemDetails.makingCharges * pieces
                "PERCENTAGE" -> {
                    val metalValue = when (item.itemDetails.metalRateOn.uppercase()) {
                        "NET WEIGHT" -> item.itemDetails.metalRate * netWeight
                        "GROSS WEIGHT" -> item.itemDetails.metalRate * item.itemDetails.grossWeight
                        else -> item.itemDetails.metalRate * netWeight
                    }
                    (metalValue * item.itemDetails.makingCharges / 100.0) * pieces
                }

                else -> 0.0
            }

            val stoneValue = item.itemDetails.diamondPrice * item.quantity
            val itemTotal = item.price * item.quantity

            // Update totals
            totalNetWeight += netWeight * pieces
            totalPieces += pieces
            totalLabour += labour
            totalStoneValue += stoneValue
            totalAmount += itemTotal

            // Draw row data
            xPos = x

            // Item description in 2 lines
            // Line 1: ItemName ItemCode
            val itemNameText = item.itemDetails.displayName
            val line1Text = itemNameText
            drawText(contentStream, line1Text, xPos + 5f, currentY, 10f, false)

            // Line 2: Purity (bold) @ Rate (bold and italic)
            val purityText = item.itemDetails.purity
            val rateText = formatter.format(item.itemDetails.metalRate)

            // We don't have a direct way to do italic in PDFBox, so we'll just use bold
            // Draw purity in bold
            drawText(contentStream, purityText, xPos + 5f, currentY - 12f, 10f, true)

            // Draw @ symbol
            val purityWidth = boldFont.getStringWidth(purityText) / 1000 * 10f
            drawText(contentStream, " @ ", xPos + 5f + purityWidth, currentY - 12f, 10f, false)

            // Draw rate in bold (we would make it italic if we could)
            val atSymbolWidth = regularFont.getStringWidth(" @ ") / 1000 * 10f
            drawText(
                contentStream,
                rateText,
                xPos + 5f + purityWidth + atSymbolWidth,
                currentY - 12f,
                10f,
                true
            )

            // Move to the next columns
            xPos += actualWidths[0]

            // Draw remaining columns
            // Column 2: Gross Weight
            val grossWeightText = formatter.format(grossWeight)
            val grossWeightX =
                if (isRightAligned[1]) xPos + actualWidths[1] - 5f - (regularFont.getStringWidth(
                    grossWeightText
                ) / 1000 * 10f)
                else xPos + 5f
            drawText(contentStream, grossWeightText, grossWeightX, currentY, 10f, false)
            xPos += actualWidths[1]

            // Column 3: Net Weight
            val netWeightText = formatter.format(netWeight)
            val netWeightX =
                if (isRightAligned[2]) xPos + actualWidths[2] - 5f - (regularFont.getStringWidth(
                    netWeightText
                ) / 1000 * 10f)
                else xPos + 5f
            drawText(contentStream, netWeightText, netWeightX, currentY, 10f, false)
            xPos += actualWidths[2]

            // Column 4: Pieces
            val piecesText = pieces.toString()
            val piecesX =
                if (isRightAligned[3]) xPos + actualWidths[3] - 5f - (regularFont.getStringWidth(
                    piecesText
                ) / 1000 * 10f)
                else xPos + 5f
            drawText(contentStream, piecesText, piecesX, currentY, 10f, false)
            xPos += actualWidths[3]

            // Column 5: Labour
            val labourText = formatter.format(labour)
            val labourX =
                if (isRightAligned[4]) xPos + actualWidths[4] - 5f - (regularFont.getStringWidth(
                    labourText
                ) / 1000 * 10f)
                else xPos + 5f
            drawText(contentStream, labourText, labourX, currentY, 10f, false)
            xPos += actualWidths[4]

            // Column 6: Stone Value
            val stoneText = formatter.format(stoneValue)
            val stoneX =
                if (isRightAligned[5]) xPos + actualWidths[5] - 5f - (regularFont.getStringWidth(
                    stoneText
                ) / 1000 * 10f)
                else xPos + 5f
            drawText(contentStream, stoneText, stoneX, currentY, 10f, false)
            xPos += actualWidths[5]

            // Column 7: Total
            val totalText = "₹${formatter.format(itemTotal)}"
            val totalX =
                if (isRightAligned[6]) xPos + actualWidths[6] - 5f - (regularFont.getStringWidth(
                    totalText
                ) / 1000 * 10f)
                else xPos + 5f
            drawText(contentStream, totalText, totalX, currentY, 10f, false)

            // Move to next row
            currentY -= rowHeight * 1.5f

            // No line between items as requested
        }


        // Draw totals line with more spacing to prevent overlapping
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.addRect(x, currentY - 10f, width, 1.5f)
        contentStream.fill()
        currentY -= 25f  // More space after line

        // Subtotal label
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(contentStream, "TOTALS", x + 5f, currentY, 10f, true)


        // Draw totals for each column (skipping description column)
        // Net Weight total
        xPos = x + actualWidths[0] + actualWidths[1]
        val netWeightTotalText = formatter.format(totalNetWeight)
        val netWeightTotalX =
            xPos + actualWidths[2] - 5f - (boldFont.getStringWidth(netWeightTotalText) / 1000 * 10f)
        drawText(contentStream, netWeightTotalText, netWeightTotalX, currentY, 10f, true)

        // Pieces total
        xPos += actualWidths[2]
        val piecesTotalText = totalPieces.toString()
        val piecesTotalX =
            xPos + actualWidths[3] - 5f - (boldFont.getStringWidth(piecesTotalText) / 1000 * 10f)
        drawText(contentStream, piecesTotalText, piecesTotalX, currentY, 10f, true)

        // Labour total
        xPos += actualWidths[3]
        val labourTotalText = formatter.format(totalLabour)
        val labourTotalX =
            xPos + actualWidths[4] - 5f - (boldFont.getStringWidth(labourTotalText) / 1000 * 10f)
        drawText(contentStream, labourTotalText, labourTotalX, currentY, 10f, true)

        // Stone Value total
        xPos += actualWidths[4]
        val stoneTotalText = formatter.format(totalStoneValue)
        val stoneTotalX =
            xPos + actualWidths[5] - 5f - (boldFont.getStringWidth(stoneTotalText) / 1000 * 10f)
        drawText(contentStream, stoneTotalText, stoneTotalX, currentY, 10f, true)

        // Amount total
        xPos += actualWidths[5]
        val amountTotalText = "₹ " + formatter.format(totalAmount)
        val amountTotalX =
            xPos + actualWidths[6] - 5f - (boldFont.getStringWidth(amountTotalText) / 1000 * 10f)
        drawText(contentStream, amountTotalText, amountTotalX, currentY, 10f, true)


        // Add extra space before drawing the totals line
        currentY += 5f

        // Draw totals line with more spacing to prevent overlapping
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.addRect(x, currentY - 15f, width, 1.5f)
        contentStream.fill()

        currentY = currentY - 15f

        return currentY - 15f
    }


    // New method for the compact right-side financial summary
    // New method for the compact right-side financial summary with horizontal line below total
    private fun drawCompactFinancialSummary(
        contentStream: PDPageContentStream,
        invoice: Invoice,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        var currentY = y
        val labelX = x
        val valueX = x + width - 5f // Right align position

        // Calculate extra charges
        val extraChargeMap = mutableMapOf<String, Double>()
        invoice.items.forEach { item ->
            item.itemDetails.listOfExtraCharges.forEach { charge ->
                val amount = charge.amount * item.quantity
                extraChargeMap[charge.name] = extraChargeMap.getOrDefault(charge.name, 0.0) + amount
            }
        }

        // Calculate tax
        val totalTax = invoice.items.sumOf { item ->
            val itemTotal = item.price * item.quantity
            val extraChargesTotal =
                item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
            val taxableAmount = itemTotal + extraChargesTotal
            taxableAmount * (item.itemDetails.taxRate / 100.0)
        }

        // Draw each tax and extra charge item
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        contentStream.setFont(regularFont, 10f)

        // Tax entry with rate
        val taxRate = invoice.items.firstOrNull()?.itemDetails?.taxRate ?: 0.0
        val taxText = "Tax (${taxRate.toInt()}%)"
        val taxAmount = "₹${formatter.format(totalTax)}"
        drawText(contentStream, taxText, labelX, currentY, 10f, false)
        val taxAmountWidth = regularFont.getStringWidth(taxAmount) / 1000 * 10f
        drawText(contentStream, taxAmount, valueX - taxAmountWidth, currentY, 10f, false)
        currentY -= 15f

        // Extra charges
        extraChargeMap.forEach { (name, amount) ->
            val chargeAmount = "₹${formatter.format(amount)}"
            drawText(contentStream, name, labelX, currentY, 10f, false)
            val chargeAmountWidth = regularFont.getStringWidth(chargeAmount) / 1000 * 10f
            drawText(contentStream, chargeAmount, valueX - chargeAmountWidth, currentY, 10f, false)
            currentY -= 15f
        }

        // Draw separator line
        drawLine(contentStream, labelX, currentY + 5f, valueX, currentY + 5f, 0.5f, primaryColor)
        currentY -= 15f

        // Draw total amount
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.setFont(boldFont, 11f)
        drawText(contentStream, "Total Amount", labelX, currentY, 11f, true)
        val totalAmount = "₹${formatter.format(invoice.totalAmount)}"
        val totalAmountWidth = boldFont.getStringWidth(totalAmount) / 1000 * 11f
        drawText(contentStream, totalAmount, valueX - totalAmountWidth, currentY, 11f, true)
        currentY -= 15f

        // Draw horizontal line below total (added as requested)
        drawLine(contentStream, labelX, currentY + 5f, valueX, currentY + 5f, 0.5f, primaryColor)
        currentY -= 15f

        // Draw payment details in compact form
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        if (invoice.payments.isNotEmpty()) {
            for (payment in invoice.payments) {
                contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
                val paymentAmount = "₹${formatter.format(payment.amount)}"
                drawText(contentStream, payment.method, labelX, currentY, 10f, false)
                val paymentAmountWidth = regularFont.getStringWidth(paymentAmount) / 1000 * 10f
                drawText(
                    contentStream,
                    paymentAmount,
                    valueX - paymentAmountWidth,
                    currentY,
                    10f,
                    false
                )
                currentY -= 15f
            }
        }

        // Draw received amount (sum of all payments)
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        val paidAmount = invoice.paidAmount
        val paidAmountText = "Received Amount"
        val paidAmountValue = "₹${formatter.format(paidAmount)}"
        drawText(contentStream, paidAmountText, labelX, currentY, 10f, true)
        val paidAmountWidth = regularFont.getStringWidth(paidAmountValue) / 1000 * 10f
        drawText(contentStream, paidAmountValue, valueX - paidAmountWidth, currentY, 10f, false)
        currentY -= 15f

        // Draw balance amount
        val balanceDue = invoice.totalAmount - paidAmount
        val balanceText = "Balance"
        val balanceValue = "₹${formatter.format(balanceDue)}"
        contentStream.setNonStrokingColor(
            if (balanceDue <= 0) AWTColor(
                0,
                128,
                0
            ) else AWTColor(192, 0, 0)
        )
        drawText(contentStream, balanceText, labelX, currentY, 10f, true)
        val balanceValueWidth = boldFont.getStringWidth(balanceValue) / 1000 * 10f
        drawText(contentStream, balanceValue, valueX - balanceValueWidth, currentY, 10f, true)

        return currentY - 15f
    }


    // Helpers for tax and total calculations

    private fun getTotalRoundOff(items: List<InvoiceItem>): Double {
        val totalBeforeRounding = items.sumOf { it.price * it.quantity } * 1.26 + 100
        val roundedTotal = Math.round(totalBeforeRounding).toDouble()
        return Math.abs(roundedTotal - totalBeforeRounding)
    }


    private fun loadFonts(document: PDDocument) {
        try {
            // Load fonts from assets
            context.assets.open("notosans.ttf").use { inputStream ->
                regularFont = PDType0Font.load(document, inputStream)
            }

            context.assets.open("NotoSans_Condensed-SemiBold.ttf").use { inputStream ->
                boldFont = PDType0Font.load(document, inputStream)
            }
        } catch (e: IOException) {
            Log.e("InvoicePdfGenerator", "Error loading fonts", e)
            throw e
        }
    }

    // Original tax and extra charges (kept for backward compatibility)
    private fun drawTaxAndExtraCharges(
        contentStream: PDPageContentStream, invoice: Invoice, x: Float, y: Float, width: Float
    ): Float {
        // Draw section title
        contentStream.setFont(boldFont, 12f)
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(x, y - 5f)
        contentStream.showText("Tax & Extra Charges")
        contentStream.endText()

        // Reset text color
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))

        // Create headers
        val headers = listOf("Description", "Amount")

        // Define column widths (70% for description, 30% for amount)
        val columnWidths = floatArrayOf(0.7f * width, 0.3f * width)

        // Define alignment (description left, amount right)
        val isRightAligned = arrayOf(false, true)

        // Define bold status for text
        val isBold = arrayOf(false, false)

        // Collect all data rows
        val dataRows = mutableListOf<List<String>>()

        // Add extra charges
        val extraChargeMap = mutableMapOf<String, Double>()

        // Collect and consolidate all extra charges
        invoice.items.forEach { item ->
            item.itemDetails.listOfExtraCharges.forEach { charge ->
                val amount = charge.amount * item.quantity
                extraChargeMap[charge.name] = extraChargeMap.getOrDefault(charge.name, 0.0) + amount
            }
        }

        // Add each extra charge as a row
        extraChargeMap.forEach { (name, amount) ->
            dataRows.add(listOf(name, "₹${formatter.format(amount)}"))
        }

        // Calculate tax
        val totalTax = invoice.items.sumOf { item ->
            val itemTotal = item.price * item.quantity
            val extraChargesTotal =
                item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
            val taxableAmount = itemTotal + extraChargesTotal
            taxableAmount * (item.itemDetails.taxRate / 100.0)
        }

        // Add tax row
        val taxRate = invoice.items.firstOrNull()?.itemDetails?.taxRate ?: 0.0
        dataRows.add(
            listOf(
                "Tax (${taxRate.toInt()}%)", "₹${formatter.format(totalTax)}"
            )
        )

        // Set the last row (tax) to be bold
        val lastRowBold = arrayOf(true, true)

        // Draw the table
        return if (dataRows.isEmpty()) {
            y - 20f // Return a small offset if no data
        } else {
            drawTableWithGrid(
                contentStream,
                headers,
                dataRows,
                x,
                y - 20f,
                width,
                columnWidths,
                isRightAligned,
                lastRowBold
            )
        }
    }

    // Original payment details method (kept for backward compatibility)
    private fun drawPaymentDetails(
        contentStream: PDPageContentStream, invoice: Invoice, x: Float, y: Float, width: Float
    ): Float {
        // Add spacing before section
        var currentY = y - 15f

        // Draw section title
        contentStream.setFont(boldFont, 12f)
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(x, currentY)
        contentStream.showText("Payment Details:")
        contentStream.endText()

        // Reset text color
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))

        currentY -= 20f

        // Create headers
        val headers = listOf("Payment Method", "Amount")

        // Define column widths (70% for method, 30% for amount)
        val columnWidths = floatArrayOf(0.7f * width, 0.3f * width)

        // Define alignment (description left, amount right)
        val isRightAligned = arrayOf(false, true)

        // Define bold status for text
        val isBold = arrayOf(false, false)

        // Collect payment data rows
        val dataRows = mutableListOf<List<String>>()

        // Add each payment method as a row
        for (payment in invoice.payments) {
            dataRows.add(
                listOf(
                    payment.method, "₹${formatter.format(payment.amount)}"
                )
            )
        }

        // Draw the table with grid
        return if (dataRows.isEmpty()) {
            // If no payments, just return the current position
            currentY
        } else {
            drawTableWithGrid(
                contentStream,
                headers,
                dataRows,
                x,
                currentY,
                width,
                columnWidths,
                isRightAligned,
                isBold
            )
        }
    }

    // Original totals section (kept for backward compatibility)
    private fun drawTotalsSection(
        contentStream: PDPageContentStream, invoice: Invoice, x: Float, y: Float, width: Float
    ): Float {
        var currentY = y - 10f

        // Calculate values
        val totalAmount = invoice.totalAmount
        val paidAmount = invoice.paidAmount
        val balanceDue = totalAmount - paidAmount

        // Draw horizontal line
        drawLine(contentStream, x, currentY + 5f, x + width, currentY + 5f, 1f, primaryColor)

        // Draw total amount
        contentStream.setNonStrokingColor(primaryColor)
        drawText(contentStream, "Total Amount:", x + 5f, currentY - 10f, 12f, true)
        drawText(
            contentStream,
            "₹${formatter.format(totalAmount)}",
            x + width - 5f - (boldFont.getStringWidth("₹${formatter.format(totalAmount)}") / 1000 * 12f),
            currentY - 10f,
            12f,
            true
        )
        currentY -= 25f

        // Draw paid amount
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(contentStream, "Amount Paid:", x + 5f, currentY, 12f, true)
        drawText(
            contentStream,
            "₹${formatter.format(paidAmount)}",
            x + width - 5f - (boldFont.getStringWidth("₹${formatter.format(paidAmount)}") / 1000 * 12f),
            currentY,
            12f,
            true
        )
        currentY -= 25f

        // Draw balance due
        contentStream.setNonStrokingColor(
            if (balanceDue <= 0) AWTColor(0, 128, 0) else AWTColor(
                192, 0, 0
            )
        )
        drawText(contentStream, "Balance Due:", x + 5f, currentY, 12f, true)
        drawText(
            contentStream,
            "₹${formatter.format(balanceDue)}",
            x + width - 5f - (boldFont.getStringWidth("₹${formatter.format(balanceDue)}") / 1000 * 12f),
            currentY,
            12f,
            true
        )
        currentY -= 25f

        // Draw amount in words
        contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
        drawText(
            contentStream,
            "Amount in words: " + numberToWords(balanceDue.toInt()),
            x + 5f,
            currentY,
            10f,
            false
        )

        return currentY - 10f
    }

    // Generate and draw QR code for UPI payments
    private fun drawUpiQrCode(
        document: PDDocument, page: PDPage, upiId: String, x: Float, y: Float
    ) {
        try {
            // Generate QR code
            val qrCodeBitmap = generateUpiQrCode(upiId)

            // Convert bitmap to PDImageXObject
            val qrCodeImage = LosslessFactory.createFromImage(document, qrCodeBitmap)

            // Draw on page
            PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { contentStream ->
                contentStream.drawImage(qrCodeImage, x, y, 80f, 80f)

                // Draw label
                contentStream.beginText()
                contentStream.setFont(boldFont, 10f)
                contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
                contentStream.newLineAtOffset(x + 15f, y - 15f)
                contentStream.showText("Scan to Pay")
                contentStream.endText()
            }
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error generating QR code", e)
        }
    }

    private fun drawUpiQrCode2(
        document: PDDocument, page: PDPage, upiId: String, x: Float, y: Float
    ) {
        try {
            // Generate QR code
            val qrCodeBitmap = generateUpiQrCode(upiId)

            // Convert bitmap to PDImageXObject
            val qrCodeImage = LosslessFactory.createFromImage(document, qrCodeBitmap)

            // Draw on page
            PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { contentStream ->
                contentStream.drawImage(qrCodeImage, x, y, 80f, 80f)
            }
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error generating QR code", e)
        }
    }

    // Original footer method (kept for backward compatibility)
    private fun drawFooter(
        document: PDDocument,
        contentStream: PDPageContentStream,
        shop: ShopInvoiceDetails,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Draw signature section if enabled
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                // Draw uploaded signature image
                try {
                    drawSignatureImage(
                        document, pageWidth - 150f, 70f, settings.signatureUri!!, contentStream
                    )
                } catch (e: Exception) {
                    Log.e("InvoicePdfGenerator", "Error drawing signature: ${e.message}")
                    // Fall back to text-only signature line
                    drawText(
                        contentStream,
                        "Authorized Signature",
                        pageWidth - 150f,
                        50f,
                        10f,
                        true
                    )
                }
            } else {
                // Draw signature line
                drawText(contentStream, "Authorized Signature", pageWidth - 150f, 50f, 10f, true)
            }
        }

        // Draw terms and conditions
        if (settings.termsAndConditions.isNotEmpty()) {
            contentStream.setNonStrokingColor(AWTColor(0, 0, 0))
            drawText(contentStream, "Terms & Conditions:", 40f, 100f, 10f, true)

            // Split terms into lines and draw each line
            val terms = settings.termsAndConditions.split("\n")
            var yPos = 85f

            for (term in terms) {
                drawText(contentStream, term, 40f, yPos, 8f, false)
                yPos -= 12f
            }
        }

// Reduced size for app logo
        val appLogoBitmap = vectorToBitmap(context, R.drawable.swarna_khata_book_03, 25, 25) // Reduced from 60x60
        val appLogoStream = ByteArrayOutputStream()
        appLogoBitmap.compress(Bitmap.CompressFormat.PNG, 100, appLogoStream)
        val appLogoBytes = appLogoStream.toByteArray()
        val appLogo = PDImageXObject.createFromByteArray(document, appLogoBytes, "app_logo")

// Reduced size for Play Store logo
        val playStoreLogoBitmap = vectorToBitmap(context, R.drawable.ion__logo_google_playstore, 25, 25) // Reduced from 60x60
        val playStoreLogoStream = ByteArrayOutputStream()
        playStoreLogoBitmap.compress(Bitmap.CompressFormat.PNG, 100, playStoreLogoStream)
        val playStoreLogoBytes = playStoreLogoStream.toByteArray()
        val playStoreLogo = PDImageXObject.createFromByteArray(document, playStoreLogoBytes, "playstore_logo")

// Draw enclosing rectangle for footer
        contentStream.setStrokingColor(secondaryColor)
        contentStream.setLineWidth(0.5f)
        contentStream.addRect(pageWidth/2 - 130f, 30f, 260f, 15f)
//        contentStream.addRect( pageWidth / 2 - 105f, 30f, 260f, 15f)
        contentStream.stroke()

// Draw logos and text
// Scale the images to appropriate size for the PDF
        val logoWidth = 15f  // Reduced from 20f
        val logoHeight = 15f // Reduced from 20f

        //reduse 120 tp 90
//        contentStream.drawImage(appLogo, pageWidth / 2 - 100f, 30f, logoWidth, logoHeight)
//        contentStream.drawImage(playStoreLogo, pageWidth / 2 + 135f, 30f, logoWidth, logoHeight)
        // Position the app logo on the left side of centered text
        contentStream.drawImage(appLogo, pageWidth/2 - 125f, 30f, logoWidth, logoHeight)
// Position the play store logo on the right side of centered text
        contentStream.drawImage(playStoreLogo, pageWidth/2 + 110f, 30f, logoWidth, logoHeight)

// Draw text centered between the logos
        contentStream.setFont(regularFont, 7f)
        contentStream.setNonStrokingColor(secondaryColor)
        contentStream.beginText()
//        contentStream.newLineAtOffset(pageWidth / 2 - 80f, 35f)
        val footerText = "Invoice Created From SwarnaKhataBook, Available On Play Store"
        val textWidth = regularFont.getStringWidth(footerText) / 1000 * 7f
        contentStream.newLineAtOffset(pageWidth/2 - textWidth/2, 35f)
        contentStream.showText(footerText)
        contentStream.endText()
    }

    // Utility methods
    private fun generateUpiQrCode(upiId: String): Bitmap {
        val upiPaymentString = "upi://pay?pa=${Uri.encode(upiId)}&cu=INR"

        val writer = QRCodeWriter()
        val bitMatrix: BitMatrix = writer.encode(upiPaymentString, BarcodeFormat.QR_CODE, 512, 512)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    // Function to convert numbers to Indian numbering format
    fun formatNumberToIndian(number: Long): String {
        val formatter = DecimalFormat("##,##,###")
        return formatter.format(number)
    }

    // Convert amount to words
    private fun numberToWords(number: Int): String {
        if (number == 0) return "Zero"
        if (number < 0) return "Negative " + numberToWords(-number)

        val units = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"
        )
        val teens = arrayOf(
            "Ten",
            "Eleven",
            "Twelve",
            "Thirteen",
            "Fourteen",
            "Fifteen",
            "Sixteen",
            "Seventeen",
            "Eighteen",
            "Nineteen"
        )
        val tens = arrayOf(
            "", "Ten", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
        )

        fun convertLessThanThousand(n: Int): String {
            if (n == 0) return ""
            if (n < 10) return units[n]
            if (n < 20) return teens[n - 10]
            if (n < 100) return tens[n / 10] + if (n % 10 != 0) " " + units[n % 10] else ""
            return units[n / 100] + " Hundred" + if (n % 100 != 0) " and " + convertLessThanThousand(
                n % 100
            ) else ""
        }

        // Indian numbering system: thousand, lakh (100,000), crore (10,000,000)
        var result = ""
        var num = number

        // Handle crores (10,000,000+)
        if (num >= 10000000) {
            result += convertLessThanThousand(num / 10000000) + " Crore "
            num %= 10000000
        }

        // Handle lakhs (100,000 to 9,999,999)
        if (num >= 100000) {
            result += convertLessThanThousand(num / 100000) + " Lakh "
            num %= 100000
        }

        // Handle thousands (1,000 to 99,999)
        if (num >= 1000) {
            result += convertLessThanThousand(num / 1000) + " Thousand "
            num %= 1000
        }

        // Handle remaining hundreds, tens and units
        if (num > 0) {
            result += convertLessThanThousand(num)
        }

        return result.trim()
    }


    // Helper drawing methods
    private fun drawText(
        contentStream: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float,
        isBold: Boolean
    ) {
        contentStream.beginText()
        contentStream.setFont(if (isBold) boldFont else regularFont, fontSize)
        contentStream.newLineAtOffset(x, y)
        contentStream.showText(text)
        contentStream.endText()
    }

    private fun drawCenteredText(
        contentStream: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float,
        isBold: Boolean
    ) {
        val font = if (isBold) boldFont else regularFont
        val textWidth = font.getStringWidth(text) / 1000 * fontSize
        drawText(contentStream, text, x - (textWidth / 2), y, fontSize, isBold)
    }

    // Improved drawing helper for rectangles
    private fun drawRectangle(
        contentStream: PDPageContentStream,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: AWTColor
    ) {
        contentStream.setStrokingColor(color)
        contentStream.setLineWidth(0.5f)

        // Draw rectangle without stroking yet
        contentStream.addRect(x, y, width, height)

        // Stroke once to prevent overlapping lines
        contentStream.stroke()
    }

    // Improved line drawing to ensure consistent line width
    private fun drawLine(
        contentStream: PDPageContentStream,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        lineWidth: Float,
        color: AWTColor
    ) {
        contentStream.setStrokingColor(color)
        contentStream.setLineWidth(lineWidth)

        // Draw the line
        contentStream.moveTo(x1, y1)
        contentStream.lineTo(x2, y2)

        // Stroke once
        contentStream.stroke()
    }

    private fun drawTableWithGrid(
        contentStream: PDPageContentStream,
        headers: List<String>,
        data: List<List<String>>,
        x: Float,
        y: Float,
        width: Float,
        columnWidths: FloatArray,
        isRightAligned: Array<Boolean>,
        isBold: Array<Boolean>
    ): Float {
        val rowHeight = 15f
        val totalHeight = rowHeight * (data.size + 1) // +1 for header row

        // Draw outer border - only once to prevent overlapping
        drawRectangle(contentStream, x, y - totalHeight, width, totalHeight, AWTColor(0, 0, 0))

        // Draw header row content
        var xPos = x
        for (i in headers.indices) {
            // Calculate text position based on alignment
            val textX = if (isRightAligned[i])
                xPos + columnWidths[i] - 5f - (boldFont.getStringWidth(headers[i]) / 1000 * 10f)
            else
                xPos + 5f

            // Draw header text
            drawText(contentStream, headers[i], textX, y - rowHeight + 5f, 10f, true)

            // Move to next column position
            xPos += columnWidths[i]
        }

        // Draw all vertical lines in one pass to prevent overlapping
        xPos = x
        for (i in 0 until headers.size) {
            xPos += columnWidths[i]
            if (i < headers.size - 1) {
                drawLine(contentStream, xPos, y, xPos, y - totalHeight, 0.5f, AWTColor(0, 0, 0))
            }
        }

        // Draw horizontal line after header
        drawLine(contentStream, x, y - rowHeight, x + width, y - rowHeight, 0.5f, AWTColor(0, 0, 0))

        // Draw data rows
        var yPos = y - rowHeight
        for (rowIndex in data.indices) {
            xPos = x
            for (colIndex in data[rowIndex].indices) {
                // Calculate text position based on alignment
                val textValue = data[rowIndex][colIndex]
                val textX = if (isRightAligned[colIndex])
                    xPos + columnWidths[colIndex] - 5f - (regularFont.getStringWidth(textValue) / 1000 * 10f)
                else
                    xPos + 5f

                drawText(
                    contentStream,
                    textValue,
                    textX,
                    yPos - rowHeight + 5f,
                    10f,
                    isBold[colIndex]
                )
                xPos += columnWidths[colIndex]
            }

            // Move to next row
            yPos -= rowHeight

            // Draw horizontal line after each row (except last)
            if (rowIndex < data.size - 1) {
                drawLine(contentStream, x, yPos, x + width, yPos, 0.5f, AWTColor(0, 0, 0))
            }
        }

        return y - totalHeight - 10f // Return the Y position after drawing the table plus some spacing
    }


    // 2. Draw Shop Logo
    private fun drawShopLogo(
        document: PDDocument,
        x: Float,
        y: Float,
        logoUri: String,
        contentStream: PDPageContentStream
    ) {
        try {
            val uri = Uri.parse(logoUri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return

            // Load logo
            val logoBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Create PDImageXObject from bitmap
            val logoImage = LosslessFactory.createFromImage(document, logoBitmap)

            // Fixed dimensions for logo
            val fixedWidth = 80f
            val fixedHeight = 40f

            // Calculate aspect ratio preserving scaling
            val imageRatio = logoImage.width.toFloat() / logoImage.height.toFloat()
            val targetRatio = fixedWidth / fixedHeight

            val width: Float
            val height: Float

            if (imageRatio > targetRatio) {
                // Image is wider than target ratio
                width = fixedWidth
                height = width / imageRatio
            } else {
                // Image is taller than target ratio
                height = fixedHeight
                width = height * imageRatio
            }

            // Center the logo within the fixed dimensions
            val xOffset = (fixedWidth - width) / 2
            val yOffset = (fixedHeight - height) / 2

            // Draw logo with fixed positioning
            contentStream.drawImage(logoImage, x - fixedWidth + xOffset, y - fixedHeight/2 + yOffset, width, height)
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
        }
    }

    // 3. Draw Signature Image
    private fun drawSignatureImage(
        document: PDDocument,
        x: Float,
        y: Float,
        signatureUri: String,
        contentStream: PDPageContentStream
    ) {
        try {
            val uri = Uri.parse(signatureUri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return

            // Load signature
            val signatureBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Create PDImageXObject from bitmap
            val signatureImage = LosslessFactory.createFromImage(document, signatureBitmap)

            // Calculate dimensions (max 100x50)
            val maxWidth = 100f
            val maxHeight = 50f
            val ratio = signatureImage.width.toFloat() / signatureImage.height.toFloat()

            val width: Float
            val height: Float

            if (ratio > 1) { // Wider than tall
                width = maxWidth
                height = width / ratio
            } else { // Taller than wide
                height = maxHeight
                width = height * ratio
            }

            // Draw signature
            contentStream.drawImage(signatureImage, x - width / 2, y, width, height)
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error loading signature: ${e.message}")
        }
    }

    private fun drawSignatureImage2(
        document: PDDocument,
        x: Float,
        y: Float,
        signatureUri: String,
        contentStream: PDPageContentStream
    ) {
        try {
            val uri = Uri.parse(signatureUri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return

            // Load signature
            val signatureBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Create PDImageXObject from bitmap
            val signatureImage = LosslessFactory.createFromImage(document, signatureBitmap)

            // Calculate dimensions (max 100x50)
            val maxWidth = 50f
            val maxHeight = 25f
            val ratio = signatureImage.width.toFloat() / signatureImage.height.toFloat()

            val width: Float
            val height: Float

            if (ratio > 1) { // Wider than tall
                width = maxWidth
                height = width / ratio
            } else { // Taller than wide
                height = maxHeight
                width = height * ratio
            }

            // Draw signature
            contentStream.drawImage(signatureImage, x - width / 2, y, width, height)
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error loading signature: ${e.message}")
        }
    }

    // 4. Apply Watermark
    private fun applyWatermark(
        document: PDDocument,
        page: PDPage,
        watermarkUri: String
    ) {
        try {
            val uri = Uri.parse(watermarkUri)
            val inputStream = context.contentResolver.openInputStream(uri)

            if (inputStream != null) {
                // Load watermark image
                val watermarkBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                // Convert to PDImageXObject
                val watermarkImage = LosslessFactory.createFromImage(document, watermarkBitmap)

                // Create content stream in append mode
                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->
                    // Set transparency for watermark
                    val graphicsState = PDExtendedGraphicsState()
                    graphicsState.setNonStrokingAlphaConstant(0.2f) // 20% opacity
                    contentStream.setGraphicsStateParameters(graphicsState)

                    // Calculate center position
                    val pageWidth = page.mediaBox.width
                    val pageHeight = page.mediaBox.height
                    val watermarkWidth = pageWidth * 0.6f // 60% of page width
                    val watermarkHeight =
                        watermarkWidth * watermarkImage.height / watermarkImage.width
                    val xPos = (pageWidth - watermarkWidth) / 2
                    val yPos = (pageHeight - watermarkHeight) / 2

                    // Draw watermark
                    contentStream.drawImage(
                        watermarkImage,
                        xPos,
                        yPos,
                        watermarkWidth,
                        watermarkHeight
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error applying watermark: ${e.message}")
            // Continue without watermark if there's an error
        }
    }


    // Save the PDF to a file
    private fun savePdfToFile(pdfDocument: PDDocument, fileName: String): File {
        val cacheDirectory = context.cacheDir
        val file = File(cacheDirectory, "$fileName.pdf")

        try {
            FileOutputStream(file).use { fileOutputStream ->
                pdfDocument.save(fileOutputStream)
            }
        } catch (e: IOException) {
            Log.e("InvoicePdfGenerator", "Error saving PDF", e)
            throw e
        }

        return file
    }
}