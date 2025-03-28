package com.jewelrypos.swarnakhatabook


import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoiceCreationBinding
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

    private var fineGoldAmount = 0.0
    private var fineSilverAmount = 0.0
    private var isMetalExchangeApplied = false

    private val customerViewModel: CustomerViewModel by viewModels {
        // Create the repository and pass it to the factory
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
        initializeFineMetalExchangeView()
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
                // Open the item selection sheet for editing (optional)
                val itemSelectionSheet = ItemSelectionBottomSheet.newInstance()
                itemSelectionSheet.setItemForEdit(item.item)
                itemSelectionSheet.setOnItemSelectedListener(object :
                    ItemSelectionBottomSheet.OnItemSelectedListener {
                    override fun onItemSelected(newItem: JewelleryItem, price: Double) {
                        // This case is for adding a new item, which is not what we want here.
                    }

                    override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
                        val updated = salesViewModel.updateSelectedItem(
                            updatedItem,
                            price
                        ) // Keep this for now, we might adjust it.

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


    // Add a method to observe selected items
    private fun observeSelectedItems() {
        salesViewModel.selectedItems.observe(viewLifecycleOwner) { items ->
            itemsAdapter.updateItems(items)
            updateTotals()

            // Update metal weights display when items change
            updateMetalWeightsDisplay()

            // Reset metal exchange if items change significantly
            if (isMetalExchangeApplied) {
                isMetalExchangeApplied = false
                fineGoldAmount = 0.0
                fineSilverAmount = 0.0

                // Reset input fields and enable them
                binding.goldFineEditText.apply {
                    setText("")
                    isEnabled = true
                }
                binding.silverFineEditText.apply {
                    setText("")
                    isEnabled = true
                }

                Toast.makeText(
                    requireContext(),
                    "Metal exchange reset due to item changes",
                    Toast.LENGTH_SHORT
                ).show()
            }

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
            if (salesViewModel.selectedCustomer.value.toString().isEmpty()) {
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


    }

    private fun updateCustomerSection(customer: Customer) {
        binding.noCustomerSelected.visibility = View.GONE
        binding.customerDetailsLayout.visibility = View.VISIBLE
        binding.customerSectionTitle.visibility = View.VISIBLE
        binding.editCustomerButton.visibility = View.VISIBLE

        binding.customerName.text = "${customer.firstName} ${customer.lastName}"
        binding.customerPhone.text = customer.phoneNumber

        // Use the formatter for better display of currency values
        val formatter = java.text.DecimalFormat("#,##,##0.00")

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
            Log.d("InvoiceCreationFragmnet", "Selected customer: $customer")
            updateCustomerSection(customer)
            salesViewModel.setSelectedCustomer(customer)
        }
        customerListBottomSheet.show(parentFragmentManager, CustomerListBottomSheet.TAG)
    }

    // In InvoiceCreationFragment.kt - modify selectItems() method

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

//    private fun updateTotals() {
//        val subtotal = salesViewModel.calculateSubtotal()
//        val extraChargesTotal = salesViewModel.calculateExtraCharges()
//        val tax = salesViewModel.calculateTax()
//        val total = salesViewModel.calculateTotal()
//        val paid = salesViewModel.calculateTotalPaid() // Use view model method
//        val due = total - paid
//
//        // Update standard values
//        binding.subtotalValue.text = "₹${String.format("%.2f", subtotal)}"
//        binding.taxValue.text = "₹${String.format("%.2f", tax)}"
//        binding.totalValue.text = "₹${String.format("%.2f", total)}"
//        binding.amountPaidValue.text = "₹${String.format("%.2f", paid)}"
//        binding.balanceDueValue.text = "₹${String.format("%.2f", due)}"
//
//        // Update payment status badge
//        updatePaymentStatusBadge(due, paid)
//
//        // Update extra charges display
//        updateExtraChargesDisplay()
//    }

    // Update the totals display with adjusted total if metal exchange is applied
    private fun updateTotals() {
        val subtotal = salesViewModel.calculateSubtotal()
        val extraChargesTotal = salesViewModel.calculateExtraCharges()
        val tax = salesViewModel.calculateTax()

        // Calculate total based on metal exchange status
        val total = if (isMetalExchangeApplied) {
            calculateAdjustedTotal(fineGoldAmount, fineSilverAmount)
        } else {
            calculateInvoiceTotal()
        }

        val paid = salesViewModel.calculateTotalPaid()
        val due = total - paid

        // Update the UI
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

    private fun calculateSubtotal(): Double {
        return itemsAdapter.getItems().sumOf { it.price * it.quantity }
    }


    // Method to initialize the fine metal exchange view
    private fun initializeFineMetalExchangeView() {

        binding.metalFineCard.visibility = View.GONE

        // Show the card only if there are gold or silver items
        salesViewModel.selectedItems.observe(viewLifecycleOwner) { items ->
            val hasGold = items.any { it.item.itemType.equals("gold", ignoreCase = true) }
            val hasSilver = items.any { it.item.itemType.equals("silver", ignoreCase = true) }

            binding.metalFineCard.visibility = if (hasGold || hasSilver) View.VISIBLE else View.GONE
            binding.goldFineSection.visibility = if (hasGold) View.VISIBLE else View.GONE
            binding.silverFineSection.visibility = if (hasSilver) View.VISIBLE else View.GONE

            // Update total weights
            val totalGoldWeight = calculateTotalGoldWeight()
            val totalSilverWeight = calculateTotalSilverWeight()

            binding.totalGoldWeightValue.text = "Total: ${String.format("%.2f", totalGoldWeight)}g"
            binding.totalSilverWeightValue.text =
                "Total: ${String.format("%.2f", totalSilverWeight)}g"
        }


        // Setup input fields with validation
        setupFineMetalInputs()

        // Setup apply button

        binding.applyGoldFineButton.setOnClickListener {
            applyMetalExchange()
        }

        binding.applySilverFineButton.setOnClickListener {
            applyMetalExchange()
        }
        // Hide the view initially
        binding.metalFineCard.visibility = View.GONE
    }


    // Setup input validation for fine metal entries
    private fun setupFineMetalInputs() {
        val fineGoldInput = binding.goldFineEditText
        val fineSilverInput = binding.silverFineEditText

        // Add text watchers for validation
        fineGoldInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toDoubleOrNull() ?: 0.0
                val totalGoldWeight = calculateTotalGoldWeight()

                if (input > totalGoldWeight) {
                    binding.goldFineInputLayout.error =
                        "Cannot exceed total gold weight (${
                            String.format(
                                "%.2f",
                                totalGoldWeight
                            )
                        }g)"
                } else {
                    binding.goldFineInputLayout.error = null
                    fineGoldAmount = input
                    updateMetalExchangeSummary()
                }
            }
        })

        fineSilverInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toDoubleOrNull() ?: 0.0
                val totalSilverWeight = calculateTotalSilverWeight()

                if (input > totalSilverWeight) {
                    binding.silverFineInputLayout.error =
                        "Cannot exceed total silver weight (${
                            String.format(
                                "%.2f",
                                totalSilverWeight
                            )
                        }g)"
                } else {
                    binding.silverFineInputLayout.error = null
                    fineSilverAmount = input
                    updateMetalExchangeSummary()
                }
            }
        })
    }

    // Update the metal exchange summary (total value saved)
    private fun updateMetalExchangeSummary() {
        // Calculate original total
        val originalTotal = calculateInvoiceTotal()

        // Calculate adjusted total with metal exchange
        val adjustedTotal = calculateAdjustedTotal(fineGoldAmount, fineSilverAmount)

        // Calculate the difference (value saved)
        val savedValue = originalTotal - adjustedTotal

        // Update the summary display
//        val metalPaymentValue = fineMetalView.findViewById<TextView>(R.id.metalPaymentValue)
//        val formatter = DecimalFormat("#,##,##0.00")
//        metalPaymentValue.text = "₹${formatter.format(savedValue)}"
    }

    // Apply the metal exchange to the invoice
    private fun applyMetalExchange() {
        if (fineGoldAmount <= 0.0 && fineSilverAmount <= 0.0) {
            Toast.makeText(requireContext(), "Please enter metal amounts first", Toast.LENGTH_SHORT)
                .show()
            return
        }

        // Set the flag to indicate metal exchange is applied
        isMetalExchangeApplied = true

//        // Disable input fields and apply button
//        binding.goldFineEditText.isEnabled = false
//        binding.silverFineEditText.isEnabled = false
//        binding.applyGoldFineButton.isEnabled = false

        // Update total calculations
        updateTotals()

        Toast.makeText(requireContext(), "Metal exchange applied", Toast.LENGTH_SHORT).show()

        binding.fineMetalSummarySection.visibility = View.VISIBLE
        if (fineGoldAmount > 0) {
            binding.goldFineDeductionLayout.visibility = View.VISIBLE
            binding.goldFineDeductionValue.text = String.format("%.2f", fineGoldAmount) + "g"
        }
        if (fineSilverAmount > 0) {
            binding.silverFineDeductionLayout.visibility = View.VISIBLE
            binding.silverFineDeductionValue.text = String.format("%.2f", fineSilverAmount) + "g"
        }


    }

    // Calculate total gold weight from selected items
    private fun calculateTotalGoldWeight(): Double {
        val selectedItems = salesViewModel.selectedItems.value ?: emptyList()

        return selectedItems.sumOf { selectedItem ->
            if (isGoldItem(selectedItem.item)) {
                val weight =
                    if (selectedItem.item.metalRateOn.equals("Net Weight", ignoreCase = true)) {
                        selectedItem.item.netWeight
                    } else {
                        selectedItem.item.grossWeight
                    }
                weight * selectedItem.quantity
            } else {
                0.0
            }
        }
    }

    // Calculate total silver weight from selected items
    private fun calculateTotalSilverWeight(): Double {
        val selectedItems = salesViewModel.selectedItems.value ?: emptyList()

        return selectedItems.sumOf { selectedItem ->
            if (isSilverItem(selectedItem.item)) {
                val weight =
                    if (selectedItem.item.metalRateOn.equals("Net Weight", ignoreCase = true)) {
                        selectedItem.item.netWeight
                    } else {
                        selectedItem.item.grossWeight
                    }
                weight * selectedItem.quantity
            } else {
                0.0
            }
        }
    }

    // Update the display of total metal weights
    private fun updateMetalWeightsDisplay() {
        val totalGoldWeight = calculateTotalGoldWeight()
        val totalSilverWeight = calculateTotalSilverWeight()

        // Update gold section
        val goldSection = binding.goldFineSection
//        val totalGoldValue = fineMetalView.findViewById<TextView>(R.id.totalGoldValue)

        if (totalGoldWeight > 0) {
            goldSection.visibility = View.VISIBLE
            binding.totalGoldWeightValue.text = "${String.format("%.2f", totalGoldWeight)} g"

            // Update gold rate display with average or first item rate
            val goldItems =
                salesViewModel.selectedItems.value?.filter { isGoldItem(it.item) } ?: emptyList()
//            if (goldItems.isNotEmpty()) {
//                // Use the first gold item's rate for display
//                val goldRate = goldItems.first().item.metalRate
//                fineMetalView.findViewById<TextView>(R.id.goldRateValue).text =
//                    "₹${DecimalFormat("#,##,##0.00").format(goldRate)}/g"
//            }
        } else {
            goldSection.visibility = View.GONE
        }

        // Update silver section
        val silverSection = binding.silverFineSection
//        val totalSilverValue = fineMetalView.findViewById<TextView>(R.id.totalSilverValue)

        if (totalSilverWeight > 0) {
            silverSection.visibility = View.VISIBLE
            binding.totalSilverWeightValue.text = "${String.format("%.2f", totalSilverWeight)} g"

            // Update silver rate display
            val silverItems =
                salesViewModel.selectedItems.value?.filter { isSilverItem(it.item) } ?: emptyList()
//            if (silverItems.isNotEmpty()) {
//                // Use the first silver item's rate for display
//                val silverRate = silverItems.first().item.metalRate
//                fineMetalView.findViewById<TextView>(R.id.silverRateValue).text =
//                    "₹${DecimalFormat("#,##,##0.00").format(silverRate)}/g"
//            }
        } else {
            silverSection.visibility = View.GONE
        }

        // Show/hide the entire metal exchange view
        binding.metalFineCard.visibility =
            if (totalGoldWeight > 0 || totalSilverWeight > 0) View.VISIBLE else View.GONE
    }

    // Helper method to determine if an item is gold
    private fun isGoldItem(item: JewelleryItem): Boolean {
        return item.itemType.contains("gold", ignoreCase = true) ||
                item.category.contains("gold", ignoreCase = true)
    }

    // Helper method to determine if an item is silver
    private fun isSilverItem(item: JewelleryItem): Boolean {
        return item.itemType.contains("silver", ignoreCase = true) ||
                item.category.contains("silver", ignoreCase = true)
    }

    // Calculate the original invoice total
    private fun calculateInvoiceTotal(): Double {
        return salesViewModel.calculateTotal()
    }

    // Calculate the adjusted total with metal exchange
    private fun calculateAdjustedTotal(fineGoldAmount: Double, fineSilverAmount: Double): Double {
        val selectedItems = salesViewModel.selectedItems.value ?: emptyList()
        var adjustedTotal = 0.0

        // Calculate total weights for proportional allocation
        val totalGoldWeight = calculateTotalGoldWeight()
        val totalSilverWeight = calculateTotalSilverWeight()

        for (selectedItem in selectedItems) {
            val item = selectedItem.item
            val quantity = selectedItem.quantity

            if (isGoldItem(item)) {
                // Determine item weight
                val itemWeight = if (item.metalRateOn.equals("Net Weight", ignoreCase = true)) {
                    item.netWeight
                } else {
                    item.grossWeight
                }
                val totalItemWeight = itemWeight * quantity

                // Calculate proportion of total gold
                val proportion = if (totalGoldWeight > 0) totalItemWeight / totalGoldWeight else 0.0

                // Calculate allocated fine gold for this item
                val allocatedFineGold = fineGoldAmount * proportion

                // Calculate remaining gold to charge
                val remainingGold = totalItemWeight - allocatedFineGold

                // Calculate the metal value using this item's rate
                val metalValue = remainingGold * item.metalRate

                // Calculate making charges (unchanged)
                val makingCharges = if (item.makingChargesType.uppercase() == "PER GRAM") {
                    item.makingCharges * itemWeight * quantity
                } else {
                    item.makingCharges * quantity
                }

                // Add other charges
                val diamondPrice = item.diamondPrice * quantity
                val extraCharges = item.listOfExtraCharges.sumOf { it.amount } * quantity

                // Calculate tax
                val subtotal = metalValue + makingCharges + diamondPrice + extraCharges
                val tax = subtotal * (item.taxRate / 100.0)

                // Add to total
                adjustedTotal += subtotal + tax
            } else if (isSilverItem(item)) {
                // Determine item weight
                val itemWeight = if (item.metalRateOn.equals("Net Weight", ignoreCase = true)) {
                    item.netWeight
                } else {
                    item.grossWeight
                }
                val totalItemWeight = itemWeight * quantity

                // Calculate proportion of total silver
                val proportion =
                    if (totalSilverWeight > 0) totalItemWeight / totalSilverWeight else 0.0

                // Calculate allocated fine silver for this item
                val allocatedFineSilver = fineSilverAmount * proportion

                // Calculate remaining silver to charge
                val remainingSilver = totalItemWeight - allocatedFineSilver

                // Calculate the metal value using this item's rate
                val metalValue =
                    remainingSilver * item.metalRate  // Using goldRate field for silver

                // Calculate making charges (unchanged)
                val makingCharges = if (item.makingChargesType.uppercase() == "PER GRAM") {
                    item.makingCharges * itemWeight * quantity
                } else {
                    item.makingCharges * quantity
                }

                // Add other charges
                val extraCharges = item.listOfExtraCharges.sumOf { it.amount } * quantity

                // Calculate tax
                val subtotal = metalValue + makingCharges + extraCharges
                val tax = subtotal * (item.taxRate / 100.0)

                // Add to total
                adjustedTotal += subtotal + tax
            } else {
                // For non-metal items, just add the regular price
                adjustedTotal += selectedItem.price * quantity
            }
        }

        return adjustedTotal
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
//            val totalAmount = salesViewModel.calculateTotal()
            val totalAmount = if (isMetalExchangeApplied) {
                calculateAdjustedTotal(fineGoldAmount, fineSilverAmount)
            } else {
                salesViewModel.calculateTotal()
            }
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

            if (isMetalExchangeApplied) {
                val notes = binding.notesEditText.text.toString()
                val metalExchangeNote = "Metal Exchange Applied: " +
                        (if (fineGoldAmount > 0) "${
                            String.format(
                                "%.2f",
                                fineGoldAmount
                            )
                        }g Gold" else "") +
                        (if (fineGoldAmount > 0 && fineSilverAmount > 0) " and " else "") +
                        (if (fineSilverAmount > 0) "${
                            String.format(
                                "%.2f",
                                fineSilverAmount
                            )
                        }g Silver" else "")

                binding.notesEditText.setText(if (notes.isEmpty()) metalExchangeNote else "$notes\n$metalExchangeNote")
            }

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



