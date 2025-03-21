package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jewelrypos.swarnakhatabook.Adapters.InvoiceItemAdapter
import com.jewelrypos.swarnakhatabook.Adapters.InvoicePrintAdapter
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

class InvoiceDetailFragment : Fragment() {

    private var _binding: FragmentInvoiceDetailBinding? = null
    private val binding get() = _binding!!

    private val args: InvoiceDetailFragmentArgs by navArgs()

    private val viewModel: InvoiceDetailViewModel by viewModels {
        InvoiceDetailViewModelFactory(requireActivity().application)
    }

    private lateinit var itemsAdapter: InvoiceItemAdapter
    private lateinit var paymentsAdapter: PaymentsAdapter

    val phoneNumber: Int = 0


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
        observeViewModel()
        setupClickListeners()

        // Load invoice data based on passed ID
        viewModel.loadInvoice(args.invoiceId)
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
//                    navigateToEditInvoice()
                    true
                }

                R.id.action_duplicate -> {
                    duplicateInvoice()
                    true
                }

                R.id.action_share_whatsapp -> {
                    shareToWhatsApp()
                    true
                }

                R.id.action_save_pdf -> {
                    generateAndSavePdf()
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

    private fun setupRecyclerViews() {
        // Setup items recycler view
        itemsAdapter = InvoiceItemAdapter(emptyList())
        binding.itemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = itemsAdapter
        }

        // Setup payments recycler view
        paymentsAdapter = PaymentsAdapter(emptyList())
        paymentsAdapter.setOnPaymentActionListener(object :
            PaymentsAdapter.OnPaymentActionListener {
            override fun onRemovePayment(payment: Payment) {
                // In detail view, users shouldn't remove payments directly
            }

            override fun onEditPayment(payment: Payment) {
                // In detail view, users shouldn't edit payments directly
            }
        })

        binding.paymentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = paymentsAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.invoice.observe(viewLifecycleOwner) { invoice ->
            invoice?.let { updateUI(it) }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUI(invoice: Invoice) {
        // Update invoice header details
        binding.invoiceNumber.text = invoice.invoiceNumber

        // Format invoice date
        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.invoiceDate.text = dateFormatter.format(Date(invoice.invoiceDate))

        // Set payment status
        val balanceDue = invoice.totalAmount - invoice.paidAmount
        val paymentStatus = when {
            balanceDue <= 0 -> "PAID"
            invoice.paidAmount > 0 -> "PARTIAL"
            else -> "UNPAID"
        }
        binding.paymentStatus.text = paymentStatus

        // Set status background color
        val statusColor = when (paymentStatus) {
            "PAID" -> R.color.status_paid
            "PARTIAL" -> R.color.status_partial
            else -> R.color.status_unpaid
        }
        binding.paymentStatus.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), statusColor)

        // Update customer details
        binding.customerName.text = invoice.customerName
        binding.customerPhone.text = invoice.customerPhone
        binding.customerAddress.text = invoice.customerAddress

        // Update items list
        itemsAdapter.updateItems(invoice.items)

        // Update financial information
        val currencyFormatter = DecimalFormat("#,##,##0.00")
        binding.subtotalValue.text = "₹${currencyFormatter.format(calculateSubtotal(invoice))}"
        binding.taxValue.text = "₹${currencyFormatter.format(calculateTax(invoice))}"
        binding.totalValue.text = "₹${currencyFormatter.format(invoice.totalAmount)}"
        binding.amountPaidValue.text = "₹${currencyFormatter.format(invoice.paidAmount)}"
        binding.balanceDueValue.text = "₹${currencyFormatter.format(balanceDue)}"

        // Add extra charges section
        updateExtraChargesDisplay(invoice)

        // Update payments list
        if (invoice.payments.isEmpty()) {
            binding.noPaymentsText.visibility = View.VISIBLE
            binding.paymentsRecyclerView.visibility = View.GONE
        } else {
            binding.noPaymentsText.visibility = View.GONE
            binding.paymentsRecyclerView.visibility = View.VISIBLE
            paymentsAdapter.updatePayments(invoice.payments)
        }

        // Update notes
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
        // Calculate subtotal (without tax)
        return invoice.items.sumOf { it.price * it.quantity }
    }

    private fun calculateTax(invoice: Invoice): Double {
        // Calculate tax amount - can be more complex if needed
        return invoice.totalAmount - calculateSubtotal(invoice)
    }

    private fun setupClickListeners() {
        // Edit items button
//        binding.editItemsButton.setOnClickListener {
//            navigateToEditItems()
//        }

        // Add payment button
        binding.addPaymentButton.setOnClickListener {
            showAddPaymentBottomSheet()
        }

        // Call customer button
        binding.callCustomerButton.setOnClickListener {
            callCustomer()
        }

        // WhatsApp customer button
        binding.whatsappCustomerButton.setOnClickListener {
            messageCustomerOnWhatsApp()
        }

        // Print button
        binding.printButton.setOnClickListener {
            printInvoice()
        }

        // Share button
        binding.shareButton.setOnClickListener {
            shareInvoice()
        }
    }

    private fun updateExtraChargesDisplay(invoice: Invoice) {
        // Clear existing extra charges
        binding.extraChargesContainer.removeAllViews()

        // Get all extra charges from all items
        val allExtraCharges = mutableListOf<Pair<String, Double>>()

        invoice.items.forEach { item ->
            item.itemDetails.listOfExtraCharges.forEach { charge ->
                // Multiply each charge by the item quantity
                allExtraCharges.add(Pair(charge.name, charge.amount * item.quantity))
            }
        }

        // If there are no extra charges, hide the container
        if (allExtraCharges.isEmpty()) {
            binding.extraChargesLayout.visibility = View.GONE
            return
        }

        // Show the container
        binding.extraChargesLayout.visibility = View.VISIBLE

        // Create a view for each extra charge
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



//    private fun navigateToEditInvoice() {
//        viewModel.invoice.value?.let { invoice ->
//            val action = InvoiceDetailFragmentDirections
//                .actionInvoiceDetailFragmentToInvoiceCreationFragment(invoice.invoiceNumber)
//            findNavController().navigate(action)
//        }
//    }

//    private fun navigateToEditItems() {
//        viewModel.invoice.value?.let { invoice ->
//            val action = InvoiceDetailFragmentDirections
//                .actionInvoiceDetailFragmentToEditInvoiceItemsFragment(invoice.invoiceNumber)
//            findNavController().navigate(action)
//        }
//    }

    private fun showAddPaymentBottomSheet() {
        viewModel.invoice.value?.let { invoice ->
            val totalAmount = invoice.totalAmount
            val paidAmount = invoice.paidAmount
            val dueAmount = totalAmount - paidAmount

            if (dueAmount <= 0) {
                Toast.makeText(context, "Invoice is already fully paid", Toast.LENGTH_SHORT).show()
                return
            }

            val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, paidAmount)
            paymentSheet.setTitle("Add Payment")
            paymentSheet.setDescription(
                "Invoice Total: ₹${
                    DecimalFormat("#,##,##0.00").format(
                        totalAmount
                    )
                }"
            )

            paymentSheet.setOnPaymentAddedListener(object :
                PaymentEntryBottomSheet.OnPaymentAddedListener {
                override fun onPaymentAdded(payment: Payment) {
                    viewModel.addPaymentToInvoice(payment)
                }
            })
            paymentSheet.show(parentFragmentManager, "PaymentEntryBottomSheet")
        }
    }

    private fun callCustomer() {
        val phone = viewModel.getCustomerPhone()
        if (phone.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
            }
            startActivity(intent)
        } else {
            Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun messageCustomerOnWhatsApp() {
        val phone = viewModel.getCustomerPhone()
        if (phone.isEmpty()) {
            Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
            return
        }

        // Format phone number for WhatsApp (remove any spaces or special chars except +)
        val formattedPhone = phone.replace(Regex("[^+0-9]"), "")

        // Prepare invoice summary message
        val invoice = viewModel.invoice.value
        val message = buildInvoiceSummaryMessage(invoice)

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${
                    Uri.encode(message)
                }".toUri()
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp not installed or error occurred", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun buildInvoiceSummaryMessage(invoice: Invoice?): String {
        if (invoice == null) return ""

        val formatter = DecimalFormat("#,##,##0.00")
        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        return """
           *Invoice: ${invoice.invoiceNumber}*
           Date: ${dateFormatter.format(Date(invoice.invoiceDate))}
           
           Total Amount: ₹${formatter.format(invoice.totalAmount)}
           Amount Paid: ₹${formatter.format(invoice.paidAmount)}
           Balance Due: ₹${formatter.format(invoice.totalAmount - invoice.paidAmount)}
           
           Thank you for your business!
           Swarna Khata Book
       """.trimIndent()
    }

    private fun shareInvoice() {
        // Generate PDF view
        val pdfFile = generatePdfFile()

        if (pdfFile != null) {
            // Get content URI via FileProvider
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                pdfFile
            )

            // Create sharing intent
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Invoice ${viewModel.invoice.value?.invoiceNumber}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Share Invoice"))
        } else {
            Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareToWhatsApp() {
        // Generate PDF view
        val pdfFile = generatePdfFile()

        if (pdfFile != null) {
            // Get content URI via FileProvider
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                pdfFile
            )

            // Create WhatsApp sharing intent
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Invoice ${viewModel.invoice.value?.invoiceNumber}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.whatsapp")
            }

            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
                // Fall back to regular sharing
                shareInvoice()
            }
        } else {
            Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAndSavePdf() {
        val pdfFile = generatePdfFile()

        if (pdfFile != null) {
            Toast.makeText(
                context,
                "PDF saved to ${pdfFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generatePdfFile(): File? {
        try {
            // Create a PdfDocument
            val pdfDocument = PdfDocument()

            // Create a page - A4 size
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            // Draw content to the page using Canvas
            val canvas = page.canvas
            renderInvoiceContent(canvas)

            // Finish the page
            pdfDocument.finishPage(page)

            // Create directory for PDF files if it doesn't exist
            val directory = File(
                requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "Invoices"
            )
            if (!directory.exists()) {
                directory.mkdirs()
            }

            // Create the output file
            val invoiceNumber = viewModel.invoice.value?.invoiceNumber ?: "unknown"
            val file = File(directory, "Invoice_${invoiceNumber}.pdf")

            // Write the PDF document to the file
            pdfDocument.writeTo(FileOutputStream(file))

            // Close the document
            pdfDocument.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun renderInvoiceContent(canvas: Canvas) {
        // This is a simplified version - a complete implementation would draw text and graphics
        // to render the invoice in a professional format

        // Get the invoice
        val invoice = viewModel.invoice.value ?: return

        // Setup text sizes and positions
        val titleTextSize = 18f
        val normalTextSize = 12f
        val smallTextSize = 10f

        // Draw company name/logo
        val paint = android.graphics.Paint().apply {
            color = ContextCompat.getColor(requireContext(), R.color.my_light_primary)
            textSize = titleTextSize
            isFakeBoldText = true
        }
        canvas.drawText("Swarna Khata Book", 50f, 50f, paint)

        // Draw invoice details
        paint.apply {
            color = android.graphics.Color.BLACK
            textSize = normalTextSize
            isFakeBoldText = false
        }
        canvas.drawText("Invoice #: ${invoice.invoiceNumber}", 50f, 80f, paint)

        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        canvas.drawText(
            "Date: ${dateFormatter.format(Date(invoice.invoiceDate))}",
            50f, 100f, paint
        )

        // Draw customer details
        paint.isFakeBoldText = true
        canvas.drawText("Customer Details", 50f, 140f, paint)
        paint.isFakeBoldText = false
        canvas.drawText("Name: ${invoice.customerName}", 50f, 160f, paint)
        canvas.drawText("Phone: ${viewModel.getCustomerPhone()}", 50f, 180f, paint)
        canvas.drawText("Address: ${viewModel.getCustomerAddress()}", 50f, 200f, paint)

        // Draw items header
        paint.isFakeBoldText = true
        canvas.drawText("Items", 50f, 240f, paint)
        paint.isFakeBoldText = false

        // Draw items
        var yPos = 260f
        for (item in invoice.items) {
            canvas.drawText(
                "${item.itemDetails.displayName} x${item.quantity}",
                50f, yPos, paint
            )

            // Draw price
            val formatter = DecimalFormat("#,##,##0.00")
            val itemTotal = item.price * item.quantity
            canvas.drawText(
                "₹${formatter.format(itemTotal)}",
                450f, yPos, paint
            )
            yPos += 20f
        }

        // Draw totals
        yPos += 20f
        canvas.drawText("Subtotal:", 350f, yPos, paint)
        canvas.drawText(
            "₹${DecimalFormat("#,##,##0.00").format(calculateSubtotal(invoice))}",
            450f, yPos, paint
        )

        yPos += 20f
        canvas.drawText("Tax:", 350f, yPos, paint)
        canvas.drawText(
            "₹${DecimalFormat("#,##,##0.00").format(calculateTax(invoice))}",
            450f, yPos, paint
        )

        yPos += 20f
        paint.isFakeBoldText = true
        canvas.drawText("Total:", 350f, yPos, paint)
        canvas.drawText(
            "₹${DecimalFormat("#,##,##0.00").format(invoice.totalAmount)}",
            450f, yPos, paint
        )

        yPos += 40f
        canvas.drawText("Amount Paid:", 350f, yPos, paint)
        canvas.drawText(
            "₹${DecimalFormat("#,##,##0.00").format(invoice.paidAmount)}",
            450f, yPos, paint
        )

        yPos += 20f
        canvas.drawText("Balance Due:", 350f, yPos, paint)
        canvas.drawText(
            "₹${DecimalFormat("#,##,##0.00").format(invoice.totalAmount - invoice.paidAmount)}",
            450f, yPos, paint
        )

        // Draw footer
        paint.isFakeBoldText = false
        paint.textSize = smallTextSize
        canvas.drawText("Thank you for your business!", 50f, 780f, paint)
    }

    private fun printInvoice() {
        // Generate bitmap of invoice layout
        val invoiceView = binding.root
        val bitmap = getBitmapFromView(invoiceView)

        // Use the Android Print Framework to print the bitmap
        val printManager =
            requireActivity().getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
        val jobName = "Invoice_${viewModel.invoice.value?.invoiceNumber}"

        val printAdapter = InvoicePrintAdapter(requireContext(), bitmap)
        printManager.print(jobName, printAdapter, null)
    }

    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(android.graphics.Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private fun duplicateInvoice() {
        viewModel.invoice.value?.let { invoice ->
            viewModel.duplicateInvoice(invoice) { success ->
                if (success) {
                    Toast.makeText(context, "Invoice duplicated successfully", Toast.LENGTH_SHORT)
                        .show()
                    // Navigate back to sales list
                    findNavController().navigateUp()
                } else {
                    Toast.makeText(context, "Failed to duplicate invoice", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun confirmDeleteInvoice() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Invoice")
            .setMessage("Are you sure you want to delete this invoice? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteInvoice()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteInvoice() {
        viewModel.invoice.value?.let { invoice ->
            viewModel.deleteInvoice(invoice) { success ->
                if (success) {
                    Toast.makeText(context, "Invoice deleted successfully", Toast.LENGTH_SHORT)
                        .show()
                    EventBus.postInvoiceDeleted()
                    // Navigate back to sales list
                    findNavController().navigateUp()
                } else {
                    Toast.makeText(context, "Failed to delete invoice", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}