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
import androidx.core.content.FileProvider
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.GstReportItem
import com.jewelrypos.swarnakhatabook.DataClasses.InventoryValueItem
import com.jewelrypos.swarnakhatabook.DataClasses.LowStockItem
import com.jewelrypos.swarnakhatabook.DataClasses.SalesReportData
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PDFExportUtility(private val context: Context) {

    private val pageWidth = 612 // A4 Width in points (8.5 x 72)
    private val pageHeight = 792 // A4 Height in points (11 x 72)
    private val margin = 50f

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

    fun exportSalesReport(startDate: String, endDate: String, salesData: SalesReportData): Boolean {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var yPosition = drawReportHeader(canvas, "Sales Report", startDate, endDate)

            // Draw summary section
            canvas.drawText("Sales Summary", margin, yPosition, headerPaint)
            yPosition += 30

            // Draw summary table
            val summaryData = listOf(
                Pair("Total Sales", "₹${currencyFormatter.format(salesData.totalSales)}"),
                Pair("Total Paid", "₹${currencyFormatter.format(salesData.totalPaid)}"),
                Pair("Total Unpaid", "₹${currencyFormatter.format(salesData.totalSales - salesData.totalPaid)}"),
                Pair("Collection Rate", "${DecimalFormat("#0.00").format(if (salesData.totalSales > 0) (salesData.totalPaid / salesData.totalSales) * 100 else 0.0)}%"),
                Pair("Total Invoices", salesData.invoiceCount.toString())
            )

            yPosition = drawTableWithTwoColumns(canvas, summaryData, yPosition)
            yPosition += 30

            // Draw category breakdown
            canvas.drawText("Sales by Category", margin, yPosition, headerPaint)
            yPosition += 30

            val categoryData = salesData.salesByCategory.map {
                Pair(it.category, "₹${currencyFormatter.format(it.amount)}")
            }

            yPosition = drawTableWithTwoColumns(canvas, categoryData, yPosition)
            yPosition += 30

            // Draw customer type breakdown
            canvas.drawText("Sales by Customer Type", margin, yPosition, headerPaint)
            yPosition += 30

            val customerTypeData = salesData.salesByCustomerType.map {
                Pair(it.customerType, "₹${currencyFormatter.format(it.amount)}")
            }

            yPosition = drawTableWithTwoColumns(canvas, customerTypeData, yPosition)

            document.finishPage(page)

            // Create a second page for sales trend
            val page2Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page2 = document.startPage(page2Info)
            val canvas2 = page2.canvas

            var y2Position = margin + 50

            // Title for second page
            canvas2.drawText("Sales Report (continued)", pageWidth / 2f, y2Position, titlePaint)
            y2Position += 50

            // Draw sales trend
            canvas2.drawText("Sales Trend", margin, y2Position, headerPaint)
            y2Position += 30

            val dateData = salesData.salesByDate.map {
                Pair(dateFormatter.format(it.date), "₹${currencyFormatter.format(it.amount)}")
            }

            drawTableWithTwoColumns(canvas2, dateData, y2Position)

            document.finishPage(page2)

            // Save and share the PDF
            return savePdfAndShare(document, "SalesReport_${startDate}_to_${endDate}.pdf")

        } catch (e: Exception) {
            Log.e("PDFExportUtility", "Error exporting sales report", e)
            return false
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
                canvas.drawText(item.code, margin + 150, yPosition, textPaint)
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