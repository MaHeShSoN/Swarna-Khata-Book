package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.CustomerSalesData
import com.jewelrypos.swarnakhatabook.DataClasses.GstReportItem
import com.jewelrypos.swarnakhatabook.DataClasses.InventoryValueItem
import com.jewelrypos.swarnakhatabook.DataClasses.ItemSalesData
import com.jewelrypos.swarnakhatabook.DataClasses.LowStockItem
import com.jewelrypos.swarnakhatabook.DataClasses.SalesReportData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PDFExportUtility(private val context: Context) {

    private val pageWidth = 612 // A4 Width in points (8.5 x 72)
    private val pageHeight = 792 // A4 Height in points (11 x 72)
    private val margin = 50f
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val percentFormatter = DecimalFormat("0.0'%'")
    private val TAG = "PDFExportUtility"

    private val titlePaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        textSize = 24f
        isFakeBoldText = true
    }

    private val headerPaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.LEFT
        textSize = 18f
        isFakeBoldText = true
    }

    private val subHeaderPaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.LEFT
        textSize = 14f
        isFakeBoldText = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.LEFT
        textSize = 12f
    }

    private val amountPaint = Paint().apply {
        color = Color.BLACK
        textAlign = Paint.Align.RIGHT
        textSize = 12f
    }

    private val currencyFormatter = DecimalFormat("#,##,##0.00")
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    suspend fun exportSalesReport(
        startDate: Date,
        endDate: Date,
        reportData: SalesReportData,
        topItems: List<ItemSalesData>,
        topCustomers: List<CustomerSalesData>
    ): Uri? = withContext(Dispatchers.IO) { // Ensure file operations are off the main thread

        val document = PdfDocument()
        // Define page dimensions (e.g., A4 size in points: 595 x 842)
        val pageHeight = 842
        val pageWidth = 595
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // --- Paint objects for drawing ---
        val titlePaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val headerPaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val textPaint = Paint().apply {
            textSize = 11f
            color = Color.DKGRAY
        }
        val boldTextPaint = Paint().apply {
            textSize = 11f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val smallTextPaint = Paint().apply {
            textSize = 9f
            color = Color.GRAY
        }

        // --- Start Drawing ---
        var yPosition = 40f // Starting Y position (top margin)
        val leftMargin = 40f
        val rightMargin = pageWidth - 40f
        val lineSpacing = 15f // Spacing between text lines
        val sectionSpacing = 25f // Spacing between sections

        try {
            // 1. Report Title and Date Range
            canvas.drawText("Sales Report", leftMargin, yPosition, titlePaint)
            yPosition += lineSpacing * 1.5f
            val dateRangeStr = "${dateFormat.format(startDate)} to ${dateFormat.format(endDate)}"
            canvas.drawText("Period: $dateRangeStr", leftMargin, yPosition, textPaint)
            yPosition += sectionSpacing

            // 2. Sales Summary Section
            canvas.drawText("Sales Summary", leftMargin, yPosition, headerPaint)
            yPosition += lineSpacing * 1.5f
            // Draw summary data in two columns
            val col1X = leftMargin
            val col2X = leftMargin + (pageWidth / 2f) - 20f // Adjust as needed

            canvas.drawText("Total Sales:", col1X, yPosition, textPaint)
            canvas.drawText(currencyFormatter.format(reportData.totalSales), col1X + 100, yPosition, boldTextPaint)
            canvas.drawText("Total Paid:", col2X, yPosition, textPaint)
            canvas.drawText(currencyFormatter.format(reportData.paidAmount), col2X + 100, yPosition, boldTextPaint)
            yPosition += lineSpacing

            canvas.drawText("Total Unpaid:", col1X, yPosition, textPaint)
            canvas.drawText(currencyFormatter.format(reportData.unpaidAmount), col1X + 100, yPosition, boldTextPaint)
            canvas.drawText("Collection Rate:", col2X, yPosition, textPaint)
            canvas.drawText(percentFormatter.format(reportData.collectionRate / 100.0), col2X + 100, yPosition, boldTextPaint)
            yPosition += lineSpacing

            canvas.drawText("Invoice Count:", col1X, yPosition, textPaint)
            canvas.drawText(reportData.invoiceCount.toString(), col1X + 100, yPosition, boldTextPaint)
            yPosition += sectionSpacing


            // 3. Top Selling Items Section
            canvas.drawText("Top Selling Items", leftMargin, yPosition, headerPaint)
            yPosition += lineSpacing * 1.5f
            if (topItems.isEmpty()) {
                canvas.drawText("No top selling item data available.", leftMargin, yPosition, textPaint)
                yPosition += lineSpacing
            } else {
                // Draw table header (optional)
                // ...
                topItems.forEachIndexed { index, item ->
                    if (yPosition > pageHeight - 60) { // Check for page break
                        document.finishPage(page)
                        // Start new page if needed (omitted for brevity)
                        // page = document.startPage(pageInfo)
                        // canvas = page.canvas
                        // yPosition = 40f // Reset Y
                        // Draw headers again if needed
                    }
                    val itemText = "${index + 1}. ${item.itemName} (Qty: ${item.quantitySold}) - ${currencyFormatter.format(item.totalRevenue)}"
                    canvas.drawText(itemText, leftMargin, yPosition, textPaint)
                    yPosition += lineSpacing
                }
            }
            yPosition += sectionSpacing


            // 4. Top Customers Section
            canvas.drawText("Top Customers", leftMargin, yPosition, headerPaint)
            yPosition += lineSpacing * 1.5f
            if (topCustomers.isEmpty()) {
                canvas.drawText("No top customer data available.", leftMargin, yPosition, textPaint)
                yPosition += lineSpacing
            } else {
                topCustomers.forEachIndexed { index, customer ->
                    if (yPosition > pageHeight - 60) { // Check for page break
                        // Handle page break as above
                    }
                    val customerText = "${index + 1}. ${customer.customerName} (Invoices: ${customer.invoiceCount}) - ${currencyFormatter.format(customer.totalPurchaseValue)}"
                    canvas.drawText(customerText, leftMargin, yPosition, textPaint)
                    yPosition += lineSpacing
                }
            }
            yPosition += sectionSpacing

            // TODO: Add other sections if needed (e.g., Sales by Category/Date as tables)
            // Drawing charts directly onto PDF canvas is complex. It's usually easier
            // to represent the chart data in tabular format in the PDF.

            // Finish the page
            document.finishPage(page)

            // --- Save the document ---
            val fileName = "SalesReport_${dateFormat.format(Date())}.pdf"
            // Use app-specific external storage for broader access if needed
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Reports")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, fileName)

            try {
                FileOutputStream(file).use { fos ->
                    document.writeTo(fos)
                }
                Log.d(TAG, "PDF saved successfully: ${file.absolutePath}")

                // Return the content URI using FileProvider
                return@withContext FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider", // Make sure this matches your provider authority in AndroidManifest.xml
                    file
                )
            } catch (e: IOException) {
                Log.e(TAG, "Error writing PDF to file", e)
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating Sales Report PDF content", e)
            // Make sure page is finished even on error before closing document
            if (document.pages.size > 0 && !page.canvas.isOpaque) { // Check if page was started
                document.finishPage(page)
            }
            return@withContext null
        } finally {
            // Close the document
            document.close()
        }
    }

    fun exportInventoryReport(inventoryItems: List<InventoryValueItem>, totalValue: Double): Boolean {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            var canvas = page.canvas

            val currentDate = dateFormatter.format(Date())
            var yPosition = drawReportHeader(canvas, "Inventory Valuation Report", currentDate, "")

            // Draw summary
            canvas.drawText("Total Inventory Value: ₹${currencyFormatter.format(totalValue)}", margin, yPosition, subHeaderPaint)
            yPosition += 30

            // Draw inventory items table header
            val tableHeaderY = yPosition
            canvas.drawText("Item", margin, tableHeaderY, subHeaderPaint)
            canvas.drawText("Type", margin + 150, tableHeaderY, subHeaderPaint)
            canvas.drawText("Stock", margin + 220, tableHeaderY, subHeaderPaint)
            canvas.drawText("Value", pageWidth - margin, tableHeaderY, amountPaint)
            yPosition += 20

            // Draw horizontal line
            canvas.drawLine(margin, yPosition - 10, pageWidth - margin, yPosition - 10, textPaint)

            // Draw inventory items
            for (item in inventoryItems) {
                // Check if we need a new page
                if (yPosition > pageHeight - 100) {
                    document.finishPage(page)
                    val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()
                    val newPage = document.startPage(newPageInfo)
                    canvas = newPage.canvas
                    yPosition = margin + 50

                    // Redraw table header on new page
                    canvas.drawText("Item", margin, yPosition, subHeaderPaint)
                    canvas.drawText("Type", margin + 150, yPosition, subHeaderPaint)
                    canvas.drawText("Stock", margin + 220, yPosition, subHeaderPaint)
                    canvas.drawText("Value", pageWidth - margin, yPosition, amountPaint)
                    yPosition += 20

                    // Draw horizontal line
                    canvas.drawLine(margin, yPosition - 10, pageWidth - margin, yPosition - 10, textPaint)
                }

                canvas.drawText(item.name, margin, yPosition, textPaint)
                canvas.drawText(item.itemType, margin + 150, yPosition, textPaint)
                canvas.drawText("${item.stock} ${item.stockUnit}", margin + 220, yPosition, textPaint)
                canvas.drawText("₹${currencyFormatter.format(item.totalStockValue)}", pageWidth - margin, yPosition, amountPaint)
                yPosition += 20
            }

            document.finishPage(page)

            // Save and share the PDF
            return savePdfAndShare(document, "InventoryValuation_${currentDate}.pdf")

        } catch (e: Exception) {
            Log.e("PDFExportUtility", "Error exporting inventory report", e)
            return false
        }
    }

    // --- Optional: Helper method to share/open the generated PDF ---
    fun sharePdf(fileUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant permission to receiving app
            }
            val chooser = Intent.createChooser(intent, "Share Report PDF")
            if (chooser.resolveActivity(context.packageManager) != null) {
                context.startActivity(chooser)
            } else {
                Toast.makeText(context, "No app found to share PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating share intent", e)
            Toast.makeText(context, "Error sharing PDF", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPdf(fileUri: Uri) = try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant permission to viewing app
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "No app found to open PDF", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error creating open intent", e)
        Toast.makeText(context, "Error opening PDF", Toast.LENGTH_SHORT).show()
    }

    fun exportGstReport(startDate: String, endDate: String, gstItems: List<GstReportItem>): Boolean {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var yPosition = drawReportHeader(canvas, "GST Report", startDate, endDate)

            // Calculate totals
            val totalTaxableAmount = gstItems.sumOf { it.taxableAmount }
            val totalCgst = gstItems.sumOf { it.cgst }
            val totalSgst = gstItems.sumOf { it.sgst }
            val totalTax = gstItems.sumOf { it.totalTax }

            // Draw summary
            canvas.drawText("GST Summary", margin, yPosition, headerPaint)
            yPosition += 30

            val summaryData = listOf(
                Pair("Total Taxable Amount", "₹${currencyFormatter.format(totalTaxableAmount)}"),
                Pair("Total CGST", "₹${currencyFormatter.format(totalCgst)}"),
                Pair("Total SGST", "₹${currencyFormatter.format(totalSgst)}"),
                Pair("Total Tax", "₹${currencyFormatter.format(totalTax)}")
            )

            yPosition = drawTableWithTwoColumns(canvas, summaryData, yPosition)
            yPosition += 30

            // Draw GST details table header
            canvas.drawText("GST Details by Rate", margin, yPosition, headerPaint)
            yPosition += 30

            val tableHeaderY = yPosition
            canvas.drawText("Rate", margin, tableHeaderY, subHeaderPaint)
            canvas.drawText("Taxable Amount", margin + 60, tableHeaderY, subHeaderPaint)
            canvas.drawText("CGST", margin + 200, tableHeaderY, subHeaderPaint)
            canvas.drawText("SGST", margin + 260, tableHeaderY, subHeaderPaint)
            canvas.drawText("IGST", margin + 320, tableHeaderY, subHeaderPaint)
            canvas.drawText("Total Tax", pageWidth - margin, tableHeaderY, amountPaint)
            yPosition += 20

            // Draw horizontal line
            canvas.drawLine(margin, yPosition - 10, pageWidth - margin, yPosition - 10, textPaint)

            // Draw GST items
            for (item in gstItems) {
                canvas.drawText("${item.taxRate}%", margin, yPosition, textPaint)
                canvas.drawText("₹${currencyFormatter.format(item.taxableAmount)}", margin + 60, yPosition, textPaint)
                canvas.drawText("₹${currencyFormatter.format(item.cgst)}", margin + 200, yPosition, textPaint)
                canvas.drawText("₹${currencyFormatter.format(item.sgst)}", margin + 260, yPosition, textPaint)
                canvas.drawText("₹${currencyFormatter.format(item.totalTax)}", pageWidth - margin, yPosition, amountPaint)
                yPosition += 20
            }

            document.finishPage(page)

            // Save and share the PDF
            return savePdfAndShare(document, "GSTReport_${startDate}_to_${endDate}.pdf")

        } catch (e: Exception) {
            Log.e("PDFExportUtility", "Error exporting GST report", e)
            return false
        }
    }

    fun exportLowStockReport(lowStockItems: List<LowStockItem>): Boolean {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val currentDate = dateFormatter.format(Date())
            var yPosition = drawReportHeader(canvas, "Low Stock Report", currentDate, "")

            // Draw summary
            canvas.drawText("Items with Low Stock: ${lowStockItems.size}", margin, yPosition, subHeaderPaint)
            yPosition += 30

            // Draw table header
            val tableHeaderY = yPosition
            canvas.drawText("Item", margin, tableHeaderY, subHeaderPaint)
            canvas.drawText("Code", margin + 150, tableHeaderY, subHeaderPaint)
            canvas.drawText("Type", margin + 220, tableHeaderY, subHeaderPaint)
            canvas.drawText("Current Stock", margin + 300, tableHeaderY, subHeaderPaint)
            canvas.drawText("Reorder Level", pageWidth - margin, tableHeaderY, amountPaint)
            yPosition += 20

            // Draw horizontal line
            canvas.drawLine(margin, yPosition - 10, pageWidth - margin, yPosition - 10, textPaint)

            // Draw items
            for (item in lowStockItems) {
                canvas.drawText(item.name, margin, yPosition, textPaint)
                canvas.drawText(item.itemType, margin + 220, yPosition, textPaint)
                canvas.drawText("${item.currentStock} ${item.stockUnit}", margin + 300, yPosition, textPaint)
                canvas.drawText("${item.reorderLevel}", pageWidth - margin, yPosition, amountPaint)
                yPosition += 20
            }

            document.finishPage(page)

            // Save and share the PDF
            return savePdfAndShare(document, "LowStockReport_${currentDate}.pdf")

        } catch (e: Exception) {
            Log.e("PDFExportUtility", "Error exporting low stock report", e)
            return false
        }
    }

    fun exportCustomerStatement(
        customer: Customer,
        startDate: String,
        endDate: String,
        openingBalance: Double,
        closingBalance: Double,
        transactions: List<Any>
    ): Boolean {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var yPosition = drawReportHeader(canvas, "Customer Account Statement", startDate, endDate)

            // Draw customer details
            canvas.drawText("Customer Details", margin, yPosition, headerPaint)
            yPosition += 30

            val customerDetails = listOf(
                Pair("Name", "${customer.firstName} ${customer.lastName}"),
                Pair("Phone", customer.phoneNumber),
                Pair("Address", "${customer.streetAddress}, ${customer.city}, ${customer.state} ${customer.postalCode}"),
                Pair("Customer Type", customer.customerType),
                Pair("Balance Type", customer.balanceType)
            )

            yPosition = drawTableWithTwoColumns(canvas, customerDetails, yPosition)
            yPosition += 30

            // Draw balance summary
            canvas.drawText("Balance Summary", margin, yPosition, headerPaint)
            yPosition += 30

            val balanceSummary = listOf(
                Pair("Opening Balance", "₹${currencyFormatter.format(openingBalance)}"),
                Pair("Closing Balance", "₹${currencyFormatter.format(closingBalance)}")
            )

            yPosition = drawTableWithTwoColumns(canvas, balanceSummary, yPosition)
            yPosition += 30

            // Draw transactions table header
            canvas.drawText("Transaction History", margin, yPosition, headerPaint)
            yPosition += 30

            val tableHeaderY = yPosition
            canvas.drawText("Date", margin, tableHeaderY, subHeaderPaint)
            canvas.drawText("Description", margin + 100, tableHeaderY, subHeaderPaint)
            canvas.drawText("Debit", margin + 250, tableHeaderY, subHeaderPaint)
            canvas.drawText("Credit", margin + 330, tableHeaderY, subHeaderPaint)
            canvas.drawText("Balance", pageWidth - margin, tableHeaderY, amountPaint)
            yPosition += 20

            // Draw horizontal line
            canvas.drawLine(margin, yPosition - 10, pageWidth - margin, yPosition - 10, textPaint)

            // Start with opening balance row
            canvas.drawText(startDate, margin, yPosition, textPaint)
            canvas.drawText("Opening Balance", margin + 100, yPosition, textPaint)
            canvas.drawText("", margin + 250, yPosition, textPaint)
            canvas.drawText("", margin + 330, yPosition, textPaint)
            canvas.drawText("₹${currencyFormatter.format(openingBalance)}", pageWidth - margin, yPosition, amountPaint)
            yPosition += 20

            // Add transactions (mocked for now)
            var runningBalance = openingBalance

            // In a real implementation, you would loop through transactions here
            // For now, we'll just add a few sample transactions

            document.finishPage(page)

            // Save and share the PDF
            return savePdfAndShare(document, "CustomerStatement_${customer.firstName}_${customer.lastName}_${startDate}_to_${endDate}.pdf")

        } catch (e: Exception) {
            Log.e("PDFExportUtility", "Error exporting customer statement", e)
            return false
        }
    }

    private fun drawReportHeader(canvas: Canvas, title: String, startDate: String, endDate: String): Float {
        var yPosition = margin + 50

        // Draw title
        canvas.drawText(title, pageWidth / 2f, yPosition, titlePaint)
        yPosition += 30

        // Draw date range if provided
        if (startDate.isNotEmpty()) {
            val dateRangeText = if (endDate.isNotEmpty()) {
                "Period: $startDate to $endDate"
            } else {
                "Date: $startDate"
            }
            canvas.drawText(dateRangeText, pageWidth / 2f, yPosition, subHeaderPaint)
            yPosition += 30
        }

        // Draw generated date
        val currentDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Generated on: $currentDate", pageWidth / 2f, yPosition, textPaint)
        yPosition += 50

        return yPosition
    }

    private fun drawTableWithTwoColumns(canvas: Canvas, data: List<Pair<String, String>>, startY: Float): Float {
        var yPosition = startY

        data.forEach { (label, value) ->
            canvas.drawText(label, margin, yPosition, textPaint)
            canvas.drawText(value, pageWidth - margin, yPosition, amountPaint)
            yPosition += 20
        }

        return yPosition
    }

    private fun savePdfAndShare(document: PdfDocument, fileName: String): Boolean {
        try {
            // Get the directory for storing PDFs
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SwarnakhataBook/Reports")

            // Create directory if it doesn't exist
            if (!directory.exists()) {
                directory.mkdirs()
            }

            // Create the file
            val file = File(directory, fileName)

            // Write to file
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }

            document.close()

            // Share the file
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Check if there's an app to handle this intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Open with"))
            } else {
                // No PDF viewer app installed
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
            }

            return true
        } catch (e: Exception) {
            Log.e("PDFExportUtility", "Error saving or sharing PDF", e)
            return false
        }
    }
}