package com.jewelrypos.swarnakhatabook

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.navigation.NavOptions
import androidx.core.os.bundleOf
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jewelrypos.swarnakhatabook.Adapters.PaymentsAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.PaymentEntryBottomSheet
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.InvoiceDetailViewModelFactory
import com.jewelrypos.swarnakhatabook.ViewModle.InvoiceDetailViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoiceDetailBinding
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.Adapters.EditableInvoiceItemAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemSelectionBottomSheet
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Shop
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch
import androidx.core.graphics.createBitmap
import com.google.android.material.button.MaterialButton
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog

class InvoiceDetailFragment : Fragment() {

    private var _binding: FragmentInvoiceDetailBinding? = null
    private val binding get() = _binding!!

    private var isEditingNotes = false

    private val args: InvoiceDetailFragmentArgs by navArgs()

    private val viewModel: InvoiceDetailViewModel by viewModels {
        InvoiceDetailViewModelFactory(requireActivity().application)
    }

    private lateinit var itemsAdapter: EditableInvoiceItemAdapter
    private lateinit var paymentsAdapter: PaymentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerViews()
        setupObservers()
        setupClickListeners()
        setupNotesEditing()
        setupAddItemButton()

        binding.topAppBar.overflowIcon =
            ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)
        // Load invoice data based on passed ID
        viewModel.loadInvoice(args.invoiceId)

        // Add the callback
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Setup menu items
        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_mark_as_paid -> {
                    markInvoiceAsPaid()
                    true
                }

//                R.id.action_whatsapp_customer -> {
//                    messageCustomerOnWhatsApp()
//                    true
//                }

