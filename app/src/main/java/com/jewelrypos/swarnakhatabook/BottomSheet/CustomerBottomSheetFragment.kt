package com.jewelrypos.swarnakhatabook.BottomSheet

import android.Manifest
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.util.Log // Added Log import
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView // Added import
import android.widget.FilterQueryProvider // Added import
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SimpleCursorAdapter // Added import
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog // Added import for rationale dialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
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

    private var isEditMode = false
    private var existingCustomerId: String? = null
    private var listener: CustomerOperationListener? = null
    private var calledFromInvoiceCreation = false
    private var calledFromCustomerDetails = false

    // Adapter for contact suggestions
    private var contactsAdapter: SimpleCursorAdapter? = null

    // Add new property for address adapter
    private var addressAdapter: ArrayAdapter<String>? = null

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
        setupFinancialInfoButton()
        setupRelationshipInfoButton()

        // Check permission and setup contacts autocomplete
        checkContactsPermission()

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
                // Show financial info if it has data
                if (customer.openingBalance != 0.0 || customer.balanceNotes?.isNotEmpty() == true) {
                    showFinancialInfo()
                }
                // Show relationship info if it has data
                if (customer.customerSince?.isNotEmpty() == true ||
                    customer.referredBy?.isNotEmpty() == true ||
                    customer.birthday?.isNotEmpty() == true ||
                    customer.anniversary?.isNotEmpty() == true ||
                    customer.notes?.isNotEmpty() == true
                ) {
                    showRelationshipInfo()
                }
            }
        }

        if (calledFromInvoiceCreation) {
            // Hide the "Save and Add" button when called from invoice creation
            binding.saveAndAddButton.visibility = View.GONE

            // Optionally, adjust the layout to make the "Save and Close" button full width
            val layoutParams = binding.saveAndCloseButton.layoutParams as LinearLayout.LayoutParams
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            binding.saveAndCloseButton.layoutParams = layoutParams
        }
        if (calledFromCustomerDetails) {
            // Hide the "Save and Add" button when called from invoice creation
            binding.saveAndAddButton.visibility = View.GONE
            binding.saveAndCloseButton.setText("Update")

            // Optionally, adjust the layout to make the "Save and Close" button full width
            val layoutParams = binding.saveAndCloseButton.layoutParams as LinearLayout.LayoutParams
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            binding.saveAndCloseButton.layoutParams = layoutParams
        }

        // Setup address suggestions
        setupAddressSuggestions()
    }

    // --- Contact Permission and Autocomplete Logic ---

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupContactsAutocomplete()
        } else {
            Toast.makeText(
                context,
                "Contacts permission denied. Cannot suggest contacts.",
                Toast.LENGTH_SHORT
            ).show()
            // Optionally disable the autocomplete feature or specific fields
        }
    }

    private fun checkContactsPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                setupContactsAutocomplete()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                // Show rationale dialog explaining why we need contacts permission
                AlertDialog.Builder(requireContext())
                    .setTitle("Permission Needed")
                    .setMessage("This app needs permission to read your contacts to suggest customer names and numbers as you type.")
                    .setPositiveButton("OK") { _, _ ->
                        requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            else -> {
                // Directly request the permission
                requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    /**
     * Sets up the AutoCompleteTextView for the first name field
     * to suggest contacts with names and phone numbers using a custom layout.
     */
    private fun setupContactsAutocomplete() {
        // Ensure the view exists and is an AutoCompleteTextView
        val autoCompleteView = binding.etFirstName as? AutoCompleteTextView
        if (autoCompleteView == null) {
            Log.e(
                TAG,
                "setupContactsAutocomplete: etFirstName is not an AutoCompleteTextView or is null."
            )
            return
        }

        // --- Define columns and view IDs for mapping ---
        // Columns to retrieve from the cursor (including the number now)
        val fromColumns = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, // Column for contact name
            ContactsContract.CommonDataKinds.Phone.NUMBER        // Column for phone number
        )
        // View IDs in your custom layout (item_contact_suggestion.xml)
        val toViews = intArrayOf(
            R.id.contactNameTextView,  // Map DISPLAY_NAME to this TextView
            R.id.contactNumberTextView // Map NUMBER to this TextView
        )

        // Create the SimpleCursorAdapter using the custom layout
        contactsAdapter = SimpleCursorAdapter(
            requireContext(),
            R.layout.item_contact_suggestion, // Use the custom layout file
            null, // Cursor will be provided by FilterQueryProvider
            fromColumns,
            toViews,
            0 // No flags needed
        )

        // Set the adapter to the AutoCompleteTextView
        autoCompleteView.apply {
            setDropDownBackgroundResource(R.color.cream_background)
            setAdapter(contactsAdapter)
        }
        autoCompleteView.threshold = 1 // Start suggesting after 1 character

        // --- Setup FilterQueryProvider (Modified Query) ---
        contactsAdapter?.filterQueryProvider = FilterQueryProvider { constraint ->
            if (constraint.isNullOrEmpty()) {
                return@FilterQueryProvider null
            }

            val contentResolver: ContentResolver = requireContext().contentResolver
            // Query the Phone content URI to get numbers directly
            val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

            // Projection: Columns needed for display and selection logic
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone._ID,           // Needed for unique ID per row
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,    // To link back to the contact if needed
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,  // Name for display
                ContactsContract.CommonDataKinds.Phone.NUMBER         // Number for display and selection
                // Add ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI here if you want to show photos
            )

            // Selection: Filter by display name OR phone number containing the constraint
            // Using '?' placeholders for safe argument binding
            val selection =
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                        "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            // Selection arguments: The constraint with wildcards for both name and number search
            val selectionArgs = arrayOf("%$constraint%", "%$constraint%")

            // Sort order: Alphabetical by display name
            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

            // Perform the query
            try {
                return@FilterQueryProvider contentResolver.query(
                    uri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during contact phone query.", e)
                Toast.makeText(context, "Permission error accessing contacts.", Toast.LENGTH_SHORT)
                    .show()
                return@FilterQueryProvider null
            }
        }

        // --- Setup CursorToStringConverter ---
        // This defines how a selected item is converted to a String for the AutoCompleteTextView itself
        contactsAdapter?.cursorToStringConverter =
            SimpleCursorAdapter.CursorToStringConverter { cursor ->
                // Display only the name in the AutoCompleteTextView after selection
                val nameIndex =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                if (nameIndex != -1) {
                    cursor.getString(nameIndex)
                } else {
                    ""
                }
            }


        // --- Handle contact selection ---
        autoCompleteView.setOnItemClickListener { parent, _, position, _ ->
            val cursor = contactsAdapter?.getItem(position) as? Cursor
            cursor?.let {
                // Get data directly from the cursor using column names from the Phone table
                val nameIndex =
                    it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val contactIdIndex =
                    it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID) // Get Contact ID index

                if (nameIndex != -1 && numberIndex != -1 && contactIdIndex != -1) { // Check contactIdIndex too
                    val contactName = it.getString(nameIndex)
                    val rawPhoneNumber = it.getString(numberIndex)
                    val contactId = it.getString(contactIdIndex) // Retrieve Contact ID

                    // Split the name
                    val nameParts = contactName.split(" ", limit = 2)
                    val firstName = nameParts.getOrNull(0) ?: contactName
                    val lastName = nameParts.getOrNull(1) ?: ""

                    // Format the phone number
                    val formattedNumber = formatPhoneNumber(rawPhoneNumber)

                    // Set the text fields
                    binding.etFirstName.setText(
                        firstName,
                        false
                    ) // Set name without triggering filter
                    binding.etLastName.setText(lastName)
                    binding.phoneNumberField.setText(formattedNumber) // Set formatted number

                    // Optionally load photo using contactId
                    // loadContactPhoto(contactId)

                    // Hide keyboard
                    val imm =
                        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(autoCompleteView.windowToken, 0)

                } else {
                    Log.e(
                        TAG,
                        "Could not find DISPLAY_NAME, NUMBER, or CONTACT_ID columns in cursor."
                    )
                }
            }
        }

        // --- Handle focus for scrolling ---
        binding.etFirstName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollView?.post {
                    binding.scrollView.smoothScrollTo(0, binding.firstNameLayout.top)
                } ?: Log.w(TAG, "ScrollView not found in binding.")
            }
        }
    }


    /**
     * Loads the primary phone number for a given contact ID and formats it.
     * NOTE: This function is now primarily used only AFTER a contact is selected
     * to ensure the number in the main field is formatted, as the list now gets
     * the number directly from the initial query.
     * Kept for potential future use or if direct ID lookup is needed elsewhere.
     */
    private fun loadContactPhoneNumber(contactId: String) {
        // This function might still be useful if you need to re-verify the number
        // or fetch other details based *only* on the contact ID later.
        // However, for simply populating the fields after selection, the data
        // is already available in the cursor within setOnItemClickListener.
        Log.d(TAG, "loadContactPhoneNumber called for ID: $contactId (may be redundant now)")

        // Example: If you needed to fetch the number again (less efficient):
        val contentResolver: ContentResolver = requireContext().contentResolver
        var rawPhoneNumber: String? = null
        val phoneUri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId)
        val phoneCursor: Cursor? = try {
            contentResolver.query(phoneUri, projection, selection, selectionArgs, null)
        } catch (e: SecurityException) {
            null
        }

        phoneCursor?.use {
            if (it.moveToFirst()) {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) rawPhoneNumber = it.getString(numberIndex)
            }
        }
        if (rawPhoneNumber != null) {
            val formattedNumber = formatPhoneNumber(rawPhoneNumber)
            // Avoid overwriting if the number is already correct from the click listener
            if (binding.phoneNumberField.text.toString() != formattedNumber) {
                binding.phoneNumberField.setText(formattedNumber)
            }
        }
    }

    /**
     * Helper function to format phone numbers.
     * Removes common prefixes (+91, 91, 0) to aim for a 10-digit number.
     * Also removes non-digit characters except '+'.
     */
    private fun formatPhoneNumber(phone: String?): String {
        if (phone.isNullOrBlank()) {
            return ""
        }
        // 1. Remove spaces, hyphens, parentheses etc. Keep digits and '+'
        var cleanedNumber = phone.filter { it.isDigit() || it == '+' }

        // 2. Handle prefixes (prioritize +91, then 91, then 0)
        if (cleanedNumber.startsWith("+91") && cleanedNumber.length > 3) {
            val potentialNumber = cleanedNumber.substring(3)
            // Check if remaining part looks like a valid number start (e.g., 10 digits)
            if (potentialNumber.length >= 10 && potentialNumber.all { it.isDigit() }) {
                cleanedNumber = potentialNumber
            }
        } else if (cleanedNumber.startsWith("91") && cleanedNumber.length > 2) {
            val potentialNumber = cleanedNumber.substring(2)
            if (potentialNumber.length >= 10 && potentialNumber.all { it.isDigit() }) {
                cleanedNumber = potentialNumber
            }
        } else if (cleanedNumber.startsWith("0") && cleanedNumber.length > 1) {
            val potentialNumber = cleanedNumber.substring(1)
            if (potentialNumber.length >= 10 && potentialNumber.all { it.isDigit() }) {
                cleanedNumber = potentialNumber
            }
        }

        // Return the cleaned number, ensuring only digits remain if no '+' was present initially
        // or if prefixes were stripped.
        return cleanedNumber.filter { it.isDigit() }
    }


    // --- Other Setup Functions (Unchanged) ---

    private fun setupBalanceTypeRadioButtons() {
        binding.creditRadioButton.isChecked = true // Default to credit
    }

    private fun setupCustomerTypeDropdown() {
        val items = listOf("Consumer", "Wholesaler")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)

        binding.customerTypeDropdown.apply {
            setAdapter(adapter)
            try {
                setDropDownBackgroundResource(R.color.cream_background) // Ensure this color exists
                // Pre-select "Consumer" if not in edit mode
                if (!isEditMode) {
                    setText("Consumer", false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting dropdown background resource", e)
            }
        }
    }

    private fun setupCountryDropdown() {
        val items = listOf("India", "United States", "United Kingdom", "Canada", "Australia")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        binding.countryDropdown.setAdapter(adapter)
        if (binding.countryDropdown.text.isNullOrEmpty()) {
            binding.countryDropdown.setText("India", false)
        }
    }

    private fun setupDatePickers() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // --- Customer Since Date ---
        if (binding.customerSinceDateField.text.isNullOrEmpty()) {
            binding.customerSinceDateField.setText(dateFormat.format(calendar.time))
        }
        val customerSinceListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            binding.customerSinceDateField.setText(dateFormat.format(calendar.time))
        }
        binding.customerSinceDateField.setOnClickListener {
            val currentDateStr = binding.customerSinceDateField.text.toString()
            try {
                dateFormat.parse(currentDateStr)?.let { calendar.time = it }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing customerSinceDate: $currentDateStr", e)
            }
            DatePickerDialog(
                requireContext(),
                customerSinceListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // --- Birthday Picker ---
        binding.birthdayField.setOnClickListener {
            val birthdayListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                binding.birthdayField.setText(dateFormat.format(calendar.time))
            }
            val currentBirthdayStr = binding.birthdayField.text.toString()
            try {
                dateFormat.parse(currentBirthdayStr)?.let { calendar.time = it }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing birthday: $currentBirthdayStr", e)
            }
            DatePickerDialog(
                requireContext(),
                birthdayListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // --- Anniversary Picker ---
        binding.anniversaryField.setOnClickListener {
            val anniversaryListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                binding.anniversaryField.setText(dateFormat.format(calendar.time))
            }
            val currentAnniversaryStr = binding.anniversaryField.text.toString()
            try {
                dateFormat.parse(currentAnniversaryStr)?.let { calendar.time = it }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing anniversary: $currentAnniversaryStr", e)
            }
            DatePickerDialog(
                requireContext(),
                anniversaryListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }


    private fun setupFormValidation() {
        class ErrorClearingTextWatcher(private val textInputLayout: com.google.android.material.textfield.TextInputLayout) :
            TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isNotEmpty() && textInputLayout.error != null) {
                    textInputLayout.error = null
                    textInputLayout.isErrorEnabled = false
                }
            }
        }

        binding.phoneNumberField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val phone = s.toString()
                if (phone.isNotEmpty()) {
                    if (!isValidPhoneNumber(phone)) {
                        binding.phoneNumberLayout.error = "Invalid phone number format (10 digits)"
                        binding.phoneNumberLayout.isErrorEnabled = true
                    } else {
                        binding.phoneNumberLayout.error = null
                        binding.phoneNumberLayout.isErrorEnabled = false
                    }
                } else {
                    binding.phoneNumberLayout.error = null
                    binding.phoneNumberLayout.isErrorEnabled = false
                }
            }
        })


        binding.etFirstName.addTextChangedListener(ErrorClearingTextWatcher(binding.firstNameLayout))
        binding.etLastName.addTextChangedListener(ErrorClearingTextWatcher(binding.lastNameLayout))
        binding.streetAddressField.addTextChangedListener(ErrorClearingTextWatcher(binding.streetAddressLayout))
        binding.cityField.addTextChangedListener(ErrorClearingTextWatcher(binding.cityLayout))
        binding.stateField.addTextChangedListener(ErrorClearingTextWatcher(binding.stateLayout))
        binding.customerTypeDropdown.addTextChangedListener(ErrorClearingTextWatcher(binding.customerTypeLayout))
        binding.businessNameField.addTextChangedListener(ErrorClearingTextWatcher(binding.businessNameLayout))
    }

    private fun setupActionButtons() {
        binding.saveAndCloseButton.setOnClickListener {
            if (validateForm()) {
                saveCustomer(true)
            } else {
                Toast.makeText(context, "Please fix the errors above", Toast.LENGTH_SHORT).show()
                findAndFocusFirstError()
            }
        }

        binding.saveAndAddButton.setOnClickListener {
            if (validateForm()) {
                saveCustomer(false)
            } else {
                Toast.makeText(context, "Please fix the errors above", Toast.LENGTH_SHORT).show()
                findAndFocusFirstError()
            }
        }
    }

    private fun findAndFocusFirstError() {
        val layoutsAndFields = listOfNotNull(
            binding.firstNameLayout to binding.etFirstName,
            binding.lastNameLayout to binding.etLastName,
            binding.phoneNumberLayout to binding.phoneNumberField,
            binding.customerTypeLayout to binding.customerTypeDropdown,
            binding.streetAddressLayout to binding.streetAddressField,
            binding.cityLayout to binding.cityField,
            binding.stateLayout to binding.stateField,
            if (binding.businessInfoCard.isVisible) binding.businessNameLayout to binding.businessNameField else null
            // Add other validated fields here...
        )

        for ((layout, field) in layoutsAndFields) {
            // Check layout directly, field might be null if added conditionally
            if (layout != null && layout.error != null && layout.isErrorEnabled) {
                binding.scrollView?.post {
                    binding.scrollView.smoothScrollTo(0, layout.top)
                }
                field?.requestFocus() // Request focus on the associated field
                val imm =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
                break
            }
        }
    }


    private fun validateForm(): Boolean {
        var isValid = true
        fun validateRequiredField(
            field: android.widget.EditText?,
            layout: com.google.android.material.textfield.TextInputLayout?,
            fieldName: String
        ): Boolean {
            if (field == null || layout == null) return true
            return if (field.text.isNullOrBlank()) {
                layout.error = "$fieldName is required"; layout.isErrorEnabled = true; false
            } else {
                layout.error = null; layout.isErrorEnabled = false; true
            }
        }

        fun validateRequiredDropdown(
            field: AutoCompleteTextView?,
            layout: com.google.android.material.textfield.TextInputLayout?,
            fieldName: String
        ): Boolean {
            if (field == null || layout == null) return true
            return if (field.text.isNullOrBlank()) {
                layout.error = "$fieldName is required"; layout.isErrorEnabled = true; false
            } else {
                layout.error = null; layout.isErrorEnabled = false; true
            }
        }

        if (!validateRequiredField(
                binding.etFirstName,
                binding.firstNameLayout,
                "First Name"
            )
        ) isValid = false
        if (!validateRequiredField(
                binding.etLastName,
                binding.lastNameLayout,
                "Last Name"
            )
        ) isValid = false
        if (!validateRequiredField(
                binding.phoneNumberField,
                binding.phoneNumberLayout,
                "Phone Number"
            )
        ) {
            isValid = false
        } else if (!isValidPhoneNumber(binding.phoneNumberField.text.toString())) {
            binding.phoneNumberLayout.error = "Invalid phone number format (10 digits)"
            binding.phoneNumberLayout.isErrorEnabled = true
            isValid = false
        }
        if (!validateRequiredDropdown(
                binding.customerTypeDropdown,
                binding.customerTypeLayout,
                "Customer Type"
            )
        ) isValid = false
        if (!validateRequiredField(
                binding.streetAddressField,
                binding.streetAddressLayout,
                "Street Address"
            )
        ) isValid = false
        if (!validateRequiredField(binding.cityField, binding.cityLayout, "City")) isValid = false
        if (!validateRequiredField(binding.stateField, binding.stateLayout, "State")) isValid =
            false


        if (binding.customerTypeDropdown.text.toString() == "Wholesaler") {
            if (!validateRequiredField(
                    binding.businessNameField,
                    binding.businessNameLayout,
                    "Business Name"
                )
            ) isValid = false
        }

        return isValid
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        return phone.matches(Regex("^[6-9]\\d{9}$"))
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun saveCustomer(shouldClose: Boolean) {
        val customer = createCustomerFromForm()
        try {
            if (isEditMode) {
                listener?.onCustomerUpdated(customer)
                Toast.makeText(context, "Customer updated successfully", Toast.LENGTH_SHORT).show()
            } else {
                listener?.onCustomerAdded(customer)
                Toast.makeText(context, "Customer added successfully", Toast.LENGTH_SHORT).show()
            }
            if (shouldClose) {
                dismissAllowingStateLoss()
            } else {
                clearForm()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving customer", e)
            Toast.makeText(context, "Error saving customer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun createCustomerFromForm(): Customer {
        val balanceType = if (binding.creditRadioButton.isChecked) "Credit" else "Debit"
        val openingBalance = binding.openingBalanceField.text.toString().toDoubleOrNull() ?: 0.0
        var calculatedCurrentBalance = openingBalance

        if (isEditMode) {
            arguments?.getSerializable(ARG_CUSTOMER)?.let { arg ->
                if (arg is Customer) {
                    val originalCustomer = arg
                    val openingBalanceDifference = openingBalance - originalCustomer.openingBalance
                    calculatedCurrentBalance =
                        originalCustomer.currentBalance + openingBalanceDifference
                } else {
                    Log.e(TAG, "Argument ARG_CUSTOMER is not of type Customer.")
                }
            } ?: Log.w(TAG, "Edit mode active but original customer data not found.")
        }

        if (balanceType == "Debit" && calculatedCurrentBalance > 0) calculatedCurrentBalance *= -1
        else if (balanceType == "Credit" && calculatedCurrentBalance < 0) calculatedCurrentBalance *= -1

        // Get current address
        val currentAddress = binding.streetAddressField.text.toString().trim()
        val city = binding.cityField.text.toString().trim()
        val state = binding.stateField.text.toString().trim()

        // Create full address string
        val fullAddress = listOfNotNull(
            currentAddress,
            city,
            state
        ).joinToString(", ")

        // Save the address to Firestore if it's not empty
        if (fullAddress.isNotEmpty()) {
            saveAddressToFirestore(fullAddress)
        }

        // Get previous addresses
        val previousAddresses = if (isEditMode) {
            arguments?.getSerializable(ARG_CUSTOMER)?.let { arg ->
                if (arg is Customer) {
                    // Add current address to previous addresses if it's not already there
                    val addresses = arg.previousAddresses.toMutableList()
                    if (fullAddress.isNotEmpty() && !addresses.contains(fullAddress)) {
                        addresses.add(fullAddress)
                    }
                    addresses
                } else {
                    listOf(fullAddress)
                }
            } ?: listOf(fullAddress)
        } else {
            listOf(fullAddress)
        }

        return Customer(
            id = existingCustomerId ?: "",
            customerType = binding.customerTypeDropdown.text.toString(),
            firstName = binding.etFirstName.text.toString().trim(),
            lastName = binding.etLastName.text.toString().trim(),
            phoneNumber = binding.phoneNumberField.text.toString().trim(),
            streetAddress = currentAddress,
            city = city,
            state = state,
            country = binding.countryDropdown.text.toString(),
            previousAddresses = previousAddresses,
            balanceType = balanceType,
            openingBalance = openingBalance,
            currentBalance = calculatedCurrentBalance,
            balanceNotes = binding.balanceNotesField.text.toString().trim(),
            businessName = binding.businessNameField.text.toString().trim(),
            gstNumber = binding.gstNumberField.text.toString().trim(),
            taxId = binding.taxIdField.text.toString().trim(),
            customerSince = binding.customerSinceDateField.text.toString(),
            referredBy = binding.referredByField.text.toString().trim(),
            birthday = binding.birthdayField.text.toString(),
            anniversary = binding.anniversaryField.text.toString(),
            notes = binding.notesField.text.toString().trim(),
            lastUpdatedAt = System.currentTimeMillis()
        )
    }


    private fun populateFormWithCustomerData(customer: Customer) {
        binding.customerTypeDropdown.setText(customer.customerType, false)
        binding.etFirstName.setText(customer.firstName)
        binding.etLastName.setText(customer.lastName)
        binding.phoneNumberField.setText(customer.phoneNumber)
        binding.streetAddressField.setText(customer.streetAddress)
        binding.cityField.setText(customer.city)
        binding.stateField.setText(customer.state)
        binding.countryDropdown.setText(customer.country, false)
        if (customer.balanceType == "Credit") binding.creditRadioButton.isChecked = true
        else binding.debitRadioButton.isChecked = true
        binding.openingBalanceField.setText(
            String.format(
                Locale.US,
                "%.2f",
                customer.openingBalance
            )
        )
        binding.balanceNotesField.setText(customer.balanceNotes ?: "")
        val isBusinessCustomer = customer.customerType == "Wholesaler"
        binding.businessInfoCard.isVisible = isBusinessCustomer
        binding.businessInfoCardText.isVisible = isBusinessCustomer
        binding.businessNameField.setText(customer.businessName ?: "")
        binding.gstNumberField.setText(customer.gstNumber ?: "")
        binding.taxIdField.setText(customer.taxId ?: "")
        binding.customerSinceDateField.setText(customer.customerSince ?: "")
        binding.referredByField.setText(customer.referredBy ?: "")
        binding.birthdayField.setText(customer.birthday ?: "")
        binding.anniversaryField.setText(customer.anniversary ?: "")
        binding.notesField.setText(customer.notes ?: "")
        binding.saveAndCloseButton.text = "Update"
        binding.saveAndAddButton.visibility = View.GONE
        val layoutParams = binding.saveAndCloseButton.layoutParams as LinearLayout.LayoutParams
        layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT
        layoutParams.weight = 0f
        binding.saveAndCloseButton.layoutParams = layoutParams
    }

    private fun clearForm() {
        binding.etFirstName.text = null
        binding.etLastName.text = null
        binding.phoneNumberField.text = null
        binding.customerTypeDropdown.text = null
        binding.streetAddressField.text = null
        binding.cityField.text = null
        binding.stateField.text = null
        binding.countryDropdown.setText("India", false)
        binding.creditRadioButton.isChecked = true
        binding.openingBalanceField.setText("0.00")
        binding.balanceNotesField.text = null
        binding.businessNameField.text = null
        binding.gstNumberField.text = null
        binding.taxIdField.text = null
        binding.businessInfoCard.isVisible = false
        binding.businessInfoCardText.isVisible = false
        val today =
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().time)
        binding.customerSinceDateField.setText(today)
        binding.referredByField.text = null
        binding.birthdayField.text = null
        binding.anniversaryField.text = null
        binding.notesField.text = null

        val layoutsToReset = listOf(
            binding.firstNameLayout, binding.lastNameLayout, binding.phoneNumberLayout,
            binding.customerTypeLayout, binding.streetAddressLayout,
            binding.cityLayout, binding.stateLayout,
            binding.countryLayout, binding.openingBalanceLayout, binding.businessNameLayout,
            binding.gstNumberLayout, binding.taxIdLayout, binding.balanceNotesLayout,
            binding.customerSinceDateLayout, binding.referredByLayout, binding.birthdayLayout,
            binding.anniversaryLayout, binding.notesLayout
        )
        layoutsToReset.forEach { layout -> layout?.error = null; layout?.isErrorEnabled = false }

        binding.etFirstName.requestFocus()
        isEditMode = false
        existingCustomerId = null
        binding.saveAndCloseButton.text = "Save & Close"

        if (!calledFromInvoiceCreation && !calledFromCustomerDetails) {
            binding.saveAndAddButton.visibility = View.VISIBLE
            val closeParams = binding.saveAndCloseButton.layoutParams as? LinearLayout.LayoutParams
            val addParams = binding.saveAndAddButton.layoutParams as? LinearLayout.LayoutParams
            if (closeParams != null && addParams != null) {
                closeParams.width = 0; addParams.width = 0
                closeParams.weight = 1f; addParams.weight = 1f
                binding.saveAndCloseButton.layoutParams = closeParams
                binding.saveAndAddButton.layoutParams = addParams
            }
        } else {
            binding.saveAndAddButton.visibility = View.GONE
            val closeParams = binding.saveAndCloseButton.layoutParams as? LinearLayout.LayoutParams
            if (closeParams != null) {
                closeParams.width = LinearLayout.LayoutParams.MATCH_PARENT
                closeParams.weight = 0f
                binding.saveAndCloseButton.layoutParams = closeParams
            }
            if (calledFromCustomerDetails) binding.saveAndCloseButton.text = "Update"
        }
    }

    fun setCalledFromInvoiceCreation(fromInvoice: Boolean) {
        calledFromInvoiceCreation = fromInvoice
    }

    fun setCalledFromCustomerDetails(fromDetails: Boolean) {
        calledFromCustomerDetails = fromDetails
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet =
                bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
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
        contactsAdapter?.changeCursor(null)
        contactsAdapter = null
        _binding = null
    }

    private fun setupAddressSuggestions() {
        // Convert street address field to AutoCompleteTextView
        val streetAddressField = binding.streetAddressField as? AutoCompleteTextView
        if (streetAddressField == null) {
            Log.e(TAG, "streetAddressField is not an AutoCompleteTextView")
            return
        }

        // Create adapter for address suggestions
        addressAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )

//        autoCompleteView.apply {
//            setDropDownBackgroundResource(R.color.cream_background)
//            setAdapter(contactsAdapter)
//        }

        streetAddressField.apply {
            setDropDownBackgroundResource(R.color.cream_background)
            setAdapter(addressAdapter)
            threshold = 1
        }

        // Load addresses from Firestore
        loadAddressesFromFirestore()

        // Add text change listener to update suggestions
        streetAddressField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Clear error if any
                binding.streetAddressLayout.error = null
                binding.streetAddressLayout.isErrorEnabled = false

                // Filter addresses based on input
                val input = s.toString().trim()
                if (input.isNotEmpty()) {
                    filterAddresses(input)
                }
            }
        })

        // Handle address selection
        streetAddressField.setOnItemClickListener { _, _, position, _ ->
            val selectedAddress = addressAdapter?.getItem(position) as? String
            selectedAddress?.let {
                // Split the address into components
                val addressParts = it.split(", ")
                if (addressParts.size >= 3) {
                    binding.streetAddressField.setText(addressParts[0], false)
                    binding.cityField.setText(addressParts[1])
                    binding.stateField.setText(addressParts[2])
                }
            }
        }
    }

    private fun loadAddressesFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("addresses")
            .get()
            .addOnSuccessListener { documents ->
                val addresses = documents.mapNotNull { it.getString("fullAddress") }
                addressAdapter?.clear()
                addressAdapter?.addAll(addresses)
                addressAdapter?.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading addresses", e)
            }
    }

    private fun filterAddresses(query: String) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users")
            .document(userId)
            .collection("addresses")
            .whereGreaterThanOrEqualTo("fullAddress", query)
            .whereLessThanOrEqualTo("fullAddress", query + '\uf8ff')
            .get()
            .addOnSuccessListener { documents ->
                val addresses = documents.mapNotNull { it.getString("fullAddress") }
                addressAdapter?.clear()
                addressAdapter?.addAll(addresses)
                addressAdapter?.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error filtering addresses", e)
            }
    }

    private fun saveAddressToFirestore(address: String) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val addressData = hashMapOf(
            "fullAddress" to address,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(userId)
            .collection("addresses")
            .add(addressData)
            .addOnSuccessListener {
                Log.d(TAG, "Address saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving address", e)
            }
    }

    private fun setupFinancialInfoButton() {
        binding.financialInfoButton.setOnClickListener {
            if (binding.financialInfoContainer.visibility == View.VISIBLE) {
                hideFinancialInfo()
            } else {
                showFinancialInfo()
            }
        }
    }

    private fun setupRelationshipInfoButton() {
        binding.relationshipInfoButton.setOnClickListener {
            if (binding.relationshipInfoContainer.visibility == View.VISIBLE) {
                hideRelationshipInfo()
            } else {
                showRelationshipInfo()
            }
        }
    }

    private fun showFinancialInfo() {
        binding.financialInfoContainer.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        binding.financialInfoButton.text = getString(R.string.hide_financial_info)
    }

    private fun hideFinancialInfo() {
        binding.financialInfoContainer.animate()
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                binding.financialInfoContainer.visibility = View.GONE
            }
            .start()
        binding.financialInfoButton.text = getString(R.string.add_financial_info)
    }

    private fun showRelationshipInfo() {
        binding.relationshipInfoContainer.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        binding.relationshipInfoButton.text = getString(R.string.hide_relationship_info)
    }

    private fun hideRelationshipInfo() {
        binding.relationshipInfoContainer.animate()
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                binding.relationshipInfoContainer.visibility = View.GONE
            }
            .start()
        binding.relationshipInfoButton.text = getString(R.string.add_relationship_info)
    }

    companion object {
        val TAG = CustomerBottomSheetFragment::class.java.simpleName
        private const val ARG_CUSTOMER = "customer_object"

        fun newInstance(): CustomerBottomSheetFragment = CustomerBottomSheetFragment()

        fun newInstance(customer: Customer): CustomerBottomSheetFragment {
            return CustomerBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CUSTOMER, customer)
                }
            }
        }
    }
}
