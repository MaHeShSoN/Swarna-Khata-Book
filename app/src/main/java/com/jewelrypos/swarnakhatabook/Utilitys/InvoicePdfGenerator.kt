package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

                    TemplateType.ADVANCE_GST -> generateAdvanceGstTemplate(
                        contentStream, document, page, invoice, shop, pageWidth, pageHeight
                    )

                    TemplateType.ADVANCE_GST_TALLY -> generateAdvanceGstTallyTemplate(
                        contentStream, document, page, invoice, shop, pageWidth, pageHeight
                    )

                    TemplateType.BILLBOOK -> generateBillbookTemplate(
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
        drawRectangle(contentStream, 20f, 20f, pageWidth - 40f, pageHeight - 40f, primaryColor)

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

        // Draw shop logo if enabled
        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(
                    document, pageWidth - 120f, pageHeight - 40f, settings.logoUri!!, contentStream
                )
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Customer section title and details
        contentStream.setNonStrokingColor(AWTColor.BLACK)
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
        drawFooter(document,contentStream, shop, pageWidth, pageHeight)
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
        val columnWidths = floatArrayOf(0.25f, 0.08f, 0.08f, 0.06f, 0.10f, 0.10f, 0.08f, 0.10f, 0.15f)

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

            val stoneValue = 1000
            val itemTotal = item.price * item.quantity

            // Update totals
            totalNetWeight += netWeight * pieces
            totalPieces += pieces
            totalLabour += labour
            totalStoneValue += stoneValue
            totalAmount += itemTotal

            // Add row data
            dataRows.add(listOf(
                item.itemDetails.displayName,
                formatter.format(item.itemDetails.grossWeight),
                formatter.format(netWeight),
                pieces.toString(),
                formatter.format(labour),
                formatter.format(stoneValue),
                item.itemDetails.purity,
                formatter.format(item.itemDetails.metalRate),
                formatter.format(itemTotal)
            ))
        }

        // Add totals row with proper formatting
        dataRows.add(listOf(
            "Totals",
            "",  // Skip gross weight total
            formatter.format(totalNetWeight),
            totalPieces.toString(),
            formatter.format(totalLabour),
            formatter.format(totalStoneValue),
            "",  // Skip purity
            "",  // Skip rate
            formatter.format(totalAmount)
        ))

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

    // Stylish template with a clean, modern layout
    private fun generateStylishTemplate(
        contentStream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        shop: ShopInvoiceDetails,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // No border, clean design

        // Draw horizontal line as header separator
        drawLine(
            contentStream,
            20f,
            pageHeight - 70f,
            pageWidth - 20f,
            pageHeight - 70f,
            2f,
            primaryColor
        )

        // TAX INVOICE title at the top left
        contentStream.setFont(boldFont, 18f)
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(25f, pageHeight - 40f)
        contentStream.showText("TAX INVOICE")
        contentStream.endText()

        // Add "ORIGINAL FOR RECIPIENT" tag 
        contentStream.setFont(regularFont, 8f)
        contentStream.beginText()
        contentStream.newLineAtOffset(pageWidth - 150f, pageHeight - 40f)
        contentStream.showText("ORIGINAL FOR RECIPIENT")
        contentStream.endText()

        // Draw shop logo if enabled (in top right)
        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(
                    document, pageWidth - 60f, pageHeight - 40f, settings.logoUri!!, contentStream
                )
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Shop name and details in gold color
        contentStream.setNonStrokingColor(primaryColor)
        drawText(contentStream, shop.shopName, 25f, pageHeight - 90f, 14f, true)
        contentStream.setNonStrokingColor(secondaryColor)
        drawText(contentStream, shop.address, 25f, pageHeight - 105f, 9f, false)
        drawText(contentStream, "Mobile: ${shop.phoneNumber}", 25f, pageHeight - 117f, 9f, false)

        // Draw horizontal line as section separator
        drawLine(
            contentStream,
            20f,
            pageHeight - 130f,
            pageWidth - 20f,
            pageHeight - 130f,
            0.5f,
            primaryColor
        )

        // Invoice details with clean layout
        contentStream.setFont(regularFont, 10f)
        contentStream.setNonStrokingColor(secondaryColor)

        // Invoice details on left
        drawText(contentStream, "Invoice No.:", 25f, pageHeight - 150f, 10f, true)
        drawText(
            contentStream,
            settings.invoicePrefix + invoice.invoiceNumber,
            120f,
            pageHeight - 150f,
            10f,
            false
        )

        // Invoice date on right
        drawText(contentStream, "Invoice Date:", pageWidth - 150f, pageHeight - 150f, 10f, true)
        drawText(
            contentStream,
            dateFormatter.format(Date(invoice.invoiceDate)),
            pageWidth - 80f,
            pageHeight - 150f,
            10f,
            false
        )

        // Customer details section
        drawText(contentStream, "BILL TO", 25f, pageHeight - 180f, 10f, true)
        drawText(contentStream, "SHIP TO", pageWidth / 2 + 20f, pageHeight - 180f, 10f, true)

        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, invoice.customerName, 25f, pageHeight - 195f, 10f, true)
        drawText(contentStream, invoice.customerAddress, 25f, pageHeight - 208f, 9f, false)
        drawText(
            contentStream, "Mobile: ${invoice.customerPhone}", 25f, pageHeight - 220f, 9f, false
        )

        // Shipping address (same as billing in this example)
        drawText(
            contentStream,
            "Address: ${invoice.customerAddress}",
            pageWidth / 2 + 20f,
            pageHeight - 195f,
            9f,
            false
        )

        // Draw items table with clean borders
        var currentY = pageHeight - 245f

        // Draw items with stylish headers
        currentY =
            drawStylishItemsTable(contentStream, invoice.items, 25f, currentY, pageWidth - 50f)

        // Draw notes and terms section
        drawText(contentStream, "NOTES", 25f, currentY - 20f, 10f, true)
        drawText(contentStream, "10 day return policy.", 25f, currentY - 35f, 9f, false)

        drawText(contentStream, "TERMS AND CONDITIONS", 25f, currentY - 55f, 10f, true)
        drawText(
            contentStream,
            "1. Goods once sold will not be taken back or exchanged",
            25f,
            currentY - 70f,
            9f,
            false
        )
        drawText(
            contentStream,
            "2. All disputes are subject to [ENTER_YOUR_CITY_NAME] jurisdiction only",
            25f,
            currentY - 85f,
            9f,
            false
        )

        // Draw delivery charges and tax summary
        drawText(contentStream, "Delivery Charges", pageWidth / 2 + 20f, currentY - 20f, 9f, false)
        drawText(contentStream, "TAXABLE AMOUNT", pageWidth / 2 + 20f, currentY - 35f, 9f, true)
        drawText(contentStream, "IGST @14%", pageWidth / 2 + 20f, currentY - 50f, 9f, false)
        drawText(contentStream, "Cess @12%", pageWidth / 2 + 20f, currentY - 65f, 9f, false)
        drawText(contentStream, "Round Off", pageWidth / 2 + 20f, currentY - 80f, 9f, false)

        // Payment amounts (right-aligned)
        drawText(contentStream, "₹ 100", pageWidth - 50f, currentY - 20f, 9f, false)
        drawText(
            contentStream,
            "₹ " + formatter.format(getTaxableAmount(invoice)),
            pageWidth - 50f,
            currentY - 35f,
            9f,
            true
        )
        drawText(
            contentStream,
            "₹ " + formatter.format(invoice.totalAmount * 0.14),
            pageWidth - 50f,
            currentY - 50f,
            9f,
            false
        )
        drawText(
            contentStream,
            "₹ " + formatter.format(invoice.totalAmount * 0.12),
            pageWidth - 50f,
            currentY - 65f,
            9f,
            false
        )
        drawText(
            contentStream,
            "₹ -" + formatter.format(getTotalRoundOff(invoice)),
            pageWidth - 50f,
            currentY - 80f,
            9f,
            false
        )

        // Total section with line separator
        drawLine(
            contentStream,
            pageWidth / 2,
            currentY - 90f,
            pageWidth - 25f,
            currentY - 90f,
            0.5f,
            primaryColor
        )

        drawText(contentStream, "TOTAL AMOUNT", pageWidth / 2 + 20f, currentY - 105f, 10f, true)
        drawText(
            contentStream,
            "₹ " + formatter.format(invoice.totalAmount),
            pageWidth - 50f,
            currentY - 105f,
            10f,
            true
        )

        drawText(contentStream, "Received Amount", pageWidth / 2 + 20f, currentY - 120f, 9f, false)
        drawText(contentStream, "Balance", pageWidth / 2 + 20f, currentY - 135f, 9f, false)

        drawText(
            contentStream,
            "₹ " + formatter.format(invoice.paidAmount),
            pageWidth - 50f,
            currentY - 120f,
            9f,
            false
        )

        // Balance with appropriate color
        contentStream.setNonStrokingColor(
            if (invoice.totalAmount - invoice.paidAmount <= 0) AWTColor(
                0, 128, 0
            ) else AWTColor(192, 0, 0)
        )
        drawText(
            contentStream,
            "₹ " + formatter.format(invoice.totalAmount - invoice.paidAmount),
            pageWidth - 50f,
            currentY - 135f,
            9f,
            true
        )
        contentStream.setNonStrokingColor(AWTColor.BLACK)

        // Amount in words
        drawText(contentStream, "Total Amount (in words)", 25f, currentY - 155f, 9f, true)
        drawText(
            contentStream,
            numberToWords(invoice.totalAmount.toInt()) + " Rupees",
            25f,
            currentY - 170f,
            9f,
            false
        )

        // Draw QR code if enabled
        if (settings.showQrCode && settings.upiId.isNotEmpty()) {
            drawUpiQrCode(document, page, settings.upiId, pageWidth / 2 - 50f, currentY - 220f)
        }

        // Draw signature
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                drawSignatureImage(
                    document,
                    pageWidth - 150f,
                    currentY - 220f,
                    settings.signatureUri!!,
                    contentStream
                )
            }

            drawText(
                contentStream,
                "AUTHORISED SIGNATORY FOR",
                pageWidth - 140f,
                currentY - 240f,
                8f,
                true
            )
            drawText(contentStream, shop.shopName, pageWidth - 140f, currentY - 250f, 8f, false)
        }

        // Draw download info at bottom
        contentStream.setFont(regularFont, 7f)
        contentStream.setNonStrokingColor(secondaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(pageWidth / 2 - 50f, 30f)
        contentStream.showText("Invoice created using myBillBook")
        contentStream.endText()
    }

    // Advanced GST template optimized for GST information
    private fun generateAdvanceGstTemplate(
        contentStream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        shop: ShopInvoiceDetails,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Clean design with structured table layout

        // Draw header with tax invoice and original label
        contentStream.setFont(boldFont, 12f)
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        contentStream.beginText()
        contentStream.newLineAtOffset(25f, pageHeight - 40f)
        contentStream.showText("TAX INVOICE")
        contentStream.endText()

        // Add "ORIGINAL FOR RECIPIENT" tag 
        contentStream.setFont(regularFont, 8f)
        contentStream.beginText()
        contentStream.newLineAtOffset(pageWidth - 150f, pageHeight - 40f)
        contentStream.showText("ORIGINAL FOR RECIPIENT")
        contentStream.endText()

        // Draw shop logo if enabled (in top right)
        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(
                    document, pageWidth - 50f, pageHeight - 40f, settings.logoUri!!, contentStream
                )
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Draw business information
        drawText(contentStream, shop.shopName, 25f, pageHeight - 70f, 14f, true)
        contentStream.setNonStrokingColor(secondaryColor)
        drawText(contentStream, shop.address, 25f, pageHeight - 85f, 9f, false)
        drawText(contentStream, "Mobile: ${shop.phoneNumber}", 25f, pageHeight - 100f, 9f, false)

        // Draw horizontal line
        drawLine(
            contentStream,
            20f,
            pageHeight - 110f,
            pageWidth - 20f,
            pageHeight - 110f,
            0.5f,
            secondaryColor
        )

        // Invoice details in a line
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "Invoice No.:", 25f, pageHeight - 130f, 9f, true)
        drawText(
            contentStream,
            "${settings.invoicePrefix}${invoice.invoiceNumber}",
            95f,
            pageHeight - 130f,
            9f,
            false
        )

        drawText(contentStream, "Invoice Date:", pageWidth - 150f, pageHeight - 130f, 9f, true)
        drawText(
            contentStream,
            dateFormatter.format(Date(invoice.invoiceDate)),
            pageWidth - 70f,
            pageHeight - 130f,
            9f,
            false
        )

        // Customer details in two columns
        drawRectangle(contentStream, 25f, pageHeight - 180f, pageWidth - 50f, 50f, secondaryColor)

        contentStream.setNonStrokingColor(secondaryColor)
        drawText(contentStream, "BILL TO", 35f, pageHeight - 145f, 9f, true)
        drawText(contentStream, "SHIP TO", pageWidth / 2 + 35f, pageHeight - 145f, 9f, true)

        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, invoice.customerName, 35f, pageHeight - 158f, 9f, true)
        drawText(
            contentStream, "Address: ${invoice.customerAddress}", 35f, pageHeight - 170f, 8f, false
        )
        drawText(
            contentStream,
            "Address: ${invoice.customerAddress}",
            pageWidth / 2 + 35f,
            pageHeight - 158f,
            8f,
            false
        )

        // Draw items table with detailed GST columns
        var currentY = pageHeight - 180f

        // Draw GST-focused table
        currentY =
            drawAdvanceGstItemsTable(contentStream, invoice.items, 25f, currentY, pageWidth - 50f)

        // Tax summary table
        currentY -= 10f
        drawText(contentStream, "HSN/SAC", 35f, currentY - 15f, 9f, true)
        drawText(contentStream, "Taxable Value", 120f, currentY - 15f, 9f, true)
        drawText(contentStream, "IGST", pageWidth / 2 - 10f, currentY - 15f, 9f, true)
        drawText(contentStream, "Cess", pageWidth / 2 + 80f, currentY - 15f, 9f, true)
        drawText(contentStream, "Total Tax Amount", pageWidth - 80f, currentY - 15f, 9f, true)

        // Draw horizontal line
        drawLine(
            contentStream,
            25f,
            currentY - 20f,
            pageWidth - 25f,
            currentY - 20f,
            0.5f,
            secondaryColor
        )

        // Sample tax data row
        drawText(contentStream, "1001", 35f, currentY - 35f, 9f, false)
        drawText(
            contentStream,
            formatter.format(getTaxableAmount(invoice)),
            120f,
            currentY - 35f,
            9f,
            false
        )

        // IGST columns
        drawText(contentStream, "Rate", pageWidth / 2 - 60f, currentY - 25f, 8f, false)
        drawText(contentStream, "Amount", pageWidth / 2 - 10f, currentY - 25f, 8f, false)
        drawText(contentStream, "14%", pageWidth / 2 - 60f, currentY - 35f, 8f, false)
        drawText(
            contentStream,
            formatter.format(invoice.totalAmount * 0.14),
            pageWidth / 2 - 10f,
            currentY - 35f,
            8f,
            false
        )

        drawText(
            contentStream,
            formatter.format(invoice.totalAmount * 0.12),
            pageWidth / 2 + 80f,
            currentY - 35f,
            9f,
            false
        )
        drawText(
            contentStream,
            "₹ " + formatter.format(invoice.totalAmount * (0.14 + 0.12)),
            pageWidth - 80f,
            currentY - 35f,
            9f,
            true
        )

        // Draw horizontal line
        drawLine(
            contentStream,
            25f,
            currentY - 45f,
            pageWidth - 25f,
            currentY - 45f,
            0.5f,
            secondaryColor
        )

        // Total row
        drawText(contentStream, "Total", 35f, currentY - 60f, 9f, true)
        drawText(
            contentStream,
            formatter.format(getTaxableAmount(invoice)),
            120f,
            currentY - 60f,
            9f,
            true
        )
        drawText(
            contentStream,
            formatter.format(invoice.totalAmount * 0.14),
            pageWidth / 2 - 10f,
            currentY - 60f,
            9f,
            true
        )
        drawText(
            contentStream,
            formatter.format(invoice.totalAmount * 0.12),
            pageWidth / 2 + 80f,
            currentY - 60f,
            9f,
            true
        )
        drawText(
            contentStream,
            "₹ " + formatter.format(invoice.totalAmount * (0.14 + 0.12)),
            pageWidth - 80f,
            currentY - 60f,
            9f,
            true
        )

        // Amount in words
        drawText(contentStream, "Total Amount (in words)", 35f, currentY - 80f, 9f, true)
        drawText(
            contentStream,
            numberToWords(invoice.totalAmount.toInt()) + " Rupees",
            35f,
            currentY - 95f,
            9f,
            false
        )

        // Notes and terms in columns
        drawText(contentStream, "Notes", 35f, currentY - 120f, 9f, true)
        drawText(
            contentStream, "Terms and Conditions", pageWidth / 2 + 35f, currentY - 120f, 9f, true
        )

        drawText(contentStream, "10 day return policy.", 35f, currentY - 135f, 8f, false)
        drawText(
            contentStream,
            "1. Goods once sold will not be taken back or",
            pageWidth / 2 + 35f,
            currentY - 135f,
            8f,
            false
        )
        drawText(contentStream, "exchanged", pageWidth / 2 + 35f, currentY - 145f, 8f, false)
        drawText(
            contentStream,
            "2. All disputes are subject to [ENTER_YOUR_CITY_NAME]",
            pageWidth / 2 + 35f,
            currentY - 160f,
            8f,
            false
        )
        drawText(
            contentStream, "jurisdiction only", pageWidth / 2 + 35f, currentY - 170f, 8f, false
        )

        // Draw signature if enabled
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                drawSignatureImage(
                    document,
                    pageWidth - 100f,
                    currentY - 170f,
                    settings.signatureUri!!,
                    contentStream
                )
            }
            drawText(
                contentStream,
                "Authorised Signatory For",
                pageWidth - 140f,
                currentY - 190f,
                8f,
                true
            )
            drawText(contentStream, shop.shopName, pageWidth - 140f, currentY - 200f, 8f, false)
        }

        // Draw QR code if enabled
        if (settings.showQrCode && settings.upiId.isNotEmpty()) {
            drawUpiQrCode(document, page, settings.upiId, 70f, currentY - 170f)
        }

        // Draw download info at bottom
        contentStream.setFont(regularFont, 7f)
        contentStream.setNonStrokingColor(secondaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(pageWidth / 2 - 50f, 30f)
        contentStream.showText("Invoice created using myBillBook")
        contentStream.endText()
    }

    // Advanced GST (Tally) template with tally-like formatting
    private fun generateAdvanceGstTallyTemplate(
        contentStream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        shop: ShopInvoiceDetails,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Draw a traditional tally-style invoice with borders
        drawRectangle(contentStream, 25f, 25f, pageWidth - 50f, pageHeight - 50f, secondaryColor)

        // Draw horizontal dividers for header sections
        drawLine(
            contentStream,
            25f,
            pageHeight - 100f,
            pageWidth - 25f,
            pageHeight - 100f,
            0.5f,
            secondaryColor
        )

        // Draw vertical divider in header
        drawLine(
            contentStream,
            pageWidth / 2,
            pageHeight - 50f,
            pageWidth / 2,
            pageHeight - 100f,
            0.5f,
            secondaryColor
        )

        // Draw header with logo space and invoice title
        contentStream.setFont(boldFont, 12f)
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        contentStream.beginText()
        contentStream.newLineAtOffset(pageWidth / 4, pageHeight - 65f)
        contentStream.showText("TAX INVOICE")
        contentStream.endText()

        // Draw shop logo if enabled
        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(
                    document,
                    pageWidth / 4 - 60f,
                    pageHeight - 75f,
                    settings.logoUri!!,
                    contentStream
                )
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Draw shop details
        drawText(contentStream, shop.shopName, 35f, pageHeight - 75f, 10f, true)
        contentStream.setNonStrokingColor(secondaryColor)
        drawText(contentStream, shop.address, 35f, pageHeight - 88f, 9f, false)

        // Draw invoice details in right section
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "Invoice No.", pageWidth / 2 + 25f, pageHeight - 70f, 9f, true)
        drawText(contentStream, "Invoice Date", pageWidth / 2 + 25f, pageHeight - 85f, 9f, true)

        drawText(
            contentStream,
            settings.invoicePrefix + invoice.invoiceNumber,
            pageWidth / 2 + 120f,
            pageHeight - 70f,
            9f,
            false
        )
        drawText(
            contentStream,
            dateFormatter.format(Date(invoice.invoiceDate)),
            pageWidth / 2 + 120f,
            pageHeight - 85f,
            9f,
            false
        )

        // Draw billing and shipping sections with colored background
        val billBgColor = AWTColor(235, 245, 220) // light green
        val shipBgColor = AWTColor(235, 245, 220) // light green

        // Fill background rectangles for better visibility
        contentStream.setNonStrokingColor(billBgColor)
        contentStream.addRect(25f, pageHeight - 170f, pageWidth / 2, 70f)
        contentStream.fill()

        contentStream.setNonStrokingColor(shipBgColor)
        contentStream.addRect(pageWidth / 2, pageHeight - 170f, pageWidth / 2 - 25f, 70f)
        contentStream.fill()

        // Draw section borders
        contentStream.setStrokingColor(secondaryColor)
        contentStream.addRect(25f, pageHeight - 170f, pageWidth / 2, 70f)
        contentStream.stroke()

        contentStream.setStrokingColor(secondaryColor)
        contentStream.addRect(pageWidth / 2, pageHeight - 170f, pageWidth / 2 - 25f, 70f)
        contentStream.stroke()

        // Draw section headings with colored background
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "BILL TO", 35f, pageHeight - 115f, 10f, true)
        drawText(contentStream, "SHIP TO", pageWidth / 2 + 10f, pageHeight - 115f, 10f, true)

        // Draw customer details
        drawText(contentStream, invoice.customerName, 35f, pageHeight - 130f, 9f, true)
        drawText(
            contentStream, "Address: ${invoice.customerAddress}", 35f, pageHeight - 145f, 8f, false
        )
        drawText(
            contentStream, "Mobile: ${invoice.customerPhone}", 35f, pageHeight - 160f, 8f, false
        )

        drawText(
            contentStream,
            "Address: ${invoice.customerAddress}",
            pageWidth / 2 + 10f,
            pageHeight - 130f,
            8f,
            false
        )

        // Draw items table section
        var currentY = pageHeight - 170f

        // Draw billbook style table with alternating colors
        currentY =
            drawBillbookItemsTable(contentStream, invoice.items, 25f, currentY, pageWidth - 50f)

        // Draw notes and terms section
        drawText(contentStream, "Notes", 35f, currentY - 25f, 9f, true)
        drawText(contentStream, "10 day return policy.", 35f, currentY - 40f, 8f, false)

        drawText(contentStream, "Terms and Conditions", 35f, currentY - 60f, 9f, true)
        drawText(
            contentStream,
            "1. Goods once sold will not be taken back or exchanged",
            35f,
            currentY - 75f,
            8f,
            false
        )
        drawText(
            contentStream,
            "2. All disputes are subject to [ENTER_YOUR_CITY_NAME] jurisdiction only",
            35f,
            currentY - 90f,
            8f,
            false
        )

        // Draw signature
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                drawSignatureImage(
                    document,
                    pageWidth - 100f,
                    currentY - 70f,
                    settings.signatureUri!!,
                    contentStream
                )
            }
            drawText(
                contentStream,
                "Authorised Signature for",
                pageWidth - 140f,
                currentY - 100f,
                8f,
                true
            )
            drawText(contentStream, shop.shopName, pageWidth - 140f, currentY - 110f, 8f, false)
        }

        // Draw download info at bottom
        contentStream.setFont(regularFont, 7f)
        contentStream.setNonStrokingColor(secondaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(pageWidth / 2 - 50f, 30f)
        contentStream.showText("Invoice created using myBillBook")
        contentStream.endText()
    }

    // Helper method to draw stylish items table
    private fun drawStylishItemsTable(
        contentStream: PDPageContentStream,
        items: List<InvoiceItem>,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        // Define headers
        val headers = listOf(
            "ITEMS", "HSN", "QTY.", "RATE", "DISC.", "TAX", "CESS", "AMOUNT"
        )

        // Define column widths proportions
        val columnWidths = floatArrayOf(0.30f, 0.10f, 0.07f, 0.10f, 0.10f, 0.10f, 0.10f, 0.13f)

        // Calculate actual widths
        val actualWidths = columnWidths.map { it * width }.toFloatArray()

        // Define which columns should be right-aligned
        val isRightAligned = arrayOf(false, true, true, true, true, true, true, true)

        // Define which columns should be bold
        val isBold = arrayOf(false, false, false, false, false, false, false, false)

        // Draw header row with a line below
        var xPos = x
        for (i in headers.indices) {
            // Calculate text position based on alignment
            val textX =
                if (isRightAligned[i]) xPos + actualWidths[i] - 5f - (boldFont.getStringWidth(
                    headers[i]
                ) / 1000 * 10f)
                else xPos + 5f

            // Draw header text
            contentStream.setNonStrokingColor(primaryColor)
            drawText(contentStream, headers[i], textX, y, 10f, true)

            // Move to next column position
            xPos += actualWidths[i]
        }

        // Draw line under headers
        drawLine(
            contentStream, x, y - 10f, x + width, y - 10f, 0.5f, primaryColor
        )

        // Reset text color
        contentStream.setNonStrokingColor(AWTColor.BLACK)

        // Prepare data rows
        val rowHeight = 15f
        var currentY = y - 10f - rowHeight

        // Track totals
        var totalQuantity = 0
        var totalAmount = 0.0

        // Build data rows
        for (item in items) {
            val quantity = item.quantity
            val price = item.price
            val discount = price * 0.10 // 10% discount for sample
            val taxRate = 0.14 // 14% tax rate
            val cessRate = 0.12 // 12% cess rate

            totalQuantity += quantity
            totalAmount += price * quantity

            // Draw row data
            xPos = x
            for (i in headers.indices) {
                val value = when (i) {
                    0 -> item.itemDetails.displayName
                    1 -> "1001" // Sample HSN code
                    2 -> "$quantity PCS"
                    3 -> formatter.format(price)
                    4 -> formatter.format(discount) + "\n(10%)"
                    5 -> formatter.format(price * taxRate) + "\n(14%)"
                    6 -> formatter.format(price * cessRate) + "\n(12%)"
                    7 -> "₹${formatter.format(price * quantity)}"
                    else -> ""
                }

                // Calculate text position based on alignment
                val textX =
                    if (isRightAligned[i]) xPos + actualWidths[i] - 5f - (regularFont.getStringWidth(
                        value
                    ) / 1000 * 10f)
                    else xPos + 5f

                drawText(contentStream, value, textX, currentY, 10f, false)

                // Move to next column
                xPos += actualWidths[i]
            }

            // Move to next row
            currentY -= rowHeight * 1.5f

            // Draw line between items
            drawLine(
                contentStream,
                x,
                currentY + rowHeight * 0.5f,
                x + width,
                currentY + rowHeight * 0.5f,
                0.2f,
                secondaryColor
            )
        }

        // Draw subtotal row
        drawLine(
            contentStream,
            x,
            currentY + rowHeight * 0.5f - 10f,
            x + width,
            currentY + rowHeight * 0.5f - 10f,
            0.5f,
            primaryColor
        )

        // Subtotal label
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "SUBTOTAL", x + 5f, currentY - 10f, 10f, true)

        // Quantity total
        xPos = x + actualWidths[0] + actualWidths[1]
        drawText(contentStream, totalQuantity.toString(), xPos + 5f, currentY - 10f, 10f, true)

        // Amount total
        xPos = x + width - actualWidths[7]
        drawText(
            contentStream,
            "₹ " + formatter.format(totalAmount),
            xPos + actualWidths[7] - 10f,
            currentY - 10f,
            10f,
            true
        )

        return currentY - 30f
    }

    // Helper method to draw advanced GST items table
    private fun drawAdvanceGstItemsTable(
        contentStream: PDPageContentStream,
        items: List<InvoiceItem>,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        // Define headers
        val headers = listOf(
            "ITEMS", "HSN", "QTY.", "RATE", "DISC.", "TAX", "CESS", "AMOUNT"
        )

        // Define column widths proportions
        val columnWidths = floatArrayOf(0.30f, 0.10f, 0.07f, 0.10f, 0.10f, 0.10f, 0.10f, 0.13f)

        // Calculate actual widths
        val actualWidths = columnWidths.map { it * width }.toFloatArray()

        // Draw table headers with colored background
        val headerBgColor = AWTColor(245, 245, 220) // light yellow

        // Fill background rectangle for header
        contentStream.setNonStrokingColor(headerBgColor)
        contentStream.addRect(x, y - 15f, width, 15f)
        contentStream.fill()

        // Draw borders
        contentStream.setStrokingColor(secondaryColor)
        contentStream.addRect(x, y - 15f, width, 15f)
        contentStream.stroke()

        // Draw header text
        var xPos = x
        for (i in headers.indices) {
            contentStream.setNonStrokingColor(AWTColor.BLACK)
            drawText(contentStream, headers[i], xPos + 5f, y - 10f, 8f, true)

            // Draw vertical lines
            if (i < headers.size - 1) {
                drawLine(
                    contentStream,
                    xPos + actualWidths[i],
                    y,
                    xPos + actualWidths[i],
                    y - 15f,
                    0.2f,
                    secondaryColor
                )
            }

            xPos += actualWidths[i]
        }

        // Reset for data rows
        var currentY = y - 15f

        // Draw rows
        for ((index, item) in items.withIndex()) {
            // Alternate row background colors
            if (index % 2 == 0) {
                contentStream.setNonStrokingColor(AWTColor(250, 250, 250)) // very light gray
                contentStream.addRect(x, currentY - 15f, width, 15f)
                contentStream.fill()
            }

            // Draw border
            contentStream.setStrokingColor(secondaryColor)
            contentStream.addRect(x, currentY - 15f, width, 15f)
            contentStream.stroke()

            // Draw row data
            xPos = x
            for (i in headers.indices) {
                val value = when (i) {
                    0 -> item.itemDetails.displayName
                    1 -> "1001" // Sample HSN code
                    2 -> "${item.quantity} PCS"
                    3 -> formatter.format(item.price)
                    4 -> formatter.format(item.price * 0.10) + "\n(10%)"
                    5 -> formatter.format(item.price * 0.14) + "\n(14%)"
                    6 -> formatter.format(item.price * 0.12) + "\n(12%)"
                    7 -> "₹${formatter.format(item.price * item.quantity)}"
                    else -> ""
                }

                contentStream.setNonStrokingColor(AWTColor.BLACK)
                drawText(contentStream, value, xPos + 5f, currentY - 10f, 8f, false)

                // Draw vertical lines
                if (i < headers.size - 1) {
                    drawLine(
                        contentStream,
                        xPos + actualWidths[i],
                        currentY,
                        xPos + actualWidths[i],
                        currentY - 15f,
                        0.2f,
                        secondaryColor
                    )
                }

                xPos += actualWidths[i]
            }

            currentY -= 15f
        }

        return currentY - 10f
    }

    // Helper method to draw tally-style items table
    private fun drawTallyStyleItemsTable(
        contentStream: PDPageContentStream,
        items: List<InvoiceItem>,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        // Define headers
        val headers = listOf(
            "S.NO.", "ITEMS", "HSN", "QTY.", "RATE", "DISC.", "TAX", "CESS", "AMOUNT"
        )

        // Define column widths proportions
        val columnWidths =
            floatArrayOf(0.05f, 0.25f, 0.10f, 0.07f, 0.10f, 0.10f, 0.10f, 0.10f, 0.13f)

        // Calculate actual widths
        val actualWidths = columnWidths.map { it * width }.toFloatArray()

        // Draw table headers with colored background
        val headerBgColor = AWTColor(245, 230, 190) // beige

        // Fill background rectangle for header
        contentStream.setNonStrokingColor(headerBgColor)
        contentStream.addRect(x, y - 15f, width, 15f)
        contentStream.fill()

        // Draw borders
        contentStream.setStrokingColor(secondaryColor)
        contentStream.addRect(x, y - 15f, width, 15f)
        contentStream.stroke()

        // Draw header text
        var xPos = x
        for (i in headers.indices) {
            contentStream.setNonStrokingColor(AWTColor.BLACK)
            drawText(contentStream, headers[i], xPos + 5f, y - 10f, 8f, true)

            // Draw vertical lines
            if (i < headers.size - 1) {
                drawLine(
                    contentStream,
                    xPos + actualWidths[i],
                    y,
                    xPos + actualWidths[i],
                    y - 15f,
                    0.2f,
                    secondaryColor
                )
            }

            xPos += actualWidths[i]
        }

        // Reset for data rows
        var currentY = y - 15f

        // Draw rows
        for ((index, item) in items.withIndex()) {
            // Draw border
            contentStream.setStrokingColor(secondaryColor)
            contentStream.addRect(x, currentY - 15f, width, 15f)
            contentStream.stroke()

            // Draw row data
            xPos = x
            for (i in headers.indices) {
                val value = when (i) {
                    0 -> (index + 1).toString()
                    1 -> item.itemDetails.displayName
                    2 -> "1001" // Sample HSN code
                    3 -> "${item.quantity} PCS"
                    4 -> formatter.format(item.price)
                    5 -> formatter.format(item.price * 0.10) + "\n(10%)"
                    6 -> formatter.format(item.price * 0.14) + "\n(14%)"
                    7 -> formatter.format(item.price * 0.12) + "\n(12%)"
                    8 -> "₹${formatter.format(item.price * item.quantity)}"
                    else -> ""
                }

                contentStream.setNonStrokingColor(AWTColor.BLACK)
                drawText(contentStream, value, xPos + 5f, currentY - 10f, 8f, false)

                // Draw vertical lines
                if (i < headers.size - 1) {
                    drawLine(
                        contentStream,
                        xPos + actualWidths[i],
                        currentY,
                        xPos + actualWidths[i],
                        currentY - 15f,
                        0.2f,
                        secondaryColor
                    )
                }

                xPos += actualWidths[i]
            }

            currentY -= 15f
        }

        // Draw extras like delivery charges
        contentStream.setStrokingColor(secondaryColor)
        contentStream.addRect(x, currentY - 15f, width, 15f)
        contentStream.stroke()

        drawText(
            contentStream, "Delivery Charges", x + actualWidths[0] + 5f, currentY - 10f, 8f, false
        )
        drawText(
            contentStream,
            "-",
            x + actualWidths[0] + actualWidths[1] + 5f,
            currentY - 10f,
            8f,
            false
        )
        drawText(
            contentStream,
            "-",
            x + actualWidths[0] + actualWidths[1] + actualWidths[2] + 5f,
            currentY - 10f,
            8f,
            false
        )
        drawText(contentStream, "100", x + width - 30f, currentY - 10f, 8f, false)

        currentY -= 15f

        // Draw total row
        contentStream.setStrokingColor(secondaryColor)
        contentStream.addRect(x, currentY - 15f, width, 15f)
        contentStream.fill()

        contentStream.setNonStrokingColor(headerBgColor)
        contentStream.addRect(x, currentY - 15f, width, 15f)
        contentStream.fill()

        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "TOTAL", x + 5f, currentY - 10f, 9f, true)

        // Calculate and display totals
        val totalQuantity = items.sumOf { it.quantity }
        drawText(
            contentStream,
            totalQuantity.toString(),
            x + actualWidths[0] + actualWidths[1] + actualWidths[2] + 5f,
            currentY - 10f,
            9f,
            true
        )

        val totalRate = items.sumOf { it.price }
        drawText(
            contentStream,
            formatter.format(totalRate),
            x + actualWidths[0] + actualWidths[1] + actualWidths[2] + actualWidths[3] + 5f,
            currentY - 10f,
            9f,
            true
        )

        val totalAmount = items.sumOf { it.price * it.quantity } + 100 // Adding delivery charges
        drawText(
            contentStream,
            "₹${formatter.format(totalAmount)}",
            x + width - 30f,
            currentY - 10f,
            9f,
            true
        )

        return currentY - 30f
    }

    // Helper method to draw billbook style items table
    private fun drawBillbookItemsTable(
        contentStream: PDPageContentStream,
        items: List<InvoiceItem>,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        // Define headers
        val headers = listOf(
            "S.NO.", "ITEMS", "HSN", "QTY.", "RATE", "DISC.", "AMOUNT"
        )

        // Define column widths proportions
        val columnWidths = floatArrayOf(0.05f, 0.35f, 0.10f, 0.10f, 0.15f, 0.10f, 0.15f)

        // Calculate actual widths
        val actualWidths = columnWidths.map { it * width }.toFloatArray()

        // Draw table border
        contentStream.setStrokingColor(secondaryColor)
        contentStream.addRect(x, y - 300f, width, 300f) // Fixed height table
        contentStream.stroke()

        // Draw table headers with colored background
        val headerBgColor = AWTColor(245, 245, 220) // light yellow

        // Fill background rectangle for header
        contentStream.setNonStrokingColor(headerBgColor)
        contentStream.addRect(x, y, width, -20f)
        contentStream.fill()

        // Draw header text
        var xPos = x
        for (i in headers.indices) {
            contentStream.setNonStrokingColor(AWTColor.BLACK)
            drawText(contentStream, headers[i], xPos + 5f, y - 15f, 9f, true)

            // Draw vertical lines through entire table
            if (i < headers.size - 1) {
                drawLine(
                    contentStream,
                    xPos + actualWidths[i],
                    y,
                    xPos + actualWidths[i],
                    y - 300f,
                    0.2f,
                    secondaryColor
                )
            }

            xPos += actualWidths[i]
        }

        // Draw horizontal line after header
        drawLine(
            contentStream, x, y - 20f, x + width, y - 20f, 0.5f, secondaryColor
        )

        // Reset for data rows
        var currentY = y - 20f

        // Draw rows
        for ((index, item) in items.withIndex()) {
            // Draw row data
            xPos = x
            for (i in headers.indices) {
                val value = when (i) {
                    0 -> (index + 1).toString()
                    1 -> item.itemDetails.displayName + "\nIMEI: ${item.itemDetails.jewelryCode}"
                    2 -> "1001" // Sample HSN code
                    3 -> "${item.quantity} PCS"
                    4 -> formatter.format(item.price)
                    5 -> formatter.format(item.price * 0.10) + "\n(10%)"
                    6 -> formatter.format(item.price * item.quantity)
                    else -> ""
                }

                contentStream.setNonStrokingColor(AWTColor.BLACK)
                drawText(contentStream, value, xPos + 5f, currentY - 15f, 9f, false)

                xPos += actualWidths[i]
            }

            currentY -= 40f

            // Draw horizontal line after each row
            drawLine(
                contentStream, x, currentY, x + width, currentY, 0.2f, secondaryColor
            )
        }

        // Draw extras like delivery charges
        xPos = x
        for (i in headers.indices) {
            val value = when (i) {
                0 -> ""
                1 -> "Delivery Charges"
                2 -> "-"
                3 -> "-"
                4 -> "-"
                5 -> "-"
                6 -> "₹ 100"
                else -> ""
            }

            contentStream.setNonStrokingColor(AWTColor.BLACK)
            drawText(contentStream, value, xPos + 5f, currentY - 15f, 9f, false)

            xPos += actualWidths[i]
        }

        currentY -= 30f

        // Draw IGST charges
        xPos = x
        for (i in headers.indices) {
            val value = when (i) {
                0 -> ""
                1 -> "IGST @14%"
                2 -> "-"
                3 -> "-"
                4 -> "-"
                5 -> "-"
                6 -> "₹ " + formatter.format(items.sumOf { it.price * it.quantity } * 0.14)
                else -> ""
            }

            contentStream.setNonStrokingColor(AWTColor.BLACK)
            drawText(contentStream, value, xPos + 5f, currentY - 15f, 9f, false)

            xPos += actualWidths[i]
        }

        currentY -= 30f

        // Draw Cess charges
        xPos = x
        for (i in headers.indices) {
            val value = when (i) {
                0 -> ""
                1 -> "Cess @12%"
                2 -> "-"
                3 -> "-"
                4 -> "-"
                5 -> "-"
                6 -> "₹ " + formatter.format(items.sumOf { it.price * it.quantity } * 0.12)
                else -> ""
            }

            contentStream.setNonStrokingColor(AWTColor.BLACK)
            drawText(contentStream, value, xPos + 5f, currentY - 15f, 9f, false)

            xPos += actualWidths[i]
        }

        currentY -= 30f

        // Draw Round off
        xPos = x
        for (i in headers.indices) {
            val value = when (i) {
                0 -> ""
                1 -> "Round Off"
                2 -> "-"
                3 -> "-"
                4 -> "-"
                5 -> "-"
                6 -> "₹ -" + formatter.format(getTotalRoundOff(items))
                else -> ""
            }

            contentStream.setNonStrokingColor(AWTColor.BLACK)
            drawText(contentStream, value, xPos + 5f, currentY - 15f, 9f, false)

            xPos += actualWidths[i]
        }

        currentY -= 30f

        // Draw total row with colored background
        contentStream.setNonStrokingColor(headerBgColor)
        contentStream.addRect(x, currentY, width, -20f)
        contentStream.fill()

        contentStream.setStrokingColor(secondaryColor)
        contentStream.addRect(x, currentY, width, -20f)
        contentStream.stroke()

        // Draw TOTAL text
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "TOTAL", x + 5f, currentY - 15f, 10f, true)

        // Calculate and display final total
        val totalAmount = items . sumOf { it.price * it.quantity } * 1.26 + 100 // Adding GST, Cess and delivery
        drawText(
            contentStream,
            "₹ " + formatter.format(totalAmount),
            x + width - 50f,
            currentY - 15f,
            10f,
            true
        )

        // Draw received amount
        currentY -= 25f
        drawLine(
            contentStream, x, currentY, x + width, currentY, 0.2f, secondaryColor
        )

        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "RECEIVED AMOUNT", x + 5f, currentY - 15f, 9f, true)
        drawText(
            contentStream,
            "₹ " + formatter.format(totalAmount),
            x + width - 50f,
            currentY - 15f,
            9f,
            false
        )

        // Draw balance amount
        currentY -= 25f
        drawLine(
            contentStream, x, currentY, x + width, currentY, 0.2f, secondaryColor
        )

        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "BALANCE AMOUNT", x + 5f, currentY - 15f, 9f, true)
        drawText(contentStream, "₹ 0", x + width - 50f, currentY - 15f, 9f, false)

        return y - 320f // Return position after table
    }

    // Helpers for tax and total calculations
    private fun getTaxableAmount(invoice: Invoice): Double {
        return invoice.items.sumOf { it.price * it.quantity }
    }

    private fun getTotalRoundOff(items: List<InvoiceItem>): Double {
        val totalBeforeRounding = items.sumOf { it.price * it.quantity } * 1.26 + 100
        val roundedTotal = Math.round(totalBeforeRounding).toDouble()
        return Math.abs(roundedTotal - totalBeforeRounding)
    }

    private fun getTotalRoundOff(invoice: Invoice): Double {
        return getTotalRoundOff(invoice.items)
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
        contentStream.setNonStrokingColor(AWTColor.BLACK)

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
        contentStream.setNonStrokingColor(AWTColor.BLACK)

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
        contentStream.setNonStrokingColor(AWTColor.BLACK)
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
        contentStream.setNonStrokingColor(AWTColor.BLACK)
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
                contentStream.setNonStrokingColor(primaryColor)
                contentStream.newLineAtOffset(x + 15f, y - 15f)
                contentStream.showText("Scan to Pay")
                contentStream.endText()
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
            contentStream.setNonStrokingColor(AWTColor.BLACK)
            drawText(contentStream, "Terms & Conditions:", 40f, 100f, 10f, true)

            // Split terms into lines and draw each line
            val terms = settings.termsAndConditions.split("\n")
            var yPos = 85f

            for (term in terms) {
                drawText(contentStream, term, 40f, yPos, 8f, false)
                yPos -= 12f
            }
        }

        // Draw thank you message
        contentStream.setNonStrokingColor(primaryColor)
        drawCenteredText(
            contentStream, "Thank you for shopping with us!", pageWidth / 2, 30f, 10f, true
        )
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
        drawRectangle(contentStream, x, y - totalHeight, width, totalHeight, AWTColor.BLACK)

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
                drawLine(contentStream, xPos, y, xPos, y - totalHeight, 0.5f, AWTColor.BLACK)
            }
        }

        // Draw horizontal line after header
        drawLine(contentStream, x, y - rowHeight, x + width, y - rowHeight, 0.5f, AWTColor.BLACK)

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
                drawLine(contentStream, x, yPos, x + width, yPos, 0.5f, AWTColor.BLACK)
            }
        }

        return y - totalHeight - 10f // Return the Y position after drawing the table plus some spacing
    }

    // 1. Generate Billbook Template
    private fun generateBillbookTemplate(
        contentStream: PDPageContentStream,
        document: PDDocument,
        page: PDPage,
        invoice: Invoice,
        shop: ShopInvoiceDetails,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Draw clean, modern billbook style with signature box

        // Draw outer border
        drawRectangle(contentStream, 20f, 20f, pageWidth - 40f, pageHeight - 40f, secondaryColor)

        // Draw header divider
        drawLine(
            contentStream,
            20f,
            pageHeight - 120f,
            pageWidth - 20f,
            pageHeight - 120f,
            0.5f,
            secondaryColor
        )

        // Header with logo
        contentStream.setFont(boldFont, 22f)
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(40f, pageHeight - 40f)
        contentStream.showText("BILL / INVOICE")
        contentStream.endText()

        // Draw shop logo if enabled
        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(
                    document, pageWidth - 80f, pageHeight - 70f, settings.logoUri!!, contentStream
                )
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Shop details
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, shop.shopName, 40f, pageHeight - 70f, 14f, true)
        drawText(contentStream, shop.address, 40f, pageHeight - 90f, 9f, false)
        drawText(contentStream, "Phone: ${shop.phoneNumber}", 40f, pageHeight - 105f, 9f, false)

        // Invoice details section
        drawText(contentStream, "Bill No: ${settings.invoicePrefix}${invoice.invoiceNumber}", pageWidth - 200f, pageHeight - 70f, 10f, true)
        drawText(contentStream, "Date: ${dateFormatter.format(Date(invoice.invoiceDate))}", pageWidth - 200f, pageHeight - 90f, 10f, false)

        // Customer details section
        drawLine(
            contentStream,
            20f,
            pageHeight - 150f,
            pageWidth - 20f,
            pageHeight - 150f,
            0.2f,
            secondaryColor
        )

        // Customer title
        contentStream.setFont(boldFont, 10f)
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(40f, pageHeight - 140f)
        contentStream.showText("BILLED TO:")
        contentStream.endText()

        // Customer info
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, invoice.customerName, 40f, pageHeight - 160f, 12f, true)
        drawText(contentStream, invoice.customerAddress, 40f, pageHeight - 175f, 9f, false)
        drawText(contentStream, "Phone: ${invoice.customerPhone}", 40f, pageHeight - 190f, 9f, false)

        // Start Y position for items table
        var currentY = pageHeight - 210f

        // Draw items table in billbook style
        currentY = drawBillbookItemsTable(contentStream, invoice.items, 30f, currentY, pageWidth - 60f)

        // Draw Tax summary
        contentStream.setNonStrokingColor(primaryColor)
        drawText(contentStream, "TAX SUMMARY:", 30f, currentY - 20f, 12f, true)
        contentStream.setNonStrokingColor(AWTColor.BLACK)

        // Draw tax details table
        val taxHeaders = listOf("HSN/SAC", "TAXABLE AMT", "RATE", "IGST", "CGST", "SGST")
        val taxWidths = floatArrayOf(0.15f, 0.25f, 0.15f, 0.15f, 0.15f, 0.15f)
        val actualTaxWidths = taxWidths.map { it * (pageWidth - 60f) }.toFloatArray()

        // Draw tax header
        var xPos = 30f
        for (i in taxHeaders.indices) {
            drawText(contentStream, taxHeaders[i], xPos + 5f, currentY - 40f, 9f, true)
            xPos += actualTaxWidths[i]
        }

        drawLine(
            contentStream,
            30f,
            currentY - 45f,
            pageWidth - 30f,
            currentY - 45f,
            0.2f,
            secondaryColor
        )

        // Sample tax data
        val taxRate = invoice.items.firstOrNull()?.itemDetails?.taxRate ?: 5.0
        val taxableAmount = getTaxableAmount(invoice)
        val igstAmount = taxableAmount * (taxRate / 100.0)

        xPos = 30f
        drawText(contentStream, "998391", xPos + 5f, currentY - 60f, 9f, false)
        xPos += actualTaxWidths[0]

        drawText(contentStream, "₹${formatter.format(taxableAmount)}", xPos + 5f, currentY - 60f, 9f, false)
        xPos += actualTaxWidths[1]

        drawText(contentStream, "${taxRate.toInt()}%", xPos + 5f, currentY - 60f, 9f, false)
        xPos += actualTaxWidths[2]

        drawText(contentStream, "₹${formatter.format(igstAmount)}", xPos + 5f, currentY - 60f, 9f, false)
        xPos += actualTaxWidths[3]

        drawText(contentStream, "-", xPos + 5f, currentY - 60f, 9f, false)
        xPos += actualTaxWidths[4]

        drawText(contentStream, "-", xPos + 5f, currentY - 60f, 9f, false)

        // Draw terms and bank details in two columns
        drawText(contentStream, "TERMS & CONDITIONS:", 30f, currentY - 100f, 10f, true)

        val terms = settings.termsAndConditions.split("\n")
        var yPos = currentY - 115f

        for (term in terms) {
            drawText(contentStream, "• $term", 30f, yPos, 8f, false)
            yPos -= 12f
        }

        // Draw bank details
        drawText(contentStream, "BANK DETAILS:", pageWidth / 2 + 20f, currentY - 100f, 10f, true)
        drawText(contentStream, "Bank Name: [YOUR BANK]", pageWidth / 2 + 20f, currentY - 115f, 8f, false)
        drawText(contentStream, "A/C No: XXXXXXXXXXXX", pageWidth / 2 + 20f, currentY - 127f, 8f, false)
        drawText(contentStream, "IFSC: XXXXXXXXX", pageWidth / 2 + 20f, currentY - 139f, 8f, false)

        // Draw totals section
        drawRectangle(contentStream, pageWidth - 200f, 120f, 170f, 100f, secondaryColor)

        drawText(contentStream, "SUBTOTAL:", pageWidth - 190f, 200f, 10f, true)
        drawText(contentStream, "₹${formatter.format(taxableAmount)}", pageWidth - 40f, 200f, 10f, false)

        drawText(contentStream, "DISCOUNT:", pageWidth - 190f, 180f, 10f, true)
        drawText(contentStream, "₹0.00", pageWidth - 40f, 180f, 10f, false)

        drawText(contentStream, "TAX:", pageWidth - 190f, 160f, 10f, true)
        drawText(contentStream, "₹${formatter.format(igstAmount)}", pageWidth - 40f, 160f, 10f, false)

        drawLine(
            contentStream,
            pageWidth - 190f,
            150f,
            pageWidth - 30f,
            150f,
            0.5f,
            secondaryColor
        )

        drawText(contentStream, "GRAND TOTAL:", pageWidth - 190f, 130f, 12f, true)
        drawText(contentStream, "₹${formatter.format(invoice.totalAmount)}", pageWidth - 40f, 130f, 12f, true)

        // Draw bottom section with signature
        if (settings.showSignature) {
            if (settings.signatureUri != null) {
                drawSignatureImage(
                    document, pageWidth - 100f, 80f, settings.signatureUri!!, contentStream
                )
            }
            drawText(contentStream, "For " + shop.shopName, pageWidth - 100f, 50f, 9f, true)
            drawText(contentStream, "Authorized Signatory", pageWidth - 100f, 35f, 8f, false)
        }

        // Draw QR code if enabled
        if (settings.showQrCode && settings.upiId.isNotEmpty()) {
            drawUpiQrCode(document, page, settings.upiId, 100f, 80f)
            drawText(contentStream, "Scan to Pay UPI", 80f, 50f, 9f, true)
        }
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

            // Calculate dimensions (max 120x60)
            val maxWidth = 120f
            val maxHeight = 60f
            val ratio = logoImage.width.toFloat() / logoImage.height.toFloat()

            val width: Float
            val height: Float

            if (ratio > 1) { // Wider than tall
                width = maxWidth
                height = width / ratio
            } else { // Taller than wide
                height = maxHeight
                width = height * ratio
            }

            // Draw logo
            contentStream.drawImage(logoImage, x - width, y - height / 2, width, height)
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error loading shop logo: ${e.message}")
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
                    val watermarkHeight = watermarkWidth * watermarkImage.height / watermarkImage.width
                    val xPos = (pageWidth - watermarkWidth) / 2
                    val yPos = (pageHeight - watermarkHeight) / 2

                    // Draw watermark
                    contentStream.drawImage(watermarkImage, xPos, yPos, watermarkWidth, watermarkHeight)
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