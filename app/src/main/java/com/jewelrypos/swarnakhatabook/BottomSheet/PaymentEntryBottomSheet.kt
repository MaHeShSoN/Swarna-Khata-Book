package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
            listOf(
                "Cash",
                "UPI",
                "Card",
                "Bank Transfer",
                "Gold Exchange",
                "Silver Exchange",
                "Store Credit"
            )
        val adapter =
            ArrayAdapter(requireContext(), R.layout.dropdown_item, methods)
        binding.paymentMethodDropdown.apply {
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.my_light_surface)
        }

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

                "Gold Exchange" -> {
                    showOldGoldFields()
                    hideAmountField()
                }

                "Silver Exchange" -> {
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
            ArrayAdapter(requireContext(), R.layout.dropdown_item, upiApps)
        upiAppDropdown.apply {
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
        }
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
            ArrayAdapter(requireContext(), R.layout.dropdown_item, banks)

        bankNameDropdown.apply {
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
        }

    }

    // Helper method to show date picker
    private fun showDatePicker(dateEditText: TextInputEditText) {
        val calendar = Calendar.getInstance()

        // If there's existing text, try to parse it
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val existingText = dateEditText.text.toString()
        if (existingText.isNotEmpty()) {
            try {
                val date = dateFormat.parse(existingText)
                if (date != null) {
                    calendar.time = date
                }
            } catch (e: Exception) {
                // Parse failed, use current date
            }
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                dateEditText.setText(dateFormat.format(calendar.time))
            },
            year, month, day
        )

        // Don't allow future dates more than a day ahead
        val maxDate = Calendar.getInstance()
        maxDate.add(Calendar.DAY_OF_MONTH, 1)
        datePickerDialog.datePicker.maxDate = maxDate.timeInMillis

        // Don't allow dates more than 30 days in the past
        val minDate = Calendar.getInstance()
        minDate.add(Calendar.DAY_OF_MONTH, -30)
        datePickerDialog.datePicker.minDate = minDate.timeInMillis

        datePickerDialog.show()
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
            ArrayAdapter(requireContext(), R.layout.dropdown_item, cardTypes)
        cardTypeDropdown.setAdapter(adapter)
        cardTypeDropdown.apply {
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.my_light_surface)
        }

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

        // Add date picker field setup
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.paymentDateEditText.setText(dateFormat.format(Date()))

        // Set up date picker on click
        binding.paymentDateEditText.setOnClickListener {
            showDatePicker(binding.paymentDateEditText)
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
        // Get selected payment method
        val method = binding.paymentMethodDropdown.text.toString()

        // For Old Gold and Old Silver, we validate the metal value instead of amount
        if (method == "Old Gold") {
            val goldValueField = binding.additionalFieldsContainer
                .findViewById<TextInputEditText>(R.id.goldValueEditText)
            val goldValue = goldValueField?.text.toString().toDoubleOrNull()

            if (goldValue == null || goldValue <= 0) {
                // Show error for gold value
                val goldValueLayout = binding.additionalFieldsContainer
                    .findViewById<TextInputLayout>(R.id.goldValueLayout)
                goldValueLayout?.error = "Please enter a valid gold value"
                return false
            }
        } else if (method == "Old Silver") {
            val silverValueField = binding.additionalFieldsContainer
                .findViewById<TextInputEditText>(R.id.silverValueEditText)
            val silverValue = silverValueField?.text.toString().toDoubleOrNull()

            if (silverValue == null || silverValue <= 0) {
                // Show error for silver value
                val silverValueLayout = binding.additionalFieldsContainer
                    .findViewById<TextInputLayout>(R.id.silverValueLayout)
                silverValueLayout?.error = "Please enter a valid silver value"
                return false
            }
        } else {
            // For regular payment methods, validate the amount
            val amount = binding.amountEditText.text.toString().toDoubleOrNull()
            if (amount == null || amount <= 0) {
                binding.amountInputLayout.error = "Please enter a valid amount"
                return false
            }

            if (amount > (invoiceTotal - amountPaid)) {
                binding.amountInputLayout.error = "Amount exceeds remaining balance"
                return false
            }
        }

        if (binding.paymentMethodDropdown.text.isNullOrEmpty()) {
            binding.paymentMethodLayout.error = "Please select a payment method"
            return false
        }

        // Other validations can remain unchanged
        return true
    }


    private fun createPaymentFromForm(): Payment {
        val method = binding.paymentMethodDropdown.text.toString()
        var paymentDate = System.currentTimeMillis() // Default to current time
        try {
            val dateStr = binding.paymentDateEditText.text.toString()
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = dateFormat.parse(dateStr)
            if (date != null) {
                paymentDate = date.time // This should be a Long timestamp
            }
        } catch (e: Exception) {
            // Use default current time if parsing fails
            Log.e("PaymentEntry", "Error parsing date", e)
        }




        // Create a map for method-specific details
        val details = mutableMapOf<String, Any>()

        // Handle method-specific fields
        when (method) {
            "Bank Transfer" -> {
                val bankNameDropdown = binding.additionalFieldsContainer
                    .findViewById<AutoCompleteTextView>(R.id.bankNameDropdown)
                val bankName = bankNameDropdown?.text?.toString() ?: ""

                // Get account number
                val accountNumberField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.accountNumberEditText)
                val accountNumber = accountNumberField?.text?.toString() ?: ""


                // Add to details map
                if (bankName.isNotEmpty()) details["bankName"] = bankName
                if (accountNumber.isNotEmpty()) details["accountNumber"] = accountNumber

            }

            "Card" -> {
                // Get card type
                val cardTypeDropdown = binding.additionalFieldsContainer
                    .findViewById<AutoCompleteTextView>(R.id.cardTypeDropdown)
                val cardType = cardTypeDropdown?.text?.toString() ?: ""

                // Get last 4 digits (if available)
                val cardNumberField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.cardNumberEditText)
                val cardNumber = cardNumberField?.text?.toString() ?: ""
                val last4Digits = if (cardNumber.length >= 4) cardNumber.takeLast(4) else cardNumber

                // Add to details map
                if (cardType.isNotEmpty()) details["cardType"] = cardType
                if (last4Digits.isNotEmpty()) details["last4Digits"] = last4Digits
            }

            "UPI" -> {
                // Get UPI app
                val upiAppDropdown = binding.additionalFieldsContainer
                    .findViewById<AutoCompleteTextView>(R.id.upiAppDropdown)
                val upiApp = upiAppDropdown?.text?.toString() ?: ""
                val upiID = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.upiIdEditText).text.toString()

                // Add to details map
                if (upiApp.isNotEmpty()) details["upiApp"] = upiApp
                if (upiID.isNotEmpty()) details["upiID"] = upiID
            }

            "Gold Exchange" -> {
                // Get gold details
                val weightField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.goldWeightEditText)
                val purityField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.goldPurityEditText)
                val rateField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.goldRateEditText)

                val weight = weightField?.text?.toString()?.toDoubleOrNull() ?: 0.0
                val purity = purityField?.text?.toString()?.toDoubleOrNull() ?: 0.0
                val rate = rateField?.text?.toString()?.toDoubleOrNull() ?: 0.0

                // Add to details map
                details["weight"] = weight
                details["purity"] = purity
                details["rate"] = rate
            }

            "Silver Exchange" -> {
                // Get silver details
                val weightField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.silverWeightEditText)
                val purityField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.silverPurityEditText)
                val rateField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.silverRateEditText)

                val weight = weightField?.text?.toString()?.toDoubleOrNull() ?: 0.0
                val purity = purityField?.text?.toString()?.toDoubleOrNull() ?: 0.0
                val rate = rateField?.text?.toString()?.toDoubleOrNull() ?: 0.0

                // Add to details map
                details["weight"] = weight
                details["purity"] = purity
                details["rate"] = rate
            }
        }

        // Determine amount based on payment method
        val amount = when (method) {
            "Gold Exchange" -> {
                val goldValueField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.goldValueEditText)
                goldValueField?.text.toString().toDoubleOrNull() ?: 0.0
            }

            "Silver Exchange" -> {
                val silverValueField = binding.additionalFieldsContainer
                    .findViewById<TextInputEditText>(R.id.silverValueEditText)
                silverValueField?.text.toString().toDoubleOrNull() ?: 0.0
            }

            else -> {
                binding.amountEditText.text.toString().toDoubleOrNull() ?: 0.0
            }
        }

        // Format reference ID if appropriate for the payment method
        val rawReference = binding.referenceEditText.text.toString().trim()
        val formattedReference = formatReferenceId(method, rawReference)

        return Payment(
            amount = amount,
            method = method,
            date = paymentDate,
            reference = formattedReference,
            notes = binding.notesEditText.text.toString(),
            details = details  // Add method-specific details
        )
    }

    /**
     * Formats reference IDs based on payment method conventions
     */
    private fun formatReferenceId(method: String, reference: String): String {
        if (reference.isEmpty()) return reference

        return when (method) {
            "UPI" -> {
                // UPI references are typically alphanumeric - ensure uppercase
                reference.uppercase()
            }

            "Bank Transfer" -> {
                // Bank transfer references (UTR numbers) are 22 characters for NEFT/RTGS
                // Just trim whitespace and ensure uppercase
                reference.uppercase()
            }

            "Card" -> {
                // Card authorization codes are typically 6 characters
                reference.uppercase()
            }

            else -> reference
        }
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