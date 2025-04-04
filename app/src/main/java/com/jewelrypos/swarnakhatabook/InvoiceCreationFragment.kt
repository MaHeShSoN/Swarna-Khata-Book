package com.jewelrypos.swarnakhatabook

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
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoiceCreationBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class InvoiceCreationFragment : Fragment() {

    private var _binding: FragmentInvoiceCreationBinding? = null
    private val binding get() = _binding!!

    private val salesViewModel: SalesViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        SalesViewModelFactory(repository, connectivityManager)
    }

    private val customerViewModel: CustomerViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth)
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
        observePayments()

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

        binding.printButton.setOnClickListener {
            generateAndSavePdf()
        }
    }

    private fun updateCustomerSection(customer: Customer) {
        binding.noCustomerSelected.visibility = View.GONE
        binding.customerDetailsLayout.visibility = View.VISIBLE
        binding.customerSectionTitle.visibility = View.VISIBLE
        binding.editCustomerButton.visibility = View.VISIBLE

        binding.customerName.text = "${customer.firstName} ${customer.lastName}"
        binding.customerPhone.text = customer.phoneNumber

        // Use the formatter for better display of currency values
        val formatter = DecimalFormat("#,##,##0.00")

        // First check if there's a current balance to display
        val balanceText = when {
            customer.currentBalance != 0.0 -> {
                when {
                    customer.balanceType == "Credit" && customer.currentBalance > 0 ->
                        "To Receive: ₹${formatter.format(customer.currentBalance)}"

                    customer.balanceType == "Debit" && customer.currentBalance > 0 ->
                        "To Pay: ₹${formatter.format(customer.currentBalance)}"

                    else -> "Balance: ₹${formatter.format(customer.currentBalance)}"
                }
            }
            // Otherwise fall back to opening balance
            customer.balanceType == "Credit" && customer.openingBalance > 0 ->
                "To Receive: ₹${formatter.format(customer.openingBalance)}"

            customer.balanceType == "Debit" && customer.openingBalance > 0 ->
                "To Pay: ₹${formatter.format(customer.openingBalance)}"

            else -> "Balance: ₹0.00"
        }
        binding.customerBalance.text = balanceText

        val address = if (customer.streetAddress.isNotEmpty()) {
            "${customer.streetAddress}, ${customer.city}"
        } else {
            "${customer.city}, ${customer.state}"
        }
        binding.customerAddress.text = address
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
                    notes = payment.notes
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
                invoiceDate = System.currentTimeMillis(),
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

    // Add this method to InvoiceCreationFragment
    private fun generateAndSavePdf() {
        // Ensure we have an invoice to generate PDF for
        val invoice = Invoice(
            invoiceNumber = generateInvoiceNumber(),
            customerId = salesViewModel.selectedCustomer.value?.id ?: "",
            customerName = salesViewModel.selectedCustomer.value?.let { "${it.firstName} ${it.lastName}" } ?: "",
            customerPhone = salesViewModel.selectedCustomer.value?.phoneNumber ?: "",
            customerAddress = salesViewModel.selectedCustomer.value?.let {
                "${it.streetAddress}, ${it.city}, ${it.state}"
            } ?: "",
            invoiceDate = System.currentTimeMillis(),
            items = itemsAdapter.getItems().map { selected ->
                InvoiceItem(
                    itemId = selected.item.id,
                    quantity = selected.quantity,
                    itemDetails = selected.item,
                    price = selected.price
                )
            },
            payments = paymentsAdapter.getPayments(),
            totalAmount = salesViewModel.calculateTotal(),
            paidAmount = salesViewModel.calculateTotalPaid(),
            notes = binding.notesEditText.text.toString()
        )

        // Get shop information
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val shop = ShopManager.getShopDetails(requireContext())

                if (shop == null) {
                    Toast.makeText(context, "Shop information not found", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
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