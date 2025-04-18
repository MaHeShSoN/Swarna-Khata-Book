package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.jewelrypos.swarnakhatabook.DataClasses.Shop
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentShopSettingsBinding
import com.google.firebase.Timestamp
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShopSettingsFragment : Fragment() {

    private var _binding: FragmentShopSettingsBinding? = null
    private val binding get() = _binding!!

    private var shop: Shop? = null

    private var hasUnsavedChanges = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShopSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ShopManager
        ShopManager.initialize(requireContext())

        // Setup toolbar
        setupToolbar()

        // Load shop details
        loadShopDetails()
        // --- NEW: Add the back press callback ---
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )
    }

    private val backPressedCallback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            if (hasUnsavedChanges) {
                showUnsavedChangesDialog()
            } else {
                // Disable this callback and pop the fragment
                isEnabled = false
                findNavController().navigateUp() // Use NavController
            }
        }
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = binding.topAppBar
        toolbar.setNavigationOnClickListener {
            // Trigger the custom back press handling
            backPressedCallback.handleOnBackPressed()
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save -> {
                    saveShopDetails()
                    true
                }

                else -> false
            }
        }
    }

    private fun loadShopDetails() {
        binding.progressBar.visibility = View.VISIBLE

        // Get shop details from ShopManager
        ShopManager.getShop(requireContext(), forceRefresh = true) { loadedShop ->
            if (loadedShop != null) {
                shop = loadedShop
                updateUI(loadedShop)
            } else {
                // Create a new shop if none exists
                shop = Shop(
                    name = "",
                    phoneNumber = "",
                    shopName = "",
                    address = "",
                    gstNumber = "",
                    hasGst = false,
                    createdAt = Timestamp.now(),
                    email = ""
                )
            }
            binding.progressBar.visibility = View.GONE
            setupChangeListeners()
        }
    }

    private fun updateUI(shop: Shop) {
        removeChangeListeners()
        // Populate fields with shop data
        binding.ownerNameEditText.setText(shop.name)
        binding.shopNameEditText.setText(shop.shopName)
        binding.addressEditText.setText(shop.address)
        binding.phoneEditText.setText(shop.phoneNumber)
        binding.emailEditText.setText(shop.email)
        binding.gstNumberEditText.setText(shop.gstNumber)

        setupChangeListeners()

        hasUnsavedChanges = false
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            // Check if the current text differs from the originally loaded shop data
            if (isDataChanged()) {
                hasUnsavedChanges = true
            }
        }
    }


    private fun isDataChanged(): Boolean {
        val originalShop = shop ?: return false // If no original data, assume changed

        val currentName = binding.shopNameEditText.text.toString()
        val currentAddress = binding.addressEditText.text.toString()
        val currentPhone = binding.phoneEditText.text.toString()
        val currentEmail = binding.emailEditText.text.toString()
        val currentGst = binding.gstNumberEditText.text.toString()

        return currentName != originalShop.shopName ||
                currentAddress != originalShop.address ||
                currentPhone != originalShop.phoneNumber ||
                currentEmail != originalShop.email ||
                currentGst != originalShop.gstNumber
    }


    private fun setupChangeListeners() {
        binding.shopNameEditText.addTextChangedListener(textWatcher)
        binding.ownerNameEditText.addTextChangedListener(textWatcher)
        binding.addressEditText.addTextChangedListener(textWatcher)
        binding.phoneEditText.addTextChangedListener(textWatcher)
        binding.emailEditText.addTextChangedListener(textWatcher)
        binding.gstNumberEditText.addTextChangedListener(textWatcher)
        // Add listeners for logo/signature URIs if you implement those changes
    }

    private fun removeChangeListeners() {
        binding.shopNameEditText.removeTextChangedListener(textWatcher)
        binding.ownerNameEditText.removeTextChangedListener(textWatcher)
        binding.addressEditText.removeTextChangedListener(textWatcher)
        binding.phoneEditText.removeTextChangedListener(textWatcher)
        binding.emailEditText.removeTextChangedListener(textWatcher)
        binding.gstNumberEditText.removeTextChangedListener(textWatcher)
        // Remove listeners for logo/signature URIs if you implement those changes
    }

    private fun showUnsavedChangesDialog() {
        ThemedM3Dialog(requireContext())
            .setTitle("Unsaved Changes")
            .setLayout(R.layout.dialog_confirmation) // Use a layout with a TextView
            .apply {
                // Set the message in the custom layout
                findViewById<TextView>(R.id.confirmationMessage)?.text =
                    "You have unsaved changes. Do you want to save them before leaving?"
            }
            .setPositiveButton("Save") { dialog, _ ->
                saveShopDetails()
                // Assuming save is successful for navigation logic
                // A better approach might be to navigate only after confirming save success
                hasUnsavedChanges = false // Allow back navigation after save attempt
                findNavController().navigateUp()
                dialog.dismiss() // Dismiss the dialog interface
            }
            .setNegativeButton("Discard") { dialog ->
                hasUnsavedChanges = false // Allow back navigation without saving
                findNavController().navigateUp()
                dialog.dismiss() // Dismiss the dialog interface
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                // Just dismiss the dialog
                dialog.dismiss() // Dismiss the dialog interface
            }
            .show() // Show the themed dialog
    }

    private fun saveShopDetails() {
        val ownerName = binding.ownerNameEditText.text.toString().trim()
        val shopName = binding.shopNameEditText.text.toString().trim()
        val address = binding.addressEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val gstNumber = binding.gstNumberEditText.text.toString().trim()
        val hasGst = gstNumber.isNotEmpty()

        // --- Enhanced Validation ---
        var isValid = true

        // Shop name validation
        if (shopName.isEmpty()) {
            binding.shopNameInputLayout.error = "Shop name is required"
            isValid = false
        } else binding.shopNameInputLayout.error = null

        // Shop name validation
        if (ownerName.isEmpty()) {
            binding.shopNameInputLayout.error = "Owner name is required"
            isValid = false
        } else binding.shopNameInputLayout.error = null

        // Address validation
        if (address.isEmpty()) {
            binding.addressInputLayout.error = "Address is required"
            isValid = false
        } else binding.addressInputLayout.error = null

        // Phone validation
        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = "Phone number is required"
            isValid = false
        } else binding.phoneInputLayout.error = null

        // Email validation
        if (email.isNotEmpty() && !isValidEmail(email)) {
            binding.emailInputLayout.error = "Please enter a valid email address"
            isValid = false
        } else binding.emailInputLayout.error = null

        // GST number validation
        if (gstNumber.isNotEmpty() && !isValidGSTNumber(gstNumber)) {
            binding.gstNumberInputLayout.error = "Please enter a valid 15-digit GST number"
            isValid = false
        } else binding.gstNumberInputLayout.error = null

        if (!isValid) return
        // --- End Enhanced Validation ---

        shop?.let { currentShop ->
            val updatedShop = currentShop.copy(
                name = ownerName,
                shopName = shopName,
                address = address,
                phoneNumber = phone,
                email = email,
                gstNumber = gstNumber, 
                hasGst = hasGst,
                // Add logo/signature URIs if implemented
                // logo = logoUri?.toString() ?: currentShop.logo,
                // signature = signatureUri?.toString() ?: currentShop.signature
            )

            // --- Access binding here is OK because view still exists ---
            binding.progressBar.visibility = View.VISIBLE

            ShopManager.saveShop(updatedShop, requireContext()) { success, error ->
                // --- Callback runs LATER, potentially after view is destroyed ---

                // --- >>> ADD THIS CHECK <<< ---
                if (_binding == null || !isAdded) {
                    // Fragment view is destroyed or fragment is detached, cannot update UI
                    Log.w("ShopSettings", "View destroyed before save callback executed. Cannot update UI.")
                    return@saveShop // Exit the callback
                }
                // --- >>> END CHECK <<< ---

                // --- Now it's safe to access binding ---
                binding.progressBar.visibility = View.GONE // This line was causing the crash

                if (success) {
                    Toast.makeText(context, "Shop details saved successfully", Toast.LENGTH_SHORT).show()
                    shop = updatedShop // Update the local copy
                    hasUnsavedChanges = false // Reset flag after successful save
                    // Optionally navigate back after save
                    // findNavController().navigateUp()
                } else {
                    Toast.makeText(context, "Failed to save shop details: ${error?.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ShopSettings", "Error saving shop details", error)
                }
            }
        }
    }

    /**
     * Validates if the provided string is a valid email address
     */
    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return email.matches(emailPattern.toRegex())
    }

    /**
     * Validates if the provided string is a valid GST number
     * GST number format: 2 digit state code + 10 digit PAN + 1 digit entity number + 1 digit check digit + Z
     */
    private fun isValidGSTNumber(gstNumber: String): Boolean {
        // GST number should be 15 characters long
        if (gstNumber.length != 15) return false
        
        // First 2 digits should be a valid state code (01-37)
        val stateCode = gstNumber.substring(0, 2).toIntOrNull() ?: return false
        if (stateCode < 1 || stateCode > 37) return false
        
        // Next 10 characters should be alphanumeric (PAN)
        val panPart = gstNumber.substring(2, 12)
        if (!panPart.matches("[A-Z0-9]{10}".toRegex())) return false
        
        // 13th digit should be a number (entity number)
        if (!gstNumber[12].isDigit()) return false
        
        // 14th digit should be a letter or number (check digit)
        if (!gstNumber[13].isLetterOrDigit()) return false
        
        // Last character should be 'Z'
        return gstNumber[14] == 'Z'
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeChangeListeners()
        _binding = null
    }
}