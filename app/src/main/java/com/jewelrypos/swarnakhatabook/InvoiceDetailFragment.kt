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
        // Regular tax calculation
        return invoice.items.sumOf { item ->
            val itemSubtotal = calculateSubtotal(invoice)
            val itemExtraCharges = calculateExtraCharges(invoice)
            (itemSubtotal + itemExtraCharges) * (item.itemDetails.taxRate / 100.0)
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
        val originalItemContribution = originalItemTotal + getItemExtraChargesTotal(item) + getItemTaxAmount(item)

        // Store total paid amount
        val totalPaid = invoice.paidAmount

        // Calculate how low the price can go without causing negative balance
        val otherItemsTotal = invoice.totalAmount - originalItemContribution
        val minimumAllowedTotal = Math.max(0.0, totalPaid - otherItemsTotal)

        // Create and configure the bottom sheet
        val bottomSheet = ItemSelectionBottomSheet.newInstance()
        bottomSheet.setItemForEdit(item.itemDetails)

        bottomSheet.setOnItemSelectedListener(object : ItemSelectionBottomSheet.OnItemSelectedListener {
            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
                // This shouldn't happen during editing
            }

            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
                Log.d("InvoiceDetailFragment", "Item updated with ${updatedItem.listOfExtraCharges.size} extra charges")

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
            }
        })

        bottomSheet.show(parentFragmentManager, "ItemEditorBottomSheet")
    }




//    private fun openItemEditor(item: InvoiceItem) {
//        val bottomSheet = ItemSelectionBottomSheet.newInstance()
//        // Pass the current item for editing
//        bottomSheet.setItemForEdit(item.itemDetails)
//
//        bottomSheet.setOnItemSelectedListener(object :
//            ItemSelectionBottomSheet.OnItemSelectedListener {
//            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
//                // This shouldn't happen during editing
//            }
//
//            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
//                Log.d(
//                    "InvoiceDetailFragment",
//                    "Item updated with ${updatedItem.listOfExtraCharges.size} extra charges"
//                )
//
//                // Create updated item while preserving the ID
//                val updatedInvoiceItem = InvoiceItem(
//                    id = item.id,            // Preserve original ID
//                    itemId = item.itemId,    // Preserve original item ID
//                    quantity = item.quantity,
//                    itemDetails = updatedItem, // Use the updated item details which include extra charges
//                    price = price
//                )
//
//                // Update in viewmodel
//                viewModel.updateInvoiceItem(updatedInvoiceItem)
//                EventBus.postInvoiceUpdated()
//            }
//        })
//
//        bottomSheet.show(parentFragmentManager, "ItemEditorBottomSheet")
//    }

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

    private fun messageCustomerOnWhatsApp() {
        val phone = viewModel.getCustomerPhone()
        if (phone.isNotEmpty()) {
            try {
                // Format phone number (remove any non-digit characters except +)
                val formattedPhone = phone.replace(Regex("[^\\d+]"), "")

                // Try regular WhatsApp first
                val whatsappIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone"))
                whatsappIntent.setPackage("com.whatsapp")

                // Try WhatsApp Business if needed
                val whatsappBusinessIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone"))
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
                        // Neither app is installed, show error message
                        showErrorMessage("WhatsApp not installed")

                        // Ask if they want to message through another app
                        AlertDialog.Builder(requireContext())
                            .setTitle("WhatsApp not found")
                            .setMessage("Would you like to contact the customer using another app?")
                            .setPositiveButton("Yes") { _, _ ->
                                // Create SMS intent as fallback
                                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:$phone")
                                }
                                if (smsIntent.resolveActivity(packageManager) != null) {
                                    startActivity(smsIntent)
                                } else {
                                    showErrorMessage("No messaging apps found")
                                }
                            }
                            .setNegativeButton("No", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                showErrorMessage("Could not open messaging app: ${e.message}")
                Log.e("InvoiceDetailFragment", "Error opening messaging app", e)
            }
        } else {
            showErrorMessage("No phone number available")
        }
    }    private fun printInvoice() {
        // Implement invoice printing
    }

    private fun shareInvoice() {
        // Implement invoice sharing
    }


    // Add this method to InvoiceDetailFragment
    private fun generateAndSavePdf() {
        val invoice = viewModel.invoice.value ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
//                val shop = ShopManager.getShopCoroutine(requireContext())
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    showErrorMessage("Shop information not found")
                    return@launch
                }


                PDFBoxResourceLoader.init(requireContext())
                
                // Generate PDF
                val pdfGenerator = InvoicePdfGenerator(requireContext())
                val pdfFile = pdfGenerator.generateInvoicePdf(
                    invoice,
                    shop,
                    "Invoice_${invoice.invoiceNumber}"
                )

                // Share or open the PDF
                sharePdfFile(pdfFile)

            } catch (e: Exception) {
                Log.e("PDFGeneration", "Error generating PDF", e)
                showErrorMessage("Failed to generate PDF")
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
                val whatsappBusinessInstalled = whatsappBusinessIntent.resolveActivity(packageManager) != null

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