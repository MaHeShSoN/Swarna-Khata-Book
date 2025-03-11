package com.jewelrypos.swarnakhatabook.BottomSheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.MetalItem
import com.jewelrypos.swarnakhatabook.Enums.MetalItemType
import com.jewelrypos.swarnakhatabook.Factorys.InventoryViewModelFactory
import com.jewelrypos.swarnakhatabook.Factorys.MetalItemViewModelFactory
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.MetalItemViewModel
import com.jewelrypos.swarnakhatabook.databinding.DialogInputMetalItemBinding
import com.jewelrypos.swarnakhatabook.databinding.FragmentItemBottomSheetBinding


class ItemBottomSheetFragment : BottomSheetDialogFragment() {

    private var listener: OnItemAddedListener? = null

    // UI Components
    private var _binding: FragmentItemBottomSheetBinding? = null
    private val binding get() = _binding!!




    private val viewModel: MetalItemViewModel by viewModels {
        MetalItemViewModelFactory(
            requireActivity().application
        )
    }
    private lateinit var itemTypeChipGroup: ChipGroup

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentItemBottomSheetBinding.inflate(inflater, container, false)

        itemTypeChipGroup = binding.itemTypeChipGroup


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.items.observe(viewLifecycleOwner) { retrievedItems ->
            populateDropdown(retrievedItems)
        }

        binding.itemTypeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            // No need to call retrieveItems again, just filter the current items
            populateDropdown(viewModel.items.value ?: emptyList())
        }

        setUpDropDownMenus()
        setupListeners()
        // Set the dialog style
