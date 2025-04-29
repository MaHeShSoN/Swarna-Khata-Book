package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jewelrypos.swarnakhatabook.R
import java.io.File

class PdfViewerBottomSheet : BottomSheetDialogFragment(), OnPageChangeListener, OnLoadCompleteListener {

    private lateinit var pdfView: PDFView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: View
    private var pdfFile: File? = null
    private var title: String = "Invoice Preview"

    companion object {
        private const val ARG_PDF_PATH = "pdf_path"
        private const val ARG_TITLE = "title"

        fun newInstance(pdfFilePath: String, title: String? = null): PdfViewerBottomSheet {
            return PdfViewerBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PDF_PATH, pdfFilePath)
                    title?.let { putString(ARG_TITLE, it) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }


    // Ensure the dialog expands to full height and is not draggable
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                setupFullHeight(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = false
            }
        }

        return dialog
    }

    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_pdf_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        pdfView = view.findViewById(R.id.pdfView)
        toolbar = view.findViewById(R.id.pdfToolbar)
        progressBar = view.findViewById(R.id.progressBar)

        // Extract arguments
        arguments?.getString(ARG_PDF_PATH)?.let { path ->
            pdfFile = File(path)
            if (!pdfFile!!.exists()) {
                Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
                dismiss()
                return
            }
        } ?: run {
            Toast.makeText(context, "Invalid PDF path", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        // Set toolbar title if provided
        arguments?.getString(ARG_TITLE)?.let {
            title = it
        }
        toolbar.title = title

        // Set close button listener
        toolbar.setNavigationOnClickListener {
            dismiss()
        }

        // Load PDF
        loadPdf()
    }

    private fun loadPdf() {
        progressBar.visibility = View.VISIBLE
        
        pdfFile?.let { file ->
            pdfView.fromFile(file)
                .defaultPage(0)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .onPageChange(this)
                .onLoad(this)
                .load()
        }
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        // Optional: Update page counter if you want to add one
    }

    override fun loadComplete(nbPages: Int) {
        progressBar.visibility = View.GONE
    }
}