//                R.id.action_send_reminder -> {
//                    sendPaymentReminder()
//                    true
//                }

                R.id.action_archive -> {
                    archiveInvoice()
                    true
                }

                R.id.action_generate_receipt -> {
                    generateReceipt()
                    true
                }

                R.id.action_save_pdf -> {
                    saveInvoicePdfToPhone()
                    true
                }

                R.id.action_duplicate -> {
                    confirmDuplicateInvoice()
                    true
                }

                R.id.action_delete -> {
                    confirmDeleteInvoice()
                    true
                }

                else -> false
            }
        }
    }

    private fun markInvoiceAsPaid() {
        // Get current invoice
        val invoice = viewModel.invoice.value ?: return

        // Check if invoice is already fully paid
        val balanceDue = invoice.totalAmount - invoice.paidAmount
        if (balanceDue <= 0) {
            Toast.makeText(context, "Invoice is already fully paid", Toast.LENGTH_SHORT).show()
            return
        }

        // Show confirmation dialog using ThemedM3Dialog
        ThemedM3Dialog(requireContext())
            .setTitle("Mark as Paid")
            .setLayout(R.layout.dialog_confirmation) // You'll need to create this layout
            .apply {
                // Set the message in the dialog
                findViewById<TextView>(R.id.confirmationMessage)?.text =
                    "Are you sure you want to mark this invoice as fully paid?"
            }
            .setPositiveButton("Yes") { dialog, _ ->
                // Add full payment for remaining balance
                val newPayment = Payment(
                    amount = balanceDue,
                    method = "Manual Adjustment",
                    date = System.currentTimeMillis(),
                    reference = "Marked as paid",
                    notes = "Invoice manually marked as paid"
                )

                viewModel.addPaymentToInvoice(newPayment)
                Toast.makeText(context, "Invoice marked as paid", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog ->
                dialog.dismiss()
            }
            .show()
    }

//    private fun sendPaymentReminder() {
//        // Get customer phone number
//        val phone = viewModel.getCustomerPhone()
//        val invoice = viewModel.invoice.value
//
//        if (phone.isEmpty() || invoice == null) {
//            Toast.makeText(context, "Customer information not available", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Check if there's a balance due
//        val balanceDue = invoice.totalAmount - invoice.paidAmount
//        if (balanceDue <= 0) {
//            Toast.makeText(context, "This invoice is fully paid", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Show confirmation dialog using ThemedM3Dialog
//        ThemedM3Dialog(requireContext())
//            .setTitle("Send Payment Reminder")
//            .setLayout(R.layout.dialog_confirmation)
//            .apply {
//                findViewById<TextView>(R.id.confirmationMessage)?.text =
//                    "Do you want to send a payment reminder to the customer via WhatsApp?"
//            }
//            .setPositiveButton("Send") { dialog, _ ->
//                // In future, integrate with WhatsApp Business API
//                // For now, open WhatsApp with a reminder message and the PDF invoice
//                messageCustomerWithReminder(phone, invoice, balanceDue)
//                dialog.dismiss()
//            }
//            .setNegativeButton("Cancel") { dialog ->
//                dialog.dismiss()
//            }
//            .show()
//    }

    private fun messageCustomerWithReminder(phone: String, invoice: Invoice, balanceDue: Double) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Get shop information
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Initialize PDFBox
                PDFBoxResourceLoader.init(requireContext())

                // Create PDF generator and apply settings
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = PdfSettingsManager(requireContext()).loadSettings()
                pdfGenerator.applySettings(pdfSettings)

                // Generate PDF
                val pdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    "Invoice_${invoice.invoiceNumber}"
                )

                // Create content URI via FileProvider
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    pdfFile
                )

                // Create reminder message
                val message = "Payment Reminder: Your invoice #${invoice.invoiceNumber} " +
                        "has a balance due of ₹${DecimalFormat("#,##,##0.00").format(balanceDue)}. " +
                        "Please make the payment at your earliest convenience."

                // Format phone number (remove any non-digit characters except +)
                val formattedPhone = phone.replace(Regex("[^\\d+]"), "")

                try {
                    // Create intent for WhatsApp
                    val whatsappIntent = Intent(Intent.ACTION_SEND)
                    whatsappIntent.type = "application/pdf"
                    whatsappIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    whatsappIntent.putExtra(Intent.EXTRA_TEXT, message)
                    whatsappIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    whatsappIntent.setPackage("com.whatsapp")

                    // Try WhatsApp Business if needed
                    val whatsappBusinessIntent = Intent(Intent.ACTION_SEND)
                    whatsappBusinessIntent.type = "application/pdf"
                    whatsappBusinessIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    whatsappBusinessIntent.putExtra(Intent.EXTRA_TEXT, message)
                    whatsappBusinessIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    whatsappBusinessIntent.setPackage("com.whatsapp.w4b")

                    // Check which app is available
                    val packageManager = requireActivity().packageManager
                    val whatsappInstalled = whatsappIntent.resolveActivity(packageManager) != null
                    val whatsappBusinessInstalled = whatsappBusinessIntent.resolveActivity(packageManager) != null

                    when {
                        whatsappInstalled -> {
                            startActivity(whatsappIntent)
                        }
                        whatsappBusinessInstalled -> {
                            startActivity(whatsappBusinessIntent)
                        }
                        else -> {
                            // Neither app is installed, show error
                            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Record that a reminder was sent
                    viewModel.updateInvoiceNotes(
                        (invoice.notes + "\n\nPayment reminder sent on " +
                                SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                    .format(Date())).trim()
                    )

                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Could not open WhatsApp: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("InvoiceDetailFragment", "Error sending reminder", e)
                }

                binding.progressBar.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("InvoiceDetailFragment", "Error creating PDF for reminder", e)
                Toast.makeText(context, "Error creating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun archiveInvoice() {
        AlertDialog.Builder(requireContext())
            .setTitle("Archive Invoice")
            .setMessage("Do you want to archive this invoice? Archived invoices are hidden from the main list but can be accessed later.")
            .setPositiveButton("Archive") { _, _ ->
                // In a real implementation, you'd update the invoice status in your database
                // For now, show a message about the future implementation
                Toast.makeText(
                    context,
                    "Archive functionality will be implemented in a future update",
                    Toast.LENGTH_SHORT
                ).show()

                // Example of how you might implement it:
                // viewModel.updateInvoiceStatus(InvoiceStatus.ARCHIVED) { success ->
                //     if (success) {
                //         Toast.makeText(context, "Invoice archived", Toast.LENGTH_SHORT).show()
                //         findNavController().navigateUp()
                //     } else {
                //         Toast.makeText(context, "Failed to archive invoice", Toast.LENGTH_SHORT).show()
                //     }
                // }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateReceipt() {
        val invoice = viewModel.invoice.value ?: return

        // Check if any payment has been made
        if (invoice.paidAmount <= 0) {
            ThemedM3Dialog(requireContext())
                .setTitle("Cannot Generate Receipt")
                .setLayout(R.layout.dialog_error)
                .apply {
                    findViewById<TextView>(R.id.errorMessage)?.text =
                        "Cannot generate receipt: No payments have been made"
                }
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Get shop information
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Initialize PDFBox
                PDFBoxResourceLoader.init(requireContext())

                // Create PDF generator and apply settings
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = PdfSettingsManager(requireContext()).loadSettings()
                pdfGenerator.applySettings(pdfSettings)

                // Generate PDF with "Receipt" in the filename
                val pdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    "Receipt_${invoice.invoiceNumber}"
                )

                
                sharePdfFile(pdfFile)

            } catch (e: Exception) {
                Log.e("InvoiceDetailFragment", "Error generating receipt: ${e.message}", e)

                ThemedM3Dialog(requireContext())
                    .setTitle("Error")
                    .setLayout(R.layout.dialog_error)
                    .apply {
                        findViewById<TextView>(R.id.errorMessage)?.text =
                            "Error generating receipt: ${e.message}"
                    }
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    companion object {
        private const val REQUEST_SAVE_PDF = 1001
    }

    // Override onActivityResult to handle the result from the file save dialog
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SAVE_PDF && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val invoice = viewModel.invoice.value ?: return

                try {
                    // Get the temp PDF file
                    val tempFile = File(
                        requireContext().cacheDir,
                        "Invoice_${invoice.invoiceNumber}.pdf"
                    )

                    // Copy to the destination URI
                    requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    Toast.makeText(context, "PDF saved successfully", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    Log.e("InvoiceDetailFragment", "Error saving PDF: ${e.message}", e)
                    Toast.makeText(
                        context,
                        "Error saving PDF: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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
                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
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
                    "Invoice_${invoice.invoiceNumber}"
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
                "PDF saved to Downloads folder",
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


    private fun setupRecyclerViews() {
        // Setup items recycler view with edit/delete functionality
        itemsAdapter = EditableInvoiceItemAdapter(emptyList())
        itemsAdapter.setOnItemActionListener(object :
            EditableInvoiceItemAdapter.OnItemActionListener {
            override fun onRemoveItem(item: InvoiceItem) {
                confirmDeleteItem(item)
            }

            override fun onEditItem(item: InvoiceItem) {
                openItemEditor(item)
            }

            override fun onQuantityChanged(item: InvoiceItem, newQuantity: Int) {
                viewModel.updateItemQuantity(item, newQuantity)
            }
        })

        binding.itemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = itemsAdapter
        }

        // Setup payments recycler view with delete functionality
        paymentsAdapter = PaymentsAdapter(emptyList())
        paymentsAdapter.setOnPaymentActionListener(object :
            PaymentsAdapter.OnPaymentActionListener {
            override fun onRemovePayment(payment: Payment) {
                confirmDeletePayment(payment)
            }

            override fun onEditPayment(payment: Payment) {
                // Optional: Implement payment edit functionality
            }
        })

        binding.paymentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = paymentsAdapter
        }
    }

    private fun setupObservers() {
        // Observe invoice changes
        viewModel.invoice.observe(viewLifecycleOwner) { invoice ->
            invoice?.let { updateInvoiceUI(it) }
        }

        // Centralized error handling
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                showErrorMessage(it)
            }
        }

        // Loading state observer
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            updateLoadingState(isLoading)
        }
    }

    private fun updateInvoiceUI(invoice: Invoice) {
        Log.d(
            "InvoiceDetailFragment",
            "Updating UI with invoice: ${invoice.invoiceNumber}, items: ${invoice.items.size}"
        )

        // Update invoice header details
        binding.invoiceNumber.text = invoice.invoiceNumber

        // Format invoice date
        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.invoiceDate.text = dateFormatter.format(Date(invoice.invoiceDate))


        // Update payment status
        updatePaymentStatus(invoice)

        // Update customer details
        binding.customerName.text = invoice.customerName
        binding.customerPhone.text = invoice.customerPhone
        binding.customerAddress.text = invoice.customerAddress

        // First fetch the customer to determine the type
        viewModel.customer.observe(viewLifecycleOwner) { customer ->
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

            // Update UI based on customer type
            updateUIForCustomerType(isWholesaler)
        }


        // Update items list
        itemsAdapter.updateItems(invoice.items)

        // Update financial information
        updateFinancialDetails(invoice)

        // Update extra charges - make sure this happens after items are updated
        updateExtraChargesDisplay(invoice)

        // Update payments list
        updatePaymentsList(invoice)

        // Update notes
        updateNotesDisplay(invoice)

        Log.d("InvoiceDetailFragment", "UI update complete")
    }


    private fun updateUIForCustomerType(isWholesaler: Boolean) {
        if (isWholesaler) {
            // Update for wholesaler/supplier
            binding.topAppBar.title = "Purchase Order Details"
            binding.customerSectionTitle.text = "Supplier Details"
            binding.addItemsButton.text = "Add Purchase"
            binding.itemsSectionTitle.text = "Items Purchased"
            binding.paymentsSectionTitle.text = "Payments to Supplier"
            binding.amountPaidLabel.text = "Amount Paid to Supplier:"
            binding.balanceDueLabel.text = "Balance to Pay:"
            binding.addPaymentButton.text = "+ Add Payment"

            // Apply wholesaler-specific colors
            binding.topAppBar.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.supplier_button_color
                )
            )
            binding.printButton.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.supplier_text_color
                )
            )
        } else {
            // Reset to default consumer UI
            binding.topAppBar.title = "Invoice Details"
            binding.customerSectionTitle.text = "Customer Details"
            binding.addItemsButton.text = "Add Item"
            binding.itemsSectionTitle.text = "Items Sold"
            binding.paymentsSectionTitle.text = "Payments"
            binding.amountPaidLabel.text = "Amount Paid:"
            binding.balanceDueLabel.text = "Balance Due:"
            binding.addPaymentButton.text = "Add Payment"

            // Reset to default colors
            binding.topAppBar.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.my_light_primary
                )
            )
            binding.printButton.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.my_light_primary
                )
            )
        }
    }

    private fun updatePaymentStatus(invoice: Invoice) {
        val isWholesaler =
            viewModel.customer.value?.customerType.equals("Wholesaler", ignoreCase = true)
        val balanceDue = invoice.totalAmount - invoice.paidAmount

        val paymentStatus = when {
            balanceDue <= 0 -> if (isWholesaler) "PAID" else "PAID"
            invoice.paidAmount > 0 -> if (isWholesaler) "PARTIALLY PAID" else "PARTIAL"
            else -> if (isWholesaler) "TO PAY" else "UNPAID"
        }
        binding.paymentStatus.text = paymentStatus

        // Set status background color
        val statusColor = when (paymentStatus) {
            "PAID" -> R.color.status_paid
            "PARTIAL", "PARTIALLY PAID" -> R.color.status_partial
            else -> if (isWholesaler) R.color.supplier_badge_color else R.color.status_unpaid
        }
        binding.paymentStatus.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), statusColor)
    }

    private fun updateFinancialDetails(invoice: Invoice) {
        val currencyFormatter = DecimalFormat("#,##,##0.00")
        val subtotal = calculateSubtotal(invoice)
        val extraCharges = calculateExtraCharges(invoice)
        val tax = calculateTax(invoice)
        val balanceDue = invoice.totalAmount - invoice.paidAmount

        binding.subtotalValue.text = "₹${currencyFormatter.format(subtotal)}"

        // If you have a UI element to display extra charges total:
        // binding.extraChargesValue.text = "₹${currencyFormatter.format(extraCharges)}"

        binding.taxValue.text = "₹${currencyFormatter.format(tax)}"
        binding.totalValue.text = "₹${currencyFormatter.format(invoice.totalAmount)}"
        binding.amountPaidValue.text = "₹${currencyFormatter.format(invoice.paidAmount)}"
        binding.balanceDueValue.text = "₹${currencyFormatter.format(balanceDue)}"
    }

    private fun updatePaymentsList(invoice: Invoice) {
        if (invoice.payments.isEmpty()) {
            binding.noPaymentsText.visibility = View.VISIBLE
            binding.paymentsRecyclerView.visibility = View.GONE
        } else {
            binding.noPaymentsText.visibility = View.GONE
            binding.paymentsRecyclerView.visibility = View.VISIBLE
            paymentsAdapter.updatePayments(invoice.payments)
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

    private fun calculateExtraCharges(invoice: Invoice): Double {
        return invoice.items.sumOf { item ->
            item.itemDetails.listOfExtraCharges.sumOf { charge ->
                charge.amount * item.quantity
            }
        }
    }

    private fun calculateSubtotal(invoice: Invoice): Double {
        return invoice.items.sumOf { it.price * it.quantity }
    }


    private fun calculateTax(invoice: Invoice): Double {
        return invoice.items.sumOf { item ->
            val itemSubtotal = item.price * item.quantity // Subtotal for THIS item
            // Use the existing helper to get extra charges for THIS item
            val itemExtraCharges = getItemExtraChargesTotal(item)
            val taxableAmountForItem = itemSubtotal + itemExtraCharges
            // Calculate tax for THIS item based on its taxable amount and rate
            taxableAmountForItem * (item.itemDetails.taxRate / 100.0)
        }
    }

    private fun setupAddItemButton() {
        binding.addItemsButton.setOnClickListener {
            openItemSelector()
        }
    }


    private fun openItemSelector() {
        val bottomSheet = ItemSelectionBottomSheet.newInstance()

        bottomSheet.setOnItemSelectedListener(object :
            ItemSelectionBottomSheet.OnItemSelectedListener {
            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
                // Create new invoice item
                val newInvoiceItem = InvoiceItem(
                    itemId = newItem.id,
                    quantity = 1,
                    itemDetails = newItem,
                    price = price
                )

                // Add to invoice
                viewModel.addInvoiceItem(newInvoiceItem)
                EventBus.postInvoiceAdded()
            }

            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
                // This shouldn't happen when adding a new item
            }
        })

        bottomSheet.show(parentFragmentManager, "ItemSelectorBottomSheet")
    }

    private fun setupClickListeners() {
        // Add payment button
        binding.addPaymentButton.setOnClickListener {
            showAddPaymentBottomSheet()
        }

        // Call customer button
        binding.callCustomerButton.setOnClickListener {
            callCustomer()
        }

//        // WhatsApp customer button
//        binding.whatsappCustomerButton.setOnClickListener {
//            messageCustomerOnWhatsApp()
//        }

        // Print button
        binding.printButton.setOnClickListener {
            printInvoice()
        }

        // Share button
        binding.shareButton.setOnClickListener {
//            shareInvoice()
            showShareOptions()
        }

        binding.infoButton.setOnClickListener {
            navigateToCustomerDetail()
        }
    }


    private fun showShareOptions() {
        val invoice = viewModel.invoice.value ?: return

        val dialog = ThemedM3Dialog(requireContext())
            .setTitle("Share Invoice")
            .setLayout(R.layout.dialog_share_options)

        // Get references to the buttons in the custom layout
        val shareImageBtn = dialog.findViewById<MaterialButton>(R.id.btnShareImage)
        val sharePdfBtn = dialog.findViewById<MaterialButton>(R.id.btnSharePdf)

        // Create the dialog but don't show it yet
        val alertDialog = dialog.create()

        // Set click listeners for the buttons
        shareImageBtn?.setOnClickListener {
            shareInvoiceAsImage(invoice)
            alertDialog.dismiss()
        }

        sharePdfBtn?.setOnClickListener {
            shareInvoiceAsPdf(invoice)
            alertDialog.dismiss()
        }

        // Show the dialog
        alertDialog.show()
    }
    private fun shareInvoiceAsImage(invoice: Invoice) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Get shop information
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Initialize PDFBox
                PDFBoxResourceLoader.init(requireContext())

                // Create PDF generator and apply settings
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = PdfSettingsManager(requireContext()).loadSettings()
                pdfGenerator.applySettings(pdfSettings)

                // Generate PDF
                val pdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    "Invoice_${invoice.invoiceNumber}"
                )

                // Convert PDF to image
                val bitmap = convertPdfToBitmap(pdfFile)

                if (bitmap != null) {
                    // Save bitmap to cache directory
                    val cachePath = File(requireContext().cacheDir, "images")
                    cachePath.mkdirs()

                    val imageFile = File(cachePath, "Invoice_${invoice.invoiceNumber}.png")

                    val outputStream = FileOutputStream(imageFile)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()

                    // Create content URI via FileProvider
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider",
                        imageFile
                    )

                    // Create sharing intent
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "image/png"
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    binding.progressBar.visibility = View.GONE
                    startActivity(Intent.createChooser(intent, "Share Invoice Image"))
                } else {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Failed to convert PDF to image", Toast.LENGTH_SHORT)
                        .show()
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Error creating image: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                Log.e("InvoiceDetailFragment", "Error sharing as image", e)
            }
        }
    }

    // 4. Helper function to convert PDF to bitmap
    private fun convertPdfToBitmap(pdfFile: File): Bitmap? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val fileDescriptor = ParcelFileDescriptor.open(
                    pdfFile, ParcelFileDescriptor.MODE_READ_ONLY
                )

                val renderer = PdfRenderer(fileDescriptor)
                val page = renderer.openPage(0)

                // Create a white background bitmap
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.eraseColor(Color.WHITE) // Ensure white background

                // Create canvas with bitmap
                val canvas = Canvas(bitmap)

                // Render the page to the canvas
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Close resources
                page.close()
                renderer.close()
                fileDescriptor.close()

                return bitmap
            }
        } catch (e: Exception) {
            Log.e("InvoiceDetailFragment", "Error converting PDF to bitmap", e)
        }
        return null
    }

    // 5. Implement sharing as PDF (using existing InvoicePdfGenerator)
    private fun shareInvoiceAsPdf(invoice: Invoice) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Get shop information
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Initialize PDFBox
                PDFBoxResourceLoader.init(requireContext())

                // Create PDF generator and apply settings
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = PdfSettingsManager(requireContext()).loadSettings()
                pdfGenerator.applySettings(pdfSettings)

                // Generate PDF
                val pdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    "Invoice_${invoice.invoiceNumber}"
                )

                // Share the PDF
                sharePdfFile(pdfFile)
                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("InvoiceDetailFragment", "Error generating PDF: ${e.message}", e)
                Toast.makeText(context, "Error generating PDF: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // 6. Implement print functionality
    private fun printInvoice() {
        val invoice = viewModel.invoice.value ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Get shop information
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Initialize PDFBox
                PDFBoxResourceLoader.init(requireContext())

                // Create PDF generator and apply settings
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfSettings = PdfSettingsManager(requireContext()).loadSettings()
                pdfGenerator.applySettings(pdfSettings)

                // Generate PDF
                val pdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    "Invoice_${invoice.invoiceNumber}"
                )

                // Create content URI via FileProvider
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    pdfFile
                )

                // Create print intent
                printPdf(uri)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("InvoiceDetailFragment", "Error printing invoice: ${e.message}", e)
                Toast.makeText(context, "Error printing invoice: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // 7. Helper function for printing PDF
    private fun printPdf(documentUri: Uri) {
        try {
            val printManager =
                requireActivity().getSystemService(Context.PRINT_SERVICE) as PrintManager

            val jobName = "Invoice_${viewModel.invoice.value?.invoiceNumber}"

            val printAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }

                        val info = PrintDocumentInfo.Builder(jobName)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build()

                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>?,
                        destination: ParcelFileDescriptor?,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        try {
                            val input =
                                requireContext().contentResolver.openInputStream(documentUri)
                            val output = FileOutputStream(destination?.fileDescriptor)

                            val buffer = ByteArray(1024)
                            var bytesRead: Int

                            while (input?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }

                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))

                            input?.close()
                            output.close()

                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message)
                            Log.e("InvoiceDetailFragment", "Error writing print data", e)
                        }
                    }
                }
            } else {
                // For older Android versions - fallback to an external viewer
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(documentUri, "application/pdf")
                intent.flags =
                    Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_GRANT_READ_URI_PERMISSION

                if (intent.resolveActivity(requireActivity().packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
                }

                binding.progressBar.visibility = View.GONE
                return
            }

            // Start print job
            printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
            Toast.makeText(context, "Preparing document for printing...", Toast.LENGTH_SHORT).show()

            binding.progressBar.visibility = View.GONE

        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            Log.e("InvoiceDetailFragment", "Error printing PDF", e)
            Toast.makeText(context, "Error printing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun navigateToCustomerDetail() {
        val customerId = viewModel.customer.value?.id

        if (customerId != null) {
            // Navigate to customer detail screen using the customerId
            val action =
                InvoiceDetailFragmentDirections.actionInvoiceDetailFragmentToCustomerDetailFragment(
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

    private fun showAddPaymentBottomSheet() {
        val invoice = viewModel.invoice.value ?: return
        val isWholesaler =
            viewModel.customer.value?.customerType.equals("Wholesaler", ignoreCase = true)

        val totalAmount = invoice.totalAmount
        val paidAmount = invoice.paidAmount
        val dueAmount = totalAmount - paidAmount

        if (dueAmount <= 0) {
            val message = if (isWholesaler)
                "Purchase is already fully paid"
            else
                "Invoice is already fully paid"
            showErrorMessage(message)
            return
        }

        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, paidAmount)
        if (isWholesaler) {
            paymentSheet.setTitle("Add Payment to Supplier")
            paymentSheet.setDescription(
                "Purchase Order Total: ₹${
                    String.format(
                        "%.2f",
                        totalAmount
                    )
                }"
            )
        } else {
            paymentSheet.setTitle("Add Payment")
            paymentSheet.setDescription("Invoice Total: ₹${String.format("%.2f", totalAmount)}")
        }
        paymentSheet.setAmount(dueAmount)

        paymentSheet.setOnPaymentAddedListener(object :
            PaymentEntryBottomSheet.OnPaymentAddedListener {
            override fun onPaymentAdded(payment: Payment) {
                viewModel.addPaymentToInvoice(payment)
            }
        })
        paymentSheet.show(parentFragmentManager, "PaymentEntryBottomSheet")
    }


    /**
     * Calculate the total extra charges for a specific item
     */
    private fun getItemExtraChargesTotal(item: InvoiceItem): Double {
        return item.itemDetails.listOfExtraCharges.sumOf { charge ->
            charge.amount * item.quantity
        }
    }

    /**
     * Calculate the tax amount for a specific item
     */
    private fun getItemTaxAmount(item: InvoiceItem): Double {
        val itemSubtotal = item.price * item.quantity
        val itemExtraCharges = getItemExtraChargesTotal(item)
        return (itemSubtotal + itemExtraCharges) * (item.itemDetails.taxRate / 100.0)
    }


    private fun openItemEditor(item: InvoiceItem) {
        // Get the current invoice
        val invoice = viewModel.invoice.value ?: return

        // Store original item price for later comparison
        val originalItemTotal = item.price * item.quantity
        val originalItemContribution =
            originalItemTotal + getItemExtraChargesTotal(item) + getItemTaxAmount(item)

        // Store total paid amount
        val totalPaid = invoice.paidAmount

        // Calculate how low the price can go without causing negative balance
        val otherItemsTotal = invoice.totalAmount - originalItemContribution
        val minimumAllowedTotal = Math.max(0.0, totalPaid - otherItemsTotal)

        // Create and configure the bottom sheet
        val bottomSheet = ItemSelectionBottomSheet.newInstance()
        bottomSheet.setItemForEdit(item.itemDetails)

        bottomSheet.setOnItemSelectedListener(object :
            ItemSelectionBottomSheet.OnItemSelectedListener {
            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
                // This shouldn't happen during editing
            }

            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
                Log.d(
                    "InvoiceDetailFragment",
                    "Item updated with ${updatedItem.listOfExtraCharges.size} extra charges"
                )

                // Calculate what the new total would be for this item
                val newQuantity = item.quantity // Quantity remains the same
                val newItemTotal = price * newQuantity

                // Create temporary item to calculate extras and tax
                val tempItem = InvoiceItem(
                    id = item.id,
                    itemId = item.itemId,
                    quantity = newQuantity,
                    itemDetails = updatedItem,
                    price = price
                )

                val newItemExtraCharges = getItemExtraChargesTotal(tempItem)
                val newItemTax = getItemTaxAmount(tempItem)
                val newItemContribution = newItemTotal + newItemExtraCharges + newItemTax

                // Check if this would cause the total to go below the minimum allowed
                if (newItemContribution < minimumAllowedTotal) {
                    // Calculate how much the item total can be reduced
                    val maxReduction = originalItemContribution - minimumAllowedTotal
                    val formatter = DecimalFormat("#,##,##0.00")

                    // Show warning dialog
                    AlertDialog.Builder(requireContext())
                        .setTitle("Cannot Update Item")
                        .setMessage(
                            "This change would reduce the invoice total below the amount already paid.\n\n" +
                                    "Current amount paid: ₹${formatter.format(totalPaid)}\n" +
                                    "Maximum reduction allowed: ₹${formatter.format(maxReduction)}\n\n" +
                                    "Please adjust your changes or remove some payments first."
                        )
                        .setPositiveButton("OK", null)
                        .show()

                    return
                }

                // Safe to proceed - create updated invoice item
                val updatedInvoiceItem = InvoiceItem(
                    id = item.id,
                    itemId = item.itemId,
                    quantity = item.quantity,
                    itemDetails = updatedItem,
                    price = price
                )

                // Update in viewmodel
                viewModel.updateInvoiceItem(updatedInvoiceItem)
                EventBus.postInvoiceUpdated()
                EventBus.postInventoryUpdated()
            }
        })

        bottomSheet.show(parentFragmentManager, "ItemEditorBottomSheet")
    }

    private fun confirmDeleteItem(item: InvoiceItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to remove this item from the invoice?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.removeInvoiceItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeletePayment(payment: Payment) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Payment")
            .setMessage(
                "Are you sure you want to delete this payment of ₹${
                    DecimalFormat("#,##,##0.00").format(
                        payment.amount
                    )
                }?"
            )
            .setPositiveButton("Delete") { _, _ ->
                viewModel.removePayment(payment)
            }
            .setNegativeButton("Cancel", null)
            .show()
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


    private fun confirmDeleteInvoice() {
        val isWholesaler = viewModel.customer.value?.customerType.equals("Wholesaler", ignoreCase = true)

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
                // Show loading indicator
                binding.progressBar.visibility = View.VISIBLE

                // Call deleteInvoice with a completion callback
                viewModel.deleteInvoice { success ->
                    // Make sure we're still attached to a context
                    if (!isAdded) return@deleteInvoice

                    requireActivity().runOnUiThread {
                        // Check if binding is still valid
                        val binding = _binding ?: return@runOnUiThread
                        binding.progressBar.visibility = View.GONE

                        if (success) {
                            // Show a success message mentioning the recycling bin
                            Toast.makeText(
                                context,
                                "Invoice moved to recycling bin",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Post the event for refreshing the invoice list
                            EventBus.postInvoiceDeleted()
                            // Navigate back
                            findNavController().navigateUp()
                        } else {
                            // Show error message if deletion failed
                            ThemedM3Dialog(requireContext())
                                .setTitle("Error")
                                .setLayout(R.layout.dialog_error)
                                .apply {
                                    findViewById<TextView>(R.id.errorMessage)?.text =
                                        "Failed to delete invoice"
                                }
                                .setPositiveButton("OK") { errorDialog, _ ->
                                    errorDialog.dismiss()
                                }
                                .show()
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog ->
                dialog.dismiss()
            }
            .show()
    }
    // Utility methods for error and loading handling
    private fun showErrorMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun updateLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    // Placeholder methods for external actions
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

//    private fun messageCustomerOnWhatsApp() {
//        val phone = viewModel.getCustomerPhone()
//        val invoice = viewModel.invoice.value
//
//        if (phone.isEmpty() || invoice == null) {
//            Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        viewLifecycleOwner.lifecycleScope.launch {
//            try {
//                binding.progressBar.visibility = View.VISIBLE
//
//                // Get shop information
//                val shop = ShopManager.getShopDetails(requireContext())
//
//                if (shop == null) {
//                    binding.progressBar.visibility = View.GONE
//                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
//                    return@launch
//                }
//
//                // Initialize PDFBox
//                PDFBoxResourceLoader.init(requireContext())
//
//                // Create PDF generator and apply settings
//                val pdfGenerator = InvoicePdfGenerator(requireContext())
//                val pdfSettings = PdfSettingsManager(requireContext()).loadSettings()
//                pdfGenerator.applySettings(pdfSettings)
//
//                // Generate PDF
//                val pdfFile = pdfGenerator.generateInvoicePdf(
//                    invoice,
//                    shop,
//                    "Invoice_${invoice.invoiceNumber}"
//                )
//
//                // Create content URI via FileProvider
//                val uri = FileProvider.getUriForFile(
//                    requireContext(),
//                    "${requireContext().packageName}.provider",
//                    pdfFile
//                )
//
//                // Format phone number (remove any non-digit characters except +)
//                val formattedPhone = phone.replace(Regex("[^\\d+]"), "")
//
//                try {
//                    // Create message text
//                    val message = "Invoice #${invoice.invoiceNumber}"
//
//                    // Create intent for WhatsApp
//                    val whatsappIntent = Intent(Intent.ACTION_SEND)
//                    whatsappIntent.type = "application/pdf"
//                    whatsappIntent.putExtra(Intent.EXTRA_STREAM, uri)
//                    whatsappIntent.putExtra(Intent.EXTRA_TEXT, message)
//                    whatsappIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                    whatsappIntent.setPackage("com.whatsapp")
//
//                    // Try WhatsApp Business if needed
//                    val whatsappBusinessIntent = Intent(Intent.ACTION_SEND)
//                    whatsappBusinessIntent.type = "application/pdf"
//                    whatsappBusinessIntent.putExtra(Intent.EXTRA_STREAM, uri)
//                    whatsappBusinessIntent.putExtra(Intent.EXTRA_TEXT, message)
//                    whatsappBusinessIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                    whatsappBusinessIntent.setPackage("com.whatsapp.w4b")
//
//                    // Check which app is available
//                    val packageManager = requireActivity().packageManager
//                    val whatsappInstalled = whatsappIntent.resolveActivity(packageManager) != null
//                    val whatsappBusinessInstalled = whatsappBusinessIntent.resolveActivity(packageManager) != null
//
//                    when {
//                        whatsappInstalled -> {
//                            startActivity(whatsappIntent)
//                        }
//                        whatsappBusinessInstalled -> {
//                            startActivity(whatsappBusinessIntent)
//                        }
//                        else -> {
//                            // Neither app is installed, show error with themed dialog
//                            ThemedM3Dialog(requireContext())
//                                .setTitle("WhatsApp Not Found")
//                                .setLayout(R.layout.dialog_error)
//                                .apply {
//                                    findViewById<TextView>(R.id.errorMessage)?.text =
//                                        "WhatsApp is not installed. Would you like to share the invoice using another app?"
//                                }
//                                .setPositiveButton("Yes") { dialog, _ ->
//                                    // Create general sharing intent as fallback
//                                    val shareIntent = Intent(Intent.ACTION_SEND)
//                                    shareIntent.type = "application/pdf"
//                                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
//                                    shareIntent.putExtra(Intent.EXTRA_TEXT, message)
//                                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                                    startActivity(Intent.createChooser(shareIntent, "Share Invoice PDF"))
//                                    dialog.dismiss()
//                                }
//                                .setNegativeButton("No") { dialog ->
//                                    dialog.dismiss()
//                                }
//                                .show()
//                        }
//                    }
//                } catch (e: Exception) {
//                    // Show error with themed dialog
//                    ThemedM3Dialog(requireContext())
//                        .setTitle("Error")
//                        .setLayout(R.layout.dialog_error)
//                        .apply {
//                            findViewById<TextView>(R.id.errorMessage)?.text =
//                                "Could not share via WhatsApp: ${e.message}"
//                        }
//                        .setPositiveButton("OK") { dialog, _ ->
//                            dialog.dismiss()
//                        }
//                        .show()
//
//                    Log.e("InvoiceDetailFragment", "Error sharing to WhatsApp: ${e.message}", e)
//                }
//
//                binding.progressBar.visibility = View.GONE
//
//            } catch (e: Exception) {
//                binding.progressBar.visibility = View.GONE
//                Log.e("InvoiceDetailFragment", "Error creating PDF: ${e.message}", e)
//
//                // Show error with themed dialog
//                ThemedM3Dialog(requireContext())
//                    .setTitle("Error")
//                    .setLayout(R.layout.dialog_error)
//                    .apply {
//                        findViewById<TextView>(R.id.errorMessage)?.text =
//                            "Error creating PDF: ${e.message}"
//                    }
//                    .setPositiveButton("OK") { dialog, _ ->
//                        dialog.dismiss()
//                    }
//                    .show()
//            }
//        }
//    }

    // Add this method to InvoiceDetailFragment
    private fun generateAndSavePdf() {
        val invoice = viewModel.invoice.value ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                // Get shop information
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                    return@launch
                }

                // Initialize PDFBox
                PDFBoxResourceLoader.init(requireContext())

                // Create the PDF generator
                val pdfGenerator = InvoicePdfGenerator(requireContext())

                // Load PDF settings and apply them - this is the key addition
                val pdfSettingsManager = PdfSettingsManager(requireContext())
                val pdfSettings = pdfSettingsManager.loadSettings()
                pdfGenerator.applySettings(pdfSettings)

                // Generate the PDF
                val pdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    "Invoice_${invoice.invoiceNumber}"
                )

                // Hide progress and share the PDF
                binding.progressBar.visibility = View.GONE
                sharePdfFile(pdfFile)

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("InvoiceDetailsFragment", "Error generating PDF: ${e.message}", e)
                Toast.makeText(
                    requireContext(),
                    "Error generating PDF: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun sharePdfFile(pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                pdfFile
            )

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


    // Share invoice summary to WhatsApp
// Share invoice summary to WhatsApp
// Share invoice summary to WhatsApp
    private fun shareInvoiceToWhatsApp() {
        val invoice = viewModel.invoice.value ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // First try to get shop information
                val shop = ShopManager.getShopCoroutine(requireContext())

                // Create a nicely formatted invoice summary
                val message = buildString {
                    // Shop information header if available
                    if (shop != null && shop.shopName.isNotEmpty()) {
                        append("*${shop.shopName}*\n")
                        if (shop.address.isNotEmpty()) {
                            append("${shop.address}\n")
                        }
                        if (shop.phoneNumber.isNotEmpty()) {
                            append("Phone: ${shop.phoneNumber}\n")
                        }
                        if (shop.hasGst && shop.gstNumber.isNotEmpty()) {
                            append("GST: ${shop.gstNumber}\n")
                        }
                        append("\n")
                    }

                    append("*INVOICE: ${invoice.invoiceNumber}*\n")

                    // Invoice date
                    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    append("Date: ${dateFormatter.format(Date(invoice.invoiceDate))}\n\n")

                    // Customer info
                    append("*Customer:*\n")
                    append("${invoice.customerName}\n")
                    if (invoice.customerPhone.isNotEmpty()) {
                        append("Phone: ${invoice.customerPhone}\n")
                    }
                    if (invoice.customerAddress.isNotEmpty()) {
                        append("Address: ${invoice.customerAddress}\n")
                    }
                    append("\n")

                    // Items summary
                    append("*Items:*\n")
                    invoice.items.forEach { item ->
                        append("• ${item.itemDetails.displayName}")
                        if (item.itemDetails.purity.isNotEmpty()) {
                            append(" (${item.itemDetails.purity})")
                        }
                        if (item.quantity > 1) {
                            append(" x ${item.quantity}")
                        }
                        append(": ₹${DecimalFormat("#,##,##0.00").format(item.price * item.quantity)}\n")
                    }
                    append("\n")

                    // Payment summary
                    val formatter = DecimalFormat("#,##,##0.00")
                    append("*Payment Details:*\n")
                    append("Subtotal: ₹${formatter.format(calculateSubtotal(invoice))}\n")

                    // Check if there are any extra charges to show
                    val extraCharges = calculateExtraCharges(invoice)
                    if (extraCharges > 0) {
                        append("Extra Charges: ₹${formatter.format(extraCharges)}\n")
                    }

                    append("Tax: ₹${formatter.format(calculateTax(invoice))}\n")
                    append("Total Amount: ₹${formatter.format(invoice.totalAmount)}\n")
                    append("Amount Paid: ₹${formatter.format(invoice.paidAmount)}\n")

                    val balanceDue = invoice.totalAmount - invoice.paidAmount
                    append("Balance Due: ₹${formatter.format(balanceDue)}\n")

                    // Payment status
                    val paymentStatus = when {
                        balanceDue <= 0 -> "PAID"
                        invoice.paidAmount > 0 -> "PARTIAL"
                        else -> "UNPAID"
                    }
                    append("Status: *$paymentStatus*\n\n")

                    // Add notes if present
                    if (invoice.notes.isNotEmpty()) {
                        append("*Notes:*\n${invoice.notes}\n")
                    }
                }

                // Create separate intents for WhatsApp and WhatsApp Business
                val whatsappIntent = Intent(Intent.ACTION_SEND)
                whatsappIntent.type = "text/plain"
                whatsappIntent.setPackage("com.whatsapp")
                whatsappIntent.putExtra(Intent.EXTRA_TEXT, message)

                val whatsappBusinessIntent = Intent(Intent.ACTION_SEND)
                whatsappBusinessIntent.type = "text/plain"
                whatsappBusinessIntent.setPackage("com.whatsapp.w4b")
                whatsappBusinessIntent.putExtra(Intent.EXTRA_TEXT, message)

                // Check which app is available
                val packageManager = requireActivity().packageManager
                val whatsappInstalled = whatsappIntent.resolveActivity(packageManager) != null
                val whatsappBusinessInstalled =
                    whatsappBusinessIntent.resolveActivity(packageManager) != null

                try {
                    when {
                        whatsappInstalled -> {
                            startActivity(whatsappIntent)
                        }

                        whatsappBusinessInstalled -> {
                            startActivity(whatsappBusinessIntent)
                        }

                        else -> {
                            // Neither WhatsApp nor WhatsApp Business installed, use general share
                            val generalIntent = Intent(Intent.ACTION_SEND)
                            generalIntent.type = "text/plain"
                            generalIntent.putExtra(Intent.EXTRA_TEXT, message)
                            startActivity(Intent.createChooser(generalIntent, "Share invoice via"))
                        }
                    }
                } catch (e: Exception) {
                    // Fall back to general share on any error
                    val generalIntent = Intent(Intent.ACTION_SEND)
                    generalIntent.type = "text/plain"
                    generalIntent.putExtra(Intent.EXTRA_TEXT, message)
                    startActivity(Intent.createChooser(generalIntent, "Share invoice via"))
                }
            } catch (e: Exception) {
                showErrorMessage("Error sharing invoice: ${e.message}")
                Log.e("InvoiceDetailFragment", "Error sharing invoice", e)
            }
        }
    }

    // Setup notes editing
    private fun setupNotesEditing() {
        binding.editNotesButton.setOnClickListener {
            if (isEditingNotes) {
                // Save notes
                val notes = binding.notesEditText.text.toString()
                viewModel.updateInvoiceNotes(notes)
                toggleNotesEditingMode(false)
            } else {
                // Enter edit mode
                toggleNotesEditingMode(true)
            }
        }
    }

    private fun toggleNotesEditingMode(isEditing: Boolean) {
        isEditingNotes = isEditing

        if (isEditing) {
            // Show edit text, hide regular text view
            binding.notesContent.visibility = View.GONE
            binding.emptyNotesText.visibility = View.GONE
            binding.notesEditLayout.visibility = View.VISIBLE
            binding.notesEditText.setText(binding.notesContent.text)
            binding.notesEditText.requestFocus()

            // Show keyboard
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.notesEditText, InputMethodManager.SHOW_IMPLICIT)

            // Change button text
            binding.editNotesButton.setText(R.string.save_notes)
        } else {
            // Hide edit text, show regular text view
            binding.notesContent.visibility = View.VISIBLE
            binding.notesEditLayout.visibility = View.GONE

            // Hide keyboard
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(binding.notesEditText.windowToken, 0)

            // Change button text back
            binding.editNotesButton.setText(R.string.edit_notes)
        }
    }

    // Back press handling for notes editing
    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isEditingNotes) {
                // If editing notes, ask to save changes
                AlertDialog.Builder(requireContext())
                    .setTitle("Save Notes")
                    .setMessage("Do you want to save your changes to the notes?")
                    .setPositiveButton("Save") { _, _ ->
                        val notes = binding.notesEditText.text.toString()
                        viewModel.updateInvoiceNotes(notes)
                        toggleNotesEditingMode(false)
                        findNavController().navigateUp()
                    }
                    .setNegativeButton("Discard") { _, _ ->
                        toggleNotesEditingMode(false)
                        findNavController().navigateUp()
                    }
                    .setNeutralButton("Cancel", null)
                    .show()
            } else {
                // Normal back behavior
                findNavController().navigateUp()
            }
        }
    }


    private fun updateExtraChargesDisplay(invoice: Invoice) {
        // Clear existing extra charges
        binding.extraChargesContainer.removeAllViews()

        // Get all extra charges from all items
        val allExtraCharges = mutableListOf<Pair<String, Double>>()

        invoice.items.forEach { item ->
            // Log for debugging
            Log.d(
                "InvoiceDetailFragment",
                "Item: ${item.itemDetails.displayName} has ${item.itemDetails.listOfExtraCharges.size} extra charges"
            )

            item.itemDetails.listOfExtraCharges.forEach { charge ->
                // Multiply each charge by the item quantity
                allExtraCharges.add(Pair(charge.name, charge.amount * item.quantity))
                // Log each charge for debugging
                Log.d(
                    "InvoiceDetailFragment",
                    "Adding charge: ${charge.name} = ${charge.amount * item.quantity}"
                )
            }
        }

        // If there are no extra charges, hide the container
        if (allExtraCharges.isEmpty()) {
            binding.extraChargesLayout.visibility = View.GONE
            Log.d("InvoiceDetailFragment", "No extra charges found, hiding container")
            return
        }

        // Show the container
        binding.extraChargesLayout.visibility = View.VISIBLE
        Log.d("InvoiceDetailFragment", "Showing ${allExtraCharges.size} extra charges")

        // Create a view for each extra charge when creating a new signed app bundle we need to fill key store ,when uploding on google play store ,should google give us the keystore
        for (charge in allExtraCharges) {
            val chargeView = layoutInflater.inflate(
                R.layout.item_extra_charge_layout,
                binding.extraChargesContainer,
                false
            )

            val chargeName = chargeView.findViewById<TextView>(R.id.extraChargeNameText)
            val chargeAmount = chargeView.findViewById<TextView>(R.id.extraChargeAmountText)

            chargeName.text = charge.first
            chargeAmount.text = "₹${DecimalFormat("#,##,##0.00").format(charge.second)}"

            binding.extraChargesContainer.addView(chargeView)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}