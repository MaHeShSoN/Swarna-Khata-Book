package com.jewelrypos.swarnakhatabook.Utilitys


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Shop
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.tom_roush.harmony.awt.AWTColor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
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

    /**
     * Main function to generate PDF from Invoice data
     */
    fun generateInvoicePdf(invoice: Invoice, shop: Shop, fileName: String): File {
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
                // Draw page structure
                drawPageStructure(contentStream, pageWidth, pageHeight)

                // Draw invoice header
                drawInvoiceHeader(contentStream, invoice, shop, pageWidth, pageHeight)

                // Separate gold and silver items
                val goldItems =
                    invoice.items.filter { it.itemDetails.itemType.lowercase() == "gold" }
                val silverItems =
                    invoice.items.filter { it.itemDetails.itemType.lowercase() == "silver" }
                val otherItems = invoice.items.filter {
                    it.itemDetails.itemType.lowercase() != "gold" &&
                            it.itemDetails.itemType.lowercase() != "silver"
                }

                // Set starting Y position
                var currentY = pageHeight - 170f

                // Draw items tables
                if (goldItems.isNotEmpty()) {
                    drawSectionHeader(contentStream, "Gold Items", 25f, currentY, pageWidth)
                    currentY -= 20f
                    currentY =
                        drawItemsTable(contentStream, goldItems, 25f, currentY, pageWidth - 50f)
                    currentY -= 20f
                }

                if (silverItems.isNotEmpty()) {
                    drawSectionHeader(contentStream, "Silver Items", 25f, currentY, pageWidth)
                    currentY -= 20f
                    currentY =
                        drawItemsTable(contentStream, silverItems, 25f, currentY, pageWidth - 50f)
                    currentY -= 20f
                }

                if (otherItems.isNotEmpty()) {
                    drawSectionHeader(contentStream, "Other Items", 25f, currentY, pageWidth)
                    currentY -= 20f
                    currentY =
                        drawItemsTable(contentStream, otherItems, 25f, currentY, pageWidth - 50f)
                    currentY -= 20f
                }

                // Draw payment details
                currentY =
                    drawPaymentDetails(contentStream, invoice, 25f, currentY, pageWidth - 50f)

                // Draw totals and balance
                drawTotalsSection(contentStream, invoice, 25f, currentY, pageWidth - 50f)

                // Draw footer
                drawFooter(contentStream, shop, pageWidth, pageHeight)

                // Draw QR code for UPI payment
//                drawUpiQrCode(document, page, shop.upiId, 25f, 100f)
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

    private fun drawPageStructure(
        contentStream: PDPageContentStream,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Main border
        drawRectangle(contentStream, 20f, 20f, pageWidth - 40f, pageHeight - 40f, AWTColor.BLACK)

        // Header section line
        drawLine(
            contentStream,
            20f,
            pageHeight - 100f,
            pageWidth - 20f,
            pageHeight - 100f,
            0.5f,
            AWTColor.BLACK
        )
    }

    private fun drawInvoiceHeader(
        contentStream: PDPageContentStream,
        invoice: Invoice,
        shop: Shop,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Title
        drawCenteredText(contentStream, "TAX INVOICE", pageWidth / 2, pageHeight - 40f, 22f, true)

        // Invoice number and date
        drawText(
            contentStream,
            "Invoice No: ${invoice.invoiceNumber}",
            25f,
            pageHeight - 70f,
            12f,
            true
        )
        drawText(
            contentStream,
            "Date: ${dateFormatter.format(Date(invoice.invoiceDate))}",
            pageWidth - 200f,
            pageHeight - 70f,
            12f,
            true
        )

        // Left side - Customer details
        drawText(contentStream, "Customer:", 25f, pageHeight - 120f, 12f, true)
        drawText(contentStream, invoice.customerName, 25f, pageHeight - 135f, 10f, false)
        drawText(
            contentStream,
            "Phone: ${invoice.customerPhone}",
            25f,
            pageHeight - 150f,
            10f,
            false
        )
        drawText(contentStream, invoice.customerAddress, 25f, pageHeight - 165f, 10f, false)

        // Right side - Shop details
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
    }

    private fun drawSectionHeader(
        contentStream: PDPageContentStream,
        title: String,
        x: Float,
        y: Float,
        pageWidth: Float
    ) {
        drawText(contentStream, title, x, y, 14f, true)
        drawLine(contentStream, x, y - 5f, pageWidth - 25f, y - 5f, 0.5f, AWTColor.BLACK)
    }

    private fun drawItemsTable(
        contentStream: PDPageContentStream,
        items: List<InvoiceItem>,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        var currentY = y

        // Table headers
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
        val columnWidths =
            floatArrayOf(0.25f, 0.08f, 0.08f, 0.06f, 0.10f, 0.10f, 0.08f, 0.10f, 0.15f)

        // Calculate actual widths
        val actualWidths = columnWidths.map { it * width }.toFloatArray()

        // Draw headers
        var xPos = x
        for (i in headers.indices) {
            drawText(contentStream, headers[i], xPos + 5f, currentY, 10f, true)
            xPos += actualWidths[i]
        }
        currentY -= 15f

        // Draw header separator line
        drawLine(contentStream, x, currentY + 5f, x + width, currentY + 5f, 0.5f, AWTColor.BLACK)

        // Calculate totals
        var totalNetWeight = 0.0
        var totalPieces = 0
        var totalLabour = 0.0
        var totalStoneValue = 0.0
        var totalAmount = 0.0

        // Draw rows
        for (item in items) {
            xPos = x

            // Calculate values
            val netWeight = item.itemDetails.netWeight
            val pieces = item.quantity
            val labour = item.itemDetails.makingCharges * pieces
            val stoneValue = getStoneValue(item.itemDetails)
            val itemTotal = item.price * item.quantity

            // Update totals
            totalNetWeight += netWeight
            totalPieces += pieces
            totalLabour += labour
            totalStoneValue += stoneValue
            totalAmount += itemTotal

            // Draw cells
            drawText(contentStream, item.itemDetails.displayName, xPos + 5f, currentY, 10f, false)
            xPos += actualWidths[0]

            drawText(
                contentStream,
                formatter.format(item.itemDetails.grossWeight),
                xPos + 5f,
                currentY,
                10f,
                false
            )
            xPos += actualWidths[1]

            drawText(contentStream, formatter.format(netWeight), xPos + 5f, currentY, 10f, false)
            xPos += actualWidths[2]

            drawText(contentStream, pieces.toString(), xPos + 5f, currentY, 10f, false)
            xPos += actualWidths[3]

            drawText(contentStream, formatter.format(labour), xPos + 5f, currentY, 10f, false)
            xPos += actualWidths[4]

            drawText(contentStream, formatter.format(stoneValue), xPos + 5f, currentY, 10f, false)
            xPos += actualWidths[5]

            drawText(contentStream, item.itemDetails.purity, xPos + 5f, currentY, 10f, false)
            xPos += actualWidths[6]

            drawText(
                contentStream,
                formatter.format(item.itemDetails.goldRate),
                xPos + 5f,
                currentY,
                10f,
                false
            )
            xPos += actualWidths[7]

            drawText(contentStream, formatter.format(itemTotal), xPos + 5f, currentY, 10f, false)

            currentY -= 15f

            // Check if we need a new page
            if (currentY < 100f) {
                // Implementation for new page would go here
                // For now, just return the current Y
                return currentY
            }
        }

        // Draw total line
        drawLine(contentStream, x, currentY + 5f, x + width, currentY + 5f, 0.5f, AWTColor.BLACK)

        // Draw totals row
        xPos = x
        drawText(contentStream, "Totals", xPos + 5f, currentY, 10f, true)
        xPos += actualWidths[0]

        // Skip gross weight total
        xPos += actualWidths[1]

        drawText(contentStream, formatter.format(totalNetWeight), xPos + 5f, currentY, 10f, true)
        xPos += actualWidths[2]

        drawText(contentStream, totalPieces.toString(), xPos + 5f, currentY, 10f, true)
        xPos += actualWidths[3]

        drawText(contentStream, formatter.format(totalLabour), xPos + 5f, currentY, 10f, true)
        xPos += actualWidths[4]

        drawText(contentStream, formatter.format(totalStoneValue), xPos + 5f, currentY, 10f, true)
        xPos += actualWidths[5]

        // Skip purity and rate
        xPos += actualWidths[6] + actualWidths[7]

        drawText(contentStream, formatter.format(totalAmount), xPos + 5f, currentY, 10f, true)

        return currentY - 20f
    }

    private fun getStoneValue(item: JewelleryItem): Double {
        // Add logic to extract stone value from item properties
        return item.diamondPrice
    }

    private fun drawPaymentDetails(
        contentStream: PDPageContentStream,
        invoice: Invoice,
        x: Float,
        y: Float,
        width: Float
    ): Float {
        var currentY = y

        drawText(contentStream, "Payment Details:", x, currentY, 12f, true)
        currentY -= 20f

        // Draw headers
        drawText(contentStream, "Payment Method", x + 5f, currentY, 10f, true)
        drawText(contentStream, "Amount", x + width - 100f, currentY, 10f, true)
        currentY -= 15f

        // Draw line
        drawLine(contentStream, x, currentY + 5f, x + width, currentY + 5f, 0.5f, AWTColor.BLACK)

        // Draw payment rows
        for (payment in invoice.payments) {
            drawText(contentStream, payment.method, x + 5f, currentY, 10f, false)
            drawText(
                contentStream,
                formatter.format(payment.amount),
                x + width - 100f,
                currentY,
                10f,
                false
            )
            currentY -= 15f
        }

        return currentY - 10f
    }

    private fun drawTotalsSection(
        contentStream: PDPageContentStream,
        invoice: Invoice,
        x: Float,
        y: Float,
        width: Float
    ) {
        var currentY = y

        // Draw line
        drawLine(contentStream, x, currentY + 5f, x + width, currentY + 5f, 0.5f, AWTColor.BLACK)

        // Calculate values
        val totalAmount = invoice.totalAmount
        val paidAmount = invoice.paidAmount
        val balanceDue = totalAmount - paidAmount

        // Draw total row
        drawText(contentStream, "Total Amount:", x + 5f, currentY, 12f, true)
        drawText(
            contentStream,
            formatter.format(totalAmount),
            x + width - 100f,
            currentY,
            12f,
            true
        )
        currentY -= 20f

        // Draw paid amount row
        drawText(contentStream, "Amount Paid:", x + 5f, currentY, 12f, true)
        drawText(contentStream, formatter.format(paidAmount), x + width - 100f, currentY, 12f, true)
        currentY -= 20f

        // Draw balance row
        drawText(contentStream, "Balance Due:", x + 5f, currentY, 12f, true)
        drawText(contentStream, formatter.format(balanceDue), x + width - 100f, currentY, 12f, true)
        currentY -= 20f

        // Add amount in words
        drawText(
            contentStream,
            "Amount in words: " + numberToWords(balanceDue.toInt()),
            x + 5f,
            currentY,
            10f,
            false
        )
    }

    private fun drawFooter(
        contentStream: PDPageContentStream,
        shop: Shop,
        pageWidth: Float,
        pageHeight: Float
    ) {
        // Draw signature line
        drawText(contentStream, "Authorized Signature", pageWidth - 150f, 50f, 10f, true)

        // Draw thank you message
        drawCenteredText(
            contentStream,
            "Thank you for shopping with us!",
            pageWidth / 2,
            30f,
            10f,
            true
        )
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
                contentStream.beginText()
                contentStream.setFont(boldFont, 10f)
                contentStream.newLineAtOffset(x + 20f, y - 15f)
                contentStream.showText("Scan to Pay")
                contentStream.endText()
            }
        } catch (e: Exception) {
            Log.e("InvoicePdfGenerator", "Error generating QR code", e)
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
        contentStream.moveTo(x1, y1)
        contentStream.lineTo(x2, y2)
        contentStream.stroke()
    }

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
        contentStream.addRect(x, y, width, height)
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
            result += convertLessThanThousand(num / 1000) + " Hazaar "
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