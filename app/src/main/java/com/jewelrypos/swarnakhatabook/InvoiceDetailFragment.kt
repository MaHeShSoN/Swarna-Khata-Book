package com.jewelrypos.swarnakhatabook

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.icu.util.Calendar
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
import android.view.LayoutInflater
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.Adapters.EditableInvoiceItemAdapter
import com.jewelrypos.swarnakhatabook.Adapters.PaymentsAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemSelectionBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.PaymentEntryBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.PdfViewerBottomSheet
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.InvoiceDetailViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.InvoiceDetailViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoiceDetailBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupObservers()
        setupClickListeners()
        setupNotesEditing()
        setupAddItemButton()

        // Setup navigation
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Load invoice data based on passed ID
        viewModel.loadInvoice(args.invoiceId)

        // Add the callback
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, backPressedCallback
        )
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
            if (!errorMessage.isNullOrEmpty()) {
                showErrorMessage(errorMessage)
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
        val dateFormatter = SimpleDateFormat(getString(R.string.date_format), Locale.getDefault())
        binding.invoiceDate.text = dateFormatter.format(Date(invoice.invoiceDate))

        // Add click listener to invoice date for editing
        binding.invoiceDate.setOnClickListener {
            showDatePickerDialog(invoice.invoiceDate)
        }

        // Add due date display and click listener if it exists
        if (invoice.dueDate != null) {
            binding.invoiceDueDate.text = dateFormatter.format(Date(invoice.dueDate!!))
            binding.invoiceDueDate.setOnClickListener {
                showDueDatePickerDialog(invoice.dueDate!!)
            }
        } else {
            binding.invoiceDueDate.text = "Add due date"
            binding.invoiceDueDate.setOnClickListener {
                showDueDatePickerDialog(System.currentTimeMillis())
            }
        }

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
            binding.topAppBar.title = getString(R.string.purchase_order_details)
            binding.customerSectionTitle.text = getString(R.string.supplier_details)
            binding.addItemsButton.text = getString(R.string.add_purchase)
            binding.itemsSectionTitle.text = getString(R.string.items_purchased)
            binding.paymentsSectionTitle.text = getString(R.string.payments_to_supplier)
            binding.amountPaidLabel.text = getString(R.string.amount_paid_to_supplier)
            binding.balanceDueLabel.text = getString(R.string.balance_to_pay)
            binding.addPaymentButton.text = getString(R.string.add_payment)

            // Apply wholesaler-specific colors
            binding.topAppBar.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(), R.color.supplier_button_color
                )
            )
        } else {
            // Reset to default consumer UI
            binding.topAppBar.title = getString(R.string.invoice_details)
            binding.customerSectionTitle.text = getString(R.string.customer_details)
            binding.addItemsButton.text = getString(R.string.add_item)
            binding.itemsSectionTitle.text = getString(R.string.items_sold)
            binding.paymentsSectionTitle.text = getString(R.string.payments)
            binding.amountPaidLabel.text = getString(R.string.amount_paid)
            binding.balanceDueLabel.text = getString(R.string.balance_due)
            binding.addPaymentButton.text = getString(R.string.add_payment)

            // Reset to default colors
            binding.topAppBar.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(), R.color.my_light_primary
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
                    itemId = newItem.id, quantity = 1, itemDetails = newItem, price = price
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

        binding.infoButton.setOnClickListener {
            navigateToCustomerDetail()
        }
    }




    // 4. Helper function to convert PDF to bitmap

    // 5. Implement sharing as PDF (using existing InvoicePdfGenerator)

    // Inside printInvoice function

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
                requireContext(), "Customer information not available", Toast.LENGTH_SHORT
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
            val message = if (isWholesaler) "Purchase is already fully paid"
            else "Invoice is already fully paid"
            showErrorMessage(message)
            return
        }

        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, paidAmount)
        if (isWholesaler) {
            paymentSheet.setTitle("Add Payment to Supplier")
            paymentSheet.setDescription(
                "Purchase Order Total: ₹${
                    String.format(
                        "%.2f", totalAmount
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
                    AlertDialog.Builder(requireContext()).setTitle("Cannot Update Item").setMessage(
                        "This change would reduce the invoice total below the amount already paid.\n\n" + "Current amount paid: ₹${
                            formatter.format(
                                totalPaid
                            )
                        }\n" + "Maximum reduction allowed: ₹${formatter.format(maxReduction)}\n\n" + "Please adjust your changes or remove some payments first."
                    ).setPositiveButton("OK", null).show()

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
        AlertDialog.Builder(requireContext()).setTitle("Delete Item")
            .setMessage("Are you sure you want to remove this item from the invoice?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.removeInvoiceItem(item)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun confirmDeletePayment(payment: Payment) {
        AlertDialog.Builder(requireContext()).setTitle("Delete Payment").setMessage(
            "Are you sure you want to delete this payment of ₹${
                DecimalFormat("#,##,##0.00").format(
                    payment.amount
                )
            }?"
        ).setPositiveButton("Delete") { _, _ ->
            viewModel.removePayment(payment)
        }.setNegativeButton("Cancel", null).show()
    }



    // Utility methods for error and loading handling
    private fun showErrorMessage(message: String) {
        if (message.isNotEmpty()) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
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
                // ... (existing notes editing logic) ...
                AlertDialog.Builder(requireContext()).setTitle("Save Notes")
                    .setMessage("Do you want to save your changes to the notes?")
                    .setPositiveButton("Save") { _, _ ->
                        val notes = binding.notesEditText.text.toString()
                        viewModel.updateInvoiceNotes(notes)
                        toggleNotesEditingMode(false)
                        findNavController().navigateUp()
                    }.setNegativeButton("Discard") { _, _ ->
                        toggleNotesEditingMode(false)
                        findNavController().navigateUp()
                    }.setNeutralButton("Cancel", null).show()
            } else {
                // ADD THIS LOGIC: Check if launched from ReportsActivity via deep link
                val fromReportsActivity =
                    activity?.intent?.getBooleanExtra("from_reports_activity", false) ?: false
                if (fromReportsActivity) {
                    // If it came from ReportsActivity, finish MainActivity to go back to ReportsActivity
                    requireActivity().finish()
                } else {
                    // Normal back behavior within MainActivity's graph
                    findNavController().navigateUp()
                }
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
                R.layout.item_extra_charge_layout, binding.extraChargesContainer, false
            )

            val chargeName = chargeView.findViewById<TextView>(R.id.extraChargeNameText)
            val chargeAmount = chargeView.findViewById<TextView>(R.id.extraChargeAmountText)

            chargeName.text = charge.first
            chargeAmount.text = "₹${DecimalFormat("#,##,##0.00").format(charge.second)}"

            binding.extraChargesContainer.addView(chargeView)
        }
    }

    private fun showDatePickerDialog(currentDate: Long) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentDate
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                viewModel.updateInvoiceDate(newCalendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun showDueDatePickerDialog(currentDate: Long) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentDate
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                viewModel.updateDueDate(newCalendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Add a "Remove Due Date" button if there's an existing due date
        if (viewModel.invoice.value?.dueDate != null) {
            datePickerDialog.setButton(
                DatePickerDialog.BUTTON_NEUTRAL, "Remove Due Date"
            ) { _, _ ->
                viewModel.updateDueDate(null)
            }
        }

        datePickerDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
