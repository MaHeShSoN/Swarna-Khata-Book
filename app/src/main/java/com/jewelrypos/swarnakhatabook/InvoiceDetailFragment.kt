package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavOptions
import androidx.core.os.bundleOf
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.Adapters.EditableInvoiceItemAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemSelectionBottomSheet
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Shop
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch

//
//class InvoiceDetailFragment : Fragment() {
//
//    private var _binding: FragmentInvoiceDetailBinding? = null
//    private val binding get() = _binding!!
//
//    private val args: InvoiceDetailFragmentArgs by navArgs()
//
//    private val viewModel: InvoiceDetailViewModel by viewModels {
//        InvoiceDetailViewModelFactory(requireActivity().application)
//    }
//
//    private lateinit var itemsAdapter: InvoiceItemAdapter
//    private lateinit var paymentsAdapter: PaymentsAdapter
//
//    val phoneNumber: Int = 0
//
//    private var isInEditMode = false
//    private lateinit var editableItemsAdapter: EditableInvoiceItemAdapter
//    private var originalItems: List<InvoiceItem> = emptyList()
//    private var editedItems: MutableList<InvoiceItem> = mutableListOf()
//
//    override fun onCreateView(
//        inflater: LayoutInflater, container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        _binding = FragmentInvoiceDetailBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        setupToolbar()
//        setupRecyclerViews()
//        observeViewModel()
//        setupClickListeners()
//        setupAddItemFab()
//
//        // Handle back press for edit mode
//        requireActivity().onBackPressedDispatcher.addCallback(
//            viewLifecycleOwner,
//            object : androidx.activity.OnBackPressedCallback(true) {
//                override fun handleOnBackPressed() {
//                    if (isInEditMode) {
//                        handleEditModeBackPress()
//                    } else {
//                        findNavController().navigateUp()
//                    }
//                }
//            }
//        )
//
//        // Load invoice data based on passed ID
//        viewModel.loadInvoice(args.invoiceId)
//    }
//
//    private fun setupToolbar() {
//        binding.topAppBar.setNavigationOnClickListener {
//            if (isInEditMode) {
//                // Handle back press in edit mode with confirmation
//                handleEditModeBackPress()
//            } else {
//                // Normal back navigation
//                findNavController().navigateUp()
//            }
//        }
//
//        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
//            when (menuItem.itemId) {
//                R.id.action_edit -> {
////                    navigateToEditInvoice()
//                    true
//                }
//
//                R.id.action_duplicate -> {
//                    duplicateInvoice()
//                    true
//                }
//
//                R.id.action_share_whatsapp -> {
//                    shareToWhatsApp()
//                    true
//                }
//
//                R.id.action_save_pdf -> {
//                    generateAndSavePdf()
//                    true
//                }
//
//                R.id.action_delete -> {
//                    confirmDeleteInvoice()
//                    true
//                }
//
//                else -> false
//            }
//        }
//    }
//
//    private fun handleEditModeBackPress() {
//        if (haveItemsChanged()) {
//            // Show confirmation dialog if items have changed
//            androidx.appcompat.app.AlertDialog.Builder(requireContext())
//                .setTitle("Unsaved Changes")
//                .setMessage("You have unsaved changes to items. Save changes?")
//                .setPositiveButton("Save") { _, _ ->
//                    saveItemChanges()
//                }
//                .setNegativeButton("Discard") { _, _ ->
//                    exitEditMode(false)
//                }
//                .setNeutralButton("Cancel", null)
//                .show()
//        } else {
//            // No changes, just exit edit mode
//            exitEditMode(false)
//        }
//    }
//
//    private fun haveItemsChanged(): Boolean {
//        if (originalItems.size != editedItems.size) return true
//
//        // Create a map of original items by ID for easier comparison
//        val originalItemsMap = originalItems.associateBy { it.itemId }
//
//        // Check if any item is different from the original
//        return editedItems.any { editedItem ->
//            val originalItem = originalItemsMap[editedItem.itemId]
//
//            // If original item doesn't exist, this is a new item
//            if (originalItem == null) return@any true
//
//            // Compare quantity and price
//            editedItem.quantity != originalItem.quantity ||
//                    editedItem.price != originalItem.price
//        }
//    }
//
//    private fun exitEditMode(saveChanges: Boolean) {
//        if (saveChanges) {
//            // Save changes will be handled in saveItemChanges()
//            return
//        }
//
//        isInEditMode = false
//
//        // Restore toolbar menu
//        binding.topAppBar.menu.findItem(R.id.action_edit)?.apply {
//            setIcon(R.drawable.material_symbols__edit_rounded)
//            setTitle("Edit")
//        }
//
//        // Show regular actions and hide edit mode actions
//        binding.actionButtonsContainer.visibility = View.VISIBLE
//
//        // Switch back to regular adapter
//        binding.itemsRecyclerView.adapter = itemsAdapter
//        itemsAdapter.updateItems(originalItems)
//
//        // Hide add item button
//
//        // Restore toolbar title
//        binding.topAppBar.title = "Invoice Details"
//    }
//
//    private fun saveItemChanges() {
////        binding.progressBar.visibility = View.VISIBLE
//
//        viewModel.updateInvoiceItems(editedItems) { success ->
////            binding.progressBar.visibility = View.GONE
//
//            if (success) {
//                Toast.makeText(context, "Items updated successfully", Toast.LENGTH_SHORT).show()
//                exitEditMode(false) // Changes are already saved, just exit edit mode
//            } else {
//                Toast.makeText(context, "Failed to update items", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    // Setup editable items adapter
//    private fun setupEditableItemsAdapter() {
//        editableItemsAdapter = EditableInvoiceItemAdapter(editedItems)
//
//        editableItemsAdapter.setOnItemActionListener(object : EditableInvoiceItemAdapter.OnItemActionListener {
//            override fun onRemoveItem(item: InvoiceItem) {
//                removeItem(item)
//            }
//
//            override fun onEditItem(item: InvoiceItem) {
//                openItemEditor(item)
//            }
//
//            override fun onQuantityChanged(item: InvoiceItem, newQuantity: Int) {
//                updateItemQuantity(item, newQuantity)
//            }
//        })
//
//        binding.itemsRecyclerView.adapter = editableItemsAdapter
//    }
//
//    // Item manipulation methods
//    private fun removeItem(item: InvoiceItem) {
//        editedItems.remove(item)
//        editableItemsAdapter.updateItems(editedItems)
//        updateEditModeUI()
//    }
//
//    private fun openItemEditor(item: InvoiceItem) {
//        val bottomSheet = ItemSelectionBottomSheet.newInstance()
//        bottomSheet.setItemForEdit(item.itemDetails)
//
//        bottomSheet.setOnItemSelectedListener(object : ItemSelectionBottomSheet.OnItemSelectedListener {
//            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
//                // This shouldn't happen during editing
//            }
//
//            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
//                // Find and update the item
//                val index = editedItems.indexOfFirst { it.itemId == item.itemId }
//                if (index != -1) {
//                    editedItems[index] = InvoiceItem(
//                        itemId = item.itemId,
//                        quantity = item.quantity,
//                        itemDetails = updatedItem,
//                        price = price
//                    )
//                    editableItemsAdapter.updateItems(editedItems)
//                    updateEditModeUI()
//                }
//            }
//        })
//
//        bottomSheet.show(parentFragmentManager, "ItemEditorBottomSheet")
//    }
//
//    private fun updateItemQuantity(item: InvoiceItem, newQuantity: Int) {
//        if (newQuantity <= 0) {
//            removeItem(item)
//            return
//        }
//
//        val index = editedItems.indexOfFirst { it.itemId == item.itemId }
//        if (index != -1) {
//            editedItems[index] = item.copy(quantity = newQuantity)
//            editableItemsAdapter.updateItems(editedItems)
//            updateEditModeUI()
//        }
//    }
//
//    private fun updateEditModeUI() {
//        // Update any UI elements that depend on the items list
//        // Such as subtotal, tax, etc.
//
//        // Enable/disable save button based on changes
//        binding.saveChangesButton.isEnabled = haveItemsChanged()
//
//        // Update title to show asterisk if there are unsaved changes
//        binding.topAppBar.title = if (haveItemsChanged()) "Edit Invoice*" else "Edit Invoice"
//    }
//    private fun setupAddItemFab() {
//        binding.addItemFab.setOnClickListener {
//            openItemSelector()
//        }
//    }
//
//    private fun openItemSelector() {
//        val bottomSheet = ItemSelectionBottomSheet.newInstance()
//
//        bottomSheet.setOnItemSelectedListener(object : ItemSelectionBottomSheet.OnItemSelectedListener {
//            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
//                // Add new item with quantity 1
//                val newInvoiceItem = InvoiceItem(
//                    itemId = newItem.id,
//                    quantity = 1,
//                    itemDetails = newItem,
//                    price = price
//                )
//
//                editedItems.add(newInvoiceItem)
//                editableItemsAdapter.updateItems(editedItems)
//                updateEditModeUI()
//            }
//
//            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
//                // This shouldn't happen during adding a new item
//            }
//        })
//
//        bottomSheet.show(parentFragmentManager, "ItemSelectorBottomSheet")
//    }
//
//    private fun setupRecyclerViews() {
//        // Setup items recycler view
//        itemsAdapter = InvoiceItemAdapter(emptyList())
//        binding.itemsRecyclerView.apply {
//            layoutManager = LinearLayoutManager(context)
//            adapter = itemsAdapter
//        }
//
//        // Setup payments recycler view
//        paymentsAdapter = PaymentsAdapter(emptyList())
//        paymentsAdapter.setOnPaymentActionListener(object :
//            PaymentsAdapter.OnPaymentActionListener {
//            override fun onRemovePayment(payment: Payment) {
//                // Show confirmation dialog before deleting
//                confirmDeletePayment(payment)
//            }
//
//            override fun onEditPayment(payment: Payment) {
//                // Payment editing could be implemented here if needed
//                Toast.makeText(context, "Payment editing is not available", Toast.LENGTH_SHORT).show()
//            }
//        })
//
//        binding.paymentsRecyclerView.apply {
//            layoutManager = LinearLayoutManager(context)
//            adapter = paymentsAdapter
//        }
//    }
//
//    private fun confirmDeletePayment(payment: Payment) {
//        androidx.appcompat.app.AlertDialog.Builder(requireContext())
//            .setTitle("Delete Payment")
//            .setMessage("Are you sure you want to delete this payment of ₹${DecimalFormat("#,##,##0.00").format(payment.amount)}?")
//            .setPositiveButton("Delete") { _, _ ->
//                deletePayment(payment)
//            }
//            .setNegativeButton("Cancel", null)
//            .show()
//    }
//
//    private fun deletePayment(payment: Payment) {
//        viewModel.removePaymentFromInvoice(payment) { success ->
//            if (success) {
//                Toast.makeText(context, "Payment deleted successfully", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(context, "Failed to delete payment", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//
//
//
//
//    private fun observeViewModel() {
//        viewModel.invoice.observe(viewLifecycleOwner) { invoice ->
//            invoice?.let { updateUI(it) }
//        }
//
//        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
//            if (!errorMessage.isNullOrEmpty()) {
//                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
//            }
//        }
//    }
//
//    private fun updateUI(invoice: Invoice) {
//        // Update invoice header details
//        binding.invoiceNumber.text = invoice.invoiceNumber
//
//        // Format invoice date
//        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
//        binding.invoiceDate.text = dateFormatter.format(Date(invoice.invoiceDate))
//
//        // Set payment status
//        val balanceDue = invoice.totalAmount - invoice.paidAmount
//        val paymentStatus = when {
//            balanceDue <= 0 -> "PAID"
//            invoice.paidAmount > 0 -> "PARTIAL"
//            else -> "UNPAID"
//        }
//        binding.paymentStatus.text = paymentStatus
//
//        // Set status background color
//        val statusColor = when (paymentStatus) {
//            "PAID" -> R.color.status_paid
//            "PARTIAL" -> R.color.status_partial
//            else -> R.color.status_unpaid
//        }
//        binding.paymentStatus.backgroundTintList =
//            ContextCompat.getColorStateList(requireContext(), statusColor)
//
//        // Update customer details
//        binding.customerName.text = invoice.customerName
//        binding.customerPhone.text = invoice.customerPhone
//        binding.customerAddress.text = invoice.customerAddress
//
//        // Update items list
//        itemsAdapter.updateItems(invoice.items)
//
//        // Update financial information
//        val currencyFormatter = DecimalFormat("#,##,##0.00")
//        binding.subtotalValue.text = "₹${currencyFormatter.format(calculateSubtotal(invoice))}"
//        binding.taxValue.text = "₹${currencyFormatter.format(calculateTax(invoice))}"
//        binding.totalValue.text = "₹${currencyFormatter.format(invoice.totalAmount)}"
//        binding.amountPaidValue.text = "₹${currencyFormatter.format(invoice.paidAmount)}"
//        binding.balanceDueValue.text = "₹${currencyFormatter.format(balanceDue)}"
//
//        // Add extra charges section
//        updateExtraChargesDisplay(invoice)
//
//        // Update payments list
//        if (invoice.payments.isEmpty()) {
//            binding.noPaymentsText.visibility = View.VISIBLE
//            binding.paymentsRecyclerView.visibility = View.GONE
//        } else {
//            binding.noPaymentsText.visibility = View.GONE
//            binding.paymentsRecyclerView.visibility = View.VISIBLE
//            paymentsAdapter.updatePayments(invoice.payments)
//        }
//
//        // Update notes
//        if (invoice.notes.isNullOrEmpty()) {
//            binding.emptyNotesText.visibility = View.VISIBLE
//            binding.notesContent.visibility = View.GONE
//        } else {
//            binding.emptyNotesText.visibility = View.GONE
//            binding.notesContent.visibility = View.VISIBLE
//            binding.notesContent.text = invoice.notes
//        }
//    }
//
//    private fun calculateSubtotal(invoice: Invoice): Double {
//        // Calculate subtotal (without tax)
//        return invoice.items.sumOf { it.price * it.quantity }
//    }
//
//    private fun calculateTax(invoice: Invoice): Double {
//        // Calculate tax amount - can be more complex if needed
//        return invoice.totalAmount - calculateSubtotal(invoice)
//    }
//
//    private fun setupClickListeners() {
////         Edit items button
//        binding.editItemsButton.setOnClickListener {
//            navigateToEditItems()
//        }
//
//        // Add payment button
//        binding.addPaymentButton.setOnClickListener {
//            showAddPaymentBottomSheet()
//        }
//
//        // Call customer button
//        binding.callCustomerButton.setOnClickListener {
//            callCustomer()
//        }
//
//        // WhatsApp customer button
//        binding.whatsappCustomerButton.setOnClickListener {
//            messageCustomerOnWhatsApp()
//        }
//
//        // Print button
//        binding.printButton.setOnClickListener {
//            printInvoice()
//        }
//
//        // Share button
//        binding.shareButton.setOnClickListener {
////            shareInvoice()
//        }
//    }
//
//    private fun updateExtraChargesDisplay(invoice: Invoice) {
//        // Clear existing extra charges
//        binding.extraChargesContainer.removeAllViews()
//
//        // Get all extra charges from all items
//        val allExtraCharges = mutableListOf<Pair<String, Double>>()
//
//        invoice.items.forEach { item ->
//            item.itemDetails.listOfExtraCharges.forEach { charge ->
//                // Multiply each charge by the item quantity
//                allExtraCharges.add(Pair(charge.name, charge.amount * item.quantity))
//            }
//        }
//
//        // If there are no extra charges, hide the container
//        if (allExtraCharges.isEmpty()) {
//            binding.extraChargesLayout.visibility = View.GONE
//            return
//        }
//
//        // Show the container
//        binding.extraChargesLayout.visibility = View.VISIBLE
//
//        // Create a view for each extra charge
//        for (charge in allExtraCharges) {
//            val chargeView = layoutInflater.inflate(
//                R.layout.item_extra_charge_layout,
//                binding.extraChargesContainer,
//                false
//            )
//
//            val chargeName = chargeView.findViewById<TextView>(R.id.extraChargeNameText)
//            val chargeAmount = chargeView.findViewById<TextView>(R.id.extraChargeAmountText)
//
//            chargeName.text = charge.first
//            chargeAmount.text = "₹${DecimalFormat("#,##,##0.00").format(charge.second)}"
//
//            binding.extraChargesContainer.addView(chargeView)
//        }
//    }
//
//
//
////    private fun navigateToEditInvoice() {
////        viewModel.invoice.value?.let { invoice ->
////            val action = InvoiceDetailFragmentDirections
////                .actionInvoiceDetailFragmentToInvoiceCreationFragment(invoice.invoiceNumber)
////            findNavController().navigate(action)
////        }
////    }
//
//    private fun navigateToEditItems() {
//        viewModel.invoice.value?.let { invoice ->
//            // Create a bundle with the current items
//            val bundle = Bundle().apply {
//                putString("invoiceId", invoice.invoiceNumber)
//            }
//
//            // Navigate to the edit items fragment
//            findNavController().navigate(
//                R.id.action_invoiceDetailFragment_to_editInvoiceItemsFragment,
//                bundle
//            )
//        }
//    }
//
//    private fun showAddPaymentBottomSheet() {
//        viewModel.invoice.value?.let { invoice ->
//            val totalAmount = invoice.totalAmount
//            val paidAmount = invoice.paidAmount
//            val dueAmount = totalAmount - paidAmount
//
//            if (dueAmount <= 0) {
//                Toast.makeText(context, "Invoice is already fully paid", Toast.LENGTH_SHORT).show()
//                return
//            }
//
//            val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, paidAmount)
//            paymentSheet.setTitle("Add Payment")
//            paymentSheet.setDescription(
//                "Invoice Total: ₹${
//                    DecimalFormat("#,##,##0.00").format(
//                        totalAmount
//                    )
//                }"
//            )
//
//            paymentSheet.setOnPaymentAddedListener(object :
//                PaymentEntryBottomSheet.OnPaymentAddedListener {
//                override fun onPaymentAdded(payment: Payment) {
//                    viewModel.addPaymentToInvoice(payment)
//                }
//            })
//            paymentSheet.show(parentFragmentManager, "PaymentEntryBottomSheet")
//        }
//    }
//
//    private fun callCustomer() {
//        val phone = viewModel.getCustomerPhone()
//        if (phone.isNotEmpty()) {
//            val intent = Intent(Intent.ACTION_DIAL).apply {
//                data = Uri.parse("tel:$phone")
//            }
//            startActivity(intent)
//        } else {
//            Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun messageCustomerOnWhatsApp() {
//        val phone = viewModel.getCustomerPhone()
//        if (phone.isEmpty()) {
//            Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Format phone number for WhatsApp (remove any spaces or special chars except +)
//        val formattedPhone = phone.replace(Regex("[^+0-9]"), "")
//
//        // Prepare invoice summary message
//        val invoice = viewModel.invoice.value
//        val message = buildInvoiceSummaryMessage(invoice)
//
//        try {
//            val intent = Intent(Intent.ACTION_VIEW).apply {
//                data = "https://api.whatsapp.com/send?phone=$formattedPhone&text=${
//                    Uri.encode(message)
//                }".toUri()
//            }
//            startActivity(intent)
//        } catch (e: Exception) {
//            Toast.makeText(context, "WhatsApp not installed or error occurred", Toast.LENGTH_SHORT)
//                .show()
//        }
//    }
//
//    private fun buildInvoiceSummaryMessage(invoice: Invoice?): String {
//        if (invoice == null) return ""
//
//        val formatter = DecimalFormat("#,##,##0.00")
//        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
//
//        return """
//           *Invoice: ${invoice.invoiceNumber}*
//           Date: ${dateFormatter.format(Date(invoice.invoiceDate))}
//
//           Total Amount: ₹${formatter.format(invoice.totalAmount)}
//           Amount Paid: ₹${formatter.format(invoice.paidAmount)}
//           Balance Due: ₹${formatter.format(invoice.totalAmount - invoice.paidAmount)}
//
//           Thank you for your business!
//           Swarna Khata Book
//       """.trimIndent()
//    }
//
////    private fun shareInvoice() {
////        // Generate PDF view
//////        val pdfFile = generatePdfFile()
////
////        if (pdfFile != null) {
////            // Get content URI via FileProvider
////            val uri = FileProvider.getUriForFile(
////                requireContext(),
////                "${requireContext().packageName}.provider",
////                pdfFile
////            )
////
////            // Create sharing intent
////            val intent = Intent(Intent.ACTION_SEND).apply {
////                type = "application/pdf"
////                putExtra(Intent.EXTRA_STREAM, uri)
////                putExtra(Intent.EXTRA_SUBJECT, "Invoice ${viewModel.invoice.value?.invoiceNumber}")
////                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
////            }
////
////            startActivity(Intent.createChooser(intent, "Share Invoice"))
////        } else {
////            Toast.makeText(context, "Could not generate PDF", Toast.LENGTH_SHORT).show()
////        }
////    }
//
//    private fun shareToWhatsApp() {
//        // Generate PDF view
//        lifecycleScope.launch {
//            PDFBoxResourceLoader.init(requireContext());
//            val shop = ShopManager.getShopCoroutine(requireContext())
//            if (shop != null) {
//                val invoice = viewModel.invoice.value
//                val pdfFile =
//                    invoice?.let {
//                        InvoicePdfGenerator(requireContext()).generateInvoicePdf(
//                            it,
//                            shop, invoice.invoiceNumber + invoice.invoiceDate
//                        )
//                    }
//
//                // Get content URI via FileProvider
//                val uri = pdfFile?.let {
//                    FileProvider.getUriForFile(
//                        requireContext(),
//                        "${requireContext().packageName}.provider",
//                        it
//                    )
//                }
//
//                // Create WhatsApp sharing intent
//                val intent = Intent(Intent.ACTION_SEND).apply {
//                    type = "application/pdf"
//                    putExtra(Intent.EXTRA_STREAM, uri)
//                    putExtra(
//                        Intent.EXTRA_TEXT,
//                        "Invoice ${viewModel.invoice.value?.invoiceNumber}"
//                    )
//                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//                    setPackage("com.whatsapp")
//                }
//
//                try {
//                    startActivity(intent)
//                } catch (e: Exception) {
//                    Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
//                    // Fall back to regular sharing
////                    shareInvoice()
//                    if (pdfFile != null) {
//                        shareInvoiceWithOutWhatsApp(pdfFile)
//                    }
//                }
//            }
//        }
//
//    }
//
//    private fun shareInvoiceWithOutWhatsApp(pdfFile: File) {
//        // Get content URI via FileProvider
//        val uri = FileProvider.getUriForFile(
//            requireContext(),
//            "${requireContext().packageName}.provider",
//            pdfFile
//        )
//
//        // Create sharing intent
//        val intent = Intent(Intent.ACTION_SEND).apply {
//            type = "application/pdf"
//            putExtra(Intent.EXTRA_STREAM, uri)
//            putExtra(Intent.EXTRA_SUBJECT, "Invoice ${viewModel.invoice.value?.invoiceNumber}")
//            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//        }
//
//        startActivity(Intent.createChooser(intent, "Share Invoice"))
//    }
//
//
//    private fun generateAndSavePdf() {
//        lifecycleScope.launch {
//            val shop = ShopManager.getShopCoroutine(requireContext())
//            PDFBoxResourceLoader.init(requireContext());
//            if (shop != null) {
//                val invoice = viewModel.invoice.value
//                val pdfFile =
//                    invoice?.let {
//                        InvoicePdfGenerator(requireContext()).generateInvoicePdf(
//                            it,
//                            shop, invoice?.invoiceNumber + invoice?.invoiceDate
//                        )
//                    }
//                Toast.makeText(
//                    context,
//                    "PDF saved to ${pdfFile?.absolutePath}",
//                    Toast.LENGTH_LONG
//                ).show()
//            }
//        }
//    }
//
////    private fun generatePdfFile(): File? {
////        try {
////            // Create a PdfDocument
////            val pdfDocument = PdfDocument()
////
////            // Create a page - A4 size
////            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
////            val page = pdfDocument.startPage(pageInfo)
////
////            // Draw content to the page using Canvas
////            val canvas = page.canvas
////            renderInvoiceContent(canvas)
////
////            // Finish the page
////            pdfDocument.finishPage(page)
////
////            // Create directory for PDF files if it doesn't exist
////            val directory = File(
////                requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
////                "Invoices"
////            )
////            if (!directory.exists()) {
////                directory.mkdirs()
////            }
////
////            // Create the output file
////            val invoiceNumber = viewModel.invoice.value?.invoiceNumber ?: "unknown"
////            val file = File(directory, "Invoice_${invoiceNumber}.pdf")
////
////            // Write the PDF document to the file
////            pdfDocument.writeTo(FileOutputStream(file))
////
////            // Close the document
////            pdfDocument.close()
////
////            return file
////        } catch (e: Exception) {
////            e.printStackTrace()
////            return null
////        }
////    }
//
//    private fun renderInvoiceContent(canvas: Canvas) {
//        // This is a simplified version - a complete implementation would draw text and graphics
//        // to render the invoice in a professional format
//
//        // Get the invoice
//        val invoice = viewModel.invoice.value ?: return
//
//        // Setup text sizes and positions
//        val titleTextSize = 18f
//        val normalTextSize = 12f
//        val smallTextSize = 10f
//
//        // Draw company name/logo
//        val paint = android.graphics.Paint().apply {
//            color = ContextCompat.getColor(requireContext(), R.color.my_light_primary)
//            textSize = titleTextSize
//            isFakeBoldText = true
//        }
//        canvas.drawText("Swarna Khata Book", 50f, 50f, paint)
//
//        // Draw invoice details
//        paint.apply {
//            color = android.graphics.Color.BLACK
//            textSize = normalTextSize
//            isFakeBoldText = false
//        }
//        canvas.drawText("Invoice #: ${invoice.invoiceNumber}", 50f, 80f, paint)
//
//        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
//        canvas.drawText(
//            "Date: ${dateFormatter.format(Date(invoice.invoiceDate))}",
//            50f, 100f, paint
//        )
//
//        // Draw customer details
//        paint.isFakeBoldText = true
//        canvas.drawText("Customer Details", 50f, 140f, paint)
//        paint.isFakeBoldText = false
//        canvas.drawText("Name: ${invoice.customerName}", 50f, 160f, paint)
//        canvas.drawText("Phone: ${viewModel.getCustomerPhone()}", 50f, 180f, paint)
//        canvas.drawText("Address: ${viewModel.getCustomerAddress()}", 50f, 200f, paint)
//
//        // Draw items header
//        paint.isFakeBoldText = true
//        canvas.drawText("Items", 50f, 240f, paint)
//        paint.isFakeBoldText = false
//
//        // Draw items
//        var yPos = 260f
//        for (item in invoice.items) {
//            canvas.drawText(
//                "${item.itemDetails.displayName} x${item.quantity}",
//                50f, yPos, paint
//            )
//
//            // Draw price
//            val formatter = DecimalFormat("#,##,##0.00")
//            val itemTotal = item.price * item.quantity
//            canvas.drawText(
//                "₹${formatter.format(itemTotal)}",
//                450f, yPos, paint
//            )
//            yPos += 20f
//        }
//
//        // Draw totals
//        yPos += 20f
//        canvas.drawText("Subtotal:", 350f, yPos, paint)
//        canvas.drawText(
//            "₹${DecimalFormat("#,##,##0.00").format(calculateSubtotal(invoice))}",
//            450f, yPos, paint
//        )
//
//        yPos += 20f
//        canvas.drawText("Tax:", 350f, yPos, paint)
//        canvas.drawText(
//            "₹${DecimalFormat("#,##,##0.00").format(calculateTax(invoice))}",
//            450f, yPos, paint
//        )
//
//        yPos += 20f
//        paint.isFakeBoldText = true
//        canvas.drawText("Total:", 350f, yPos, paint)
//        canvas.drawText(
//            "₹${DecimalFormat("#,##,##0.00").format(invoice.totalAmount)}",
//            450f, yPos, paint
//        )
//
//        yPos += 40f
//        canvas.drawText("Amount Paid:", 350f, yPos, paint)
//        canvas.drawText(
//            "₹${DecimalFormat("#,##,##0.00").format(invoice.paidAmount)}",
//            450f, yPos, paint
//        )
//
//        yPos += 20f
//        canvas.drawText("Balance Due:", 350f, yPos, paint)
//        canvas.drawText(
//            "₹${DecimalFormat("#,##,##0.00").format(invoice.totalAmount - invoice.paidAmount)}",
//            450f, yPos, paint
//        )
//
//        // Draw footer
//        paint.isFakeBoldText = false
//        paint.textSize = smallTextSize
//        canvas.drawText("Thank you for your business!", 50f, 780f, paint)
//    }
//
//    private fun printInvoice() {
//        // Generate bitmap of invoice layout
//        val invoiceView = binding.root
//        val bitmap = getBitmapFromView(invoiceView)
//
//        // Use the Android Print Framework to print the bitmap
//        val printManager =
//            requireActivity().getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
//        val jobName = "Invoice_${viewModel.invoice.value?.invoiceNumber}"
//
//        val printAdapter = InvoicePrintAdapter(requireContext(), bitmap)
//        printManager.print(jobName, printAdapter, null)
//    }
//
//    private fun getBitmapFromView(view: View): Bitmap {
//        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(returnedBitmap)
//        val bgDrawable = view.background
//        if (bgDrawable != null) {
//            bgDrawable.draw(canvas)
//        } else {
//            canvas.drawColor(android.graphics.Color.WHITE)
//        }
//        view.draw(canvas)
//        return returnedBitmap
//    }
//
//    private fun duplicateInvoice() {
//        viewModel.invoice.value?.let { invoice ->
//            viewModel.duplicateInvoice(invoice) { success ->
//                if (success) {
//                    Toast.makeText(context, "Invoice duplicated successfully", Toast.LENGTH_SHORT)
//                        .show()
//                    // Navigate back to sales list
//                    findNavController().navigateUp()
//                } else {
//                    Toast.makeText(context, "Failed to duplicate invoice", Toast.LENGTH_SHORT)
//                        .show()
//                }
//            }
//        }
//    }
//
//    private fun confirmDeleteInvoice() {
//        MaterialAlertDialogBuilder(requireContext())
//            .setTitle("Delete Invoice")
//            .setMessage("Are you sure you want to delete this invoice? This action cannot be undone.")
//            .setPositiveButton("Delete") { _, _ ->
//                deleteInvoice()
//            }
//            .setNegativeButton("Cancel", null)
//            .show()
//    }
//
//    private fun deleteInvoice() {
//        viewModel.invoice.value?.let { invoice ->
//            viewModel.deleteInvoice(invoice) { success ->
//                if (success) {
//                    Toast.makeText(context, "Invoice deleted successfully", Toast.LENGTH_SHORT)
//                        .show()
//                    EventBus.postInvoiceDeleted()
//                    // Navigate back to sales list
//                    findNavController().navigateUp()
//                } else {
//                    Toast.makeText(context, "Failed to delete invoice", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}


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

        binding.topAppBar.overflowIcon = ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)
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
                R.id.action_duplicate -> {
                    confirmDuplicateInvoice()
                    true
                }

                R.id.action_share_whatsapp -> {
                    shareInvoiceToWhatsApp()
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


    private fun updatePaymentStatus(invoice: Invoice) {
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
        val subtotal = calculateSubtotal(invoice)
        val extraCharges = calculateExtraCharges(invoice)

        // Tax is whatever remains after subtracting subtotal and extra charges
        return invoice.totalAmount - subtotal - extraCharges
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

    private fun showAddPaymentBottomSheet() {
        val invoice = viewModel.invoice.value ?: return
        val totalAmount = invoice.totalAmount
        val paidAmount = invoice.paidAmount
        val dueAmount = totalAmount - paidAmount

        if (dueAmount <= 0) {
            showErrorMessage("Invoice is already fully paid")
            return
        }

        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, paidAmount)
        paymentSheet.setTitle("Add Payment")
        paymentSheet.setDescription("Invoice Total: ₹${String.format("%.2f", totalAmount)}")
        paymentSheet.setAmount(dueAmount)

        paymentSheet.setOnPaymentAddedListener(object :
            PaymentEntryBottomSheet.OnPaymentAddedListener {
            override fun onPaymentAdded(payment: Payment) {
                viewModel.addPaymentToInvoice(payment)
            }
        })
        paymentSheet.show(parentFragmentManager, "PaymentEntryBottomSheet")
    }


    private fun openItemEditor(item: InvoiceItem) {
        val bottomSheet = ItemSelectionBottomSheet.newInstance()
        // Pass the current item for editing
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

                // Create updated item while preserving the ID
                val updatedInvoiceItem = InvoiceItem(
                    id = item.id,            // Preserve original ID
                    itemId = item.itemId,    // Preserve original item ID
                    quantity = item.quantity,
                    itemDetails = updatedItem, // Use the updated item details which include extra charges
                    price = price
                )

                // Update in viewmodel
                viewModel.updateInvoiceItem(updatedInvoiceItem)
                EventBus.postInvoiceUpdated()
            }
        })

        bottomSheet.show(parentFragmentManager, "ItemEditorBottomSheet")
    }

