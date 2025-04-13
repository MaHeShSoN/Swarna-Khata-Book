package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enhanced utility class for exporting sales data to professional CSV reports
 */
class EnhancedCsvExportUtil {
    companion object {
        private const val TAG = "EnhancedCsvExportUtil"

        /**
         * Exports a comprehensive sales report with detailed metrics and analysis
         */
        fun exportSalesReport(
            context: Context,
            invoices: List<Invoice>,
            filterDescription: String
        ): Uri? {
            try {
                // Generate a timestamped filename
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                val fileName = "Sales_Report_$timestamp.csv"

                // Get app's private files directory
                val filesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir

                // Ensure directory exists
                if (!filesDir.exists()) {
                    filesDir.mkdirs()
                }

                // Create file
                val file = File(filesDir, fileName)
                Log.d(TAG, "Writing enhanced CSV to: ${file.absolutePath}")

                // Write CSV data to file
                FileOutputStream(file).use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writeEnhancedReport(writer, invoices, filterDescription)
                    }
                }

                // Create and return content URI via FileProvider
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting enhanced CSV: ${e.message}", e)
                return null
            }
        }

        /**
         * Writes a professional, comprehensive sales report with multiple data sections
         */
        private fun writeEnhancedReport(
            writer: OutputStreamWriter,
            invoices: List<Invoice>,
            filterDescription: String
        ) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val timestamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                .format(Date())

            try {
                // Add UTF-8 BOM for Excel compatibility
                writer.write("\uFEFF")

                // ===== REPORT HEADER SECTION =====
                writer.write("SWARNA KHATA BOOK - SALES REPORT\r\n")
                writer.write("Filter: $filterDescription\r\n")
                writer.write("Generated: $timestamp\r\n\r\n")

                // ===== SUMMARY METRICS SECTION =====
                writer.write("SUMMARY METRICS\r\n")

                // Calculate summary metrics
                val totalInvoices = invoices.size
                val totalSalesAmount = invoices.sumOf { it.totalAmount }
                val totalPaidAmount = invoices.sumOf { it.paidAmount }
                val totalPendingAmount = totalSalesAmount - totalPaidAmount
                val avgOrderValue = if (totalInvoices > 0) totalSalesAmount / totalInvoices else 0.0
                val paidInvoices = invoices.count { it.totalAmount <= it.paidAmount }
                val partialInvoices = invoices.count { it.paidAmount > 0 && it.paidAmount < it.totalAmount }
                val unpaidInvoices = invoices.count { it.paidAmount <= 0 }
                val paymentRate = if (totalInvoices > 0) (paidInvoices.toDouble() / totalInvoices) * 100 else 0.0

                // Write summary metrics
                writer.write("Total Invoices,${totalInvoices}\r\n")
                writer.write("Total Sales Amount,${totalSalesAmount}\r\n")
                writer.write("Total Paid Amount,${totalPaidAmount}\r\n")
                writer.write("Total Pending Amount,${totalPendingAmount}\r\n")
                writer.write("Average Order Value,${String.format("%.2f", avgOrderValue)}\r\n")
                writer.write("Fully Paid Invoices,${paidInvoices}\r\n")
                writer.write("Partially Paid Invoices,${partialInvoices}\r\n")
                writer.write("Unpaid Invoices,${unpaidInvoices}\r\n")
                writer.write("Payment Completion Rate,${String.format("%.1f", paymentRate)}%\r\n\r\n")

                // ===== DETAILED INVOICE LIST SECTION =====
                writer.write("DETAILED INVOICE LIST\r\n")

                // Column headers
                writer.write("Invoice No.,Date,Customer,Customer Phone,Total Items,Payment Status,Paid Amount,Total Amount,Balance,Items Details\r\n")

                // Data rows
                for (invoice in invoices) {
                    val date = dateFormat.format(Date(invoice.invoiceDate))
                    val totalItems = invoice.items.sumOf { it.quantity }
                    val balance = invoice.totalAmount - invoice.paidAmount

                    // Determine payment status
                    val paymentStatus = when {
                        balance <= 0 -> "Paid"
                        invoice.paidAmount > 0 -> "Partial"
                        else -> "Unpaid"
                    }

                    // Create a summary of items (first 3 with count)
                    val itemsList = invoice.items.take(3).joinToString(", ") {
                        "${it.quantity}x ${it.itemDetails.displayName}"
                    }
                    val itemsSummary = if (invoice.items.size > 3) {
                        "$itemsList... (+ ${invoice.items.size - 3} more)"
                    } else {
                        itemsList
                    }

                    // Escape quotes in strings
                    val escapedInvoiceNumber = invoice.invoiceNumber.replace("\"", "\"\"")
                    val escapedCustomerName = invoice.customerName.replace("\"", "\"\"")
                    val escapedItemsSummary = itemsSummary.replace("\"", "\"\"")

                    // Write the row
                    writer.write("\"$escapedInvoiceNumber\",")
                    writer.write("\"$date\",")
                    writer.write("\"$escapedCustomerName\",")
                    writer.write("\"${invoice.customerPhone}\",")
                    writer.write("$totalItems,")
                    writer.write("\"$paymentStatus\",")
                    writer.write("${invoice.paidAmount},")
                    writer.write("${invoice.totalAmount},")
                    writer.write("$balance,")
                    writer.write("\"$escapedItemsSummary\"\r\n")
                }

                // Add empty line before next section
                writer.write("\r\n")

                // ===== PAYMENT ANALYSIS SECTION =====
                if (invoices.isNotEmpty()) {
                    writer.write("PAYMENT ANALYSIS\r\n")

                    // Calculate payment method distribution
                    val paymentMethods = mutableMapOf<String, Double>()
                    invoices.flatMap { it.payments }.forEach { payment ->
                        paymentMethods[payment.method] =
                            (paymentMethods[payment.method] ?: 0.0) + payment.amount
                    }

                    // Write payment method distribution
                    writer.write("Payment Method,Amount,Percentage\r\n")
                    paymentMethods.forEach { (method, amount) ->
                        val percentage = if (totalPaidAmount > 0)
                            (amount / totalPaidAmount) * 100 else 0.0
                        writer.write("\"$method\",$amount,${String.format("%.1f", percentage)}%\r\n")
                    }

                    // Add empty line
                    writer.write("\r\n")
                }

                // ===== CUSTOMER ANALYSIS =====
                if (invoices.isNotEmpty()) {
                    writer.write("TOP CUSTOMERS\r\n")

                    // Group sales by customer
                    val customerSales = mutableMapOf<String, Double>()
                    invoices.forEach { invoice ->
                        customerSales[invoice.customerName] =
                            (customerSales[invoice.customerName] ?: 0.0) + invoice.totalAmount
                    }

                    // Sort and get top 5 customers
                    val topCustomers = customerSales.entries
                        .sortedByDescending { it.value }
                        .take(5)

                    // Write top customers
                    writer.write("Customer,Total Sales,Percentage of Total\r\n")
                    topCustomers.forEach { (customer, amount) ->
                        val percentage = if (totalSalesAmount > 0)
                            (amount / totalSalesAmount) * 100 else 0.0
                        writer.write("\"$customer\",$amount,${String.format("%.1f", percentage)}%\r\n")
                    }
                }

                writer.flush()
                Log.d(TAG, "Successfully wrote enhanced CSV data")
            } catch (e: IOException) {
                Log.e(TAG, "Error writing enhanced CSV data: ${e.message}", e)
                throw e
            }
        }
    }
}