package com.jewelrypos.swarnakhatabook.Adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream

class InvoicePrintAdapter(
    private val context: Context,
    private val bitmap: Bitmap
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: PrintDocumentAdapter.LayoutResultCallback,
        extras: Bundle?
    ) {
        // Create a new PdfDocument with the requested page size
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        // Create print document info
        val info = PrintDocumentInfo.Builder("invoice.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: PrintDocumentAdapter.WriteResultCallback
    ) {
        // Check for cancellation
        if (cancellationSignal.isCanceled) {
            callback.onWriteCancelled()
            return
        }

        // Create a new PDF document
        val pdfDocument = PdfDocument()

        // Calculate page size from bitmap
        val pageInfo = PdfDocument.PageInfo.Builder(
            bitmap.width, bitmap.height, 1
        ).create()

        // Start a page and draw the bitmap
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        try {
            // Write PDF document to destination
            pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.toString())
        } finally {
            pdfDocument.close()
        }
    }
}