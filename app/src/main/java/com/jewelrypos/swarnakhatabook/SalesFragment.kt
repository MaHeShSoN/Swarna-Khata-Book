package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.jewelrypos.swarnakhatabook.Adapters.SalesViewPagerAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerListBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemSelectionBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.OrderListBottomSheet
import com.jewelrypos.swarnakhatabook.BottomSheet.PaymentEntryBottomSheet
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Order
import com.jewelrypos.swarnakhatabook.DataClasses.OrderItem
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.DataClasses.SelectedItemWithPrice
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.TabFragment.InvoicesFragment
import com.jewelrypos.swarnakhatabook.TabFragment.OrdersFragment
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentSalesBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalesFragment : Fragment(), ItemSelectionBottomSheet.OnItemsSelectedListener {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    // ViewModel for handling sales data
    private val salesViewModel: SalesViewModel by viewModels()

    // Reference to child fragments
    private var ordersFragment: OrdersFragment? = null
    private var invoicesFragment: InvoicesFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabLayout()
        setupFab()
        setupBackdrop()
        setupSearchView()
        setupObservers()
    }

    private fun setupSearchView() {
        val searchView = binding.topAppBar.menu.findItem(R.id.action_search).actionView as? androidx.appcompat.widget.SearchView
        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Pass search query to the current active fragment
                val currentPosition = binding.viewPager.currentItem
                when (currentPosition) {
                    0 -> ordersFragment?.searchOrders(query ?: "")
                    1 -> invoicesFragment?.searchInvoices(query ?: "")
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Pass search query to the current active fragment
                val currentPosition = binding.viewPager.currentItem
                when (currentPosition) {
                    0 -> ordersFragment?.searchOrders(newText ?: "")
                    1 -> invoicesFragment?.searchInvoices(newText ?: "")
                }
                return true
            }
        })
    }

    private fun setupObservers() {
        // Observe view model data as needed
        salesViewModel.selectedCustomer.observe(viewLifecycleOwner) { customer ->
            // Update UI or pass to child fragments as needed
        }

        salesViewModel.selectedItems.observe(viewLifecycleOwner) { items ->
            // Update UI or pass to child fragments as needed
        }
    }

    private fun setupTabLayout() {
        val viewPager = binding.viewPager
        val tabLayout = binding.tabLayout

        val adapter = SalesViewPagerAdapter(childFragmentManager, lifecycle).also {
            // Store references to the fragments
            ordersFragment = it.getOrdersFragment()
            invoicesFragment = it.getInvoicesFragment()
        }
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when(position) {
                0 -> "Orders"
                1 -> "Invoices"
                else -> null
            }
        }.attach()

        // Listen for tab changes to update toolbar menu
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Update toolbar actions based on current tab
                updateToolbarForTab(position)
            }
        })
    }

    private fun updateToolbarForTab(position: Int) {
        // Update toolbar based on whether we're showing orders or invoices
        val filterItem = binding.topAppBar.menu.findItem(R.id.action_filter)
        val sortItem = binding.topAppBar.menu.findItem(R.id.action_sort)

        when (position) {
            0 -> { // Orders
                filterItem.isVisible = true
                sortItem.isVisible = true
            }
            1 -> { // Invoices
                filterItem.isVisible = true
                sortItem.isVisible = true
            }
        }
    }

    private fun setupBackdrop() {
        binding.fabBackdrop.setOnClickListener {
            toggleFabMenu(false)
        }
    }

    private fun setupFab() {
        binding.mainFab.setOnClickListener {
            // Show/hide the expanded FAB options
            toggleFabMenu()
        }

        binding.newOrderFab.setOnClickListener {
            // Launch new order creation flow
            showNewOrderFlow()
            toggleFabMenu(false)
        }

        binding.newInvoiceFab.setOnClickListener {
            // Launch new invoice creation flow
            showNewInvoiceFlow()
            toggleFabMenu(false)
        }
    }

    private fun toggleFabMenu(show: Boolean? = null) {
        val isVisible = show ?: (binding.fabMenu.visibility != View.VISIBLE)

        if (isVisible) {
            binding.fabMenu.visibility = View.VISIBLE
            binding.fabBackdrop.visibility = View.VISIBLE
            binding.mainFab.setImageResource(R.drawable.material_symbols__close_rounded)

            // Animate the FABs
            binding.newOrderFab.show()
            binding.newInvoiceFab.show()
        } else {
            binding.fabMenu.visibility = View.GONE
            binding.fabBackdrop.visibility = View.GONE
            binding.mainFab.setImageResource(R.drawable.material_symbols__add_rounded)

            // Animate the FABs
            binding.newOrderFab.hide()
            binding.newInvoiceFab.hide()
        }
    }

    private fun showNewOrderFlow() {
        // First select a customer
        selectCustomer { customer ->
            // Then select items
            selectItems { selectedItems ->
                // Collect advance payment
                collectAdvancePayment(customer, selectedItems) { advanceAmount, paymentDetails ->
                    // Create the order with selected customer, items, and advance payment
                    createNewOrder(customer, selectedItems, advanceAmount, paymentDetails)
                }
            }
        }
    }


    private fun collectAdvancePayment(
        customer: Customer,
        selectedItems: List<SelectedItemWithPrice>,
        onPaymentCollected: (Double, Payment) -> Unit
    ) {
        // Calculate total amount
        val totalAmount = selectedItems.sumOf { it.price * it.quantity }

        // Create a payment entry sheet for advance payment
        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, 0.0)
        paymentSheet.setTitle("Collect Advance Payment")
        paymentSheet.setDescription("Collect advance payment for this order")

        paymentSheet.setOnPaymentAddedListener(object : PaymentEntryBottomSheet.OnPaymentAddedListener {
            override fun onPaymentAdded(payment: PaymentEntryBottomSheet.Payment) {
                // Convert the payment to our domain model
                val domainPayment = Payment(
                    id = generatePaymentId(),
                    amount = payment.amount,
                    method = payment.method,
                    date = payment.date,
                    reference = payment.reference,
                    notes = payment.notes
                )

                // Pass the advance amount and payment details to the callback
                onPaymentCollected(payment.amount, domainPayment)
            }
        })

        paymentSheet.show(parentFragmentManager, "AdvancePaymentSheet")
    }

    private fun showNewInvoiceFlow() {
        // First select a customer
        selectCustomer { customer ->
            // Then select items or convert from an order
            selectItemsForInvoice { selectedItems ->
                // Create the invoice with selected customer and items
                createNewInvoice(customer, selectedItems)
            }
        }
    }

    private fun selectCustomer(onCustomerSelected: (Customer) -> Unit) {

        // Show customer selection dialog
        val customerListBottomSheet = CustomerListBottomSheet.newInstance()
        customerListBottomSheet.setOnCustomerSelectedListener { customer ->
            onCustomerSelected(customer)
        }
        customerListBottomSheet.show(parentFragmentManager, "CustomerListBottomSheet")
    }

    private fun selectItems(onItemsSelected: (List<SelectedItemWithPrice>) -> Unit) {
        val itemSelectionSheet = ItemSelectionBottomSheet.newInstance()
        itemSelectionSheet.setOnItemsSelectedListener(this@SalesFragment)
        itemSelectionSheet.show(parentFragmentManager, "ItemSelectionBottomSheet")

        // Store the callback for later use when items are selected
        salesViewModel.setItemSelectionCallback(onItemsSelected)
    }

    private fun selectItemsForInvoice(onItemsSelected: (List<SelectedItemWithPrice>) -> Unit) {
        // Show option to create from scratch or convert order
        val options = arrayOf("Create new invoice", "Convert from order")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Create Invoice")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Path 1: Create new invoice from scratch
                        selectItems { selectedItems ->
                            // After items are selected, collect payment information
                            collectPaymentForInvoice(selectedItems, onItemsSelected)
                        }
                    }
                    1 -> {
                        // Path 2: Convert order to invoice
                        selectOrderToConvert { order ->
                            // Convert order to invoice items
                            val invoiceItems = convertOrderToInvoiceItems(order)

                            // Handle existing payments from the order
                            val existingPayments = order.payments
                            val existingPaidAmount = order.advanceAmount
                            val totalAmount = invoiceItems.sumOf { it.price * it.quantity }
                            val remainingAmount = totalAmount - existingPaidAmount

                            // If there's a remaining balance, collect additional payment
                            if (remainingAmount > 0) {
                                collectAdditionalPayment(invoiceItems, existingPayments, existingPaidAmount, totalAmount) {
                                        finalItems, finalPayments ->
                                    // Update the items with payment information and call the callback
                                    onItemsSelected(finalItems)
                                }
                            } else {
                                // No additional payment needed, proceed with items as-is
                                onItemsSelected(invoiceItems)
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun collectPaymentForInvoice(
        selectedItems: List<SelectedItemWithPrice>,
        onComplete: (List<SelectedItemWithPrice>) -> Unit
    ) {
        // Calculate total amount
        val totalAmount = selectedItems.sumOf { it.price * it.quantity }

        // Show payment options dialog
        val paymentOptions = arrayOf("Collect full payment now", "Collect partial payment", "No payment now")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Payment Collection")
            .setItems(paymentOptions) { _, which ->
                when (which) {
                    0 -> {
                        // Full payment
                        collectSpecificPayment(selectedItems, totalAmount, totalAmount) { updatedItems ->
                            onComplete(updatedItems)
                        }
                    }
                    1 -> {
                        // Partial payment - show a dialog to enter amount
                        showPartialPaymentDialog(selectedItems, totalAmount) { partialAmount ->
                            collectSpecificPayment(selectedItems, totalAmount, partialAmount) { updatedItems ->
                                onComplete(updatedItems)
                            }
                        }
                    }
                    2 -> {
                        // No payment now - proceed with items as-is
                        onComplete(selectedItems)
                    }
                }
            }
            .show()
    }


    private fun showPartialPaymentDialog(
        selectedItems: List<SelectedItemWithPrice>,
        totalAmount: Double,
        onAmountSelected: (Double) -> Unit
    ) {
        // Create a dialog with an EditText to enter partial amount
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_partial_payment, null)
        val amountEditText = dialogView.findViewById<TextInputEditText>(R.id.amountEditText)
        val totalAmountText = dialogView.findViewById<TextView>(R.id.totalAmountText)

        // Show the total amount
        totalAmountText.text = "₹${String.format("%.2f", totalAmount)}"

        // Default to half amount
        amountEditText.setText(String.format("%.2f", totalAmount / 2))

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Enter Partial Payment Amount")
            .setView(dialogView)
            .setPositiveButton("Collect") { _, _ ->
                val amount = amountEditText.text.toString().toDoubleOrNull() ?: 0.0
                if (amount > 0 && amount <= totalAmount) {
                    onAmountSelected(amount)
                } else {
                    Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                    // Retry
                    showPartialPaymentDialog(selectedItems, totalAmount, onAmountSelected)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Cancel - proceed with no payment
                onAmountSelected(0.0)
            }
            .show()
    }

    private fun collectSpecificPayment(
        selectedItems: List<SelectedItemWithPrice>,
        totalAmount: Double,
        amountToCollect: Double,
        onComplete: (List<SelectedItemWithPrice>) -> Unit
    ) {
        // Create a payment entry sheet
        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, 0.0)
        paymentSheet.setTitle("Collect Payment")
        paymentSheet.setDescription("Total: ₹${String.format("%.2f", totalAmount)} - Collecting: ₹${String.format("%.2f", amountToCollect)}")

        // Pre-fill the amount
        paymentSheet.setAmount(amountToCollect)

        paymentSheet.setOnPaymentAddedListener(object : PaymentEntryBottomSheet.OnPaymentAddedListener {
            override fun onPaymentAdded(payment: PaymentEntryBottomSheet.Payment) {
                // Store payment information with the items
                // In a real implementation, you'd attach this to the invoice model
                // For now, we'll just pass the updated items back

                // Create a copy of the items with payment information
                val updatedItems = selectedItems.map { it.copy() }

                // Store the payment information in SalesViewModel for later use
                salesViewModel.setCurrentPayment(
                    Payment(
                        id = generatePaymentId(),
                        amount = payment.amount,
                        method = payment.method,
                        date = payment.date,
                        reference = payment.reference,
                        notes = payment.notes
                    )
                )

                onComplete(updatedItems)
            }
        })

        paymentSheet.show(parentFragmentManager, "PaymentEntryBottomSheet")
    }

    private fun collectAdditionalPayment(
        invoiceItems: List<SelectedItemWithPrice>,
        existingPayments: List<Payment>,
        existingPaidAmount: Double,
        totalAmount: Double,
        onComplete: (List<SelectedItemWithPrice>, List<Payment>) -> Unit
    ) {
        val remainingAmount = totalAmount - existingPaidAmount

        // Show payment options dialog
        val paymentOptions = arrayOf(
            "Collect remaining balance (₹${String.format("%.2f", remainingAmount)})",
            "Collect different amount",
            "No additional payment"
        )

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Additional Payment")
            .setMessage("This order has an existing payment of ₹${String.format("%.2f", existingPaidAmount)}")
            .setItems(paymentOptions) { _, which ->
                when (which) {
                    0 -> {
                        // Collect remaining balance
                        collectPaymentAmount(invoiceItems, existingPayments, existingPaidAmount, totalAmount, remainingAmount) {
                                updatedItems, allPayments ->
                            onComplete(updatedItems, allPayments)
                        }
                    }
                    1 -> {
                        // Collect different amount
                        showPartialPaymentDialog(invoiceItems, remainingAmount) { partialAmount ->
                            collectPaymentAmount(invoiceItems, existingPayments, existingPaidAmount, totalAmount, partialAmount) {
                                    updatedItems, allPayments ->
                                onComplete(updatedItems, allPayments)
                            }
                        }
                    }
                    2 -> {
                        // No additional payment
                        onComplete(invoiceItems, existingPayments)
                    }
                }
            }
            .show()
    }

    private fun collectPaymentAmount(
        items: List<SelectedItemWithPrice>,
        existingPayments: List<Payment>,
        existingPaidAmount: Double,
        totalAmount: Double,
        amountToCollect: Double,
        onComplete: (List<SelectedItemWithPrice>, List<Payment>) -> Unit
    ) {
        // Create a payment entry sheet
        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, existingPaidAmount)
        paymentSheet.setTitle("Additional Payment")
        paymentSheet.setDescription("Total: ₹${String.format("%.2f", totalAmount)} - Already Paid: ₹${String.format("%.2f", existingPaidAmount)}")

        // Pre-fill the amount
        paymentSheet.setAmount(amountToCollect)

        paymentSheet.setOnPaymentAddedListener(object : PaymentEntryBottomSheet.OnPaymentAddedListener {
            override fun onPaymentAdded(payment: PaymentEntryBottomSheet.Payment) {
                // Create a new payment
                val newPayment = Payment(
                    id = generatePaymentId(),
                    amount = payment.amount,
                    method = payment.method,
                    date = payment.date,
                    reference = payment.reference,
                    notes = payment.notes
                )

                // Combine existing and new payment
                val allPayments = existingPayments + newPayment

                // Store the payment information in SalesViewModel for later use
                salesViewModel.setCurrentPayment(newPayment)

                // Pass back updated items and all payments
                onComplete(items, allPayments)
            }
        })

        paymentSheet.show(parentFragmentManager, "AdditionalPaymentSheet")
    }


    private fun selectOrderToConvert(onOrderSelected: (Order) -> Unit) {
        // Show list of orders to convert
        val orderListBottomSheet = OrderListBottomSheet.newInstance()
        orderListBottomSheet.setOnOrderSelectedListener { order ->
            onOrderSelected(order)
        }
        orderListBottomSheet.show(parentFragmentManager, "OrderListBottomSheet")
    }

    private fun convertOrderToInvoiceItems(order: Order): List<SelectedItemWithPrice> {
        // Convert order items to selected items with prices
        return order.items.map { orderItem ->
            SelectedItemWithPrice(
                item = orderItem.itemDetails,
                quantity = orderItem.quantity,
                price = calculateItemPrice(orderItem.itemDetails)
            )
        }
    }

    private fun calculateItemPrice(item: JewelleryItem): Double {
        // Calculate price based on weight, making charges, etc.
        val metalRate = when (item.itemType.lowercase()) {
            "gold" -> 6500.0 // Per gram rate for gold
            "silver" -> 85.0  // Per gram rate for silver
            else -> 1000.0    // Default rate
        }

        // Calculate based on weight and making charges
        val weightToUse = if (item.makingChargesOn == "GrossWeight") item.grossWeight else item.netWeight
        val metalValue = weightToUse * metalRate

        // Apply making charges
        val makingChargeValue = if (item.makingChargesType == "PER GRAM") {
            weightToUse * item.makingCharges
        } else {
            item.makingCharges // Fixed making charge
        }

        // Add diamond price if any
        val totalPrice = metalValue + makingChargeValue + item.diamondPrice

        return totalPrice
    }

    private fun createNewOrder(
        customer: Customer,
        selectedItems: List<SelectedItemWithPrice>,
        advanceAmount: Double,
        paymentDetails: Payment
    ) {
        // Create new order object
        val orderNumber = generateOrderNumber()
        val orderItems = selectedItems.map { selected ->
            OrderItem(
                itemId = selected.item.id,
                quantity = selected.quantity,
                itemDetails = selected.item
            )
        }

        val totalAmount = selectedItems.sumOf { it.price * it.quantity }

        val newOrder = Order(
            orderNumber = orderNumber,
            customerId = customer.id,
            customerName = "${customer.firstName} ${customer.lastName}",
            orderDate = System.currentTimeMillis(),
            deliveryDate = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000), // 7 days later
            items = orderItems,
            totalAmount = totalAmount,
            advanceAmount = advanceAmount,
            payments = listOf(paymentDetails), // Include payment details
            status = "Pending"
        )

        // Save the order
        salesViewModel.saveOrder(newOrder) { success ->
            if (success) {
                Toast.makeText(context, "Order created successfully", Toast.LENGTH_SHORT).show()
                // Refresh the orders list
                ordersFragment?.refreshOrders()
                // Switch to Orders tab
                binding.viewPager.currentItem = 0
            } else {
                Toast.makeText(context, "Failed to create order", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createNewInvoice(customer: Customer, selectedItems: List<SelectedItemWithPrice>) {
        // Create new invoice object
        val invoiceNumber = generateInvoiceNumber()
        val invoiceItems = selectedItems.map { selected ->
            InvoiceItem(
                itemId = selected.item.id,
                quantity = selected.quantity,
                itemDetails = selected.item,
                price = selected.price
            )
        }

        val totalAmount = selectedItems.sumOf { it.price * it.quantity }

        val newInvoice = Invoice(
            invoiceNumber = invoiceNumber,
            customerId = customer.id,
            customerName = "${customer.firstName} ${customer.lastName}",
            invoiceDate = System.currentTimeMillis(),
            items = invoiceItems,
            totalAmount = totalAmount
        )

        // Show payment collection dialog
        collectPayment(newInvoice, totalAmount) { invoice, payment ->
            // Add payment to invoice
            val updatedInvoice = invoice.copy(
                payments = listOf(payment),
                paidAmount = payment.amount
            )

            // Save the invoice
            salesViewModel.saveInvoice(updatedInvoice) { success ->
                if (success) {
                    Toast.makeText(context, "Invoice created successfully", Toast.LENGTH_SHORT).show()
                    // Refresh the invoices list
                    invoicesFragment?.refreshInvoices()
                    // Switch to Invoices tab
                    binding.viewPager.currentItem = 1
                } else {
                    Toast.makeText(context, "Failed to create invoice", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun collectPayment(invoice: Invoice, totalAmount: Double, onPaymentCollected: (Invoice, Payment) -> Unit) {
        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, 0.0)
        paymentSheet.setOnPaymentAddedListener(object : PaymentEntryBottomSheet.OnPaymentAddedListener {
            override fun onPaymentAdded(payment: PaymentEntryBottomSheet.Payment) {
                // Convert the payment to our domain model
                val domainPayment = Payment(
                    id = generatePaymentId(),
                    amount = payment.amount,
                    method = payment.method,
                    date = payment.date,
                    reference = payment.reference,
                    notes = payment.notes
                )

                onPaymentCollected(invoice, domainPayment)
            }
        })
        paymentSheet.show(parentFragmentManager, "PaymentEntryBottomSheet")
    }

    private fun generateOrderNumber(): String {
        val dateFormat = SimpleDateFormat("yyMMdd", Locale.getDefault())
        val datePart = dateFormat.format(Date())
        val random = (1000..9999).random()
        return "ORD-$datePart-$random"
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

    override fun onItemsSelected(items: List<ItemSelectionBottomSheet.SelectedItem>) {
        // Convert the selected items to SelectedItemWithPrice
        val selectedWithPrice = items.map { selected ->
            SelectedItemWithPrice(
                item = selected.item,
                quantity = selected.quantity,
                price = calculateItemPrice(selected.item)
            )
        }

        // Notify the view model
        salesViewModel.setSelectedItems(selectedWithPrice)

        // Call the stored callback
        salesViewModel.getItemSelectionCallback()?.invoke(selectedWithPrice)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



}