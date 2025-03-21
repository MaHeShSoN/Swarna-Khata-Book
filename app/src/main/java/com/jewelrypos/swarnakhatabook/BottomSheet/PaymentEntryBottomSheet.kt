package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
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

        // Update payment summary display
        binding.totalAmountText.text = "₹${String.format("%.2f", invoiceTotal)}"
        binding.amountPaidText.text = "₹${String.format("%.2f", amountPaid)}"
        binding.balanceDueText.text = "₹${String.format("%.2f", invoiceTotal - amountPaid)}"

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
        val methods =
            listOf("Cash", "Card", "UPI", "Bank Transfer", "Old Gold", "Old Silver", "Store Credit")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, methods)
        binding.paymentMethodDropdown.setAdapter(adapter)

        binding.paymentMethodDropdown.setOnItemClickListener { _, _, position, _ ->
            // Show additional fields based on payment method
            when (methods[position]) {
                "Card" -> {
                    showCardFields()
                    showAmountField()
                }

                "UPI" -> {
                    showUpiFields()
                    showAmountField()
                }

                "Bank Transfer" -> {
                    showBankTransferFields()
                    showAmountField()
                }

                "Old Gold" -> {
                    showOldGoldFields()
                    hideAmountField()
                }

                "Old Silver" -> {
                    showOldSilverFields()
                    hideAmountField()
                }

                else -> {
                    hideAdditionalFields()
                    showAmountField()
                }
            }
        }
    }

    // Add these helper methods
    private fun hideAmountField() {
        // Hide the amount field and quick amount buttons
        binding.amountInputLayout.visibility = View.GONE
        binding.fullAmountButton.visibility = View.GONE
        binding.halfAmountButton.visibility = View.GONE
    }

    private fun showAmountField() {
        // Show the amount field and quick amount buttons
        binding.amountInputLayout.visibility = View.VISIBLE
        binding.fullAmountButton.visibility = View.VISIBLE
        binding.halfAmountButton.visibility = View.VISIBLE
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

        // Get references to all fields
        val weightEditText = goldFields.findViewById<TextInputEditText>(R.id.goldWeightEditText)
        val purityEditText = goldFields.findViewById<TextInputEditText>(R.id.goldPurityEditText)
        val rateEditText = goldFields.findViewById<TextInputEditText>(R.id.goldRateEditText)
        val valueEditText = goldFields.findViewById<TextInputEditText>(R.id.goldValueEditText)

        // Set default for common purities as a helper text
        goldFields.findViewById<TextInputLayout>(R.id.goldPurityLayout).helperText =
            "Common purities: 99.9% (24K), 91.6% (22K), 75% (18K)"

        // Add text watchers to recalculate on any change
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateGoldValue(
                    weightEditText.text.toString().toDoubleOrNull() ?: 0.0,
                    purityEditText.text.toString().toDoubleOrNull() ?: 0.0,
                    rateEditText.text.toString().toDoubleOrNull() ?: 0.0,
                    valueEditText
                )
            }
        }

        weightEditText.addTextChangedListener(textWatcher)
        purityEditText.addTextChangedListener(textWatcher)
        rateEditText.addTextChangedListener(textWatcher)

        // Initialize with default values if needed
        purityEditText.setText("91.6") // Default to 22K
    }

    private fun calculateGoldValue(
        weight: Double,
        purity: Double,
        ratePerGram: Double,
        valueEditText: TextInputEditText
    ) {
        if (weight > 0 && purity > 0 && ratePerGram > 0) {
            // Calculate value based on weight, purity percentage, and rate
            val purityFactor = purity / 100.0
            val value = weight * purityFactor * ratePerGram
            valueEditText.setText(String.format("%.2f", value))
        }
    }

    private fun showOldSilverFields() {
        binding.additionalFieldsContainer.visibility = View.VISIBLE
        binding.additionalFieldsContainer.removeAllViews()

        // Inflate silver exchange fields layout
        val silverFields = layoutInflater.inflate(
            R.layout.payment_silver_exchange_fields,
            binding.additionalFieldsContainer,
            true
        )

        // Get references to all fields
        val weightEditText = silverFields.findViewById<TextInputEditText>(R.id.silverWeightEditText)
        val purityEditText = silverFields.findViewById<TextInputEditText>(R.id.silverPurityEditText)
        val rateEditText = silverFields.findViewById<TextInputEditText>(R.id.silverRateEditText)
        val valueEditText = silverFields.findViewById<TextInputEditText>(R.id.silverValueEditText)

        // Set default for common purities as a helper text
        silverFields.findViewById<TextInputLayout>(R.id.silverPurityLayout).helperText =
            "Common purities: 99.9% (Fine), 92.5% (Sterling), 80% (Coin)"

        // Add text watchers to recalculate on any change
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateSilverValue(
                    weightEditText.text.toString().toDoubleOrNull() ?: 0.0,
                    purityEditText.text.toString().toDoubleOrNull() ?: 0.0,
                    rateEditText.text.toString().toDoubleOrNull() ?: 0.0,
                    valueEditText
                )
            }
        }

        weightEditText.addTextChangedListener(textWatcher)
        purityEditText.addTextChangedListener(textWatcher)
        rateEditText.addTextChangedListener(textWatcher)

        // Initialize with default values if needed
        purityEditText.setText("92.5") // Default to sterling silver
    }

    private fun calculateSilverValue(
        weight: Double,
        purity: Double,
        ratePerGram: Double,
        valueEditText: TextInputEditText
    ) {
        if (weight > 0 && purity > 0 && ratePerGram > 0) {
            // Calculate value based on weight, purity percentage, and rate
            val purityFactor = purity / 100.0
            val value = weight * purityFactor * ratePerGram
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

        // Setup card type dropdown
        val cardTypeDropdown = cardFields.findViewById<AutoCompleteTextView>(R.id.cardTypeDropdown)
        val cardTypes = listOf("Visa", "MasterCard", "RuPay", "American Express", "Other")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, cardTypes)
        cardTypeDropdown.setAdapter(adapter)

        // Set up formatting for card number
        val cardNumberEditText = cardFields.findViewById<TextInputEditText>(R.id.cardNumberEditText)
        cardNumberEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // No need to implement complex formatting here
            }
        })

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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                setupFullHeight(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
            }
        }

        return dialog
    }

    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
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
        val method = binding.paymentMethodDropdown.text.toString()

        // Determine amount based on payment method
        val amount = when (method) {
            "Old Gold" -> {
                // Get the gold value from the value field
                val goldValueField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.goldValueEditText)
                goldValueField?.text.toString().toDoubleOrNull() ?: 0.0
            }

            "Old Silver" -> {
                // Get the silver value from the value field
                val silverValueField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.silverValueEditText)
                silverValueField?.text.toString().toDoubleOrNull() ?: 0.0
            }

            else -> {
                // For other payment methods, use the amount field
                binding.amountEditText.text.toString().toDoubleOrNull() ?: 0.0
            }
        }

        return Payment(
            id = "", // Will be set by the Invoice fragment
            amount = amount,
            method = method,
            date = System.currentTimeMillis(),
            reference = binding.referenceEditText.text.toString(),
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

}