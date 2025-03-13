package com.jewelrypos.swarnakhatabook.BottomSheet

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.textfield.TextInputEditText
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.R

class ModifiedItemBottomSheetFragment : ItemBottomSheetFragment() {

    // Fields for our custom views
    private var diamondPriceField: TextInputEditText? = null
    private var makingChargesRadioGroup: RadioGroup? = null
    private var grossWeightRadioButton: RadioButton? = null
    private var netWeightRadioButton: RadioButton? = null
    private var quantityField: TextInputEditText? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Update the title
        binding.titleTextView.text = "Add Item to Sale"

        // Hide stock and location fields
        binding.stockLinerLayout.visibility = View.GONE
        binding.locationInputLayout.visibility = View.GONE

        // Add our custom fields
        addDiamondPriceField()
        addMakingChargesToggle()
        addQuantityField()
    }

    private fun addDiamondPriceField() {
        // Dynamically create the field
        val diamondPriceLayout = layoutInflater.inflate(
            R.layout.field_diamond_price,
            binding.weightLinerLayout.parent as ViewGroup,
            false
        )

        // Insert it after the weightLinerLayout
        val parentView = binding.weightLinerLayout.parent as ViewGroup
        val index = parentView.indexOfChild(binding.weightLinerLayout) + 1
        parentView.addView(diamondPriceLayout, index)

        // Store reference to the field
        diamondPriceField = diamondPriceLayout.findViewById(R.id.diamondPriceEditText)
    }

    private fun addMakingChargesToggle() {
        // Dynamically create the toggle
        val makingChargesToggleLayout = layoutInflater.inflate(
            R.layout.field_making_charges_toggle,
            binding.mackingChargeLinerLayout.parent as ViewGroup,
            false
        )

        // Insert it after the mackingChargeLinerLayout
        val parentView = binding.mackingChargeLinerLayout.parent as ViewGroup
        val index = parentView.indexOfChild(binding.mackingChargeLinerLayout) + 1
        parentView.addView(makingChargesToggleLayout, index)

        // Store references to the toggle components
        makingChargesRadioGroup = makingChargesToggleLayout.findViewById(R.id.makingChargesRadioGroup)
        grossWeightRadioButton = makingChargesToggleLayout.findViewById(R.id.grossWeightRadioButton)
        netWeightRadioButton = makingChargesToggleLayout.findViewById(R.id.netWeightRadioButton)

        // Set default to gross weight
        grossWeightRadioButton?.isChecked = true
    }

    private fun addQuantityField() {
        // Dynamically create the quantity field
        val quantityLayout = layoutInflater.inflate(
            R.layout.field_quantity,
            binding.stockLinerLayout.parent as ViewGroup,
            false
        )

        // Insert it where stockLinerLayout used to be
        val parentView = binding.stockLinerLayout.parent as ViewGroup
        val index = parentView.indexOfChild(binding.stockLinerLayout)
        parentView.addView(quantityLayout, index)

        // Store reference to the field
        quantityField = quantityLayout.findViewById(R.id.quantityEditText)

        // Set default quantity to 1
        quantityField?.setText("1")
    }

    override fun setupListeners() {
        // Override the original method and implement our own version

        binding.goldImageButton1.setOnClickListener {
            showThemedDialog()
        }

        // Save Button Click Listener
        binding.saveAddButton.setOnClickListener {
            if (validateAndShowErrors()) {
                // Call our own save method instead
                saveModifiedJewelryItem(closeAfterSave = false)
            }
        }

        // Cancel Button Click Listener
        binding.saveCloseButton.setOnClickListener {
            if (validateAndShowErrors()) {
                // Call our own save method instead
                saveModifiedJewelryItem(closeAfterSave = true)
            }
        }
    }

    // Create our own save method
    private fun saveModifiedJewelryItem(closeAfterSave: Boolean) {
        // Get the selected item type
        val itemType = when {
            binding.goldChip.isChecked -> "GOLD"
            binding.silverChip.isChecked -> "SILVER"
            binding.otherChip.isChecked -> "OTHER"
            else -> "GOLD" // Default
        }

        // Create a jewelry item object with all the form data
        val jewelryItem = JewelleryItem(
            id = if (editMode && itemToEdit != null) itemToEdit!!.id else "",
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
            stock = 1.0, // Set default stock to 1 for items created during sales
            stockUnit = "PIECE", // Default to piece
            location = "Sales", // Default location
            // Add our custom fields
            diamondPrice = diamondPriceField?.text.toString().toDoubleOrNull() ?: 0.0,
            makingChargesOn = if(grossWeightRadioButton?.isChecked == true) "GrossWeight" else "NetWeight"
        )

        // Notify listener based on mode
        if (editMode) {
            listener?.onItemUpdated(jewelryItem)
        } else {
            listener?.onItemAdded(jewelryItem)
        }

        if (closeAfterSave) {
            dismiss()
        } else {
            // Only clear the form if we're not in edit mode
            if (!editMode) {
                clearForm()
            }
        }
    }

    companion object {
        fun newInstance(): ModifiedItemBottomSheetFragment {
            return ModifiedItemBottomSheetFragment()
        }
    }
}