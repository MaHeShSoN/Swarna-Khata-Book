package com.jewelrypos.swarnakhatabook

import android.app.DatePickerDialog
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import kotlinx.coroutines.launch
import java.io.File
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.PaymentsAdapter
import com.jewelrypos.swarnakhatabook.Adapters.SelectedItemsAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerListBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemSelectionBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.PaymentEntryBottomSheet
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.DataClasses.SelectedItemWithPrice
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.Factorys.SalesViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.Repository.CustomerSelectionManager
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoiceCreationBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class InvoiceCreationFragment : Fragment() {

    private var _binding: FragmentInvoiceCreationBinding? = null
    private val binding get() = _binding!!

    private var selectedDueDate: Long? = null
    private var selectedInvoiceDate = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private val salesViewModel: SalesViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth,requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager,requireContext())
    }

    private val customerViewModel: CustomerViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth,requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager)
    }

    private lateinit var itemsAdapter: SelectedItemsAdapter
    private lateinit var paymentsAdapter: PaymentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoiceCreationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews()
        setupListeners()
        setupDatePicker()
        setupDueDatePicker() // Setup due date picker
        observePayments()

        // Set default due date (15 days from invoice date)
        setDefaultDueDate()

        // Check for a customer ID passed as an argument first
        val passedCustomerId = arguments?.getString("customerId")

        // If no ID in arguments, check the CustomerSelectionManager
        val effectiveCustomerId = passedCustomerId ?: CustomerSelectionManager.selectedCustomerId

        // Check if we came from CustomerInvoicesFragment
        val fromCustomerInvoice = arguments?.getBoolean("FROM_CUSTOMER_INVOICE", false) ?: false

        // Load the customer data if we have an ID
        effectiveCustomerId?.let { id ->
            // Load the customer data using this ID
            customerViewModel.getCustomerById(id).observe(viewLifecycleOwner) { result ->
                if (result.isSuccess) {
                    val customer = result.getOrNull()
                    if (customer != null) {
                        // Update UI with customer info
                        updateCustomerSection(customer)
                        salesViewModel.setSelectedCustomer(customer)
                    }
                }
            }

            // Clear the selection only if using the singleton approach
            if (passedCustomerId == null) {
                CustomerSelectionManager.selectedCustomerId = null
            }
        }
    }

    private fun setDefaultDueDate() {
        // Set due date 15 days from invoice date
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedInvoiceDate
        calendar.add(Calendar.DAY_OF_YEAR, 15)
        selectedDueDate = calendar.timeInMillis

        // Update UI to display the default due date
        binding.dueDateText.text = dateFormat.format(Date(selectedDueDate!!))
    }

    private fun observePayments() {
        salesViewModel.payments.observe(viewLifecycleOwner) { payments ->
            paymentsAdapter.updatePayments(payments)
            updateTotals() // Update totals when payments change

            // Update empty state
            if (payments.isEmpty()) {
                binding.noPaymentsText.visibility = View.VISIBLE
                binding.paymentsRecyclerView.visibility = View.GONE
            } else {
                binding.noPaymentsText.visibility = View.GONE
                binding.paymentsRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun initializeViews() {
        paymentsAdapter = PaymentsAdapter(emptyList())
        paymentsAdapter.setOnPaymentActionListener(object :
            PaymentsAdapter.OnPaymentActionListener {
            override fun onRemovePayment(payment: Payment) {
                salesViewModel.removePayment(payment)
            }

            override fun onEditPayment(payment: Payment) {
                // Not implemented yet
            }
        })
        binding.paymentsRecyclerView.adapter = paymentsAdapter
        binding.paymentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Set up the selected items adapter
        itemsAdapter = SelectedItemsAdapter(emptyList())
        itemsAdapter.setOnItemActionListener(object : SelectedItemsAdapter.OnItemActionListener {
            override fun onRemoveItem(item: SelectedItemWithPrice) {
                salesViewModel.removeSelectedItem(item)
            }

            override fun onEditItem(item: SelectedItemWithPrice) {
                // Open the item selection sheet for editing
                val itemSelectionSheet = ItemSelectionBottomSheet.newInstance()
                itemSelectionSheet.setItemForEdit(item.item)
                itemSelectionSheet.setOnItemSelectedListener(object :
                    ItemSelectionBottomSheet.OnItemSelectedListener {
                    override fun onItemSelected(newItem: JewelleryItem, price: Double) {
                        // This case is for adding a new item, which is not what we want here.
                    }

                    override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
                        val updated = salesViewModel.updateSelectedItem(updatedItem, price)

                        if (updated) {
                            Toast.makeText(context, "Item updated successfully", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(
                                context,
                                "Failed to update item. Item not found in selection.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
                itemSelectionSheet.show(parentFragmentManager, "ItemSelectionBottomSheet")
            }

            override fun onQuantityChanged(item: SelectedItemWithPrice, newQuantity: Int) {
                salesViewModel.updateItemQuantity(item, newQuantity)
            }
        })
        binding.itemsRecyclerView.adapter = itemsAdapter
        binding.itemsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Set up observers
        observeSelectedItems()
    }

    private fun observeSelectedItems() {
        salesViewModel.selectedItems.observe(viewLifecycleOwner) { items ->
            itemsAdapter.updateItems(items)

            // Update totals
            updateTotals()

            // Update empty state
            if (items.isEmpty()) {
                binding.noItemSelected.visibility = View.VISIBLE
                binding.itemsRecyclerView.visibility = View.GONE
                binding.addItemButton.visibility = View.GONE
                binding.itemsSectionTitle.visibility = View.GONE
                binding.itemViewWithDetailes.visibility = View.GONE
            } else {
                binding.noItemSelected.visibility = View.GONE
                binding.itemsRecyclerView.visibility = View.VISIBLE
                binding.addItemButton.visibility = View.VISIBLE
                binding.itemsSectionTitle.visibility = View.VISIBLE
                binding.itemViewWithDetailes.visibility = View.VISIBLE
            }
        }
    }

    private fun setupListeners() {
        binding.selectCustomerButton.setOnClickListener {
            selectCustomer()
        }

        binding.editCustomerButton.setOnClickListener {
            selectCustomer()
        }

        binding.addItemButton.setOnClickListener {
            selectItems()
        }

        binding.selectAddItemForFirstTimeButton.setOnClickListener {
            if (salesViewModel.selectedCustomer.value == null) {
                Toast.makeText(requireContext(), "Please Add Customer First", Toast.LENGTH_SHORT)
                    .show()
            } else {
                selectItems()
            }
        }

        binding.addPaymentButton.setOnClickListener {
            addPayment()
        }

        binding.saveButton.setOnClickListener {
            saveInvoice()
        }

        binding.cancelButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun updateCustomerSection(customer: Customer) {
        // Check if the customer is a wholesaler
        val isWholesaler = customer.customerType.equals("Wholesaler", ignoreCase = true)

        // Update UI elements based on customer type
        if (isWholesaler) {
            // Update section titles and labels for wholesaler (supplier)
            binding.titleTextView.text = "Create Purchase Order"
            binding.customerSectionTitle.text = "Supplier Details"
            binding.itemsSectionTitle.text = "Items Purchased"
            binding.paymentsSectionTitle.text = "Payments To Supplier"
            binding.amountPaidLabel.text = "Amount Paid To Supplier:"
            binding.balanceDueLabel.text = "Balance To Pay:"
            binding.saveButton.text = "Save Purchase"
            binding.saveButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.selectCustomerButton.text = "Select Supplier"
            binding.selectAddItemForFirstTimeButton.text = "Add Purchase Item"
            binding.addItemButton.text = "+ Add Purchase"
            binding.addPaymentButton.text = "+ Add Payment"
            binding.NoTextAddedId.text = "No Items Purchased"

            // Optionally change colors to visually differentiate
            binding.saveButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.supplier_button_color
                )
            )
            binding.titleTextView.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.supplier_text_color
                )
            )
            binding.paymentStatusBadge.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.supplier_badge_color)
        } else {
            // Reset UI elements for normal consumer customer
            binding.titleTextView.text = "Create Invoice"
            binding.customerSectionTitle.text = "Customer Details"
            binding.itemsSectionTitle.text = "Items Sold"
            binding.paymentsSectionTitle.text = "Payments"
            binding.amountPaidLabel.text = "Amount Paid:"
            binding.balanceDueLabel.text = "Balance Due:"
            binding.saveButton.text = "Save Invoice"
            binding.selectCustomerButton.text = "Select Customer"
            binding.selectAddItemForFirstTimeButton.text = "Add Item"
            binding.addItemButton.text = "+ Add Item"
            binding.addPaymentButton.text = "+ Add Payment"
            binding.NoTextAddedId.text = "No Items Added"

            // Reset colors to default
            binding.saveButton.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.my_light_primary
                )
            )
            binding.titleTextView.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.my_light_on_surface
                )
            )
            binding.paymentStatusBadge.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.status_unpaid)
        }

        // Update notes hint based on customer type
        binding.notesInputLayout.hint =
            if (isWholesaler) "Add notes for this purchase" else "Add notes for this invoice"

        // Change payment status badge text for wholesalers
        binding.paymentStatusBadge.text = if (isWholesaler) "TO PAY" else "UNPAID"

        // Show customer/supplier details
        binding.noCustomerSelected.visibility = View.GONE
        binding.customerDetailsLayout.visibility = View.VISIBLE
        binding.customerSectionTitle.visibility = View.VISIBLE
        binding.editCustomerButton.visibility = View.VISIBLE

        // Set customer/supplier name and contact info
        binding.customerName.text = "${customer.firstName} ${customer.lastName}"
        binding.customerPhone.text = customer.phoneNumber

        // Use formatter for better display of currency values
        val formatter = DecimalFormat("#,##,##0.00")

        // Adjust balance display based on customer type and balance type
        val balanceText = when {
            customer.currentBalance != 0.0 -> {
                when {
                    isWholesaler && customer.balanceType == "Debit" && customer.currentBalance > 0 ->
                        "They Owe: ₹${formatter.format(customer.currentBalance)}"

                    isWholesaler && customer.balanceType == "Credit" && customer.currentBalance > 0 ->
                        "You Owe: ₹${formatter.format(customer.currentBalance)}"

                    customer.balanceType == "Credit" && customer.currentBalance > 0 ->
                        "To Receive: ₹${formatter.format(customer.currentBalance)}"

                    customer.balanceType == "Debit" && customer.currentBalance > 0 ->
                        "To Pay: ₹${formatter.format(customer.currentBalance)}"

                    else -> "Balance: ₹${formatter.format(customer.currentBalance)}"
                }
            }
            // Otherwise fall back to opening balance
            isWholesaler && customer.balanceType == "Debit" && customer.openingBalance > 0 ->
                "They Owe: ₹${formatter.format(customer.openingBalance)}"

            isWholesaler && customer.balanceType == "Credit" && customer.openingBalance > 0 ->
                "You Owe: ₹${formatter.format(customer.openingBalance)}"

            customer.balanceType == "Credit" && customer.openingBalance > 0 ->
                "To Receive: ₹${formatter.format(customer.openingBalance)}"

            customer.balanceType == "Debit" && customer.openingBalance > 0 ->
                "To Pay: ₹${formatter.format(customer.openingBalance)}"

            else -> "Balance: ₹0.00"
        }
        binding.customerBalance.text = balanceText

        // Set customer/supplier address
        val address = if (customer.streetAddress.isNotEmpty()) {
            "${customer.streetAddress}, ${customer.city}"
        } else {
            "${customer.city}, ${customer.state}"
        }
        binding.customerAddress.text = address

        // Store customer type in Tag for later use in other methods
        binding.customerCard.tag = if (isWholesaler) "wholesaler" else "consumer"
    }

    private fun selectCustomer() {
        val customerListBottomSheet = CustomerListBottomSheet.newInstance()
        customerListBottomSheet.setOnCustomerSelectedListener { customer ->
            Log.d("InvoiceCreationFragment", "Selected customer: $customer")
            updateCustomerSection(customer)
            salesViewModel.setSelectedCustomer(customer)
        }
        customerListBottomSheet.show(parentFragmentManager, CustomerListBottomSheet.TAG)
    }

    private fun setupDueDatePicker() {
        // Initialize due date UI
        if (selectedDueDate != null) {
            binding.dueDateText.text = dateFormat.format(Date(selectedDueDate!!))
        }

        // Set up click listener for the due date picker button
        binding.selectDueDateButton.setOnClickListener {
            showDueDatePicker()
        }
    }

    private fun showDueDatePicker() {
        val calendar = Calendar.getInstance()

        // If a due date is already selected, use it as the default
        if (selectedDueDate != null) {
            calendar.timeInMillis = selectedDueDate!!
        } else {
            // Otherwise, set a default of 15 days from invoice date
            calendar.timeInMillis = selectedInvoiceDate
            calendar.add(Calendar.DAY_OF_YEAR, 15)
        }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance()
                newCalendar.set(year, month, dayOfMonth)
                selectedDueDate = newCalendar.timeInMillis

                // Update UI to display selected date
                binding.dueDateText.text = dateFormat.format(Date(selectedDueDate!!))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun setupDatePicker() {
        // Initialize with current date
        updateDateDisplay()

        binding.changeDateButton.setOnClickListener {
            showDatePickerDialog()
        }
    }

    private fun updateDateDisplay() {
        binding.invoiceDateText.text = dateFormat.format(Date(selectedInvoiceDate))
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedInvoiceDate

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                selectedInvoiceDate = calendar.timeInMillis
                updateDateDisplay()

                // When invoice date changes, update due date to be 15 days later
                updateDueDateBasedOnInvoiceDate()
            },
            year, month, day
        )

        // Set date range if needed
        // For example, you might not want to allow future dates
        val today = Calendar.getInstance()
        datePickerDialog.datePicker.maxDate = today.timeInMillis

        datePickerDialog.show()
    }

    private fun updateDueDateBasedOnInvoiceDate() {
        // Set due date 15 days from the new invoice date
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedInvoiceDate
        calendar.add(Calendar.DAY_OF_YEAR, 15)
        selectedDueDate = calendar.timeInMillis

        // Update the UI
        binding.dueDateText.text = dateFormat.format(Date(selectedDueDate!!))
    }

    private fun selectItems() {
        val itemSelectionSheet = ItemSelectionBottomSheet.newInstance()
        itemSelectionSheet.setOnItemSelectedListener(object :
            ItemSelectionBottomSheet.OnItemSelectedListener {
            override fun onItemSelected(item: JewelleryItem, price: Double) {
                // Default to quantity of 1 when adding a new item
                val requestedQuantity = 1

                // Check if we have enough stock
                if (item.stock <= 0) {
                    // No stock available - warn user but still allow adding
                    salesViewModel.addSelectedItem(item, price)
                    Toast.makeText(
                        context,
                        "Warning: Item has no stock available",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (requestedQuantity > item.stock) {
                    // Not enough stock - warn user but still allow adding
                    salesViewModel.addSelectedItem(item, price)
                    Toast.makeText(
                        context,
                        "Warning: Requested quantity exceeds available stock (${item.stock})",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Enough stock available
                    salesViewModel.addSelectedItem(item, price)
                }
            }

            override fun onItemUpdated(item: JewelleryItem, price: Double) {
                val updated = salesViewModel.updateSelectedItem(item, price)
                if (updated) {
                    Toast.makeText(context, "Item updated successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to update item", Toast.LENGTH_SHORT).show()
                }
            }
        })
        itemSelectionSheet.show(parentFragmentManager, "ItemSelectionBottomSheet")
    }

    private fun addPayment() {
        val totalAmount = salesViewModel.calculateTotal()
        val paidAmount = salesViewModel.calculateTotalPaid()
        val dueAmount = totalAmount - paidAmount

        if (dueAmount <= 0) {
            Toast.makeText(context, "Invoice is already fully paid", Toast.LENGTH_SHORT).show()
            return
        }

        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, paidAmount)
        paymentSheet.setTitle("Add Payment")
        paymentSheet.setDescription("Invoice Total: ₹${String.format("%.2f", totalAmount)}")
        paymentSheet.setAmount(dueAmount) // Set the correct amount due

        paymentSheet.setOnPaymentAddedListener(object :
            PaymentEntryBottomSheet.OnPaymentAddedListener {
            override fun onPaymentAdded(payment: Payment) {
                // Create new payment with generated ID
                val newPayment = Payment(
                    id = generatePaymentId(),
                    amount = payment.amount,
                    method = payment.method,
                    date = payment.date,
                    reference = payment.reference,
                    notes = payment.notes,
                    details = payment.details,
                )

                // Add to view model
                salesViewModel.addPayment(newPayment)
            }
        })
        paymentSheet.show(parentFragmentManager, "PaymentEntryBottomSheet")
    }

    private fun updateTotals() {
        val subtotal = salesViewModel.calculateSubtotal()
        val extraChargesTotal = salesViewModel.calculateExtraCharges()
        val tax = salesViewModel.calculateTax()
        val total = salesViewModel.calculateTotal()
        val paid = salesViewModel.calculateTotalPaid()
        val due = total - paid

        // Update standard values
        binding.subtotalValue.text = "₹${String.format("%.2f", subtotal)}"
        binding.taxValue.text = "₹${String.format("%.2f", tax)}"
        binding.totalValue.text = "₹${String.format("%.2f", total)}"
        binding.amountPaidValue.text = "₹${String.format("%.2f", paid)}"
        binding.balanceDueValue.text = "₹${String.format("%.2f", due)}"

        // Update payment status badge
        updatePaymentStatusBadge(due, paid)

        // Update extra charges display
        updateExtraChargesDisplay()
    }

    private fun updateExtraChargesDisplay() {
        // Get all extra charges
        val extraCharges = salesViewModel.getAllExtraCharges()

        // Clear any existing extra charge views
        binding.extraChargesContainer.removeAllViews()

        // If there are no extra charges, hide the container
        if (extraCharges.isEmpty()) {
            binding.extraChargesContainer.visibility = View.GONE
            return
        }

        // Make container visible
        binding.extraChargesContainer.visibility = View.VISIBLE

        // Create a view for each extra charge
        for (charge in extraCharges) {
            val chargeView = layoutInflater.inflate(
                R.layout.item_extra_charge_layout,
                binding.extraChargesContainer,
                false
            )

            val chargeName = chargeView.findViewById<TextView>(R.id.extraChargeNameText)
            val chargeAmount = chargeView.findViewById<TextView>(R.id.extraChargeAmountText)

            chargeName.text = charge.first
            chargeAmount.text = "₹${String.format("%.2f", charge.second)}"

            binding.extraChargesContainer.addView(chargeView)
        }
    }

    private fun updatePaymentStatusBadge(due: Double, paid: Double) {
        val paymentStatus = when {
            due <= 0 -> "PAID"
            paid > 0 -> "PARTIAL"
            else -> "UNPAID"
        }

        binding.paymentStatusBadge.text = paymentStatus
        binding.paymentStatusBadge.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                when (paymentStatus) {
                    "PAID" -> R.color.status_paid
                    "PARTIAL" -> R.color.status_partial
                    else -> R.color.status_unpaid
                }
            )
        )
    }

    private fun saveInvoice() {
        // Validate
        if (!validateInvoice()) {
            return
        }

        proceedWithSavingInvoice()
    }

    private fun proceedWithSavingInvoice() {
        try {
            // Create invoice object
            val invoiceNumber = generateInvoiceNumber()
            val customer = salesViewModel.selectedCustomer.value
                ?: throw IllegalStateException("Customer must be selected")

            // Log the customer information for debugging
            Log.d(
                "InvoiceCreation",
                "Customer selected: ${customer.firstName} ${customer.lastName}, " +
                        "id: ${customer.id}, balanceType: ${customer.balanceType}"
            )

            val invoiceItems = itemsAdapter.getItems().map { selected ->
                InvoiceItem(
                    id = UUID.randomUUID().toString(), // Ensure each item has a unique ID
                    itemId = selected.item.id,
                    quantity = selected.quantity,
                    itemDetails = selected.item,
                    price = selected.price
                )
            }

            // Validate that we have items
            if (invoiceItems.isEmpty()) {
                Toast.makeText(context, "Please add at least one item", Toast.LENGTH_SHORT).show()
                return
            }

            val payments = paymentsAdapter.getPayments()
            val totalAmount = salesViewModel.calculateTotal()
            val paidAmount = salesViewModel.calculateTotalPaid()

            // Get customer address components
            val address = if (customer.streetAddress.isNotEmpty()) {
                "${customer.streetAddress}, ${customer.city}, ${customer.state}"
            } else {
                "${customer.city}, ${customer.state}"
            }

            val invoice = Invoice(
                id = "", // Let repository assign ID based on invoice number
                invoiceNumber = invoiceNumber,
                customerId = customer.id,
                customerName = "${customer.firstName} ${customer.lastName}",
                customerPhone = customer.phoneNumber,
                customerAddress = address,
                invoiceDate = selectedInvoiceDate,
                dueDate = selectedDueDate,
                items = invoiceItems,
                payments = payments,
                totalAmount = totalAmount,
                paidAmount = paidAmount,
                notes = binding.notesEditText.text.toString()
            )

            // Log the invoice details for debugging
            Log.d(
                "InvoiceCreation", "Saving invoice: number=$invoiceNumber, " +
                        "items=${invoiceItems.size}, total=$totalAmount, paid=$paidAmount"
            )

            // Show loading state
            binding.progressOverlay.visibility = View.VISIBLE

            // Save the invoice
            salesViewModel.saveInvoice(invoice) { success ->
                if (!isAdded) return@saveInvoice

                requireActivity().runOnUiThread {
                    if (_binding == null) return@runOnUiThread

                    binding.progressOverlay.visibility = View.GONE

                    if (success) {
                        context?.let { ctx ->
                            Toast.makeText(ctx, "Invoice saved successfully", Toast.LENGTH_SHORT)
                                .show()
                        }

                        EventBus.postInvoiceAdded()

                        // Check if we came from CustomerInvoicesFragment
                        val fromCustomerInvoice =
                            arguments?.getBoolean("FROM_CUSTOMER_INVOICE", false) ?: false

                        try {
                            if (fromCustomerInvoice) {
                                // Get the customer ID
                                val customerId = arguments?.getString("customerId")
                                if (customerId != null) {
                                    // Navigate back to CustomerDetailFragment
                                    findNavController().navigate(
                                        R.id.customerDetailFragment,
                                        bundleOf("customerId" to customerId),
                                        NavOptions.Builder()
                                            .setPopUpTo(R.id.customerDetailFragment, true)
                                            .build()
                                    )
                                } else {
                                    // Fallback
                                    findNavController().navigateUp()
                                }
                            } else {
                                // Standard navigateUp() for other cases
                                findNavController().navigateUp()
                            }
                        } catch (e: Exception) {
                            Log.e("InvoiceCreation", "Navigation error: ${e.message}", e)
                        }
                    } else {
                        context?.let { ctx ->
                            Toast.makeText(ctx, "Failed to save invoice", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Handle any unexpected errors
            Log.e("InvoiceCreation", "Error saving invoice", e)
            binding.progressOverlay.visibility = View.GONE
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInvoice(): Boolean {
        if (salesViewModel.selectedCustomer.value == null) {
            Toast.makeText(context, "Please select a customer", Toast.LENGTH_SHORT).show()
            return false
        }

        if (itemsAdapter.getItems().isEmpty()) {
            Toast.makeText(context, "Please add at least one item", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
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
            Toast.makeText(context, "Failed to share PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateInvoiceNumber(): String {
        val dateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
        val datePart = dateFormat.format(Date())
        val random = (1000..9999).random()
        return "INV-$datePart-$random"
    }

    private fun generatePaymentId(): String {
        val dateFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault())
        val datePart = dateFormat.format(Date())
        val random = (10..99).random()
        return "PAY-$datePart-$random"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}