//        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogStyle)

    }

    private fun setUpDropDownMenus() {

        //Purity list
        val listOfPurity = listOf<String>("24k", "22k", "20k", "18k", "14k", "10k")

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            listOfPurity
        )

        binding.purityEditText.setAdapter(adapter)


        //MackingChargeType list
        val listOfMakingChargeType = listOf<String>("PER GRAM", "FIX")

        val adapter2 = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            listOfMakingChargeType
        )

        binding.mackingChargesTypeEditText.setAdapter(adapter2)


        //Purity list
        val listOfUnits = listOf<String>("PIECE", "SET", "PAIR")

        val adapter3 = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            listOfUnits
        )

        binding.stockChargesTypeEditText.setAdapter(adapter3)


    }

    private fun populateDropdown(items: List<MetalItem>) {
        val selectedChipId = binding.itemTypeChipGroup.checkedChipId
        val selectedType = when (selectedChipId) {
            binding.goldChip.id -> MetalItemType.GOLD
            binding.silverChip.id -> MetalItemType.SILVER
            binding.otherChip.id -> MetalItemType.OTHER
            else -> null // No chip selected or default
        }

        val filteredItems = if (selectedType != null) {
            items.filter { it.type == selectedType }
        } else {
            items // Show all if no chip selected
        }

        val itemNames = filteredItems.map { it.fieldName }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            itemNames
        )

        binding.categoryDropdown.setAdapter(adapter)
    }

    private fun showThemedDialog() {
        // Create and customize the themed dialog
        ThemedM3Dialog(requireContext())
            .setTitle("Add Category")
            .setLayout(R.layout.dialog_input_metal_item) // Your horizontal EditText layout
            .setPositiveButton("Add") { dialog, dialogView ->
                // Access the EditText fields
                val dialogBinding = DialogInputMetalItemBinding.bind(dialogView!!)


                dialogBinding.editText2

                // Access the AutoCompleteTextView value
                val itemName = dialogBinding.editText1.text.toString()
                val itemType =
                    dialogBinding.editText2.text.toString() // Assuming editText2 is another field

                if (itemType.isNotEmpty() && itemName.isNotEmpty()) {

                    var selectedMetal = MetalItemType.GOLD
                    if (itemType == MetalItemType.GOLD.toString()) {
                        selectedMetal = MetalItemType.GOLD
                    }
                    if (itemType == MetalItemType.OTHER.toString()) {
                        selectedMetal = MetalItemType.OTHER
                    }
                    if (itemType == MetalItemType.SILVER.toString()) {
                        selectedMetal = MetalItemType.SILVER
                    }


                    processInputValues(itemName, selectedMetal)
                    dialog.dismiss()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Please fill in both fields",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog ->
                dialog.dismiss()
            }
            .apply {
                // Set up the AutoCompleteTextView before showing the dialog
                val dialogView = layoutInflater.inflate(R.layout.dialog_input_metal_item, null)
                val dialogBinding = DialogInputMetalItemBinding.bind(dialogView)


                // Populate AutoCompleteTextView with enum values
                val metalTypes = MetalItemType.values().map { it.name }
                val adapter = ArrayAdapter(
                    requireContext(), // Context
                    android.R.layout.simple_dropdown_item_1line, // Default dropdown layout
                    metalTypes
                )
                dialogBinding.editText2.setAdapter(adapter)

                // Set the prepared view to the dialog
                setView(dialogView)
            }
            .show()
    }

    private fun processInputValues(value1: String, value2: MetalItemType) {
        val metalItem = MetalItem(value1, value2)
        viewModel.addItem(metalItem)
        binding.categoryDropdown.setText(value1)
    }

    fun validateJewelryItemForm(): Pair<Boolean, String> {
        // Get references to all form fields
        val displayName = binding.displayNameEditText.text.toString().trim()
        val jewelryCode = binding.jewelryCodeEditText.text.toString().trim()
        val grossWeight = binding.grossWeightEditText.text.toString().trim()
        val netWeight = binding.netWeightEditText.text.toString().trim()
        val wastage = binding.wastageEditText.text.toString().trim()
        val purity = binding.purityEditText.text.toString().trim()
        val makingCharges = binding.mackingChargesEditText.text.toString().trim()
        val makingChargesType = binding.mackingChargesTypeEditText.text.toString().trim()
        val stock = binding.stockEditText.text.toString().trim()
        val stockType = binding.stockChargesTypeEditText.text.toString().trim()
        val location = binding.locationEditText.text.toString().trim()
        val category = binding.categoryDropdown.text.toString().trim()

        // Check if required fields are empty
        if (displayName.isEmpty()) {
            binding.displayNameInputLayout.error = "Display name is required"
            binding.displayNameEditText.requestFocus()
            return Pair(false, "Display name is required")
        } else {
            binding.displayNameInputLayout.error = null
        }

        if (jewelryCode.isEmpty()) {
            binding.jewelryCodeInputLayout.error = "Jewelry code is required"
            binding.jewelryCodeEditText.requestFocus()
            return Pair(false, "Jewelry code is required")
        } else {
            binding.jewelryCodeInputLayout.error = null
        }

        if (category.isEmpty()) {
            binding.categoryInputLayout.error = "Category is required"
            binding.categoryDropdown.requestFocus()
            return Pair(false, "Category is required")
        } else {
            binding.categoryInputLayout.error = null
        }

        // Validate numeric fields
        if (grossWeight.isEmpty()) {
            binding.grossWeightInputLayout.error = "Gross weight is required"
            binding.grossWeightEditText.requestFocus()
            return Pair(false, "Gross weight is required")
        } else {
            try {
                val grossWeightValue = grossWeight.toDouble()
                if (grossWeightValue <= 0) {
                    binding.grossWeightInputLayout.error = "Gross weight must be greater than zero"
                    binding.grossWeightEditText.requestFocus()
                    return Pair(false, "Gross weight must be greater than zero")
                } else {
                    binding.grossWeightInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.grossWeightInputLayout.error = "Invalid gross weight"
                binding.grossWeightEditText.requestFocus()
                return Pair(false, "Invalid gross weight")
            }
        }

        if (netWeight.isNotEmpty()) {
            try {
                val netWeightValue = netWeight.toDouble()
                val grossWeightValue = grossWeight.toDouble()

                if (netWeightValue <= 0) {
                    binding.netWeightInputLayout.error = "Net weight must be greater than zero"
                    return Pair(false, "Net weight must be greater than zero")
                } else if (netWeightValue > grossWeightValue) {
                    binding.netWeightInputLayout.error = "Net weight cannot exceed gross weight"
                    binding.netWeightEditText.requestFocus()
                    return Pair(false, "Net weight cannot exceed gross weight")
                } else {
                    binding.netWeightInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.netWeightInputLayout.error = "Invalid net weight"
                binding.netWeightEditText.requestFocus()
                return Pair(false, "Invalid net weight")
            }
        }

        if (wastage.isNotEmpty()) {
            try {
                val wastageValue = wastage.toDouble()
                if (wastageValue < 0) {
                    binding.wastageInputLayout.error = "Wastage cannot be negative"
                    binding.wastageEditText.requestFocus()
                    return Pair(false, "Wastage cannot be negative")
                } else {
                    binding.wastageInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.wastageInputLayout.error = "Invalid wastage value"
                binding.wastageEditText.requestFocus()
                return Pair(false, "Invalid wastage value")
            }
        }

        if (purity.isEmpty()) {
            binding.purityInputLayout.error = "Purity is required"
            binding.purityEditText.requestFocus()
            return Pair(false, "Purity is required")
        } else {
            binding.purityInputLayout.error = null
        }

        if (makingCharges.isNotEmpty()) {
            try {
                val makingChargesValue = makingCharges.toDouble()
                if (makingChargesValue < 0) {
                    binding.mackingChargesInputLayout.error = "Making charges cannot be negative"
                    binding.mackingChargesEditText.requestFocus()
                    return Pair(false, "Making charges cannot be negative")
                } else {
                    binding.mackingChargesInputLayout.error = null
                }

                // If making charges are provided, type must also be provided
                if (makingChargesType.isEmpty()) {
                    binding.mackingChargesTypeInputLayout.error = "Making charges type is required"
                    binding.mackingChargesTypeEditText.requestFocus()
                    return Pair(false, "Making charges type is required")
                } else {
                    binding.mackingChargesTypeInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.mackingChargesInputLayout.error = "Invalid making charges"
                binding.mackingChargesEditText.requestFocus()
                return Pair(false, "Invalid making charges")
            }
        }

        if (stock.isNotEmpty()) {
            try {
                val stockValue = stock.toDouble()
                if (stockValue < 0) {
                    binding.stockInputLayout.error = "Stock value cannot be negative"
                    binding.stockEditText.requestFocus()
                    return Pair(false, "Stock value cannot be negative")
                } else {
                    binding.stockInputLayout.error = null
                }

                // If stock value is provided, type must also be provided
                if (stockType.isEmpty()) {
                    binding.stockTypeInputLayout.error = "Stock unit is required"
                    binding.stockChargesTypeEditText.requestFocus()
                    return Pair(false, "Stock unit is required")
                } else {
                    binding.stockTypeInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.stockInputLayout.error = "Invalid stock value"
                binding.stockEditText.requestFocus()
                return Pair(false, "Invalid stock value")
            }
        }

        // All validations passed
        return Pair(true, "")
    }

    /**
     * Extension function to easily call validation from the activity or fragment
     * @return Boolean - Returns true if all validations pass
     */
    fun validateAndShowErrors(): Boolean {
        val (isValid, errorMessage) = validateJewelryItemForm()

        if (!isValid && errorMessage.isNotEmpty()) {
            // Optionally show a toast or snackbar with the error message
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    // Implementation of saveJewelryItem function to actually save the data
    private fun saveJewelryItem(closeAfterSave: Boolean) {
        // Get the selected item type
        val itemType = when {
            binding.goldChip.isChecked -> "GOLD"
            binding.silverChip.isChecked -> "SILVER"
            binding.otherChip.isChecked -> "OTHER"
            else -> "GOLD" // Default
        }

        // Create a jewelry item object with all the form data
        val jewellryItem = JewelleryItem(
            displayName = binding.displayNameEditText.text.toString().trim(),
            jewelryCode = binding.jewelryCodeEditText.text.toString().trim(),
            itemType = itemType,
            category = binding.categoryDropdown.text.toString().trim(),
            grossWeight = binding.grossWeightEditText.text.toString().toDoubleOrNull() ?: 0.0,
            netWeight = binding.netWeightEditText.text.toString().toDoubleOrNull() ?: 0.0,
            wastage = binding.wastageEditText.text.toString().toDoubleOrNull() ?: 0.0,
            purity = binding.purityEditText.text.toString(),
            makingCharges = binding.mackingChargesEditText.text.toString().toDoubleOrNull() ?: 0.0,
            makingChargesType = binding.mackingChargesTypeEditText.text.toString(),
            stock = binding.stockEditText.text.toString().toDoubleOrNull() ?: 0.0,
            stockUnit = binding.stockChargesTypeEditText.text.toString(),
            location = binding.locationEditText.text.toString()
        )


        listener?.onItemAdded(jewellryItem)

        if (closeAfterSave) {
            dismiss()
        } else {
            // Clear the form for adding another item
            clearForm()
        }

        // Show success message
//        Toast.makeText(context, "Jewelry item saved successfully", Toast.LENGTH_SHORT).show()
    }

    /**
     * Clears all form fields for adding a new item
     */
    private fun clearForm() {
        binding.displayNameEditText.text?.clear()
        binding.jewelryCodeEditText.text?.clear()
        binding.grossWeightEditText.text?.clear()
        binding.netWeightEditText.text?.clear()
        binding.wastageEditText.text?.clear()
        binding.purityEditText.setText("")
        binding.mackingChargesEditText.text?.clear()
        binding.mackingChargesTypeEditText.setText("")
        binding.stockEditText.text?.clear()
        binding.stockChargesTypeEditText.setText("")
        binding.locationEditText.text?.clear()

        // Reset any error states
        binding.displayNameInputLayout.error = null
        binding.jewelryCodeInputLayout.error = null
        binding.grossWeightInputLayout.error = null
        binding.netWeightInputLayout.error = null
        binding.wastageInputLayout.error = null
        binding.purityInputLayout.error = null
        binding.mackingChargesInputLayout.error = null
        binding.mackingChargesTypeInputLayout.error = null
        binding.stockInputLayout.error = null
        binding.stockTypeInputLayout.error = null

        // Reset focus to first field
        binding.displayNameEditText.requestFocus()
    }


    private fun setupListeners() {

        binding.goldImageButton1.setOnClickListener {
            showThemedDialog()

        }


        // Save Button Click Listener
        binding.saveAddButton.setOnClickListener {
            if (validateAndShowErrors()) {
                // Proceed with saving the jewelry item
                saveJewelryItem(closeAfterSave = false)
            }
        }

        // Cancel Button Click Listener
        binding.saveCloseButton.setOnClickListener {
            if (validateAndShowErrors()) {
                // Proceed with saving the jewelry item and close the form
                saveJewelryItem(closeAfterSave = true)
            }
        }
    }


    fun setOnItemAddedListener(listener: OnItemAddedListener) {
        this.listener = listener
    }

    interface OnItemAddedListener {
        fun onItemAdded(item: JewelleryItem)
    }

    companion object {
        fun newInstance(): ItemBottomSheetFragment {
            return ItemBottomSheetFragment()
        }
    }

}


