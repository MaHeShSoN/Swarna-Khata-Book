package com.jewelrypos.swarnakhatabook


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
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
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoiceCreationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class InvoiceCreationFragment : Fragment() {

    private var _binding: FragmentInvoiceCreationBinding? = null
    private val binding get() = _binding!!
    private val salesViewModel: SalesViewModel by activityViewModels()



    private lateinit var itemsAdapter: SelectedItemsAdapter

//    private lateinit var paymentsAdapter: PaymentsAdapter


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

    }

    private fun initializeViews() {


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
                        val updated = salesViewModel.updateSelectedItem(updatedItem, price) // Keep this for now, we might adjust it.

                        if (updated) {
                            Toast.makeText(context, "Item updated successfully", Toast.LENGTH_SHORT).show()
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
    }


    private fun updateCustomerSection(customer: Customer) {
        binding.noCustomerSelected.visibility = View.GONE
        binding.customerDetailsLayout.visibility = View.VISIBLE
        binding.customerSectionTitle.visibility = View.VISIBLE
        binding.editCustomerButton.visibility = View.VISIBLE

        binding.customerName.text = "${customer.firstName} ${customer.lastName}"
        binding.customerPhone.text = customer.phoneNumber
        val balanceText = when {
            customer.balanceType == "Credit" && customer.openingBalance > 0 ->
                "To Receive: ₹${customer.openingBalance}"

            customer.balanceType == "Debit" && customer.openingBalance > 0 ->
                "To Pay: ₹${customer.openingBalance}"

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
                salesViewModel.addSelectedItem(item, price)
            }

            override fun onItemUpdated(item: JewelleryItem, price: Double) {
                val updated = salesViewModel.updateSelectedItem(item, price)

                if (updated) {
                    Toast.makeText(context, "Item updated successfully", Toast.LENGTH_SHORT).show()
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

//    private fun addPayment() {
//        val totalAmount = calculateTotal()
//        val paidAmount = calculateTotalPaid()
//        val dueAmount = totalAmount - paidAmount
//
//        if (dueAmount <= 0) {
//            Toast.makeText(context, "Invoice is already fully paid", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val paymentSheet = PaymentEntryBottomSheet.newInstance(totalAmount, paidAmount)
//        paymentSheet.setOnPaymentAddedListener(object :
//            PaymentEntryBottomSheet.OnPaymentAddedListener {
//            override fun onPaymentAdded(payment: PaymentEntryBottomSheet.Payment) {
//                // Add payment to list
//                val newPayment = Payment(
//                    id = generatePaymentId(),
//                    amount = payment.amount,
//                    method = payment.method,
//                    date = payment.date,
//                    reference = payment.reference,
//                    notes = payment.notes
//                )
//                val currentPayments = paymentsAdapter.getPayments().toMutableList()
//                currentPayments.add(newPayment)
//                paymentsAdapter.updatePayments(currentPayments)
//
//                // Update totals
//                updateTotals()
//            }
//        })
//        paymentSheet.show(parentFragmentManager, "PaymentEntryBottomSheet")
//    }

    private fun updateTotals() {
        val subtotal = salesViewModel.calculateSubtotal()
        val extraChargesTotal = salesViewModel.calculateExtraCharges()
        val tax = salesViewModel.calculateTax()
        val total = salesViewModel.calculateTotal()
        val paid = 0.0 // Replace with actual payment calculation when implemented
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
            val chargeView = layoutInflater.inflate(R.layout.item_extra_charge_layout, binding.extraChargesContainer, false)

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

    private fun calculateTax(subtotal: Double): Double {
        // Simplified - in real app you'd have tax rates configuration
        return subtotal * 0.03 // 3% tax
    }

    private fun calculateTotal(): Double {
        val subtotal = calculateSubtotal()
        val tax = calculateTax(subtotal)
        return subtotal + tax
    }

//    private fun calculateTotalPaid(): Double {
//        return paymentsAdapter.getPayments().sumOf { it.amount }
//    }

//    private fun saveInvoice() {
//        // Validate
//        if (!validateInvoice()) {
//            return
//        }
//
//        // Create invoice object
//        val invoiceNumber = generateInvoiceNumber()
//        val customer = salesViewModel.selectedCustomer.value
//            ?: throw IllegalStateException("Customer must be selected")
//
//        val invoiceItems = itemsAdapter.getItems().map { selected ->
//            InvoiceItem(
//                itemId = selected.item.id,
//                quantity = selected.quantity,
//                itemDetails = selected.item,
//                price = selected.price
//            )
//        }
//
//        val payments = paymentsAdapter.getPayments()
//        val totalAmount = calculateTotal()
//        val paidAmount = calculateTotalPaid()
//
//        val invoice = Invoice(
//            invoiceNumber = invoiceNumber,
//            customerId = customer.id,
//            customerName = "${customer.firstName} ${customer.lastName}",
//            invoiceDate = System.currentTimeMillis(),
//            items = invoiceItems,
//            payments = payments,
//            totalAmount = totalAmount,
//            paidAmount = paidAmount
//        )
//
//        // Save the invoice
//        salesViewModel.saveInvoice(invoice) { success ->
//            if (success) {
//                Toast.makeText(context, "Invoice saved successfully", Toast.LENGTH_SHORT).show()
//                findNavController().navigateUp()
//            } else {
//                Toast.makeText(context, "Failed to save invoice", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }

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