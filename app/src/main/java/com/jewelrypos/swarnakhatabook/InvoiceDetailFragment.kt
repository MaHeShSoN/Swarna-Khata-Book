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

        Log.d("InvoiceDetailFragment", invoice.isMetalExchangeApplied.toString())


        // Check if there's metal fine payment info
        if (invoice.isMetalExchangeApplied) {
            // Show a section for metal fine payments
            showMetalFineExchangeInfo(invoice)
        } else {
            // Hide metal fine section if it exists
            hideMetalFinePaymentsSection()
        }

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


    private fun showMetalFineExchangeInfo(invoice: Invoice) {
        Log.d("InvoiceDetailFragment", "Showing metal fine info: gold=${invoice.fineGoldAmount}, silver=${invoice.fineSilverAmount}")

        // Show the metal fine card
        binding.metalFineCard.visibility = View.VISIBLE

        // Update total gold weight display
        if (invoice.fineGoldAmount > 0.0) {
            binding.goldFineSection.visibility = View.VISIBLE
            binding.totalGoldWeightValue.text = "Total: ${String.format("%.2f", calculateTotalGoldWeight(invoice))}g"
            binding.goldFineEditText.setText(String.format("%.2f", invoice.fineGoldAmount))
            binding.goldFineEditText.isEnabled = false
            binding.applyGoldFineButton.text = "Reset"
            binding.applyGoldFineButton.visibility = View.GONE // Hide in detail view
            binding.goldFineDeductionLayout.visibility = View.VISIBLE
            binding.goldFineDeductionValue.text = "${String.format("%.2f", invoice.fineGoldAmount)}g"
        } else {
            binding.goldFineSection.visibility = View.GONE
        }

        // Update total silver weight display
        if (invoice.fineSilverAmount > 0.0) {
            binding.silverFineSection.visibility = View.VISIBLE
            binding.totalSilverWeightValue.text = "Total: ${String.format("%.2f", calculateTotalSilverWeight(invoice))}g"
            binding.silverFineEditText.setText(String.format("%.2f", invoice.fineSilverAmount))
            binding.silverFineEditText.isEnabled = false
            binding.applySilverFineButton.text = "Reset"
            binding.applySilverFineButton.visibility = View.GONE // Hide in detail view
            binding.silverFineDeductionLayout.visibility = View.VISIBLE
            binding.silverFineDeductionValue.text = "${String.format("%.2f", invoice.fineSilverAmount)}g"
        } else {
            binding.silverFineSection.visibility = View.GONE
        }

        // Show fine metal summary section
        binding.fineMetalSummarySection.visibility = View.VISIBLE

        // Show original and adjusted totals
        val formatter = DecimalFormat("#,##,##0.00")
        binding.originalTotalLayout.visibility = View.VISIBLE
        binding.originalTotalValue.text = "₹${formatter.format(invoice.originalTotalBeforeFine)}"

        binding.adjustedTotalLayout.visibility = View.VISIBLE
        binding.adjustedTotalValue.text = "₹${formatter.format(invoice.totalAmount)}"
    }

    private fun hideMetalFinePaymentsSection() {
        binding.metalFineCard.visibility = View.GONE
    }

    // Helper methods to calculate total metal weights from items
    private fun calculateTotalGoldWeight(invoice: Invoice): Double {
        return invoice.items.sumOf { item ->
            if (isGoldItem(item.itemDetails)) {
                val weight = if (item.itemDetails.metalRateOn.equals("Net Weight", ignoreCase = true)) {
                    item.itemDetails.netWeight
                } else {
                    item.itemDetails.grossWeight
                }
                weight * item.quantity
            } else {
                0.0
            }
        }
    }

    private fun calculateTotalSilverWeight(invoice: Invoice): Double {
        return invoice.items.sumOf { item ->
            if (isSilverItem(item.itemDetails)) {
                val weight = if (item.itemDetails.metalRateOn.equals("Net Weight", ignoreCase = true)) {
                    item.itemDetails.netWeight
                } else {
                    item.itemDetails.grossWeight
                }
                weight * item.quantity
            } else {
                0.0
            }
        }
    }

    // Helper methods to check item types
    private fun isGoldItem(item: JewelleryItem): Boolean {
        return item.itemType.contains("gold", ignoreCase = true) ||
                item.category.contains("gold", ignoreCase = true)
    }

    private fun isSilverItem(item: JewelleryItem): Boolean {
        return item.itemType.contains("silver", ignoreCase = true) ||
                item.category.contains("silver", ignoreCase = true)
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

//    private fun calculateTax(invoice: Invoice): Double {
//        val subtotal = calculateSubtotal(invoice)
//        val extraCharges = calculateExtraCharges(invoice)
//
//        // Tax is whatever remains after subtracting subtotal and extra charges
//        return invoice.totalAmount - subtotal - extraCharges
//    }

    private fun calculateTax(invoice: Invoice): Double {
        // If metal exchange is applied, use a direct calculation instead of a difference
        if (invoice.isMetalExchangeApplied) {
            // Sum up tax directly from items
            return invoice.items.sumOf { item ->
                val itemSubtotal = item.price * item.quantity
                val itemExtraCharges = item.itemDetails.listOfExtraCharges.sumOf { charge ->
                    charge.amount * item.quantity
                }

                // Calculate tax using the tax rate from item
                (itemSubtotal + itemExtraCharges) * (item.itemDetails.taxRate / 100.0)
            }
        } else {
            // Regular tax calculation for invoices without metal exchange
            val subtotal = calculateSubtotal(invoice)
            val extraCharges = calculateExtraCharges(invoice)

            // Tax is calculated as a direct value, not as a remainder
            return invoice.items.sumOf { item ->
                val itemSubtotal = item.price * item.quantity
                val itemExtraCharges = item.itemDetails.listOfExtraCharges.sumOf {
                        charge -> charge.amount * item.quantity
                }
                (itemSubtotal + itemExtraCharges) * (item.itemDetails.taxRate / 100.0)
            }
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