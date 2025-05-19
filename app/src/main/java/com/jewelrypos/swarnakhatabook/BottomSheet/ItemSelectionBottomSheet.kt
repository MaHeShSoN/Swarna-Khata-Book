package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.ExtraChargeAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.ExtraCharge
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Enums.InventoryType
import com.jewelrypos.swarnakhatabook.Factorys.InventoryViewModelFactory
import com.jewelrypos.swarnakhatabook.R

import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.databinding.ItemSelectionBottomSheetBinding
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ItemSelectionBottomSheet : BottomSheetDialogFragment() {


    // UI Components
    internal var _binding: ItemSelectionBottomSheetBinding? = null
    val binding get() = _binding!!

    private var itemSelectedListener: OnItemSelectedListener? = null

    private var selectedItem: JewelleryItem = JewelleryItem(
        id = UUID.randomUUID().toString(),
        displayName = "",
        itemType = "",
        category = "",
        grossWeight = 0.0,
        netWeight = 0.0,
        wastage = 0.0,
        purity = "",
        makingCharges = 0.0,
        makingChargesType = "",
        stock = 0.0,
        stockUnit = "",
        location = "",
        diamondPrice = 0.0,
        metalRate = 0.0,
        metalRateOn = "Net Weight",
        taxRate = 0.0,
        totalTax = 0.0,
        listOfExtraCharges = emptyList()
    )
    private lateinit var chargeAdapter: ExtraChargeAdapter

    // Add InventoryViewModel to access inventory items
    private val inventoryViewModel: InventoryViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        InventoryViewModelFactory(repository, connectivityManager, requireContext())
    }
    internal lateinit var inventoryRepository: InventoryRepository

    // Store inventory items for dropdown
    private var inventoryItems: List<JewelleryItem> = emptyList()
    var editMode = false
    var itemToEdit: JewelleryItem? = null

    // Add this section to properly track TextWatchers
    private val textWatchers = mutableMapOf<TextView, TextWatcher>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ItemSelectionBottomSheetBinding.inflate(inflater, container, false)

        // Update title if in edit mode
        if (editMode) {
            binding.titleTextView.text = "Edit Jewellery Item"
            binding.saveAddButton.text = "Update"
            binding.saveAddButton.visibility = View.GONE
            binding.saveCloseButton.text = "Update & Close"
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize repository directly
        initializeRepository()

        // If we're in edit mode, populate the form with the item data
        if (editMode && itemToEdit != null) {
            populateFormWithItemData(itemToEdit!!)
        } else {
            // If it's not edit mode, set up initial values
            setupInitialValues()
        }

        setupMetalTypeChips()
        setUpDropDownMenus()
        setupChangeListeners()
        setupListeners()
        setupTaxFields()
        loadAllInventoryItems()
    }

    private fun initializeRepository() {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        inventoryRepository = InventoryRepository(firestore, auth, requireContext())
    }

    // Load all inventory items for the dropdown
    private fun loadAllInventoryItems() {
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load all items without pagination
                val result = inventoryRepository.getAllInventoryItems()

                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { items ->
                            inventoryItems = items
                            setupInventoryDropdown()
                            binding.progressBar.visibility = View.GONE
                        },
                        onFailure = { exception ->
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Failed to load inventory items: ${exception.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                binding.progressBar.visibility = View.GONE
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }


    private fun setupTaxFields() {
        // Create tax rate input field if it doesn't exist
        if (binding.taxRateInputLayout == null) {
            // Add text change listener for tax rate
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    calculateTotalTexCharges()
                }
            }
            binding.taxRateEditText.addTextChangedListener(watcher)
            textWatchers[binding.taxRateEditText] = watcher
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


    private inner class DetailedInventoryItemAdapter(
        context: android.content.Context,
        private val items: List<JewelleryItem>
    ) : ArrayAdapter<JewelleryItem>(context, R.layout.dropdown_item_inventory, items), Filterable {

        private var filteredItems: List<JewelleryItem> = items
        private var selectedMetalType: String? = null // "Gold", "Silver", "Other", or null for all

        override fun getCount(): Int = filteredItems.size

        override fun getItem(position: Int): JewelleryItem = filteredItems[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.dropdown_item_inventory, parent, false)

            val item = getItem(position)
            val primaryText = view.findViewById<TextView>(R.id.primary_text)
            val secondaryText = view.findViewById<TextView>(R.id.secondary_text)
            val detailsText = view.findViewById<TextView>(R.id.details_text)
            val stockText = view.findViewById<TextView>(R.id.stock_text)
            
            // Check if this is a quick-added item
            val isQuickAdded = item.id.startsWith("QUICK_")
            
            // Set the primary text (item name) with a star for quick-added items
            primaryText.apply {
                text = if (isQuickAdded) "★ ${item.displayName}" else item.displayName
                // Gold color for the star if quick-added
                if (isQuickAdded) {
                    val spannableString = SpannableString(text)
                    spannableString.setSpan(
                        ForegroundColorSpan(context.getColor(R.color.my_light_primary)),
                        0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text = spannableString
                }
            }
            
            // Always hide the secondary text (inventory type)
            secondaryText.visibility = View.GONE
            
            // Format based on inventory type
            when(item.inventoryType) {
                InventoryType.BULK_STOCK -> {
                    // For weight-based items, hide details and show stock
                    detailsText.visibility = View.GONE
                    
                    // Show total available weight next to item name
                    stockText.text = "${item.totalWeightGrams}g"
                    
                    // Apply color coding based on weight thresholds for bulk stock
                    when {
                        item.totalWeightGrams <= 100.0 -> stockText.setTextColor(context.getColor(R.color.status_unpaid)) // Red for low stock
                        item.totalWeightGrams <= 250.0 -> stockText.setTextColor(context.getColor(R.color.status_partial)) // Yellow for medium stock
                        else -> stockText.setTextColor(context.getColor(R.color.status_paid)) // Green for good stock
                    }
                }
                
                InventoryType.IDENTICAL_BATCH -> {
                    // For quantity-based items, show details and stock
                    detailsText.text = "Weight: ${item.grossWeight}g | Purity: ${item.purity}"
                    detailsText.visibility = View.VISIBLE
                    
                    // Show stock quantity next to item name
                    stockText.text = "${item.stock.toInt()} ${item.stockUnit}"
                    
                    // Apply color coding based on quantity thresholds
                    when {
                        item.stock <= 1.0 -> stockText.setTextColor(context.getColor(R.color.status_unpaid)) // Red for very low stock
                        item.stock <= 5.0 -> stockText.setTextColor(context.getColor(R.color.status_partial)) // Yellow for low stock
                        else -> stockText.setTextColor(context.getColor(R.color.status_paid)) // Green for good stock
                    }
                }
            }
            
            // Add divider after each item (except the last one) in the dropdown
            if (parent is ListView && position < filteredItems.size - 1) {
                // Get the divider height from resources
                val dividerHeight = context.resources.getDimensionPixelSize(R.dimen.divider_height)
                
                // Set bottom padding to create space for divider
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    view.paddingBottom + dividerHeight
                )
                
                // Draw a divider line at the bottom
                view.setBackgroundResource(R.drawable.dropdown_item_divider)
            } else {
                // Reset padding if this is the last item or not in a ListView
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    view.paddingBottom
                )
                view.setBackgroundResource(R.color.cream_background)
            }

            return view
        }

        // This method determines what appears in the dropdown field after selection
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return getView(position, convertView, parent)
        }

        fun setMetalTypeFilter(metalType: String?) {
            this.selectedMetalType = metalType
            filter.filter(null) // Apply the filter
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val query = constraint?.toString()?.lowercase() ?: ""

                    // First filter by metal type if selected
                    val metalTypeFiltered = if (selectedMetalType != null) {
                        when (selectedMetalType) {
                            "Gold" -> items.filter { it.itemType.contains("Gold", ignoreCase = true) }
                            "Silver" -> items.filter { it.itemType.contains("Silver", ignoreCase = true) }
                            "Other" -> items.filter { 
                                !it.itemType.contains("Gold", ignoreCase = true) && 
                                !it.itemType.contains("Silver", ignoreCase = true) 
                            }
                            else -> items
                        }
                    } else {
                        items
                    }

                    // Then filter by search query if provided
                    filteredItems = if (query.isEmpty()) {
                        metalTypeFiltered
                    } else {
                        metalTypeFiltered.filter {
                            it.displayName.lowercase().contains(query) ||
                                    it.category.lowercase().contains(query) ||
                                    it.itemType.lowercase().contains(query)
                        }
                    }

                    return FilterResults().apply {
                        values = filteredItems
                        count = filteredItems.size
                    }
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    filteredItems = results?.values as? List<JewelleryItem> ?: items
                    notifyDataSetChanged()
                }
            }
        }
    }


    // Setup inventory dropdown
    private fun setupInventoryDropdown() {
        // Create adapter with inventory items
        val adapter = DetailedInventoryItemAdapter(requireContext(), inventoryItems)
        
        // Apply initial filter based on selected chip
        val selectedMetalType = when {
            binding.goldChip.isChecked -> "Gold"
            binding.silverChip.isChecked -> "Silver"
            binding.otherChip.isChecked -> "Other"
            else -> null
        }
        adapter.setMetalTypeFilter(selectedMetalType)

        // Set adapter to dropdown
        binding.itemNameDropdown.apply {
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
        }

        // Handle item selection
        binding.itemNameDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedItem = adapter.getItem(position)

            // Set just the display name in the field
            binding.itemNameDropdown.setText(selectedItem.displayName, false)
            binding.itemNameInputLayout.isHelperTextEnabled = true
            
            // Show stock information based on inventory type
            val stockUnit = if (selectedItem.inventoryType == InventoryType.BULK_STOCK) "g" else "pcs"
            val stockDisplay = "${selectedItem.stock} $stockUnit in inventory"
            binding.itemNameInputLayout.helperText = stockDisplay

            // Update chip selection based on item type
            when {
                selectedItem.itemType.contains("Gold", ignoreCase = true) -> binding.goldChip.isChecked = true
                selectedItem.itemType.contains("Silver", ignoreCase = true) -> binding.silverChip.isChecked = true
                else -> binding.otherChip.isChecked = true
            }

            // Fill other fields
            fillFieldsFromSelectedItem(selectedItem)
        }
        
        // Allow manual text input for quick-add
        binding.itemNameDropdown.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val enteredText = s?.toString()?.trim() ?: ""
                
                // Check if entered text doesn't match any item but we have a valid name
                if (enteredText.isNotEmpty() && 
                    inventoryItems.none { it.displayName.equals(enteredText, ignoreCase = true) }) {
                    
                    // Mark as a quick-add item by showing helper text
                    binding.itemNameInputLayout.isHelperTextEnabled = true
                    binding.itemNameInputLayout.helperText = "New item - will be added to inventory"
                }
            }
        })
    }


    private fun fillFieldsFromSelectedItem(item: JewelleryItem) {
        // Fill all available fields
        binding.grossWeightEditText.setText(item.grossWeight.toString())
        binding.netWeightEditText.setText(item.netWeight.toString())
        binding.wastageEditText.setText(item.wastage.toString())
        binding.wastageTypeDropdown.setText(item.wastageType ?: "Percentage", false)
        binding.purityEditText.setText(item.purity)
        binding.mackingChargesEditText.setText(item.makingCharges.toString())
        binding.mackingChargesTypeEditText.setText(item.makingChargesType, false)
        binding.goldRateEditText.setText(item.metalRate.toString())
        binding.goldRateOnEditText.setText(item.metalRateOn, false)
        binding.diamondPrizeEditText.setText(item.diamondPrice.toString())

        // Set tax rate and update checkbox
        if (item.taxRate > 0) {
            binding.taxApplicableCheckbox.isChecked = true
            binding.taxRateEditText.setText(item.taxRate.toString())
            binding.taxRateInputLayout.visibility = View.VISIBLE
        } else {
            binding.taxApplicableCheckbox.isChecked = false
            binding.taxRateEditText.setText("0.0")
            binding.taxRateInputLayout.visibility = View.GONE
        }

        // Handle extra charges
        if (::chargeAdapter.isInitialized) {
            // Clear any existing charges first
            chargeAdapter.updateCharges(emptyList())

            // Add all extra charges to the adapter
            if (item.listOfExtraCharges.isNotEmpty()) {
                item.listOfExtraCharges.forEach { charge ->
                    chargeAdapter.addCharge(charge)
                }
            }
            updateChargesVisibility()
        }

        // Update calculated fields
        updateCalculatedFields()
    }

    private fun populateFormWithItemData(item: JewelleryItem) {
        // Fill in the fields
        binding.itemNameDropdown.setText(item.displayName)
        binding.grossWeightEditText.setText(item.grossWeight.toString())
        binding.netWeightEditText.setText(item.netWeight.toString())
        binding.wastageEditText.setText(item.wastage.toString())
        binding.wastageTypeDropdown.setText(item.wastageType ?: "Percentage", false)
        binding.purityEditText.setText(item.purity)
        binding.mackingChargesEditText.setText(item.makingCharges.toString())
        binding.mackingChargesTypeEditText.setText(item.makingChargesType, false)
        binding.goldRateEditText.setText(item.metalRate.toString() ?: "0.0")
        binding.goldRateOnEditText.setText(item.metalRateOn ?: "Net Weight", false)
        binding.diamondPrizeEditText.setText(item.diamondPrice.toString() ?: "0.0")


        if (item.taxRate > 0) {
            binding.taxApplicableCheckbox.isChecked = true
            binding.taxRateEditText.setText(item.taxRate.toString())
            // Make the tax rate field visible if it's controlled by the checkbox
            binding.taxRateInputLayout.visibility = View.VISIBLE
        } else {
            binding.taxApplicableCheckbox.isChecked = false
            binding.taxRateEditText.setText("0.0")
            // Hide the tax rate field if applicable
            binding.taxRateInputLayout.visibility = View.GONE
        }

        setUpInitalRecyclerView()



        if (item.listOfExtraCharges.isNotEmpty()) {
            // Clear any existing charges first (important!)
            chargeAdapter.updateCharges(emptyList())

            // Add all extra charges to the adapter one by one
            item.listOfExtraCharges.forEach { charge ->
                android.util.Log.d(
                    "ItemSelection",
                    "Adding charge: ${charge.name} = ${charge.amount}"
                )
                chargeAdapter.addCharge(charge)
            }
        } else {
            // Clear any existing charges if the item has none
            chargeAdapter.updateCharges(emptyList())
            android.util.Log.d("ItemSelection", "No extra charges to display")
        }


        // Update calculated fields
        updateCalculatedFields()

        updateChargesVisibility()
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
        }

        // Rate on list
        val listOfRatOn = listOf<String>("Net Weight", "Gross Weight", "Fine")
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            listOfRatOn
        )
        binding.goldRateOnEditText.apply {
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
        }


        // Making charge type list
        val listOfMakingChargeType = listOf<String>("PER GRAM", "FIX", "PERCENTAGE")
        val adapter2 = ArrayAdapter(
            requireContext(),
            R.layout.dropdown_item,
            listOfMakingChargeType
        )
        binding.mackingChargesTypeEditText.apply {
            setAdapter(adapter2)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
        }
    }

    fun validateJewelryItemForm(): Pair<Boolean, String> {
        // Get references to all form fields
        val grossWeight = binding.grossWeightEditText.text.toString().trim()
        val netWeight = binding.netWeightEditText.text.toString().trim()
        val wastage = binding.wastageEditText.text.toString().trim()
        val purity = binding.purityEditText.text.toString().trim()
        val makingCharges = binding.mackingChargesEditText.text.toString().trim()
        val makingChargesType = binding.mackingChargesTypeEditText.text.toString().trim()
        val goldRate = binding.goldRateEditText.text.toString().trim()
        val goldRateOn = binding.goldRateOnEditText.text.toString().trim()
        val diamondPrice = binding.diamondPrizeEditText.text.toString().trim()

        // Add this check near the beginning of your validateJewelryItemForm method
        val itemName = binding.itemNameDropdown.text.toString().trim()
        if (itemName.isEmpty()) {
            binding.itemNameInputLayout.error = "Item name is required"
            binding.itemNameDropdown.requestFocus()
            return Pair(false, "Item name is required")
        } else {
            binding.itemNameInputLayout.error = null
        }

        // Numeric field validations
        // Gross Weight - required
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

        // Net Weight - should be less than gross weight if provided
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

        // Purity validation
        if (purity.isEmpty()) {
            binding.purityInputLayout.error = "Purity is required"
            binding.purityEditText.requestFocus()
            return Pair(false, "Purity is required")
        } else {
            try {
                val purityValue = purity.toDouble()
                if (purityValue <= 0 || purityValue > 100) {
                    binding.purityInputLayout.error = "Purity must be between 0 and 100"
                    binding.purityEditText.requestFocus()
                    return Pair(false, "Purity must be between 0 and 100")
                } else {
                    binding.purityInputLayout.error = null
                }

                // Extra validation when "Fine" is selected for gold rate
                if (goldRateOn.equals("Fine", ignoreCase = true)) {
                    if (purityValue <= 0) {
                        binding.purityInputLayout.error =
                            "Purity must be greater than zero when using Fine weight"
                        binding.purityEditText.requestFocus()
                        return Pair(
                            false,
                            "Purity must be greater than zero when using Fine weight"
                        )
                    }
                }
            } catch (e: NumberFormatException) {
                binding.purityInputLayout.error = "Invalid purity value"
                binding.purityEditText.requestFocus()
                return Pair(false, "Invalid purity value")
            }
        }

        // Validate wastage if provided
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

        // Making charges validation
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

                // If making charges type is PERCENTAGE, validate it's between 0 and 100
                if (makingChargesType.equals("PERCENTAGE", ignoreCase = true) &&
                    (makingChargesValue < 0 || makingChargesValue > 100)
                ) {
                    binding.mackingChargesInputLayout.error = "Percentage must be between 0 and 100"
                    binding.mackingChargesEditText.requestFocus()
                    return Pair(false, "Percentage must be between 0 and 100")
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

        // Gold rate validation
        if (goldRate.isNotEmpty()) {
            try {
                val goldRateValue = goldRate.toDouble()
                if (goldRateValue < 0) {
                    binding.goldRateInputLayout.error = "Gold rate cannot be negative"
                    binding.goldRateEditText.requestFocus()
                    return Pair(false, "Gold rate cannot be negative")
                } else {
                    binding.goldRateInputLayout.error = null
                }

                // If gold rate is provided, gold rate on must also be provided
                if (goldRateOn.isEmpty()) {
                    binding.goldRateOnInputLayout.error = "Rate Based On is required"
                    binding.goldRateOnEditText.requestFocus()
                    return Pair(false, "Rate Based On is required")
                } else {
                    binding.goldRateOnInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.goldRateInputLayout.error = "Invalid gold rate"
                binding.goldRateEditText.requestFocus()
                return Pair(false, "Invalid gold rate")
            }
        }

        // Diamond price validation if provided
        if (diamondPrice.isNotEmpty()) {
            try {
                val diamondPriceValue = diamondPrice.toDouble()
                if (diamondPriceValue < 0) {
                    binding.diamondPrizeInputLayout.error = "Diamond price cannot be negative"
                    binding.diamondPrizeEditText.requestFocus()
                    return Pair(false, "Diamond price cannot be negative")
                } else {
                    binding.diamondPrizeInputLayout.error = null
                }
            } catch (e: NumberFormatException) {
                binding.diamondPrizeInputLayout.error = "Invalid diamond price"
                binding.diamondPrizeEditText.requestFocus()
                return Pair(false, "Invalid diamond price")
            }
        }

        // All validations passed
        return Pair(true, "")
    }

    /**
     * Calculates the total making charges based on the type and weight
     */
    internal fun calculateMakingCharges(): Double {
        val makingCharges = binding.mackingChargesEditText.text.toString().toDoubleOrNull() ?: 0.0
        val makingChargesType = binding.mackingChargesTypeEditText.text.toString()
        val netWeight = binding.netWeightEditText.text.toString().toDoubleOrNull() ?: 0.0

        return when (makingChargesType.uppercase()) {
            "PER GRAM" -> makingCharges * netWeight
            "FIX" -> makingCharges
            "PERCENTAGE" -> {
                // Calculate percentage of total gold value
                val goldValue = calculateGoldValue()
                goldValue * (makingCharges / 100.0)
            }

            else -> 0.0
        }
    }

    /**
     * Calculates the total gold value based on weight, wastage, and gold rate
     */
    /**
     * Calculates the total gold value based on weight, wastage, and gold rate
     * Now supports "Fine" calculation based on purity percentage
     */
    internal fun calculateGoldValue(): Double {
        val goldRate = binding.goldRateEditText.text.toString().toDoubleOrNull() ?: 0.0
        val goldRateOn = binding.goldRateOnEditText.text.toString()
        val grossWeight = binding.grossWeightEditText.text.toString().toDoubleOrNull() ?: 0.0
        val netWeight = binding.netWeightEditText.text.toString().toDoubleOrNull() ?: 0.0
        val wastage = binding.wastageEditText.text.toString().toDoubleOrNull() ?: 0.0
        val wastageType = binding.wastageTypeDropdown.text.toString()
        val purity = binding.purityEditText.text.toString().toDoubleOrNull() ?: 0.0

        // Calculate the weight to use based on the selection
        val selectedWeight = when (goldRateOn) {
            "Net Weight" -> netWeight
            "Gross Weight" -> grossWeight
            "Fine" -> {
                // For "Fine" option, we calculate with the purity percentage
                // First determine which weight to use (net or gross)
                val baseWeight = if (netWeight > 0.0) netWeight else grossWeight

                // Calculate fine weight (applying purity percentage)
                val purityDecimal = purity / 100.0

                // Return the fine weight
                baseWeight * purityDecimal
            }

            else -> netWeight // Default to net weight if something unexpected
        }

        val effectiveWeight = when (wastageType) {
            "Percentage" -> {
                // If wastageType is Percentage, calculate wastage as a percentage of netWeight
                val wastageWeight = netWeight * (wastage / 100.0)
                selectedWeight + wastageWeight
            }

            "Gram" -> {
                // If wastageType is Gram, add the wastage directly to the selectedWeight
                selectedWeight + wastage
            }

            else -> {
                // Default to percentage calculation
                val wastageWeight = netWeight * (wastage / 100.0)
                selectedWeight + wastageWeight
            }
        }

        // Multiply by gold rate
        return effectiveWeight * goldRate
    }

    /**
     * Calculates the total charges by adding gold value, making charges, and diamond price
     */
    internal fun calculateTotalCharges(): Double {
        val goldValue = calculateGoldValue()
        val makingCharges = calculateMakingCharges()
        val diamondPrice = binding.diamondPrizeEditText.text.toString().toDoubleOrNull() ?: 0.0


        return goldValue + makingCharges + diamondPrice
    }

    internal fun calculateTotalTexCharges(): Double {
        val goldValue = calculateGoldValue()
        val makingCharges = calculateMakingCharges()
        val diamondPrice = binding.diamondPrizeEditText.text.toString().toDoubleOrNull() ?: 0.0
        val extraChargesTotal =
            if (::chargeAdapter.isInitialized && chargeAdapter.getExtraChargeList()
                    .isNotEmpty()
            ) chargeAdapter.getTotalCharges() else 0.0

        val subtotal = goldValue + makingCharges + diamondPrice + extraChargesTotal

        // Calculate tax
        val taxRate = binding.taxRateEditText.text.toString().toDoubleOrNull() ?: 0.0
        val taxAmount = subtotal * (taxRate / 100.0)

        return taxAmount
    }

    /**
     * Updates all calculated fields in the UI
     */
    private fun updateCalculatedFields() {
        val makingCharges = calculateMakingCharges()
        val goldValue = calculateGoldValue()
        val totalCharges = calculateTotalCharges()
        val totalTex = calculateTotalTexCharges()
        val totalExtraCharge =
            if (::chargeAdapter.isInitialized) chargeAdapter.getTotalCharges() else 0.0
        // Update the UI with formatted values
        binding.totalMakingChargesEditText.setText(String.format("%.2f", makingCharges))
        binding.totalEditText.setText(String.format("%.2f", goldValue))
        binding.totalChargesEditText.setText(String.format("%.2f", totalCharges))
        binding.taxAmountEditText.setText(String.format("%.2f", totalTex))
        binding.totalExtraChargesEditText.setText(String.format("%.2f", totalExtraCharge))

    }

    /**
     * Sets up initial values for all fields
     */
    /**
     * Sets up initial values for all fields
     * Now includes "Fine" option in default values
     */
    private fun setupInitialValues() {
        // Reset selectedItem to a new default instance
        selectedItem = JewelleryItem(
            id = UUID.randomUUID().toString(),
            displayName = "",
            itemType = "",
            category = "",
            grossWeight = 0.0,
            netWeight = 0.0,
            wastage = 0.0,
            wastageType = "",
            purity = "",
            makingCharges = 0.0,
            makingChargesType = "",
            stock = 0.0,
            stockUnit = "",
            location = "",
            diamondPrice = 0.0,
            metalRate = 0.0,
            metalRateOn = "Net Weight", // Default to Net Weight
            taxRate = 0.0,
            totalTax = 0.0,
            listOfExtraCharges = emptyList()
        )

        // Set default values for UI fields
        binding.diamondPrizeEditText.setText("0.0")
        binding.goldRateEditText.setText("0.0")
        binding.wastageEditText.setText("0.0")
        binding.purityEditText.setText("0.0")
        binding.netWeightEditText.setText("0.0")
        binding.grossWeightEditText.setText("0.0")
        binding.mackingChargesEditText.setText("0.0")
        // Pre-select dropdown values
        binding.mackingChargesTypeEditText.setText("PER GRAM", false)
        binding.wastageTypeDropdown.setText("Percentage", false)
        binding.goldRateOnEditText.setText("Net Weight", false)

        // Set default tax values and visibility
        binding.taxApplicableCheckbox.isChecked = false
        binding.taxRateEditText.setText("0.0")
        binding.taxRateInputLayout.visibility = View.GONE

        setUpInitalRecyclerView()

        // Update calculated fields
        updateCalculatedFields()
    }

    internal fun setUpInitalRecyclerView() {
        chargeAdapter = ExtraChargeAdapter()
        binding.chargesRecyclerView.apply {
            adapter = chargeAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        chargeAdapter.onDeleteClickListener = { charge ->
            deleteCharge(charge)
        }

        chargeAdapter.onChargesUpdatedListener = {
            updateCalculatedFields()
            updateChargesVisibility()
        }


    }

    /**
     * Sets up change listeners for all input fields to update calculations in real-time
     */
    private fun setupChangeListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCalculatedFields()
            }
        }

        // Add text watchers to all fields that affect calculations
        binding.goldRateEditText.addTextChangedListener(textWatcher)
        binding.goldRateOnEditText.addTextChangedListener(textWatcher)
        binding.grossWeightEditText.addTextChangedListener(textWatcher)
        binding.netWeightEditText.addTextChangedListener(textWatcher)
        binding.wastageEditText.addTextChangedListener(textWatcher)
        binding.mackingChargesEditText.addTextChangedListener(textWatcher)
        binding.mackingChargesTypeEditText.addTextChangedListener(textWatcher)
        binding.diamondPrizeEditText.addTextChangedListener(textWatcher)

        // Handle dropdown item selection
        binding.goldRateOnEditText.setOnItemClickListener { _, _, _, _ ->
            updateCalculatedFields()
        }

        binding.mackingChargesTypeEditText.setOnItemClickListener { _, _, _, _ ->
            updateCalculatedFields()
        }

        binding.wastageTypeDropdown.setOnItemClickListener { _, _, _, _ ->
            updateCalculatedFields()
        }

        binding.taxRateEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCalculatedFields()
            }
        })

    }

    fun validateAndShowErrors(): Boolean {
        val (isValid, errorMessage) = validateJewelryItemForm()

        if (!isValid && errorMessage.isNotEmpty()) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    /**
     * Clears all form fields for adding a new item
     */
    fun clearForm() {
        // Reset selectedItem to a new default instance
        selectedItem = JewelleryItem(
            id = UUID.randomUUID().toString(),
            // Set other fields to default values
        )

        // Clear existing UI field values
        binding.itemNameDropdown.setText("")
        binding.grossWeightEditText.text?.clear()
        binding.netWeightEditText.text?.clear()
        binding.wastageEditText.text?.clear()
        binding.purityEditText.setText("")
        binding.mackingChargesEditText.text?.clear()
        binding.mackingChargesTypeEditText.setText("")
        binding.goldRateEditText.text?.clear()
        binding.goldRateOnEditText.setText("")
        binding.diamondPrizeEditText.text?.clear()

        // Reset any error states
        binding.grossWeightInputLayout.error = null
        binding.netWeightInputLayout.error = null
        binding.wastageInputLayout.error = null
        binding.purityInputLayout.error = null
        binding.mackingChargesInputLayout.error = null
        binding.mackingChargesTypeInputLayout.error = null
        binding.goldRateInputLayout.error = null
        binding.goldRateOnInputLayout.error = null
        binding.diamondPrizeInputLayout.error = null
        binding.itemNameInputLayout.error = null
        binding.itemNameInputLayout.helperText = null
    }

    fun setupListeners() {
        // Add Item Button Click Listener - Opens ItemBottomSheetFragment
        binding.addItemButton.setOnClickListener {
            openItemBottomSheet()
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

        binding.addExtraChargeButton.setOnClickListener {
            showAddChargeDialog()
        }

        // Add this to your setupListeners() method
        binding.taxApplicableCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.taxRateInputLayout.visibility = View.VISIBLE
                // Set default tax rate if empty
                if (binding.taxRateEditText.text.isNullOrEmpty() ||
                    binding.taxRateEditText.text.toString() == "0.0"
                ) {
                    binding.taxRateEditText.setText("3.0") // Default tax rate
                }
            } else {
                binding.taxRateInputLayout.visibility = View.GONE
                binding.taxRateEditText.setText("0.0")
            }
            updateCalculatedFields()
        }


    }

    private fun updateChargesVisibility() {
        // First check if the chargeAdapter is initialized
        if (!::chargeAdapter.isInitialized) {
            // If adapter isn't initialized, hide everything
            binding.chargesRecyclerView.visibility = View.GONE
            binding.emptyExtraChargesText.visibility = View.VISIBLE
            android.util.Log.d("ItemSelection", "Charge adapter not initialized")
            return
        }

        // Get the current charges list
        val charges = chargeAdapter.getExtraChargeList()

        // Log for debugging
        android.util.Log.d(
            "ItemSelection",
            "Updating charges visibility. Found ${charges.size} charges"
        )

        if (charges.isNotEmpty()) {
            // Show RecyclerView, hide empty state text
            binding.chargesRecyclerView.visibility = View.VISIBLE
            binding.emptyExtraChargesText.visibility = View.GONE
        } else {
            // Hide RecyclerView, show empty state text
            binding.chargesRecyclerView.visibility = View.GONE
            binding.emptyExtraChargesText.visibility = View.VISIBLE
        }
    }

    private fun showAddChargeDialog() {
        val dialog = ThemedM3Dialog(requireContext())
            .setTitle("Add New Charge")
            .setLayout(R.layout.dialog_add_extra_charge)

        dialog.setPositiveButton("Add") { _, dialogView ->
            val nameEditText = dialog.findViewById<TextInputEditText>(R.id.chargeNameEditText)
            val amountEditText = dialog.findViewById<TextInputEditText>(R.id.chargeAmountEditText)

            val name = nameEditText?.text.toString().trim()
            val amountStr = amountEditText?.text.toString().trim()

            if (name.isNotEmpty() && amountStr.isNotEmpty()) {
                try {
                    val amount = amountStr.toDouble()
                    addCharge(name, amount)
                } catch (e: NumberFormatException) {
                    Toast.makeText(
                        requireContext(),
                        "Please enter a valid amount",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        dialog.setNegativeButton("Cancel", null)
        dialog.show()
    }

    private fun deleteCharge(charge: ExtraCharge) {
        ThemedM3Dialog(requireContext())
            .setTitle("Confirm Deletion")
            .setPositiveButton("Delete") { _, _ ->
                chargeAdapter.removeCharge(charge)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    internal fun addCharge(name: String, amount: Double) {
        val newCharge = ExtraCharge(name = name, amount = amount)
        chargeAdapter.addCharge(newCharge)
    }

    // Open ItemBottomSheetFragment to add a new item
    private fun openItemBottomSheet() {
        val bottomSheetFragment = ItemBottomSheetFragment.newInstance()

        // Set listener to handle the case when a new item is added
        bottomSheetFragment.setOnItemAddedListener(object :
            ItemBottomSheetFragment.OnItemAddedListener {
            override fun onItemAdded(item: JewelleryItem) {
                // When a new item is added, update our list and select the new item
                inventoryViewModel.addJewelleryItem(item)
                fillFieldsFromSelectedItem(item)
                binding.itemNameDropdown.setText(item.displayName)
            }

            override fun onItemUpdated(item: JewelleryItem) {
                // Handle item update if needed
                inventoryViewModel.updateJewelleryItem(item)
                fillFieldsFromSelectedItem(item)
                binding.itemNameDropdown.setText(item.displayName)
            }
        })

        bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
    }

    // Method to set the item for editing
    fun setItemForEdit(item: JewelleryItem) {
        editMode = true
        itemToEdit = item
        selectedItem = item
    }

    internal fun saveJewelryItem(closeAfterSave: Boolean) {
        // Get the display name from the dropdown
        val displayName = binding.itemNameDropdown.text.toString().trim()
        
        // Check if this is a quick-add item (not from inventory)
        val isQuickAdd = selectedItem.id == UUID.randomUUID().toString() || 
                         (displayName != selectedItem.displayName && selectedItem.displayName.isEmpty())
        
        // Generate ID for quick-add items with a prefix
        val itemId = if (isQuickAdd) {
            "QUICK_" + UUID.randomUUID().toString()
        } else if (editMode && itemToEdit != null) {
            itemToEdit!!.id
        } else {
            selectedItem.id
        }

        // Determine inventory type based on form data
        val inventoryType = if (isQuickAdd) {
            // For quick-add items, we'll default to quantity-based (IDENTICAL_BATCH)
            InventoryType.IDENTICAL_BATCH
        } else {
            selectedItem.inventoryType
        }
        
        // For quick-add, use the display name as both name and category
        val category = if (isQuickAdd) displayName else selectedItem.category
        
        // Determine metal type from chip selection
        val metalType = when {
            binding.goldChip.isChecked -> "Gold ${binding.purityEditText.text.toString()}"
            binding.silverChip.isChecked -> "Silver ${binding.purityEditText.text.toString()}"
            binding.otherChip.isChecked -> "Other Metal"
            else -> selectedItem.itemType
        }
        
        // Set default stock value for quick-add items
        val stockValue = if (isQuickAdd) 1.0 else selectedItem.stock
        val stockUnit = if (isQuickAdd) "pcs" else selectedItem.stockUnit

        // Get extra charges from adapter if initialized
        val extraCharges = if (::chargeAdapter.isInitialized) {
            val charges = chargeAdapter.getExtraChargeList()
            android.util.Log.d("ItemSelection", "Saving with ${charges.size} extra charges")
            charges
        } else {
            android.util.Log.d("ItemSelection", "Charge adapter not initialized, no charges saved")
            emptyList()
        }

        // Create a jewelry item object with all the form data
        val jewelryItem = JewelleryItem(
            id = itemId,
            displayName = displayName,
            itemType = metalType,
            category = category,
            grossWeight = binding.grossWeightEditText.text.toString().toDoubleOrNull() ?: 0.0,
            netWeight = binding.netWeightEditText.text.toString().toDoubleOrNull() ?: 0.0,
            wastage = binding.wastageEditText.text.toString().toDoubleOrNull() ?: 0.0,
            wastageType = binding.wastageTypeDropdown.text.toString(),
            purity = binding.purityEditText.text.toString(),
            makingCharges = binding.mackingChargesEditText.text.toString().toDoubleOrNull() ?: 0.0,
            makingChargesType = binding.mackingChargesTypeEditText.text.toString(),
            stock = stockValue,
            stockUnit = stockUnit,
            location = selectedItem.location,
            diamondPrice = binding.diamondPrizeEditText.text.toString().toDoubleOrNull() ?: 0.0,
            metalRate = binding.goldRateEditText.text.toString().toDoubleOrNull() ?: 0.0,
            metalRateOn = binding.goldRateOnEditText.text.toString(),
            taxRate = binding.taxRateEditText.text.toString().toDoubleOrNull() ?: 0.0,
            totalTax = binding.taxAmountEditText.text.toString().toDoubleOrNull() ?: 0.0,
            listOfExtraCharges = extraCharges,
            inventoryType = inventoryType
        )

        val calculatedPrice = binding.totalChargesEditText.text.toString().toDoubleOrNull() ?: 0.0

        // If it's a quick-add item, add it to inventory first
        if (isQuickAdd && !editMode) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = inventoryRepository.addJewelleryItem(jewelryItem)
                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            Toast.makeText(context, "Quick-added item saved to inventory", Toast.LENGTH_SHORT).show()
                        } else {
                            // Continue even if inventory save fails
                            Toast.makeText(context, "Note: Item not saved to inventory", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    // Log but continue with selection
                    android.util.Log.e("ItemSelection", "Failed to save quick-add item to inventory", e)
                }
            }
        }

        // Notify listener based on mode
        if (editMode) {
            itemSelectedListener?.onItemUpdated(
                jewelryItem,
                calculatedPrice
            )
        } else {
            itemSelectedListener?.onItemSelected(
                jewelryItem,
                calculatedPrice
            )
        }

        if (closeAfterSave) {
            dismiss()
        } else {
            // Only clear the form if we're not in edit mode
            if (!editMode) {
                clearForm()
                setupInitialValues()
            }
        }
    }


    fun setOnItemSelectedListener(listener: OnItemSelectedListener) {
        this.itemSelectedListener = listener
    }

    // Add an interface for item selection
    interface OnItemSelectedListener {
        fun onItemSelected(item: JewelleryItem, price: Double)

        fun onItemUpdated(item: JewelleryItem, price: Double)
    }

    companion object {
        fun newInstance(): ItemSelectionBottomSheet {
            return ItemSelectionBottomSheet()
        }
    }

    override fun onDestroyView() {
        // Remove all TextWatchers to prevent memory leaks
        textWatchers.forEach { (textView, watcher) ->
            textView.removeTextChangedListener(watcher)
        }
        textWatchers.clear()
        
        super.onDestroyView()
        _binding = null
    }

    private fun setupMetalTypeChips() {
        // Set default chip selection
        binding.goldChip.isChecked = true
        
        // Setup chip click listeners
        binding.metalTypeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedMetalType = when {
                binding.goldChip.isChecked -> "Gold"
                binding.silverChip.isChecked -> "Silver"
                binding.otherChip.isChecked -> "Other"
                else -> null // No selection
            }
            
            // Apply the filter to adapter if it's initialized
            if (inventoryItems.isNotEmpty()) {
                val adapter = binding.itemNameDropdown.adapter
                if (adapter is DetailedInventoryItemAdapter) {
                    adapter.setMetalTypeFilter(selectedMetalType)
                }
            }
        }
    }
}