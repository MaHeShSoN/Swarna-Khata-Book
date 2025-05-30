package com.jewelrypos.swarnakhatabook

import android.R.attr.fontFamily
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.PaymentsAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoiceSummeryFragmnetBinding
import com.jewelrypos.swarnakhatabook.ViewModle.InvoiceSummaryViewModel
import com.jewelrypos.swarnakhatabook.Factorys.InvoiceSummaryViewModelFactory
import com.jewelrypos.swarnakhatabook.BottomSheet.PdfViewerBottomSheet
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.jewelrypos.swarnakhatabook.Adapters.InvoiceSummaryItemAdapter
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager

class InvoiceSummeryFragmnet : Fragment() {

    private var _binding: FragmentInvoiceSummeryFragmnetBinding? = null
    private val binding get() = _binding!!

    private val args: InvoiceSummeryFragmnetArgs by navArgs()

    private val viewModel: InvoiceSummaryViewModel by viewModels {
        InvoiceSummaryViewModelFactory(requireActivity().application)
    }

    private lateinit var itemsSummaryAdapter: InvoiceSummaryItemAdapter

    // Removed paymentsAdapter as it's no longer needed for a RecyclerView
    // private lateinit var paymentsAdapter: PaymentsAdapter

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            findNavController().navigateUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceSummeryFragmnetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupItemsRecyclerView() // Setup for items only
        setupToolbar()
        // Removed setupPaymentsRecyclerView call as payments are now consolidated
        setupObservers()
        setupClickListeners()


