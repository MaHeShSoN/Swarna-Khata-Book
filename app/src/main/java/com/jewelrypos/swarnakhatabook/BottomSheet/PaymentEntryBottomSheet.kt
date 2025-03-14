package com.jewelrypos.swarnakhatabook.BottomSheet

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.databinding.BottomsheetpaymententryBinding

// PaymentEntryBottomSheet.kt
class PaymentEntryBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetpaymententryBinding? = null
    private val binding get() = _binding!!

    private var invoiceTotal: Double = 0.0
    private var amountPaid: Double = 0.0
    private var listener: OnPaymentAddedListener? = null

    // Add these properties to store pending values
    private var pendingTitle: String? = null
    private var pendingDescription: String? = null

    private var pendingAmount: Double? = null

    interface OnPaymentAddedListener {
        fun onPaymentAdded(payment: Payment)
    }

    fun setOnPaymentAddedListener(listener: OnPaymentAddedListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetpaymententryBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun setTitle(title: String) {
        // Check if binding is initialized and view is attached
        if (_binding != null) {
            binding.titleTextView.text = title
        } else {
            // Store the title to set when view is created
            pendingTitle = title
        }
    }

    fun setDescription(description: String) {
        // Check if binding is initialized and view is attached
        if (_binding != null) {
            binding.descriptionTextView.text = description
            binding.descriptionTextView.visibility = View.VISIBLE
        } else {
            // Store the description to set when view is created
            pendingDescription = description
        }
    }

    fun setAmount(amount: Double) {
        if (_binding != null) {
            binding.amountEditText.setText(String.format("%.2f", amount))
        } else {
            pendingAmount = amount
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply any pending values
        pendingAmount?.let {
            binding.amountEditText.setText(String.format("%.2f", it))
            pendingAmount = null
        }


        // Apply any pending title/description
        pendingTitle?.let {
            binding.titleTextView.text = it
            pendingTitle = null
        }

        pendingDescription?.let {
            binding.descriptionTextView.text = it
            binding.descriptionTextView.visibility = View.VISIBLE
            pendingDescription = null
        }


        setupPaymentAmountSuggestions()
        setupPaymentMethodSelection()
        setupButtons()
    }

    private fun setupPaymentAmountSuggestions() {
        val remainingAmount = invoiceTotal - amountPaid

        binding.fullAmountButton.text = "Full (₹${String.format("%.2f", remainingAmount)})"
        binding.fullAmountButton.setOnClickListener {
            binding.amountEditText.setText(String.format("%.2f", remainingAmount))
        }

        binding.halfAmountButton.text = "Half (₹${String.format("%.2f", remainingAmount / 2)})"
        binding.halfAmountButton.setOnClickListener {
            binding.amountEditText.setText(String.format("%.2f", remainingAmount / 2))
        }
    }

    private fun setupPaymentMethodSelection() {
        val methods = listOf("Cash", "Card", "UPI", "Bank Transfer", "Old Gold", "Store Credit")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, methods)
        binding.paymentMethodDropdown.setAdapter(adapter)

        binding.paymentMethodDropdown.setOnItemClickListener { _, _, position, _ ->
            // Show additional fields based on payment method
            when (methods[position]) {
                "Card" -> showCardFields()
                "UPI" -> showUpiFields()
                "Bank Transfer" -> showBankTransferFields()
                "Old Gold" -> showOldGoldFields()
                else -> hideAdditionalFields()
            }
        }
    }

    private fun showUpiFields() {
        binding.additionalFieldsContainer.visibility = View.VISIBLE
        binding.additionalFieldsContainer.removeAllViews()

        // Inflate UPI fields layout
        val upiFields = layoutInflater.inflate(
            R.layout.payment_upi_fields,
            binding.additionalFieldsContainer,
            true
        )

        // Setup UPI app dropdown
        val upiAppDropdown = upiFields.findViewById<AutoCompleteTextView>(R.id.upiAppDropdown)
        val upiApps = listOf("PhonePe", "Google Pay", "Paytm", "BHIM", "Amazon Pay", "Other")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, upiApps)
        upiAppDropdown.setAdapter(adapter)
    }

    private fun showBankTransferFields() {
        binding.additionalFieldsContainer.visibility = View.VISIBLE
        binding.additionalFieldsContainer.removeAllViews()

        // Create bank transfer fields layout
        val bankFields = layoutInflater.inflate(
            R.layout.payment_bank_fields,
            binding.additionalFieldsContainer,
            true
        )

        // Setup fields
        val accountNumberField =
            bankFields.findViewById<TextInputEditText>(R.id.accountNumberEditText)
        val bankNameDropdown = bankFields.findViewById<AutoCompleteTextView>(R.id.bankNameDropdown)

        // Setup bank dropdown
        val banks =
            listOf("SBI", "HDFC", "ICICI", "Axis", "PNB", "Bank of Baroda", "Union Bank", "Other")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, banks)
        bankNameDropdown.setAdapter(adapter)
    }

    private fun showOldGoldFields() {
        binding.additionalFieldsContainer.visibility = View.VISIBLE
        binding.additionalFieldsContainer.removeAllViews()

        // Inflate gold exchange fields layout
        val goldFields = layoutInflater.inflate(
            R.layout.payment_gold_exchange_fields,
            binding.additionalFieldsContainer,
            true
        )

        // Setup purity dropdown
        val purityDropdown = goldFields.findViewById<AutoCompleteTextView>(R.id.goldPurityDropdown)
        val purities = listOf("24K", "22K", "20K", "18K", "14K", "Other")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, purities)
        purityDropdown.setAdapter(adapter)

        // Auto-calculate value based on weight and purity if possible
        val weightEditText = goldFields.findViewById<TextInputEditText>(R.id.goldWeightEditText)
        val valueEditText = goldFields.findViewById<TextInputEditText>(R.id.goldValueEditText)

        weightEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateGoldValue(
                    goldFields.findViewById<AutoCompleteTextView>(R.id.goldPurityDropdown).text.toString(),
                    s.toString().toDoubleOrNull() ?: 0.0,
                    valueEditText
                )
            }
        })

        purityDropdown.setOnItemClickListener { _, _, _, _ ->
            calculateGoldValue(
                purityDropdown.text.toString(),
                weightEditText.text.toString().toDoubleOrNull() ?: 0.0,
                valueEditText
            )
        }
    }

    // Let's also create the XML for the Bank Transfer fields
    private fun calculateGoldValue(
        purity: String,
        weight: Double,
        valueEditText: TextInputEditText
    ) {
        // This is a simplified calculation. In a real app, you might want to fetch current gold rates
        val ratePerGram = when (purity) {
            "24K" -> 6500.0
            "22K" -> 6000.0
            "20K" -> 5500.0
            "18K" -> 5000.0
            "14K" -> 4000.0
            else -> 0.0
        }

        if (ratePerGram > 0 && weight > 0) {
            val value = weight * ratePerGram
            valueEditText.setText(String.format("%.2f", value))
        }
    }

    private fun showCardFields() {
        binding.additionalFieldsContainer.visibility = View.VISIBLE
        binding.additionalFieldsContainer.removeAllViews()

        // Inflate card fields layout
        val cardFields = layoutInflater.inflate(
            R.layout.payment_card_fields,
            binding.additionalFieldsContainer,
            true
        )
        // Setup card fields
    }

    // Similar methods for other payment types

    private fun hideAdditionalFields() {
        binding.additionalFieldsContainer.visibility = View.GONE
    }

    private fun setupButtons() {
        binding.addPaymentButton.setOnClickListener {
            if (validatePayment()) {
                val payment = createPaymentFromForm()
                listener?.onPaymentAdded(payment)
                dismiss()
            }
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun validatePayment(): Boolean {
        val amount = binding.amountEditText.text.toString().toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.amountInputLayout.error = "Please enter a valid amount"
            return false
        }

        if (amount > (invoiceTotal - amountPaid)) {
            binding.amountInputLayout.error = "Amount exceeds remaining balance"
            return false
        }

        if (binding.paymentMethodDropdown.text.isNullOrEmpty()) {
            binding.paymentMethodLayout.error = "Please select a payment method"
            return false
        }

        // Validate additional fields based on payment method

        return true
    }

    private fun createPaymentFromForm(): Payment {
        return Payment(
            id = "", // Will be set by repository
            amount = binding.amountEditText.text.toString().toDouble(),
            method = binding.paymentMethodDropdown.text.toString(),
            date = System.currentTimeMillis(),
            reference = binding.referenceEditText.text.toString(),
            // Add additional fields based on payment method
            notes = binding.notesEditText.text.toString()
        )
    }

    fun setInvoiceDetails(invoiceTotal: Double, amountPaid: Double) {
        this.invoiceTotal = invoiceTotal
        this.amountPaid = amountPaid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(invoiceTotal: Double, amountPaid: Double): PaymentEntryBottomSheet {
            val fragment = PaymentEntryBottomSheet()
            fragment.setInvoiceDetails(invoiceTotal, amountPaid)
            return fragment
        }
    }

    data class Payment(
        val id: String,
        val amount: Double,
        val method: String,
        val date: Long,
        val reference: String,
        val notes: String
    )
}