package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.MetalItem
import com.jewelrypos.swarnakhatabook.Enums.MetalItemType
import com.jewelrypos.swarnakhatabook.Factorys.MetalItemViewModelFactory
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.MetalItemViewModel
import com.jewelrypos.swarnakhatabook.databinding.DialogInputMetalItemBinding
import com.jewelrypos.swarnakhatabook.databinding.FragmentItemBottomSheetBinding


open class ItemBottomSheetFragment : BottomSheetDialogFragment() {

    var listener: OnItemAddedListener? = null
    var editMode = false
    var itemToEdit: JewelleryItem? = null

    // UI Components
    private var _binding: FragmentItemBottomSheetBinding? = null
    val binding get() = _binding!!


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

        // Update title if in edit mode
        if (editMode) {
            binding.titleTextView.text = getString(R.string.edit_jewellery_item)
            binding.saveAddButton.visibility = View.GONE
            binding.saveCloseButton.text = getString(R.string.update)

            // Optionally, adjust the layout to make the "Save and Close" button full width
            val layoutParams = binding.saveCloseButton.layoutParams as LinearLayout.LayoutParams
            layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            binding.saveCloseButton.layoutParams = layoutParams
        }


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

        // If we're in edit mode, populate the form with the item data
        if (editMode && itemToEdit != null) {
            populateFormWithItemData(itemToEdit!!)
        } else {
            // If it's not edit mode, set up initial values
            setupInitialValues()
        }


