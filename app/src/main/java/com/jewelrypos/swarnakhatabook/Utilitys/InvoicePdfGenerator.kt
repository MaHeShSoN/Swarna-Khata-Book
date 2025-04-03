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
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings
import com.jewelrypos.swarnakhatabook.DataClasses.Shop
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.jewelrypos.swarnakhatabook.DataClasses.ShopInvoiceDetails
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

                // Draw page structure with border
                drawPageStructure(contentStream, pageWidth, pageHeight)

                // Draw invoice header with shop logo if enabled
                drawInvoiceHeader(contentStream, invoice, shop, pageWidth, pageHeight)

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
                drawFooter(contentStream, shop, pageWidth, pageHeight)
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

    // Apply watermark to the page
    private fun applyWatermark(document: PDDocument, page: PDPage, watermarkUri: String) {
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

    private fun drawPageStructure(
        contentStream: PDPageContentStream,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Main border with primary color
        contentStream.setStrokingColor(primaryColor)
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
    }

    private fun drawInvoiceHeader(
        contentStream: PDPageContentStream,
        invoice: Invoice,
        shop: ShopInvoiceDetails,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // TAX INVOICE title - larger, bold and centered
        contentStream.setFont(boldFont, 22f)
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.beginText()
        val titleText = "TAX INVOICE"
        val titleWidth = boldFont.getStringWidth(titleText) / 1000 * 22f
        contentStream.newLineAtOffset(pageWidth/2 - titleWidth/2, pageHeight - 40f)
        contentStream.showText(titleText)
        contentStream.endText()

        // Invoice number with prefix
        val invoiceDisplayNumber = if (invoice.invoiceNumber.startsWith(settings.invoicePrefix)) {
            invoice.invoiceNumber
        } else {
            "${settings.invoicePrefix}${invoice.invoiceNumber}"
        }

        drawText(contentStream, "Invoice No: $invoiceDisplayNumber", 25f, pageHeight - 70f, 12f, true)
        drawText(contentStream, "Date: ${dateFormatter.format(Date(invoice.invoiceDate))}", pageWidth - 200f, pageHeight - 70f, 12f, true)

        // Draw shop logo if enabled
        if (settings.showLogo && settings.logoUri != null) {
            try {
                drawShopLogo(pageWidth - 120f, pageHeight - 40f, settings.logoUri!!, contentStream)
            } catch (e: Exception) {
                Log.e("InvoicePdfGenerator", "Error drawing logo: ${e.message}")
            }
        }

        // Customer section title and details
        contentStream.setNonStrokingColor(AWTColor.BLACK)
        drawText(contentStream, "Customer:", 25f, pageHeight - 120f, 12f, true)
        drawText(contentStream, invoice.customerName, 25f, pageHeight - 135f, 10f, false)
        drawText(contentStream, "Phone: ${invoice.customerPhone}", 25f, pageHeight - 150f, 10f, false)
        drawText(contentStream, invoice.customerAddress, 25f, pageHeight - 165f, 10f, false)

        // Shop section title and details
        drawText(contentStream, "Shop:", pageWidth / 2 + 20f, pageHeight - 120f, 12f, true)
        drawText(contentStream, shop.shopName, pageWidth / 2 + 20f, pageHeight - 135f, 10f, false)
        drawText(contentStream, shop.address, pageWidth / 2 + 20f, pageHeight - 150f, 10f, false)
        drawText(contentStream, "GSTIN: ${shop.gstNumber}", pageWidth / 2 + 20f, pageHeight - 165f, 10f, false)
    }

    private fun drawShopLogo(x: Float, y: Float, logoUri: String, contentStream: PDPageContentStream) {
        try {
            val uri = Uri.parse(logoUri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return

            // Load logo
            val logoBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Create PDImageXObject from bitmap
            val document = PDDocument()
            val logoImage = LosslessFactory.createFromImage(document, logoBitmap)

            // Calculate dimensions (max 100x50)
            val maxWidth = 100f
            val maxHeight = 50f
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

            // Close temporary document
            document.close()
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error loading shop logo: ${e.message}")
        }
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

        // Draw outer border with primary color
        drawRectangle(contentStream, x, y - totalHeight, width, totalHeight, primaryColor)

        // Draw header row content with secondary color
        contentStream.setNonStrokingColor(primaryColor)
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

        // Reset text color
        contentStream.setNonStrokingColor(AWTColor.BLACK)

        // Draw all vertical lines in one pass
        xPos = x
        for (i in 0 until headers.size) {
            xPos += columnWidths[i]
            if (i < headers.size - 1) {
                drawLine(contentStream, xPos, y, xPos, y - totalHeight, 0.5f, secondaryColor)
            }
        }

        // Draw horizontal line after header with primary color
        drawLine(contentStream, x, y - rowHeight, x + width, y - rowHeight, 0.5f, primaryColor)

        // Draw data rows
        var yPos = y - rowHeight
        for (rowIndex in data.indices) {
            xPos = x
            for (colIndex in data[rowIndex].indices) {
                // Calculate text position based on alignment
                val textValue = data[rowIndex][colIndex]
                val font = if (rowIndex == data.size - 1 || isBold[colIndex]) boldFont else regularFont
                val textX = if (isRightAligned[colIndex])
                    xPos + columnWidths[colIndex] - 5f - (font.getStringWidth(textValue) / 1000 * 10f)
                else
                    xPos + 5f

                // Use bold for last row (totals) or if specified
                val useBold = rowIndex == data.size - 1 || isBold[colIndex]

                drawText(
                    contentStream,
                    textValue,
                    textX,
                    yPos - rowHeight + 5f,
                    10f,
                    useBold
                )
                xPos += columnWidths[colIndex]
            }

            // Move to next row
            yPos -= rowHeight

            // Draw horizontal line after each row (except last)
            if (rowIndex < data.size - 1) {
                drawLine(contentStream, x, yPos, x + width, yPos, 0.5f, secondaryColor)
            }
        }

        return y - totalHeight - 10f // Return the Y position after drawing the table plus some spacing
    }

    private fun drawItemsTable(
        contentStream: PDPageContentStream,
        items: List<InvoiceItem>,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        // Draw section title
        contentStream.setFont(boldFont, 12f)
        contentStream.setNonStrokingColor(primaryColor)
        contentStream.beginText()
        contentStream.newLineAtOffset(x, y + 15f)
        contentStream.showText("Items")
        contentStream.endText()

        // Reset text color
        contentStream.setNonStrokingColor(AWTColor.BLACK)

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

            val stoneValue = item.itemDetails.diamondPrice * pieces
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
                "₹${formatter.format(itemTotal)}"
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
            "₹${formatter.format(totalAmount)}"
        ))

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

    private fun drawTaxAndExtraCharges(
        contentStream: PDPageContentStream,
        invoice: Invoice,
        x: Float,
        y: Float,
        width: Float
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
            val extraChargesTotal = item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
            val taxableAmount = itemTotal + extraChargesTotal
            taxableAmount * (item.itemDetails.taxRate / 100.0)
        }

        // Add tax row
        val taxRate = invoice.items.firstOrNull()?.itemDetails?.taxRate ?: 0.0
        dataRows.add(listOf(
            "Tax (${taxRate.toInt()}%)",
            "₹${formatter.format(totalTax)}"
        ))

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

    private fun drawPaymentDetails(
        contentStream: PDPageContentStream,
        invoice: Invoice,
        x: Float,
        y: Float,
        width: Float
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
            dataRows.add(listOf(
                payment.method,
                "₹${formatter.format(payment.amount)}"
            ))
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

    private fun drawTotalsSection(
        contentStream: PDPageContentStream,
        invoice: Invoice,
        x: Float,
        y: Float,
        width: Float
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
        contentStream.setNonStrokingColor(if (balanceDue <= 0) AWTColor(0, 128, 0) else AWTColor(192, 0, 0))
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

    private fun drawUpiQrCode(
        document: PDDocument,
        page: PDPage,
        upiId: String,
        x: Float,
        y: Float
    ) {
        try {
            // Generate QR code
            val qrCodeBitmap = generateUpiQrCode(upiId)

            // Convert bitmap to PDImageXObject
            val qrCodeImage = LosslessFactory.createFromImage(document, qrCodeBitmap)

            // Draw on page
            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
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

    private fun drawFooter(
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
                    drawSignatureImage(pageWidth - 150f, 70f, settings.signatureUri!!, contentStream)
                } catch (e: Exception) {
                    Log.e("InvoicePdfGenerator", "Error drawing signature: ${e.message}")
                    // Fall back to text-only signature line
                    drawText(contentStream, "Authorized Signature", pageWidth - 150f, 50f, 10f, true)
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
            contentStream,
            "Thank you for shopping with us!",
            pageWidth / 2,
            30f,
            10f,
            true
        )
    }

    private fun drawSignatureImage(x: Float, y: Float, signatureUri: String, contentStream: PDPageContentStream) {
        try {
            val uri = Uri.parse(signatureUri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return

            // Load signature
            val signatureBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Create PDImageXObject from bitmap
            val document = PDDocument()
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

            // Draw label below signature
            contentStream.beginText()
            contentStream.setFont(boldFont, 10f)
            contentStream.newLineAtOffset(x - 50f, y - 15f)
            contentStream.showText("Authorized Signature")
            contentStream.endText()

            // Close temporary document
            document.close()
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error loading signature: ${e.message}")
        }
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

    private fun numberToWords(number: Int): String {
        if (number == 0) return "Zero"
        if (number < 0) return "Negative " + numberToWords(-number)

        val units = arrayOf(
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"
        )
        val teens = arrayOf(
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen",
            "Sixteen", "Seventeen", "Eighteen", "Nineteen"
        )
        val tens = arrayOf(
            "", "Ten", "Twenty", "Thirty", "Forty", "Fifty",
            "Sixty", "Seventy", "Eighty", "Ninety"
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