        binding.topAppBar.overflowIcon =
            ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)

        viewModel.loadInvoice(args.invoiceId)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.print_invoice -> {
                    printInvoice()
                    true
                }


                R.id.save_invoice -> {
                    saveInvoicePdfToPhone()
                    true
                }

                R.id.duplicate_invoice -> {
                    confirmDuplicateInvoice()
                    true
                }

                R.id.call_customer -> {
                    callCustomer()
                    true
                }

                R.id.about_customer -> {
                    navigateToCustomerDetail()
                    true
                }

                else -> false
            }
        }

    }

    private fun navigateToCustomerDetail() {
        val customerId = viewModel.customer.value?.id

        if (customerId != null) {
            // Navigate to customer detail screen using the customerId
            val action =
                InvoiceSummeryFragmnetDirections.actionInvoiceSummeryFragmnetToCustomerDetailFragment(
                    customerId
                )
            findNavController().navigate(action)
        } else {
            // If customer ID is not available, show a message
            Toast.makeText(
                requireContext(),
                "Customer information not available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun formatPhoneNumber(phone: String): String {
        // If phone doesn't start with +, assume it's an Indian number and add +91
        // You can modify this logic based on your app's region
        return if (phone.startsWith("+")) {
            phone
        } else if (phone.startsWith("0")) {
            "+${phone.substring(1)}"
        } else {
            "+91$phone" // Default to India country code
        }
    }

    private fun callCustomer() {
        val phone = viewModel.getCustomerPhone()
        if (phone.isNotEmpty()) {
            try {
                // Create phone call intent
                val intent = Intent(Intent.ACTION_DIAL)
                // Format phone number to ensure it has proper format with country code
                val formattedPhone = formatPhoneNumber(phone)
                intent.data = Uri.parse("tel:$formattedPhone")
                startActivity(intent)
            } catch (e: Exception) {
                showErrorMessage("Could not place call: ${e.message}")
                Log.e("InvoiceDetailFragment", "Error placing call", e)
            }
        } else {
            showErrorMessage("No phone number available")
        }
    }


    private fun printInvoice() {
        Log.d("PrintInvoice", "Starting print process")
        val invoice = viewModel.invoice.value
        if (invoice == null) {
            Log.e("PrintInvoice", "Invoice is null, cannot proceed with printing")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("PrintInvoice", "Showing progress indicator")
                binding.progressBar.visibility = View.VISIBLE

                // Get shop information
                Log.d("PrintInvoice", "Fetching shop details")
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    Log.e("PrintInvoice", "Shop information not found")
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.shop_info_not_found), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                Log.d("PrintInvoice", "Shop details retrieved successfully")

                // Initialize PDFBox
                Log.d("PrintInvoice", "Initializing PDFBox")
                PDFBoxResourceLoader.init(requireContext())
                Log.d("PrintInvoice", "PDFBox initialized successfully")

                // Create PDF generator and apply settings
                Log.d("PrintInvoice", "Creating PDF generator and applying settings")
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = PdfSettingsManager(requireContext()).loadSettings()
                pdfGenerator.applySettings(pdfSettings)
                Log.d("PrintInvoice", "PDF generator configured successfully")

                // Generate PDF
                Log.d("PrintInvoice", "Starting PDF generation")
                val pdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    "Invoice_${invoice.invoiceNumber}"
                )
                Log.d("PrintInvoice", "PDF generated successfully: ${pdfFile.absolutePath}")

                // Create print job name
                val jobName = "Invoice_${invoice.invoiceNumber}"
                Log.d("PrintInvoice", "Print job name created: $jobName")

                // Get print manager using activity context
                Log.d("PrintInvoice", "Getting print manager service")
                val activity = requireActivity()
                if (activity == null) {
                    Log.e("PrintInvoice", "Activity is null, cannot proceed with printing")
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                Log.d("PrintInvoice", "Print manager obtained successfully")

                // Create print adapter
                Log.d("PrintInvoice", "Creating print adapter")
                val printAdapter = object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: Bundle?
                    ) {
                        Log.d("PrintInvoice", "onLayout called")
                        if (cancellationSignal?.isCanceled == true) {
                            Log.d("PrintInvoice", "Print job cancelled during layout")
                            callback?.onLayoutCancelled()
                            return
                        }

                        // Create print document info
                        Log.d("PrintInvoice", "Creating print document info")
                        val info = PrintDocumentInfo.Builder(jobName)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                            .build()

                        Log.d("PrintInvoice", "Layout finished successfully")
                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>?,
                        destination: ParcelFileDescriptor?,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        Log.d("PrintInvoice", "onWrite called")
                        try {
                            Log.d("PrintInvoice", "Starting to write PDF to print destination")
                            // Copy PDF file to print destination
                            pdfFile.inputStream().use { input ->
                                FileOutputStream(destination?.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d("PrintInvoice", "PDF written successfully")
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            Log.e("PrintInvoice", "Error writing PDF: ${e.message}", e)
                            callback?.onWriteFailed(e.message)
                        }
                    }
                }

                // Create print job using activity context
                Log.d("PrintInvoice", "Creating print job")
                activity.runOnUiThread {
                    try {
                        printManager.print(
                            jobName,
                            printAdapter,
                            PrintAttributes.Builder().build()
                        )
                        Log.d("PrintInvoice", "Print job created successfully")
                    } catch (e: Exception) {
                        Log.e("PrintInvoice", "Error creating print job: ${e.message}", e)
                        showErrorMessage("Error creating print job: ${e.message}")
                    }
                    binding.progressBar.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("PrintInvoice", "Error during print process: ${e.message}", e)
                binding.progressBar.visibility = View.GONE

                ThemedM3Dialog(requireContext())
                    .setTitle("Error")
                    .setLayout(R.layout.dialog_error)
                    .apply {
                        findViewById<TextView>(R.id.errorMessage)?.text =
                            "Error printing invoice: ${e.message}"
                    }
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun confirmDuplicateInvoice() {
        AlertDialog.Builder(requireContext())
            .setTitle("Duplicate Invoice")
            .setMessage("Are you sure you want to create a duplicate of this invoice?")
            .setPositiveButton("Duplicate") { _, _ ->
                viewModel.duplicateInvoice()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun saveInvoicePdfToPhone() {
        val invoice = viewModel.invoice.value ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Get shop information
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.shop_info_not_found), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Initialize PDFBox
                PDFBoxResourceLoader.init(requireContext())

                // Create PDF generator and apply settings
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = PdfSettingsManager(requireContext()).loadSettings()
                pdfGenerator.applySettings(pdfSettings)

                // Generate PDF
                val tempPdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    getString(R.string.invoice_pdf, invoice.invoiceNumber)
                )

                // Create content URI via FileProvider
                val contentUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    tempPdfFile
                )

                // Use the Storage Access Framework to let the user save the file
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, "Invoice_${invoice.invoiceNumber}.pdf")
                }

                // Check if there's an app that can handle this intent
                if (intent.resolveActivity(requireActivity().packageManager) != null) {
                    startActivityForResult(intent, REQUEST_SAVE_PDF)
                } else {
                    // Fallback: Copy to Downloads folder directly (requires WRITE_EXTERNAL_STORAGE permission)
                    savePdfToDownloads(tempPdfFile, "Invoice_${invoice.invoiceNumber}.pdf")
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("InvoiceDetailFragment", "Error saving PDF: ${e.message}", e)
                Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePdfToDownloads(sourceFile: File, filename: String) {
        try {
            // Get the Downloads directory
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // Create the destination file
            val destFile = File(downloadsDir, filename)

            // Copy the file
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Notify user
            Toast.makeText(
                context,
                getString(R.string.pdf_saved_downloads),
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {
            Log.e("InvoiceDetailFragment", "Error saving to Downloads: ${e.message}", e)
            Toast.makeText(
                context,
                "Error saving to Downloads: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupItemsRecyclerView() { // Renamed method
        itemsSummaryAdapter = InvoiceSummaryItemAdapter(emptyList())
        binding.itemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = itemsSummaryAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupObservers() {
        viewModel.invoice.observe(viewLifecycleOwner) { invoice ->
            invoice?.let { updateInvoiceUI(it) }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                showErrorMessage(errorMessage)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            updateLoadingState(isLoading)
        }
    }

    private fun setupClickListeners() {
        binding.actionEditInvoice.setOnClickListener {
            val action = InvoiceSummeryFragmnetDirections.actionInvoiceSummeryFragmnetToInvoiceDetailFragment(args.invoiceId)
            findNavController().navigate(action)
        }
        binding.actionMarkPaid.setOnClickListener {
            val invoice = viewModel.invoice.value ?: return@setOnClickListener
            val balanceDue = invoice.totalAmount - invoice.paidAmount
            if (balanceDue <= 0) {
                Toast.makeText(context, getString(R.string.invoice_already_paid), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmMarkAsPaid(balanceDue)
        }
        binding.actionPreview.setOnClickListener { openInvoicePreview() }
        binding.actionSavePdf.setOnClickListener { showShareOptions() }
        binding.actionDeleteInvoice.setOnClickListener { confirmDeleteInvoice() }
    }

    private fun updateInvoiceUI(invoice: Invoice) {
        binding.invoiceNumber.text = invoice.invoiceNumber

        val dateFormatter = SimpleDateFormat(getString(R.string.date_format), Locale.getDefault())
        binding.invoiceDate.text = dateFormatter.format(Date(invoice.invoiceDate))

        if (invoice.dueDate != null) {
            binding.invoiceDueDate.text = dateFormatter.format(Date(invoice.dueDate!!))
        } else {
            binding.invoiceDueDate.text = "N/A"
        }

        updatePaymentStatus(invoice)

//        binding.customerName.text = invoice.customerName
//        binding.customerPhone.text = invoice.customerPhone
//        binding.customerAddress.text = invoice.customerAddress
        binding.topAppBar.title = invoice.customerName
        binding.topAppBar.subtitle = invoice.customerAddress

        val isWholesaler = viewModel.customer.value?.customerType.equals("Wholesaler", ignoreCase = true)
        updateUIForCustomerType(isWholesaler)

        itemsSummaryAdapter.updateItems(invoice.items)
        updateFinancialDetails(invoice)
        updatePaymentsDisplay(invoice) // New method to display consolidated payments
        updateNotesDisplay(invoice)

        if (invoice.items.isEmpty()) {
            binding.itemsSectionTitle.visibility = View.GONE
            binding.itemsRecyclerView.visibility = View.GONE
        } else {
            binding.itemsSectionTitle.visibility = View.VISIBLE
            binding.itemsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun updateUIForCustomerType(isWholesaler: Boolean) {
        if (isWholesaler) {
            binding.itemsSectionTitle.text = getString(R.string.items_purchased)
            binding.paymentsSectionTitle.text = getString(R.string.payments_to_supplier)
            binding.amountPaidLabel.text = getString(R.string.amount_paid_to_supplier)
            binding.balanceDueLabel.text = getString(R.string.balance_to_pay)
            binding.topAppBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.supplier_button_color))
        } else {
            binding.itemsSectionTitle.text = getString(R.string.items_sold)
            binding.paymentsSectionTitle.text = getString(R.string.payments)
            binding.amountPaidLabel.text = getString(R.string.amount_paid)
            binding.balanceDueLabel.text = getString(R.string.balance_due)
            binding.topAppBar.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.my_light_primary))
        }
    }

    private fun updatePaymentStatus(invoice: Invoice) {
        val isWholesaler = viewModel.customer.value?.customerType.equals("Wholesaler", ignoreCase = true)
        val balanceDue = invoice.totalAmount - invoice.paidAmount

        val paymentStatus = when {
            balanceDue <= 0 -> if (isWholesaler) "PAID" else "PAID"
            invoice.paidAmount > 0 -> if (isWholesaler) "PARTIALLY PAID" else "PARTIAL"
            else -> if (isWholesaler) "TO PAY" else "UNPAID"
        }
        binding.paymentStatus.text = paymentStatus

        val statusColor = when (paymentStatus) {
            "PAID" -> R.color.status_paid
            "PARTIAL", "PARTIALLY PAID" -> R.color.status_partial
            else -> if (isWholesaler) R.color.supplier_badge_color else R.color.status_unpaid
        }
        binding.paymentStatus.backgroundTintList = ContextCompat.getColorStateList(requireContext(), statusColor)
    }

    private fun updateFinancialDetails(invoice: Invoice) {
        val currencyFormatter = DecimalFormat("#,##,##0")
        val subtotal = calculateSubtotal(invoice)
        val totalExtraCharges = calculateTotalExtraCharges(invoice)
        val tax = calculateTax(invoice)
        val balanceDue = invoice.totalAmount - invoice.paidAmount

        binding.subtotalValue.text = "₹${currencyFormatter.format(subtotal)}"

        if (totalExtraCharges > 0) {
            binding.extraChargesLayout.visibility = View.VISIBLE
            binding.extraChargesValue.text = "+ ₹${currencyFormatter.format(totalExtraCharges.toInt())}"
        } else {
            binding.extraChargesLayout.visibility = View.GONE
        }

        binding.taxValue.text = "+ ₹${currencyFormatter.format(tax.toInt())}"
        binding.totalValue.text = "₹${currencyFormatter.format(invoice.totalAmount)}"
        binding.amountPaidValue.text = "₹${currencyFormatter.format(invoice.paidAmount)}"
        binding.balanceDueValue.text = "₹${currencyFormatter.format(balanceDue)}"
    }

    private fun updatePaymentsDisplay(invoice: Invoice) { // New method for consolidated payments
        binding.paymentsSummaryContainer.removeAllViews() // Clear existing views

        if (invoice.payments.isEmpty()) {
            binding.noPaymentsText.visibility = View.VISIBLE
            binding.paymentsSummaryContainer.visibility = View.GONE
        } else {
            binding.noPaymentsText.visibility = View.GONE
            binding.paymentsSummaryContainer.visibility = View.VISIBLE

            val formatter = DecimalFormat("#,##,##0")

//            invoice.payments.forEach { payment ->
//                val paymentTextView = TextView(context).apply {
//                    layoutParams = LinearLayout.LayoutParams(
//                        LinearLayout.LayoutParams.MATCH_PARENT,
//                        LinearLayout.LayoutParams.WRAP_CONTENT
//                    ).also { it.setMargins(0, resources.getDimensionPixelSize(R.dimen.margin_small), 0, 0) } // Adjust margin as needed
//                    text = "${payment.method} - ₹${formatter.format(payment.amount)}"
//                    textSize = 14f // Adjust text size as needed
//                    setTextColor(ContextCompat.getColor(context, R.color.my_light_on_background))
//
//                }
//                binding.paymentsSummaryContainer.addView(paymentTextView)
//            }
            invoice.payments.forEach { payment ->
                val paymentRowLayout = LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMargins(0, resources.getDimensionPixelSize(R.dimen.margin_small), 0, 0) }
                    orientation = LinearLayout.HORIZONTAL // Important: Set orientation to horizontal
                }

                val methodNameTextView = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, // Width: 0dp
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f // Weight: 1, will take up available space
                    )
                    val typeface = ResourcesCompat.getFont(context, R.font.montserratmedium)
                    typeface?.let {
                        setTypeface(it)
                    }
                    text = payment.method
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.my_light_on_background))
                }

                val paymentAmountTextView = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, // Width: wrap content
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = "- ₹${formatter.format(payment.amount)}"
                    textSize = 14f
                    val typeface = ResourcesCompat.getFont(context, R.font.montserratsemibold)
                    typeface?.let {
                        setTypeface(it)
                    }
                    setTextColor(ContextCompat.getColor(context, R.color.my_light_on_background))
                    // Gravity to align text to the right within its own bounds
                    gravity = Gravity.END // or Gravity.RIGHT
                }

                paymentRowLayout.addView(methodNameTextView)
                paymentRowLayout.addView(paymentAmountTextView)

                binding.paymentsSummaryContainer.addView(paymentRowLayout)
            }
        }
    }


    private fun updateNotesDisplay(invoice: Invoice) {
        if (invoice.notes.isNullOrEmpty()) {
            binding.emptyNotesText.visibility = View.VISIBLE
            binding.notesContent.visibility = View.GONE
        } else {
            binding.emptyNotesText.visibility = View.GONE
            binding.notesContent.visibility = View.VISIBLE
            binding.notesContent.text = invoice.notes
        }
    }

    private fun calculateSubtotal(invoice: Invoice): Double {
        return invoice.items.sumOf { it.price * it.quantity }
    }

    private fun calculateTotalExtraCharges(invoice: Invoice): Double {
        return invoice.items.sumOf { item ->
            item.itemDetails.listOfExtraCharges.sumOf { charge ->
                charge.amount * item.quantity
            }
        }
    }

    private fun calculateTax(invoice: Invoice): Double {
        return invoice.items.sumOf { item ->
            val itemSubtotal = item.price * item.quantity
            val itemExtraCharges = item.itemDetails.listOfExtraCharges.sumOf { it.amount * item.quantity }
            val taxableAmountForItem = itemSubtotal + itemExtraCharges
            taxableAmountForItem * (item.itemDetails.taxRate / 100.0)
        }
    }

    private fun showErrorMessage(message: String) {
        if (message.isNotEmpty()) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun confirmMarkAsPaid(balanceDue: Double) {
        ThemedM3Dialog(requireContext())
            .setTitle(getString(R.string.mark_as_paid))
            .setLayout(R.layout.dialog_confirmation)
            .apply {
                findViewById<TextView>(R.id.confirmationMessage)?.text = getString(R.string.confirm_mark_paid)
            }
            .setPositiveButton(getString(R.string.yes)) { dialog, _ ->
                viewModel.markInvoiceAsPaid(balanceDue)
                Toast.makeText(context, getString(R.string.invoice_marked_paid), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openInvoicePreview() {
        val invoice = viewModel.invoice.value ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val shop = viewModel.getShopDetails()
                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.shop_info_not_found), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                PDFBoxResourceLoader.init(requireContext())
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = viewModel.getPdfSettings()
                pdfGenerator.applySettings(pdfSettings)
                val pdfFile = pdfGenerator.generateInvoicePdf(invoice, shop, getString(R.string.preview_invoice, invoice.invoiceNumber))
                binding.progressBar.visibility = View.GONE
                val pdfBottomSheet = PdfViewerBottomSheet.newInstance(pdfFile.absolutePath, "Invoice #${invoice.invoiceNumber}")
                pdfBottomSheet.show(parentFragmentManager, "PdfViewerBottomSheet")
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("InvoiceSummaryFragment", "Error generating PDF preview: ${e.message}", e)
                Toast.makeText(context, "Could not generate PDF preview. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_SAVE_PDF = 1001
    }

    private fun showShareOptions() {
        val invoice = viewModel.invoice.value ?: return

        val dialog = ThemedM3Dialog(requireContext())
            .setLayout(R.layout.dialog_share_options)

        val shareImageBtn = dialog.findViewById<com.google.android.material.card.MaterialCardView>(R.id.imageOptionCard)
        val sharePdfBtn = dialog.findViewById<com.google.android.material.card.MaterialCardView>(R.id.pdfOptionCard)
        val btnCancelBtn = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        val alertDialog = dialog.create()

        shareImageBtn?.setOnClickListener {
            shareInvoiceAsImage(invoice)
            alertDialog.dismiss()
        }

        sharePdfBtn?.setOnClickListener {
            shareInvoiceAsPdf(invoice)
            alertDialog.dismiss()
        }

        btnCancelBtn?.setOnClickListener {
            alertDialog.dismiss()
        }
        alertDialog.show()
    }

    private fun shareInvoiceAsImage(invoice: Invoice) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val shop = viewModel.getShopDetails()
                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.shop_info_not_found), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                PDFBoxResourceLoader.init(requireContext())
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = viewModel.getPdfSettings()
                pdfGenerator.applySettings(pdfSettings)
                val pdfFile = pdfGenerator.generateInvoicePdf(invoice, shop, "Invoice_${invoice.invoiceNumber}")
                val bitmap = convertPdfToBitmap(pdfFile)
                if (bitmap != null) {
                    val cachePath = File(requireContext().cacheDir, "images")
                    cachePath.mkdirs()
                    val imageFile = File(cachePath, "Invoice_${invoice.invoiceNumber}.png")
                    FileOutputStream(imageFile).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", imageFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    binding.progressBar.visibility = View.GONE
                    startActivity(Intent.createChooser(intent, "Share Invoice Image"))
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Failed to convert PDF to image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Error creating image: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("InvoiceSummaryFragment", "Error sharing as image", e)
            }
        }
    }

    private fun convertPdfToBitmap(pdfFile: File): Bitmap? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fileDescriptor ->
                    val renderer = PdfRenderer(fileDescriptor)
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    return bitmap
                }
            }
        } catch (e: Exception) {
            Log.e("InvoiceSummaryFragment", "Error converting PDF to bitmap", e)
        }
        return null
    }

    private fun shareInvoiceAsPdf(invoice: Invoice) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val shop = viewModel.getShopDetails()
                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, getString(R.string.shop_info_not_found), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                PDFBoxResourceLoader.init(requireContext())
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = viewModel.getPdfSettings()
                pdfGenerator.applySettings(pdfSettings)
                val pdfFile = pdfGenerator.generateInvoicePdf(invoice, shop, "Invoice_${invoice.invoiceNumber}")
                sharePdfFile(pdfFile)
                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("InvoiceSummaryFragment", "Error generating PDF: ${e.message}", e)
                Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sharePdfFile(pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Invoice PDF"))
        } catch (e: Exception) {
            Log.e("PDFSharing", "Error sharing PDF", e)
            showErrorMessage("Failed to share PDF")
        }
    }

    private fun confirmDeleteInvoice() {
        val isWholesaler = viewModel.getCustomerType().equals("Wholesaler", ignoreCase = true)
        val title = if (isWholesaler) "Delete Purchase Order" else "Delete Invoice"
        val message = if (isWholesaler)
            "Are you sure you want to delete this purchase order? It will be moved to the recycling bin where you can restore it within 30 days if needed."
        else
            "Are you sure you want to delete this invoice? It will be moved to the recycling bin where you can restore it within 30 days if needed."

        ThemedM3Dialog(requireContext())
            .setTitle(title)
            .setLayout(R.layout.dialog_confirmation)
            .apply {
                findViewById<TextView>(R.id.confirmationMessage)?.text = message
            }
            .setPositiveButton("Delete") { dialog, _ ->
                binding.progressBar.visibility = View.VISIBLE
                viewModel.deleteInvoice { success ->
                    if (!isAdded) return@deleteInvoice
                    requireActivity().runOnUiThread {
                        val binding = _binding ?: return@runOnUiThread
                        binding.progressBar.visibility = View.GONE
                        if (success) {
                            Toast.makeText(context, "Invoice moved to recycling bin", Toast.LENGTH_SHORT).show()
                            EventBus.postInvoiceDeleted()
                            findNavController().navigateUp()
                        } else {
                            ThemedM3Dialog(requireContext())
                                .setTitle("Error")
                                .setLayout(R.layout.dialog_error)
                                .apply {
                                    findViewById<TextView>(R.id.errorMessage)?.text = "Failed to delete invoice"
                                }
                                .setPositiveButton("OK") { errorDialog, _ -> errorDialog.dismiss() }
                                .show()
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog -> dialog.dismiss() }
            .show()
    }

    private fun updateExtraChargesDisplay(invoice: Invoice) {
        // This method is no longer directly used for displaying individual extra charges
        // The total is now calculated and displayed in updateFinancialDetails
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}