//    private fun openItemEditor(item: InvoiceItem) {
//        val bottomSheet = ItemSelectionBottomSheet.newInstance()
//        bottomSheet.setItemForEdit(item.itemDetails)
//
//        bottomSheet.setOnItemSelectedListener(object : ItemSelectionBottomSheet.OnItemSelectedListener {
//            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
//                // This shouldn't happen during editing
//            }
//
//            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
//                // Create updated item
//                val updatedInvoiceItem = InvoiceItem(
//                    itemId = item.itemId,
//                    quantity = item.quantity,
//                    itemDetails = updatedItem,
//                    price = price
//                )
//
//                // Update in viewmodel
//                viewModel.updateInvoiceItem(updatedInvoiceItem)
//            }
//        })
//
//        bottomSheet.show(parentFragmentManager, "ItemEditorBottomSheet")
//    }

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
        // Check if we came from CustomerInvoicesFragment
        val fromCustomerInvoice = arguments?.getBoolean("FROM_CUSTOMER_INVOICE", false) ?: false
        // Store the customerId for later use
        val customerId = if (fromCustomerInvoice) viewModel.invoice.value?.customerId else null

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Invoice")
            .setMessage("Are you sure you want to delete this invoice? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
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
                            // Post the event for refreshing the invoice list
                            EventBus.postInvoiceDeleted()

                            try {
                                if (fromCustomerInvoice && customerId != null) {
                                    // Navigate back to CustomerDetailFragment with popUpTo
                                    findNavController().navigate(
                                        R.id.customerDetailFragment,
                                        bundleOf("customerId" to customerId),
                                        NavOptions.Builder()
                                            .setPopUpTo(R.id.customerDetailFragment, true)
                                            .build()
                                    )
                                } else {
                                    // Regular navigation up for other cases
                                    findNavController().navigateUp()
                                }
                            } catch (e: Exception) {
                                Log.e("InvoiceDetailFragment", "Navigation error after deletion", e)
                                // Try fallback navigation
                                try {
                                    findNavController().popBackStack()
                                } catch (e: Exception) {
                                    Log.e("InvoiceDetailFragment", "Fallback navigation failed", e)
                                }
                            }
                        } else {
                            // Show error message if deletion failed
                            Toast.makeText(requireContext(), "Failed to delete invoice", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
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
            // Implement phone call intent
        } else {
            showErrorMessage("No phone number available")
        }
    }

    private fun messageCustomerOnWhatsApp() {
        val phone = viewModel.getCustomerPhone()
        if (phone.isNotEmpty()) {
            // Implement WhatsApp messaging
        } else {
            showErrorMessage("No phone number available")
        }
    }

    private fun printInvoice() {
        // Implement invoice printing
    }

    private fun shareInvoice() {
        // Implement invoice sharing
    }

    private fun shareInvoiceToWhatsApp() {
        // Implement WhatsApp invoice sharing
    }

    private fun generateAndSavePdf() {
        // Implement PDF generation
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


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}