        setUpDropDownMenus()
        setupListeners()
        // Set the dialog style
//        setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetDialogStyle)

    }

    private fun populateFormWithItemData(item: JewelleryItem) {
        // Set item type chip
        when (item.itemType.lowercase()) {
            "gold" -> binding.goldChip.isChecked = true
            "silver" -> binding.silverChip.isChecked = true
            "other" -> binding.otherChip.isChecked = true
        }

        // Fill in the fields
        binding.displayNameEditText.setText(item.displayName)
        binding.jewelryCodeEditText.setText(item.jewelryCode)
        binding.categoryDropdown.setText(item.category)
        binding.grossWeightEditText.setText(item.grossWeight.toString())
        binding.netWeightEditText.setText(item.netWeight.toString())
        binding.wastageEditText.setText(item.wastage.toString())
        binding.wastageTypeDropdown.setText(item.wastageType)
        binding.purityEditText.setText(item.purity)
        binding.stockEditText.setText(item.stock.toString())
        binding.stockChargesTypeEditText.setText(item.stockUnit)

        // Disable the jewelry code field to prevent editing the document ID reference
        binding.jewelryCodeEditText.isEnabled = false
    }


    private fun setUpDropDownMenus() {
        //Wastage Type
        val listOfWastageType = listOf<String>("Percentage", "Gram")
        val adapter0 = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            listOfWastageType
        )
        binding.wastageTypeDropdown.apply {
            setAdapter(adapter0)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        }

        //Purity list
        val listOfUnits = listOf<String>("PIECE", "SET", "PAIR")

        val adapter3 = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            listOfUnits
        )
        binding.stockChargesTypeEditText.apply {
            setAdapter(adapter3)
            setDropDownBackgroundResource(R.color.cream_background)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        }
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
            R.layout.dropdown_item,
            if (itemNames.isEmpty()) {
                // If no items are available, add a suggestion message
                listOf("No items available - Click + to add")
            } else {
                itemNames
            }
        )
        binding.categoryDropdown.apply {
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.my_light_primary_container)

            // If there are no items, set a hint text as helper
            if (itemNames.isEmpty()) {
                binding.categoryInputLayout.helperText = "Use the + button to add a category"
                binding.categoryInputLayout.setHelperTextColor(ContextCompat.getColorStateList(requireContext(), R.color.black))
                binding.goldImageButton1.alpha = 1.0f  // Make sure button is fully visible
                // Optional: add a gentle pulse animation to draw attention to the button
                binding.goldImageButton1.startAnimation(
                    android.view.animation.AnimationUtils.loadAnimation(
                        context,
                        android.R.anim.fade_in
                    )
                )
            } else {
                binding.categoryInputLayout.helperText = null
            }

        }

        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            if (itemNames.isEmpty() && position == 0) {
                // If user clicks on the suggestion, automatically trigger the add button
                binding.goldImageButton1.performClick()
                binding.categoryDropdown.setText("")  // Clear the suggestion text
            }
        }

        // Set text colors for input fields
        binding.displayNameEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        binding.jewelryCodeEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        binding.grossWeightEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        binding.netWeightEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        binding.wastageEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        binding.purityEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        binding.stockEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
        binding.categoryDropdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))

        // Set text colors for labels
        binding.displayNameInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.jewelryCodeInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.grossWeightInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.netWeightInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.wastageInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.wastageTypeInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.purityInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.stockInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.stockTypeInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
        binding.categoryInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.black)!!)
    }

    fun showThemedDialog() {
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
                        getString(R.string.please_fill_in_both_fields),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog ->
                dialog.dismiss()
            }
            .apply {
                // Set up the AutoCompleteTextView before showing the dialog
                val dialogView = layoutInflater.inflate(R.layout.dialog_input_metal_item, null)
                val dialogBinding = DialogInputMetalItemBinding.bind(dialogView)


                // Populate AutoCompleteTextView with enum values
                val metalTypes = MetalItemType.entries.map { it.name }
                val adapter = ArrayAdapter(
                    requireContext(), // Context
                    R.layout.dropdown_item, // Default dropdown layout
                    metalTypes
                )
                dialogBinding.editText2.apply {
                    setAdapter(adapter)
                    setDropDownBackgroundResource(R.color.my_light_primary_container)
                }

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
        val wastageType = binding.wastageTypeDropdown.text.toString().trim()
        val purity = binding.purityEditText.text.toString().trim()
        val stock = binding.stockEditText.text.toString().trim()
        val stockType = binding.stockChargesTypeEditText.text.toString().trim()
        val category = binding.categoryDropdown.text.toString().trim()

        // Basic validation for required fields
        if (displayName.isEmpty()) {
            binding.displayNameInputLayout.error = getString(R.string.display_name_is_required)
            binding.displayNameEditText.requestFocus()
            return Pair(false, getString(R.string.display_name_is_required))
        } else {
            binding.displayNameInputLayout.error = null
        }

        if (jewelryCode.isEmpty()) {
            binding.jewelryCodeInputLayout.error = getString(R.string.jewelry_code_is_required)
            binding.jewelryCodeEditText.requestFocus()
            return Pair(false, getString(R.string.jewelry_code_is_required))
        } else {
            binding.jewelryCodeInputLayout.error = null
        }

        if (category.isEmpty()) {
            binding.categoryInputLayout.error = getString(R.string.category_is_required)
            binding.categoryDropdown.requestFocus()
            return Pair(false, getString(R.string.category_is_required))
        } else {
            binding.categoryInputLayout.error = null
        }

        // Numeric field validations
        // Gross Weight - required
        if (grossWeight.isEmpty()) {
            binding.grossWeightInputLayout.error = getString(R.string.gross_weight_is_required)
            binding.grossWeightEditText.requestFocus()
            return Pair(false, getString(R.string.gross_weight_is_required))
        } else {
            try {
                val grossWeightValue = grossWeight.toDouble()
                if (grossWeightValue <= 0) {
                    binding.grossWeightInputLayout.error =
                        getString(R.string.gross_weight_must_be_greater_than_zero)
                    binding.grossWeightEditText.requestFocus()
                    return Pair(false, getString(R.string.gross_weight_must_be_greater_than_zero))
                } else {
                    binding.grossWeightInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.grossWeightInputLayout.error = getString(R.string.invalid_gross_weight)
                binding.grossWeightEditText.requestFocus()
                return Pair(false, getString(R.string.invalid_gross_weight))
            }
        }

        // Net Weight - should be less than gross weight if provided
        if (netWeight.isNotEmpty()) {
            try {
                val netWeightValue = netWeight.toDouble()
                val grossWeightValue = grossWeight.toDouble()

                if (netWeightValue <= 0) {
                    binding.netWeightInputLayout.error =
                        getString(R.string.net_weight_must_be_greater_than_zero)
                    return Pair(false, getString(R.string.net_weight_must_be_greater_than_zero))
                } else if (netWeightValue > grossWeightValue) {
                    binding.netWeightInputLayout.error =
                        getString(R.string.net_weight_cannot_exceed_gross_weight)
                    binding.netWeightEditText.requestFocus()
                    return Pair(false, getString(R.string.net_weight_cannot_exceed_gross_weight))
                } else {
                    binding.netWeightInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.netWeightInputLayout.error = getString(R.string.invalid_net_weight)
                binding.netWeightEditText.requestFocus()
                return Pair(false, getString(R.string.invalid_net_weight))
            }
        }

        // Purity validation
        if (purity.isEmpty()) {
            binding.purityInputLayout.error = getString(R.string.purity_is_required)
            binding.purityEditText.requestFocus()
            return Pair(false, getString(R.string.purity_is_required))
        } else {
            binding.purityInputLayout.error = null
        }

        // Validate wastage if provided
        if (wastage.isNotEmpty()) {
            try {
                val wastageValue = wastage.toDouble()
                if (wastageValue < 0) {
                    binding.wastageInputLayout.error =
                        getString(R.string.wastage_cannot_be_negative)
                    binding.wastageEditText.requestFocus()
                    return Pair(false, getString(R.string.wastage_cannot_be_negative))
                } else {
                    binding.wastageInputLayout.error = null
                }
                
                // Validate wastage type is selected
                if (wastageType.isEmpty()) {
                    binding.wastageTypeInputLayout.error =
                        getString(R.string.wastage_type_is_required)
                    binding.wastageTypeDropdown.requestFocus()
                    return Pair(false, getString(R.string.wastage_type_is_required))
                } else {
                    binding.wastageTypeInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.wastageInputLayout.error = getString(R.string.invalid_wastage_value)
                binding.wastageEditText.requestFocus()
                return Pair(false, getString(R.string.invalid_wastage_value))
            }
        }


        // Stock validation
        if (stock.isNotEmpty()) {
            try {
                val stockValue = stock.toDouble()
                if (stockValue < 0) {
                    binding.stockInputLayout.error =
                        getString(R.string.stock_value_cannot_be_negative)
                    binding.stockEditText.requestFocus()
                    return Pair(false, getString(R.string.stock_value_cannot_be_negative))
                } else {
                    binding.stockInputLayout.error = null
                }

                // If stock value is provided, type must also be provided
                if (stockType.isEmpty()) {
                    binding.stockTypeInputLayout.error = getString(R.string.stock_unit_is_required)
                    binding.stockChargesTypeEditText.requestFocus()
                    return Pair(false, getString(R.string.stock_unit_is_required))
                } else {
                    binding.stockTypeInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.stockInputLayout.error = getString(R.string.invalid_stock_value)
                binding.stockEditText.requestFocus()
                return Pair(false, getString(R.string.invalid_stock_value))
            }
        }

        // All validations passed
        return Pair(true, "")
    }
    /**
     * Extension function to easily call validation from the activity or fragment
     * @return Boolean - Returns true if all validations pass
     */


    /**
     * Sets up initial values for all fields
     */
    private fun setupInitialValues() {
        // Set default values
        binding.wastageEditText.setText("0.0")
        binding.wastageTypeDropdown.setText(getString(R.string.percentage), false)
        binding.purityEditText.setText("0.0")
        binding.netWeightEditText.setText("0.0")
        binding.grossWeightEditText.setText("0.0")
        binding.stockEditText.setText("0.0")
        binding.stockChargesTypeEditText.setText(getString(R.string.piece),false) // Default stock unit value
    }


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
            id = if (editMode && itemToEdit != null) itemToEdit!!.id else "",
            displayName = binding.displayNameEditText.text.toString().trim(),
            jewelryCode = binding.jewelryCodeEditText.text.toString().trim(),
            itemType = itemType,
            category = binding.categoryDropdown.text.toString().trim(),
            grossWeight = binding.grossWeightEditText.text.toString().toDoubleOrNull() ?: 0.0,
            netWeight = binding.netWeightEditText.text.toString().toDoubleOrNull() ?: 0.0,
            wastage = binding.wastageEditText.text.toString().toDoubleOrNull() ?: 0.0,
            wastageType = binding.wastageTypeDropdown.text.toString(),
            purity = binding.purityEditText.text.toString(),
            stock = binding.stockEditText.text.toString().toDoubleOrNull() ?: 0.0,
            stockUnit = binding.stockChargesTypeEditText.text.toString(),
            location = "", // Empty location as it's removed
        )


        // Notify listener based on mode
        if (editMode) {
            listener?.onItemUpdated(jewellryItem)
        } else {
            listener?.onItemAdded(jewellryItem)
        }

        if (closeAfterSave) {
            dismiss()
        } else {
            // Only clear the form if we're not in edit mode
            if (!editMode) {
                clearForm()
            }
        }

        // Show success message
//        Toast.makeText(context, "Jewelry item saved successfully", Toast.LENGTH_SHORT).show()
    }

    // Method to set the item for editing
    fun setItemForEdit(item: JewelleryItem) {
        editMode = true
        itemToEdit = item
    }

    /**
     * Clears all form fields for adding a new item
     */
    fun clearForm() {
        binding.displayNameEditText.text?.clear()
        binding.jewelryCodeEditText.text?.clear()
        binding.grossWeightEditText.text?.clear()
        binding.netWeightEditText.text?.clear()
        binding.wastageEditText.text?.clear()
        binding.wastageTypeDropdown.setText(getString(R.string.percentage), false)
        binding.purityEditText.setText("")
        binding.stockEditText.text?.clear()
        binding.stockChargesTypeEditText.setText(getString(R.string.piece),false) // Default value after clearing

        // Reset any error states
        binding.displayNameInputLayout.error = null
        binding.jewelryCodeInputLayout.error = null
        binding.grossWeightInputLayout.error = null
        binding.netWeightInputLayout.error = null
        binding.wastageInputLayout.error = null
        binding.wastageTypeInputLayout.error = null
        binding.purityInputLayout.error = null
        binding.stockInputLayout.error = null
        binding.stockTypeInputLayout.error = null

        // Reset focus to first field
        binding.displayNameEditText.requestFocus()
    }


    open fun setupListeners() {

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
        
        // Handle wastage type dropdown selection
        binding.wastageTypeDropdown.setOnItemClickListener { _, _, _, _ ->
            // Update calculated fields or validations if needed
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

    fun setOnItemAddedListener(listener: OnItemAddedListener) {
        this.listener = listener
    }

    interface OnItemAddedListener {
        fun onItemAdded(item: JewelleryItem)
        fun onItemUpdated(item: JewelleryItem)
    }

    companion object {
        fun newInstance(): ItemBottomSheetFragment {
            return ItemBottomSheetFragment()
        }
    }

}


