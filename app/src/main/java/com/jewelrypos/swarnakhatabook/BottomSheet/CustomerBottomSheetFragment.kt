

package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.RadioButton
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.databinding.CustomerBottomSheetFragmentBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CustomerBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: CustomerBottomSheetFragmentBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private var isEditMode = false
    private var existingCustomerId: String? = null
    private var listener: CustomerOperationListener? = null

    interface CustomerOperationListener {
        fun onCustomerAdded(customer: Customer)
        fun onCustomerUpdated(customer: Customer)
    }

    fun setCustomerOperationListener(listener: CustomerOperationListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = CustomerBottomSheetFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set full expanded height
        (view.parent as View).setBackgroundColor(Color.TRANSPARENT)

        setupCustomerTypeDropdown()
        setupCountryDropdown()
        setupDatePickers()
        setupFormValidation()
        setupActionButtons()
        setupBalanceTypeRadioButtons()

        // Toggle visibility of business fields based on customer type
        binding.customerTypeDropdown.addTextChangedListener {
            val isBusinessCustomer = it.toString() == "Wholesaler"
            binding.businessInfoCard.isVisible = isBusinessCustomer
            binding.businessInfoCardText.isVisible = isBusinessCustomer
        }

        // Check if we're in edit mode
        arguments?.let {
            val customer = it.getSerializable(ARG_CUSTOMER) as? Customer
            if (customer != null) {
                isEditMode = true
                existingCustomerId = customer.id
                populateFormWithCustomerData(customer)
            }
        }
    }

    private fun setupBalanceTypeRadioButtons() {
        binding.creditRadioButton.isChecked = true // Default to credit
    }

    private fun setupCustomerTypeDropdown() {
        val items = listOf("Consumer", "Wholesaler")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        binding.customerTypeDropdown.setAdapter(adapter)
    }

    private fun setupCountryDropdown() {
        val items = listOf("India", "United States", "United Kingdom", "Canada", "Australia") // Add more countries as needed
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        binding.countryDropdown.setAdapter(adapter)
        binding.countryDropdown.setText("India", false) // Set default to India
    }

    private fun setupDatePickers() {
        // Customer since date (default to current date)
        val calendar = Calendar.getInstance()
        binding.customerSinceDateField.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time))

        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
            binding.customerSinceDateField.setText(date)
        }

        binding.customerSinceDateField.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Setup for birthday picker
        binding.birthdayField.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
                    binding.birthdayField.setText(date)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Setup for anniversary picker
        binding.anniversaryField.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, day)
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)
                    binding.anniversaryField.setText(date)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupFormValidation() {
        // Fix for error spacing - ensure the error doesn't leave space when it's gone
        class ErrorClearingTextWatcher(private val textInputLayout: com.google.android.material.textfield.TextInputLayout) : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (textInputLayout.error != null) {
                    textInputLayout.isErrorEnabled = true
                } else {
                    textInputLayout.isErrorEnabled = false // This removes the space
                }
            }
        }

        // Phone number validation with improved error handling
        binding.phoneNumberField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty() && !isValidPhoneNumber(s.toString())) {
                    binding.phoneNumberLayout.isErrorEnabled = true
                    binding.phoneNumberLayout.error = "Invalid phone number format"
                } else {
                    binding.phoneNumberLayout.isErrorEnabled = false
                }
            }
        })

        // Email validation with improved error handling
        binding.emailField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty() && !isValidEmail(s.toString())) {
                    binding.emailLayout.isErrorEnabled = true
                    binding.emailLayout.error = "Invalid email format"
                } else {
                    binding.emailLayout.isErrorEnabled = false
                }
            }
        })

        // Add error clearing for other required fields
        binding.firstNameField.addTextChangedListener(ErrorClearingTextWatcher(binding.firstNameLayout))
        binding.lastNameField.addTextChangedListener(ErrorClearingTextWatcher(binding.lastNameLayout))
        binding.streetAddressField.addTextChangedListener(ErrorClearingTextWatcher(binding.streetAddressLayout))
        binding.cityField.addTextChangedListener(ErrorClearingTextWatcher(binding.cityLayout))
        binding.stateField.addTextChangedListener(ErrorClearingTextWatcher(binding.stateLayout))
        binding.customerTypeDropdown.addTextChangedListener(ErrorClearingTextWatcher(binding.customerTypeLayout))
    }

    private fun setupActionButtons() {
        binding.saveAndCloseButton.setOnClickListener {
            if (validateForm()) {
                saveCustomer(true)
                dismiss()
            }
        }

        binding.saveAndAddButton.setOnClickListener {
            if (validateForm()) {
                saveCustomer(false)
                clearForm()
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        // Validate required fields (marked with *)
        if (binding.firstNameField.text.isNullOrEmpty()) {
            binding.firstNameLayout.isErrorEnabled = true
            binding.firstNameLayout.error = "Required"
            isValid = false
        }

        if (binding.lastNameField.text.isNullOrEmpty()) {
            binding.lastNameLayout.isErrorEnabled = true
            binding.lastNameLayout.error = "Required"
            isValid = false
        }

        if (binding.phoneNumberField.text.isNullOrEmpty()) {
            binding.phoneNumberLayout.isErrorEnabled = true
            binding.phoneNumberLayout.error = "Required"
            isValid = false
        } else if (!isValidPhoneNumber(binding.phoneNumberField.text.toString())) {
            binding.phoneNumberLayout.isErrorEnabled = true
            binding.phoneNumberLayout.error = "Invalid phone number format"
            isValid = false
        }

        if (binding.customerTypeDropdown.text.isNullOrEmpty()) {
            binding.customerTypeLayout.isErrorEnabled = true
            binding.customerTypeLayout.error = "Required"
            isValid = false
        }

        // Address validation
        if (binding.streetAddressField.text.isNullOrEmpty()) {
            binding.streetAddressLayout.isErrorEnabled = true
            binding.streetAddressLayout.error = "Required"
            isValid = false
        }

        if (binding.cityField.text.isNullOrEmpty()) {
            binding.cityLayout.isErrorEnabled = true
            binding.cityLayout.error = "Required"
            isValid = false
        }

        if (binding.stateField.text.isNullOrEmpty()) {
            binding.stateLayout.isErrorEnabled = true
            binding.stateLayout.error = "Required"
            isValid = false
        }

        // Validate business fields if customer is a wholesaler
        if (binding.customerTypeDropdown.text.toString() == "Wholesaler") {
            if (binding.businessNameField.text.isNullOrEmpty()) {
                binding.businessNameLayout.isErrorEnabled = true
                binding.businessNameLayout.error = "Required for wholesalers"
                isValid = false
            }
        }

        return isValid
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        return phone.matches(Regex("^[6-9]\\d{9}$")) // Example for Indian phone numbers
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun saveCustomer(shouldClose: Boolean) {
        // Create customer object from form fields
        val customer = createCustomerFromForm()

        if (isEditMode) {
            // Return the updated customer through the listener
            listener?.onCustomerUpdated(customer)
        } else {
            // Return the new customer through the listener
            listener?.onCustomerAdded(customer)
        }

        if (shouldClose) {
            dismiss()
        } else {
            clearForm()
        }
    }


    private fun createCustomerFromForm(): Customer {
        val balanceType = if (binding.creditRadioButton.isChecked) "Credit" else "Debit"
        val openingBalance = binding.openingBalanceField.text.toString().toDoubleOrNull() ?: 0.0

        // Determine the current balance properly
        val currentBalance = if (isEditMode && existingCustomerId != null) {
            // For edited customers, we need to preserve their current balance to maintain
            // the accumulated invoice history
            arguments?.let {
                val customer = it.getSerializable(ARG_CUSTOMER) as? Customer
                if (customer != null) {
                    // If opening balance is changed in the form but we have an existing customer,
                    // we need to adjust the current balance by the same amount
                    val balanceDifference = openingBalance - customer.openingBalance
                    customer.currentBalance + balanceDifference
                } else {
                    openingBalance
                }
            } ?: openingBalance
        } else {
            // For new customers, set currentBalance equal to openingBalance
            openingBalance
        }

        return Customer(
            id = existingCustomerId ?: "",
            customerType = binding.customerTypeDropdown.text.toString(),
            firstName = binding.firstNameField.text.toString(),
            lastName = binding.lastNameField.text.toString(),
            phoneNumber = binding.phoneNumberField.text.toString(),
            email = binding.emailField.text.toString(),
            streetAddress = binding.streetAddressField.text.toString(),
            city = binding.cityField.text.toString(),
            state = binding.stateField.text.toString(),
            postalCode = binding.postalCodeField.text.toString(),
            country = binding.countryDropdown.text.toString(),
            balanceType = balanceType,
            openingBalance = openingBalance,
            currentBalance = currentBalance, // Use the properly calculated current balance
            balanceNotes = binding.balanceNotesField.text.toString(),
            businessName = binding.businessNameField.text.toString(),
            gstNumber = binding.gstNumberField.text.toString(),
            taxId = binding.taxIdField.text.toString(),
            customerSince = binding.customerSinceDateField.text.toString(),
            referredBy = binding.referredByField.text.toString(),
            birthday = binding.birthdayField.text.toString(),
            anniversary = binding.anniversaryField.text.toString(),
            notes = binding.notesField.text.toString(),
            lastUpdatedAt = System.currentTimeMillis()
        )
    }


    private fun populateFormWithCustomerData(customer: Customer) {
        // Basic Information
        binding.customerTypeDropdown.setText(customer.customerType, false)
        binding.firstNameField.setText(customer.firstName)
        binding.lastNameField.setText(customer.lastName)
        binding.phoneNumberField.setText(customer.phoneNumber)
        binding.emailField.setText(customer.email)

        // Address Information
        binding.streetAddressField.setText(customer.streetAddress)
        binding.cityField.setText(customer.city)
        binding.stateField.setText(customer.state)
        binding.postalCodeField.setText(customer.postalCode)
        binding.countryDropdown.setText(customer.country, false)

        // Financial Information
        if (customer.balanceType == "Credit") {
            binding.creditRadioButton.isChecked = true
        } else {
            binding.debitRadioButton.isChecked = true
        }
        binding.openingBalanceField.setText(customer.openingBalance.toString())
        binding.balanceNotesField.setText(customer.balanceNotes)

        // Business Information
        binding.businessNameField.setText(customer.businessName)
        binding.gstNumberField.setText(customer.gstNumber)
        binding.taxIdField.setText(customer.taxId)

        // Relationship Information
        binding.customerSinceDateField.setText(customer.customerSince)
        binding.referredByField.setText(customer.referredBy)
        binding.birthdayField.setText(customer.birthday)
        binding.anniversaryField.setText(customer.anniversary)
        binding.notesField.setText(customer.notes)
    }

    private fun clearForm() {
        binding.firstNameField.text = null
        binding.lastNameField.text = null
        binding.phoneNumberField.text = null
        binding.emailField.text = null

        binding.streetAddressField.text = null
        binding.cityField.text = null
        binding.stateField.text = null
        binding.postalCodeField.text = null
        binding.countryDropdown.setText("India", false)

        binding.creditRadioButton.isChecked = true
        binding.openingBalanceField.setText("0.00")
        binding.balanceNotesField.text = null
        binding.businessNameField.text = null
        binding.gstNumberField.text = null
        binding.taxIdField.text = null

        // Keep today's date for customer since
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        binding.customerSinceDateField.setText(today)
        binding.referredByField.text = null
        binding.birthdayField.text = null
        binding.anniversaryField.text = null
        binding.notesField.text = null

        // Reset error states
        binding.firstNameLayout.isErrorEnabled = false
        binding.lastNameLayout.isErrorEnabled = false
        binding.phoneNumberLayout.isErrorEnabled = false
        binding.emailLayout.isErrorEnabled = false
        binding.customerTypeLayout.isErrorEnabled = false
        binding.streetAddressLayout.isErrorEnabled = false
        binding.cityLayout.isErrorEnabled = false
        binding.stateLayout.isErrorEnabled = false
        binding.businessNameLayout.isErrorEnabled = false
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




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CustomerBottomSheetFragment"
        private const val ARG_CUSTOMER = "customer_object"

        fun newInstance(): CustomerBottomSheetFragment {
            return CustomerBottomSheetFragment()
        }

        fun newInstance(customer: Customer): CustomerBottomSheetFragment {
            val fragment = CustomerBottomSheetFragment()
            val args = Bundle()
            // We need to put the customer as a serializable
            args.putSerializable(ARG_CUSTOMER, customer)
            fragment.arguments = args
            return fragment
        }
    }
}