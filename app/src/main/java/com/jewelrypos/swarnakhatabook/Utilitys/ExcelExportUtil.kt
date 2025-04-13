package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import org.apache.poi.hssf.usermodel.HSSFCellStyle
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellRangeAddress
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility class to handle Excel file generation and export
 */
class ExcelExportUtil {
    companion object {
        private const val TAG = "ExcelExportUtil"

        /**
         * Exports a list of invoices to an Excel file
         *
         * @param context The context to use for file operations
         * @param invoices The list of invoices to export
         * @param filterDescription A description of the applied filters for the title
         * @return The Uri of the generated Excel file or null if export failed
         */
        fun exportInvoicesToExcel(
            context: Context,
            invoices: List<Invoice>,
            filterDescription: String
        ): Uri? {
            try {
                // Create a new workbook
                val workbook = HSSFWorkbook()

                // Create styles for the workbook
                val styles = createStyles(workbook)

                // Create a sheet for the invoices
                val sheet = workbook.createSheet("Invoices")

                // Create title row
                val titleRow = sheet.createRow(0)
                val titleCell = titleRow.createCell(0)
                titleCell.setCellValue("Sales Report - $filterDescription")
                titleCell.cellStyle = (styles["title"]
                    ?: workbook.createCellStyle()) as HSSFCellStyle // Provide a default if null
                sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 5))

                // Create header row
                val headerRow = sheet.createRow(1)
                val headers = arrayOf(
                    "Invoice No.",
                    "Date",
                    "Customer",
                    "Total Items",
                    "Paid Amount",
                    "Total Amount"
                )
                headers.forEachIndexed { index, header ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(header)
                    cell.cellStyle = (styles["header"]
                        ?: workbook.createCellStyle()) as HSSFCellStyle // Provide a default if null
                }

                // Add data rows
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val currencyFormat = java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))

                invoices.forEachIndexed { index, invoice ->
                    val row = sheet.createRow(index + 2)

                    // Invoice number
                    createCell(
                        row,
                        0,
                        invoice.invoiceNumber,
                        styles["cell"] ?: workbook.createCellStyle()
                    )

                    // Invoice date
                    val dateString = dateFormat.format(Date(invoice.invoiceDate))
                    createCell(row, 1, dateString, styles["cell"] ?: workbook.createCellStyle())

                    // Customer name
                    createCell(
                        row,
                        2,
                        invoice.customerName,
                        styles["cell"] ?: workbook.createCellStyle()
                    )

                    // Total items (sum of quantities)
                    val totalItems = invoice.items.sumOf { it.quantity }
                    createCell(
                        row,
                        3,
                        totalItems.toString(),
                        styles["cell_center"] ?: workbook.createCellStyle()
                    )

                    // Paid amount
                    createCell(
                        row,
                        4,
                        currencyFormat.format(invoice.paidAmount),
                        styles["cell_amount"] ?: workbook.createCellStyle()
                    )

                    // Total amount
                    createCell(
                        row,
                        5,
                        currencyFormat.format(invoice.totalAmount),
                        styles["cell_amount"] ?: workbook.createCellStyle()
                    )
                }

                // Auto-size columns
                for (i in headers.indices) {
                    sheet.autoSizeColumn(i)
                }

                // Create a summary row
                val summaryRowIndex = invoices.size + 2
                val summaryRow = sheet.createRow(summaryRowIndex)

                // Create summary cells
                createCell(summaryRow, 0, "Total:", styles["summary"] ?: workbook.createCellStyle())
                sheet.addMergedRegion(CellRangeAddress(summaryRowIndex, summaryRowIndex, 0, 3))

                // Calculate totals
                val totalPaid = invoices.sumOf { it.paidAmount }
                val totalAmount = invoices.sumOf { it.totalAmount }

                // Add total cells
                createCell(
                    summaryRow,
                    4,
                    currencyFormat.format(totalPaid),
                    styles["summary_amount"] ?: workbook.createCellStyle()
                )
                createCell(
                    summaryRow,
                    5,
                    currencyFormat.format(totalAmount),
                    styles["summary_amount"] ?: workbook.createCellStyle()
                )

                // Write the workbook to a file
                val fileName = "Sales_Report_${
                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                }.xls"

                // Get the downloads directory
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)

                // Write to file
                FileOutputStream(file).use { fileOut ->
                    workbook.write(fileOut)
                }
                workbook.close()

                // Create a content URI for the file using FileProvider
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting invoices to Excel", e)
                return null
            }
        }

        /**
         * Helper method to create a cell with a value and style
         */
        private fun createCell(row: Row, columnIndex: Int, value: String, style: CellStyle) {
            val cell = row.createCell(columnIndex)
            cell.setCellValue(value)
            cell.cellStyle = style
        }

        /**
         * Creates styles for the workbook
         */
        private fun createStyles(workbook: Workbook): Map<String, CellStyle> {
            val styles = mutableMapOf<String, CellStyle>()

            // Title style
            val titleStyle = workbook.createCellStyle()
            val titleFont = workbook.createFont()
            titleFont.fontHeightInPoints = 16
            titleFont.bold = true
            titleFont.color = IndexedColors.DARK_BLUE.index
            titleStyle.setFont(titleFont)
            titleStyle.alignment = HorizontalAlignment.CENTER
            titleStyle.verticalAlignment = VerticalAlignment.CENTER
            styles["title"] = titleStyle

            // Header style
            val headerStyle = workbook.createCellStyle()
            val headerFont = workbook.createFont()
            headerFont.fontHeightInPoints = 12
            headerFont.bold = true
            headerFont.color = IndexedColors.WHITE.index
            headerStyle.setFont(headerFont)
            headerStyle.alignment = HorizontalAlignment.CENTER
            headerStyle.verticalAlignment = VerticalAlignment.CENTER
            headerStyle.fillForegroundColor = IndexedColors.DARK_BLUE.index
            headerStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
            headerStyle.borderBottom = BorderStyle.THIN
            headerStyle.borderLeft = BorderStyle.THIN
            headerStyle.borderRight = BorderStyle.THIN
            headerStyle.borderTop = BorderStyle.THIN
            styles["header"] = headerStyle

            // Cell style
            val cellStyle = workbook.createCellStyle()
            cellStyle.borderBottom = BorderStyle.THIN
            cellStyle.borderLeft = BorderStyle.THIN
            cellStyle.borderRight = BorderStyle.THIN
            cellStyle.borderTop = BorderStyle.THIN
            styles["cell"] = cellStyle

            // Cell centered style
            val cellCenterStyle = workbook.createCellStyle()
            cellCenterStyle.cloneStyleFrom(cellStyle)
            cellCenterStyle.alignment = HorizontalAlignment.CENTER
            styles["cell_center"] = cellCenterStyle

            // Cell amount style
            val cellAmountStyle = workbook.createCellStyle()
            cellAmountStyle.cloneStyleFrom(cellStyle)
            cellAmountStyle.alignment = HorizontalAlignment.RIGHT
            styles["cell_amount"] = cellAmountStyle

            // Summary style
            val summaryStyle = workbook.createCellStyle()
            val summaryFont = workbook.createFont()
            summaryFont.bold = true
            summaryStyle.setFont(summaryFont)
            summaryStyle.alignment = HorizontalAlignment.RIGHT
            summaryStyle.verticalAlignment = VerticalAlignment.CENTER
            summaryStyle.fillForegroundColor = IndexedColors.LIGHT_YELLOW.index
            summaryStyle.fillPattern = FillPatternType.SOLID_FOREGROUND
            summaryStyle.borderBottom = BorderStyle.THIN
            summaryStyle.borderLeft = BorderStyle.THIN
            summaryStyle.borderRight = BorderStyle.THIN
            summaryStyle.borderTop = BorderStyle.THIN
            styles["summary"] = summaryStyle

            // Summary amount style
            val summaryAmountStyle = workbook.createCellStyle()
            summaryAmountStyle.cloneStyleFrom(summaryStyle)
            summaryAmountStyle.alignment = HorizontalAlignment.RIGHT
            styles["summary_amount"] = summaryAmountStyle

            return styles
        }
    }
}