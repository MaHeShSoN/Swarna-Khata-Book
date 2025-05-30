package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.JewelryCategory
import com.jewelrypos.swarnakhatabook.DataClasses.MetalItem
import com.jewelrypos.swarnakhatabook.Enums.InventoryType
import com.jewelrypos.swarnakhatabook.Enums.MetalItemType
import com.jewelrypos.swarnakhatabook.Factorys.MetalItemViewModelFactory
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.JewelryCategoryRepository
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.MetalItemViewModel
import com.jewelrypos.swarnakhatabook.databinding.DialogInputMetalItemBinding
import com.jewelrypos.swarnakhatabook.databinding.FragmentItemBottomSheetBinding
import com.squareup.picasso.Picasso
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Scale
import com.jewelrypos.swarnakhatabook.Adapters.JewelleryAdapter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import android.graphics.Typeface
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.Job

open class ItemBottomSheetFragment : BottomSheetDialogFragment() {

    var listener: OnItemAddedListener? = null
    var editMode = false
    var itemToEdit: JewelleryItem? = null

    // UI Components
    private var _binding: FragmentItemBottomSheetBinding? = null
    val binding get() = _binding!!

    // Current selected inventory type
    private var selectedInventoryType = InventoryType.IDENTICAL_BATCH
    
    // Image handling variables
    private var currentImagePath: String? = null
    private var imageUri: Uri? = null
    private var imageUrl: String = ""
    private lateinit var storageRef: StorageReference
    
    // ActivityResultLaunchers for image capture and selection
    private var _cameraLauncher: ActivityResultLauncher<Intent>? = null
    private var _galleryLauncher: ActivityResultLauncher<Intent>? = null

    // Constants
    private val TAG_TEXT_WATCHERS = 12345 // Arbitrary unique value for the tag

    private val viewModel: MetalItemViewModel by viewModels {
        MetalItemViewModelFactory(
            requireActivity().application
        )
    }
    private lateinit var itemTypeChipGroup: ChipGroup

    private val categoryRepository by lazy {
        JewelryCategoryRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
    }

    // Add to track coroutine jobs
    private val coroutineJobs = mutableListOf<Job>()

    // Add this at class level
    private val textWatchers = mutableMapOf<android.widget.TextView, TextWatcher>()

    // Implement a helper method for adding TextWatchers safely
    private fun addSafeTextWatcher(view: android.widget.TextView, watcher: TextWatcher) {
        // Remove any existing watcher for this view
        textWatchers[view]?.let {
            view.removeTextChangedListener(it)
        }
        
        // Add the new watcher and track it
        view.addTextChangedListener(watcher)
        textWatchers[view] = watcher
    }

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

        // Initialize replace image button visibility
        binding.replaceImageButton?.visibility = View.GONE

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize radio buttons with the default selection
        selectedInventoryType = InventoryType.IDENTICAL_BATCH
        updateInventoryTypeRadioSelection()

        // Update to use categoryRepository instead of viewModel
        val job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get all categories initially
                categoryRepository.getCategoriesByMetalType("GOLD").fold(
                    onSuccess = { categories ->
                        populateDropdown(categories)
                    },
                    onFailure = { e ->
                        Log.e("ItemBottomSheet", "Error loading categories", e)
                        Toast.makeText(requireContext(), "Error loading categories", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                Log.e("ItemBottomSheet", "Error loading categories", e)
            }
        }
        coroutineJobs.add(job)

        binding.itemTypeChipGroup.setOnCheckedChangeListener { group, checkedIds ->
            // Load categories for the selected metal type
            val metalType = when (checkedIds) {
                binding.goldChip.id -> "GOLD"
                binding.silverChip.id -> "SILVER"
                binding.otherChip.id -> "OTHER"
                else -> "GOLD" // Default
            }
            loadCategoriesForMetalType(metalType)
        }

        // Setup inventory type radio buttons
        setupInventoryTypeRadioGroup()

        // If we're in edit mode, populate the form with the item data
        if (editMode && itemToEdit != null) {
            populateFormWithItemData(itemToEdit!!)
        } else {
            // If it's not edit mode, set up initial values
            setupInitialValues()
        }

        setUpDropDownMenus()
        setupListeners()
        // Dynamically show/hide fields based on inventory type
        updateFormFieldsForInventoryType()

        // Add default categories on first launch (now per-user)
        val job2 = viewLifecycleOwner.lifecycleScope.launch {
            try {
                categoryRepository.addDefaultCategories()
            } catch (e: Exception) {
                Log.e("ItemBottomSheet", "Error adding default categories", e)
            }
        }
        coroutineJobs.add(job2)

        // Set up chip group listener
        binding.itemTypeChipGroup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                binding.goldChip.id -> loadCategoriesForMetalType("GOLD")
                binding.silverChip.id -> loadCategoriesForMetalType("SILVER")
                binding.otherChip.id -> loadCategoriesForMetalType("OTHER")
            }
        }
    }

    private fun setupInventoryTypeRadioGroup() {
        // Make sure the radio group reflects the current selection state
        updateInventoryTypeRadioSelection()
        
        // Use the XML-defined radio group
        binding.inventoryTypeRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            selectedInventoryType = when (checkedId) {
                R.id.quantityBasedRadio -> InventoryType.IDENTICAL_BATCH
                R.id.weightBasedRadio -> InventoryType.BULK_STOCK
                else -> InventoryType.IDENTICAL_BATCH // Default
            }
            updateFormFieldsForInventoryType()
        }

        // Add click listeners to the cards to handle selection
        binding.quantityBasedCard.setOnClickListener {
            // First clear any existing selection to avoid race conditions
            binding.inventoryTypeRadioGroup.clearCheck()
            // Then set our selection after a small delay to ensure UI thread has processed the clear
            binding.inventoryTypeRadioGroup.post {
                binding.inventoryTypeRadioGroup.check(R.id.quantityBasedRadio)
            }
        }

        binding.weightBasedCard.setOnClickListener {
            // First clear any existing selection to avoid race conditions
            binding.inventoryTypeRadioGroup.clearCheck()
            // Then set our selection after a small delay to ensure UI thread has processed the clear
            binding.inventoryTypeRadioGroup.post {
                binding.inventoryTypeRadioGroup.check(R.id.weightBasedRadio)
            }
        }
    }

    private fun updateFormFieldsForInventoryType() {
        when (selectedInventoryType) {
            InventoryType.IDENTICAL_BATCH -> {
                // For batch items: Code optional, stock is editable
                binding.stockInputLayout.visibility = View.VISIBLE
                binding.stockTypeInputLayout.visibility = View.VISIBLE
                binding.totalWeightInputLayout?.visibility = View.GONE
                
                // Show inventory information card
                binding.inventoryCard?.visibility = View.VISIBLE
                
                // Show weight details for batch items
                binding.weightCard?.visibility = View.VISIBLE
            }
            
            InventoryType.BULK_STOCK -> {
                // For bulk stock: Code not needed, show total weight, hide stock count
                binding.stockInputLayout.visibility = View.GONE
                binding.stockTypeInputLayout.visibility = View.GONE
                
                // Show inventory information card with total weight field
                binding.inventoryCard?.visibility = View.VISIBLE
                
                // Hide weight details for bulk stock since they're irrelevant
                binding.weightCard?.visibility = View.GONE
                
                // Create total weight field if it doesn't exist
                if (binding.totalWeightInputLayout == null) {
                    // This code would create the field dynamically
                    // In a real implementation, you'd add these fields to your XML layout
                    Toast.makeText(context, 
                        "Please update your layout to include totalWeightInputLayout", 
                        Toast.LENGTH_SHORT).show()
                } else {
                    binding.totalWeightInputLayout?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun populateFormWithItemData(item: JewelleryItem) {
        // Set inventory type radio button first
        selectedInventoryType = item.inventoryType
        updateInventoryTypeRadioSelection()
        // Update form fields based on inventory type immediately
        updateFormFieldsForInventoryType()
        
        when (item.itemType.lowercase()) {
            "gold" -> binding.goldChip.isChecked = true
            "silver" -> binding.silverChip.isChecked = true
            "other" -> binding.otherChip.isChecked = true
        }

        // Fill in the fields
        binding.displayNameEditText.setText(item.displayName)
        binding.categoryDropdown.setText(item.category)
        binding.grossWeightEditText.setText(item.grossWeight.toString())
        binding.netWeightEditText.setText(item.netWeight.toString())
        binding.wastageEditText.setText(item.wastage.toString())
        binding.wastageTypeDropdown.setText(item.wastageType)
        binding.purityEditText.setText(item.purity)
        
        // Load the item image if available
        if (item.imageUrl.isNotEmpty()) {
            imageUrl = item.imageUrl
            binding.imageInstructionText.visibility = View.GONE
            binding.replaceImageButton?.visibility = View.VISIBLE
            binding.cameraButton.visibility = View.GONE
            binding.galleryButton.visibility = View.GONE
            
            // Load image with Coil

            val request = ImageRequest.Builder(requireContext())
                .data(item.imageUrl)
                // Match the same size as in the adapter for cache consistency
                .size(JewelleryAdapter.TARGET_WIDTH, JewelleryAdapter.TARGET_HEIGHT)
                .scale(Scale.FILL)
                // Apply the same transformations for cache consistency
                // Set to null target for cache-only loading (no view attached)
                .target(null)
                // Instead of priority, use placeholderMemoryCacheKey for caching
                .placeholderMemoryCacheKey(item.imageUrl)
                // Ensure we're using memory cache
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()



            requireContext().imageLoader.enqueue(request)
        } else {
            // No image, show camera and gallery buttons
            binding.replaceImageButton?.visibility = View.GONE
            binding.cameraButton.visibility = View.VISIBLE
            binding.galleryButton.visibility = View.VISIBLE
            binding.imageInstructionText.visibility = View.VISIBLE
        }
        
        // Set fields based on inventory type
        when (item.inventoryType) {
            InventoryType.BULK_STOCK -> {
                binding.totalWeightInputLayout?.let {
                    it.editText?.setText(item.totalWeightGrams.toString())
                }
            }
            else -> {
                binding.stockEditText.setText(item.stock.toString())
                binding.stockChargesTypeEditText.setText(item.stockUnit)
            }
        }
    }

    private fun updateInventoryTypeRadioSelection() {
        // Make sure both aren't checked at the same time (which should be impossible with RadioGroup)
        binding.inventoryTypeRadioGroup.clearCheck()
        
        // Set the radio button based on the selected inventory type
        when (selectedInventoryType) {
            InventoryType.IDENTICAL_BATCH -> binding.inventoryTypeRadioGroup.check(R.id.quantityBasedRadio)
            InventoryType.BULK_STOCK -> binding.inventoryTypeRadioGroup.check(R.id.weightBasedRadio)
        }
    }

    private fun setUpDropDownMenus() {
        //Wastage Type
        val listOfWastageType = listOf<String>("Percentage", "Gram")
        val adapter0 = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.dropdown_item_jewellery,
            listOfWastageType
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                
                // Apply typography according to guidelines
                (view as TextView).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                
                // Apply typography according to guidelines
                (view as TextView).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                view.setTextColor(ContextCompat.getColor(context, R.color.my_light_on_surface))
                
                return view
            }
        }
        
        binding.wastageTypeDropdown.apply {
            setAdapter(adapter0)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        //Stock Unit list
        val listOfUnits = listOf<String>("PIECE", "SET", "PAIR")

        val adapter3 = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.dropdown_item_jewellery,
            listOfUnits
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                
                // Apply typography according to guidelines
                (view as TextView).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                
                // Apply typography according to guidelines
                (view as TextView).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                view.setTextColor(ContextCompat.getColor(context, R.color.my_light_on_surface))
                
                return view
            }
        }
        
        binding.stockChargesTypeEditText.apply {
            setAdapter(adapter3)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    private fun populateDropdown(categories: List<JewelryCategory>) {
        val selectedChipId = binding.itemTypeChipGroup.checkedChipId
        val selectedType = when (selectedChipId) {
            binding.goldChip.id -> "GOLD"
            binding.silverChip.id -> "SILVER"
            binding.otherChip.id -> "OTHER"
            else -> null
        }

        val filteredCategories = if (selectedType != null) {
            categories.filter { it.metalType == selectedType }
        } else {
            categories
        }

        val categoryNames = filteredCategories.map { it.name }

        // Custom adapter that uses the jewelry-specific dropdown layout
        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.dropdown_item_jewellery,
            categoryNames
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                view.setTextColor(ContextCompat.getColor(context, R.color.my_light_on_surface))
                return view
            }
        }
        
        binding.categoryDropdown.apply {
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.my_light_primary_container)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(context, R.color.my_light_on_surface))
            
            if (categoryNames.isEmpty()) {
                binding.categoryInputLayout.helperText = "Type to add a new category"
                binding.categoryInputLayout.setHelperTextColor(ContextCompat.getColorStateList(requireContext(), R.color.my_light_secondary))
            } else {
                binding.categoryInputLayout.helperText = null
            }
        }

        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = binding.categoryDropdown.adapter.getItem(position).toString()
            binding.displayNameEditText.setText(selectedItem)
        }

        // Set text colors for input fields and make typography consistent
        binding.displayNameEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
        binding.displayNameEditText.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        
        binding.grossWeightEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
        binding.netWeightEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
        binding.wastageEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
        binding.purityEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
        binding.stockEditText.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
        binding.categoryDropdown.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))

        // Set box stroke colors for input layouts
        binding.displayNameInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
        binding.grossWeightInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
        binding.netWeightInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
        binding.wastageInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
        binding.wastageTypeInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
        binding.purityInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
        binding.stockInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
        binding.stockTypeInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
        binding.categoryInputLayout.setBoxStrokeColorStateList(ContextCompat.getColorStateList(requireContext(), R.color.my_light_outline)!!)
    }

    private fun loadCategoriesForMetalType(metalType: String) {
        val job = viewLifecycleOwner.lifecycleScope.launch {
            try {
                categoryRepository.getCategoriesByMetalType(metalType).fold(
                    onSuccess = { categories ->
                        val categoryNames = categories.map { it.name }
                        
                        // Save original categories for reference
                        val originalCategoryNames = categoryNames.toList()
                        
                        // Custom adapter that uses the jewelry-specific dropdown layout
                        val adapter = object : ArrayAdapter<String>(
                            requireContext(),
                            R.layout.dropdown_item_jewellery,
                            categoryNames
                        ) {
                            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                val view = super.getView(position, convertView, parent)
                                
                                // Apply typography according to guidelines
                                (view as TextView).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                
                                return view
                            }
                            
                            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                                val view = super.getDropDownView(position, convertView, parent)
                                
                                // Apply typography according to guidelines
                                (view as TextView).typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                                view.setTextColor(ContextCompat.getColor(context, R.color.my_light_on_surface))
                                
                                return view
                            }
                        }
                        
                        binding.categoryDropdown.apply {
                            setAdapter(adapter)
                            setDropDownBackgroundResource(R.color.my_light_primary_container)
                            
                            // Apply typography to the input field itself
                            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                            setTextColor(ContextCompat.getColor(context, R.color.my_light_on_surface))
                            
                            // Initially show dropdown if no categories
                            if (categoryNames.isEmpty()) {
                                binding.categoryInputLayout.helperText = "Type to add a new category"
                                binding.categoryInputLayout.setHelperTextColor(ContextCompat.getColorStateList(requireContext(), R.color.my_light_secondary))
                                
                                // Don't auto-show dropdown here, let the focus listener handle it
                            } else {
                                binding.categoryInputLayout.helperText = null
                            }
                        }

                        // Remove any existing text watchers to avoid duplicates
                        val existingWatchers = binding.categoryDropdown.getTag(TAG_TEXT_WATCHERS) as? ArrayList<TextWatcher>
                        if (existingWatchers != null) {
                            for (watcher in existingWatchers) {
                                binding.categoryDropdown.removeTextChangedListener(watcher)
                            }
                            existingWatchers.clear()
                        }
                        

                        // Add item click listener
                        binding.categoryDropdown.setOnItemClickListener { _, _, position, _ ->
                            val selectedItem = binding.categoryDropdown.adapter.getItem(position).toString()
                            
                            if (selectedItem.startsWith("➕ Create new category:")) {
                                // Extract the new category name from the suggestion
                                val newCategoryName = selectedItem.substringAfter("\"").substringBefore("\"")
                                
                                // Clear the current text and prepare to create a new category
                                binding.categoryDropdown.setText(newCategoryName, false)
                                
                            } else if (selectedItem == "Add new category with + button") {
                                // Clear the dropdown text and click the add button
                                binding.categoryDropdown.setText("", false)
                            } else if (originalCategoryNames.contains(selectedItem)) {
                                // Regular category selection - auto-fill display name
                                binding.displayNameEditText.setText(selectedItem)
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.e("ItemBottomSheet", "Error loading categories", e)
                        Toast.makeText(
                            requireContext(),
                            "Error loading categories",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Log.e("ItemBottomSheet", "Error loading categories", e)
            }
        }
        coroutineJobs.add(job)
    }

    fun validateJewelryItemForm(): Pair<Boolean, String> {
        // Get references to all form fields
        val displayName = binding.displayNameEditText.text.toString().trim()
        val grossWeight = binding.grossWeightEditText.text.toString().trim()
        val netWeight = binding.netWeightEditText.text.toString().trim()
        val wastage = binding.wastageEditText.text.toString().trim()
        val wastageType = binding.wastageTypeDropdown.text.toString().trim()
        val purity = binding.purityEditText.text.toString().trim()
        val stock = binding.stockEditText.text.toString().trim()
        val stockType = binding.stockChargesTypeEditText.text.toString().trim()
        val category = binding.categoryDropdown.text.toString().trim()
        val totalWeight = binding.totalWeightInputLayout?.editText?.text.toString().trim() ?: ""

        // Basic validation for required fields
        if (displayName.isEmpty()) {
            binding.displayNameInputLayout.error = getString(R.string.display_name_is_required)
            binding.displayNameEditText.requestFocus()
            return Pair(false, getString(R.string.display_name_is_required))
        } else {
            binding.displayNameInputLayout.error = null
        }


        if (category.isEmpty()) {
            binding.categoryInputLayout.error = getString(R.string.category_is_required)
            binding.categoryDropdown.requestFocus()
            return Pair(false, getString(R.string.category_is_required))
        } else {
            binding.categoryInputLayout.error = null
        }

        // For bulk stock, validate total weight instead of gross/net weight
        if (selectedInventoryType == InventoryType.BULK_STOCK) {
            if (totalWeight.isEmpty()) {
                binding.totalWeightInputLayout?.error = "Total weight is required"
                binding.totalWeightInputLayout?.requestFocus()
                return Pair(false, "Total weight is required")
            } else {
                try {
                    val totalWeightValue = totalWeight.toDouble()
                    if (totalWeightValue <= 0) {
                        binding.totalWeightInputLayout?.error = "Total weight must be greater than zero"
                        binding.totalWeightInputLayout?.requestFocus()
                        return Pair(false, "Total weight must be greater than zero")
                    } else {
                        binding.totalWeightInputLayout?.error = null
                    }
                } catch (e: NumberFormatException) {
                    binding.totalWeightInputLayout?.error = "Invalid total weight"
                    binding.totalWeightInputLayout?.requestFocus()
                    return Pair(false, "Invalid total weight")
                }
            }
            // Skip other weight validations for bulk stock
            return Pair(true, "")
        }

        // Numeric field validations for non-bulk inventory types
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
        
        // Initialize total weight field if it exists
        binding.totalWeightInputLayout?.editText?.setText("0.0")

        // Setup auto-select for numeric fields
        setupAutoSelectForNumericFields()
    }

    private fun setupAutoSelectForNumericFields() {
        // List of all numeric EditText fields
        val numericFields = listOf(
            binding.grossWeightEditText,
            binding.netWeightEditText,
            binding.wastageEditText,
            binding.purityEditText,
            binding.stockEditText,
            binding.totalWeightInputLayout?.editText
        ).filterNotNull()

        // Apply auto-select to each field
        numericFields.forEach { editText ->
            editText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    (view as EditText).setSelection(0, view.text.length)
                }
            }
        }
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
        val itemType = when {
            binding.goldChip.isChecked -> "GOLD"
            binding.silverChip.isChecked -> "SILVER"
            binding.otherChip.isChecked -> "OTHER"
            else -> "GOLD"
        }
        val categoryName = binding.categoryDropdown.text.toString().trim()
        // Before saving, ensure category exists for this user and metal type
        val job = viewLifecycleOwner.lifecycleScope.launch {
            val categoriesResult = categoryRepository.getCategoriesByMetalType(itemType)
            val categories = categoriesResult.getOrNull() ?: emptyList()
            val exists = categories.any { it.name.equals(categoryName, ignoreCase = true) }
            if (!exists && categoryName.isNotEmpty()) {
                // Auto-add the category
                categoryRepository.addCategory(
                    JewelryCategory(
                        name = categoryName,
                        metalType = itemType,
                        isDefault = false
                    )
                )
            }
            // Now proceed to save the item as before
            val jewellryItem = JewelleryItem(
                id = if (editMode && itemToEdit != null) itemToEdit!!.id else "",
                displayName = binding.displayNameEditText.text.toString().trim(),
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
                inventoryType = selectedInventoryType,
                totalWeightGrams = binding.totalWeightInputLayout?.editText?.text.toString().toDoubleOrNull() ?: 0.0,
                imageUrl = imageUrl // Use the uploaded image URL
            )
            if (editMode) {
                listener?.onItemUpdated(jewellryItem)
            } else {
                listener?.onItemAdded(jewellryItem)
            }
            if (closeAfterSave) {
                dismiss()
            } else {
                if (!editMode) clearForm()
            }
        }
        coroutineJobs.add(job)
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
        binding.grossWeightEditText.text?.clear()
        binding.netWeightEditText.text?.clear()
        binding.wastageEditText.text?.clear()
        binding.wastageTypeDropdown.setText(getString(R.string.percentage), false)
        binding.purityEditText.setText("")
        binding.stockEditText.text?.clear()
        binding.stockChargesTypeEditText.setText(getString(R.string.piece),false) // Default value after clearing

        // Reset image view and URL
        imageUrl = ""
        imageUri = null
        binding.itemImageView.setImageResource(R.drawable.image_placeholder)
        binding.imageInstructionText.visibility = View.VISIBLE
        binding.replaceImageButton?.visibility = View.GONE
        binding.cameraButton.visibility = View.VISIBLE
        binding.galleryButton.visibility = View.VISIBLE

        // Reset any error states
        binding.displayNameInputLayout.error = null
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
        
        // Image-related listeners - direct camera and gallery actions
        binding.cameraButton.setOnClickListener {
            dispatchTakePictureIntent()
        }
        
        binding.galleryButton.setOnClickListener {
            dispatchGalleryIntent()
        }
        
        // Replace image button
        binding.replaceImageButton?.setOnClickListener {
            showImageSourceDialog()
        }
        
        // Image view also allows direct camera capture when tapped
        binding.itemImageView.setOnClickListener {
            // If we already have an image, show the replace image button
            if (imageUrl.isNotEmpty() || imageUri != null) {
                showImageSourceDialog()
            } else {
                // If no image, default to camera
                dispatchTakePictureIntent()
            }
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf(getString(R.string.camera), getString(R.string.gallery))
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.select_image_source))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> dispatchTakePictureIntent() // Camera
                    1 -> dispatchGalleryIntent() // Gallery
                }
            }
            .show()
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
        
        private const val REQUEST_CAMERA_PERMISSION = 100
        private const val REQUEST_STORAGE_PERMISSION = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Storage reference
        storageRef = FirebaseStorage.getInstance().reference.child("jewelry_images")
        
        // Register activity launchers
        registerActivityResultLaunchers()
    }
    
    private fun registerActivityResultLaunchers() {
        // Camera launcher - capture image with device camera
        _cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    // Image captured successfully
                    currentImagePath?.let { path ->
                        // Create a File object from the path
                        val imageFile = File(path)
                        
                        // Check if the file exists and has content
                        if (imageFile.exists() && imageFile.length() > 0) {
                            // Create a URI from the file using FileProvider
                            val uri = FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.provider",
                                imageFile
                            )
                            
                            // Save the URI for later use
                            imageUri = uri
                            
                            // Display and upload the image
                            displaySelectedImage(uri)
                            uploadImageToFirebase(uri)
                        } else {
                            Toast.makeText(requireContext(), 
                                getString(R.string.captured_image_empty), 
                                Toast.LENGTH_SHORT).show()
                        }
                    } ?: run {
                        // Try to get the image from the result data URI as a fallback
                        result.data?.data?.let { uri ->
                            imageUri = uri
                            displaySelectedImage(uri)
                            uploadImageToFirebase(uri)
                        } ?: run {
                            Toast.makeText(requireContext(), 
                                getString(R.string.failed_retrieve_captured_image), 
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), 
                    getString(R.string.error_processing_camera_result, e.message), 
                    Toast.LENGTH_SHORT).show()
                Log.e("ItemBottomSheet", "Camera error: ${e.message}", e)
            }
        }
        
        // Gallery launcher - pick image from device gallery
        _galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // Image selected from gallery, use it directly
                    imageUri = uri
                    displaySelectedImage(uri)
                    uploadImageToFirebase(uri)
                } ?: run {
                    Toast.makeText(requireContext(), getString(R.string.failed_retrieve_selected_image), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Display the selected/cropped image in the ImageView
    private fun displaySelectedImage(uri: Uri) {
        try {
            // Show the image and hide the instruction text
            binding.imageInstructionText.visibility = View.GONE
            
            // Show replace button and hide camera/gallery buttons
            binding.replaceImageButton?.visibility = View.VISIBLE
            binding.cameraButton.visibility = View.GONE
            binding.galleryButton.visibility = View.GONE

            if (uri.scheme == "file" || uri.scheme == "content") {
                // Try to fix rotation
                val rotatedBitmap = fixImageRotation(uri)
                if (rotatedBitmap != null) {
                    // Use the rotated bitmap directly
                    binding.itemImageView.setImageBitmap(rotatedBitmap)
                } else {
                    // Fall back to Coil if rotation fixing fails
                    val request = ImageRequest.Builder(requireContext())
                        .data(uri)
                        // Match the same size as in the adapter for cache consistency
                        .size(JewelleryAdapter.TARGET_WIDTH, JewelleryAdapter.TARGET_HEIGHT)
                        .scale(Scale.FILL)
                        // Apply the same transformations for cache consistency
                        // Set to null target for cache-only loading (no view attached)
                        .target(null)
                        // Instead of priority, use placeholderMemoryCacheKey for caching
                        // Ensure we're using memory cache
                        .build()


                    requireContext().imageLoader.enqueue(request)
                }
            } else {
                // For non-file URIs, use Coil as before
                val request = ImageRequest.Builder(requireContext())
                    .data(uri)
                    // Match the same size as in the adapter for cache consistency
                    .size(JewelleryAdapter.TARGET_WIDTH, JewelleryAdapter.TARGET_HEIGHT)
                    .scale(Scale.FILL)
                    // Apply the same transformations for cache consistency
                    // Set to null target for cache-only loading (no view attached)
                    .target(null)
                    // Instead of priority, use placeholderMemoryCacheKey for caching
                    // Ensure we're using memory cache
                    .build()

                requireContext().imageLoader.enqueue(request)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), 
                "Error displaying image: ${e.message}", 
                Toast.LENGTH_SHORT).show()
            binding.imageInstructionText.visibility = View.VISIBLE
            binding.replaceImageButton?.visibility = View.GONE
            binding.cameraButton.visibility = View.VISIBLE
            binding.galleryButton.visibility = View.VISIBLE
        }
    }
    
    // Create a temporary file for storing the camera image
    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).apply {
            currentImagePath = absolutePath
        }
    }
    
    // Launch the camera activity to capture an image
    private fun dispatchTakePictureIntent() {
        try {
            // Check for camera permission
            if (requireContext().checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
                return
            }
            
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                // Ensure there's a camera activity to handle the intent
                takePictureIntent.resolveActivity(requireActivity().packageManager)?.also {
                    // Create the file where the photo should go
                    val photoFile: File? = try {
                        createImageFile()
                    } catch (ex: IOException) {
                        Toast.makeText(requireContext(), 
                            "${getString(R.string.error_creating_image_file)}: ${ex.message}", 
                            Toast.LENGTH_SHORT).show()
                        null
                    }
                    
                    // Continue only if the file was successfully created
                    photoFile?.also {
                        try {
                            val photoURI: Uri = FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.provider",
                                it
                            )
                            imageUri = photoURI
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                            
                            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            
                            _cameraLauncher?.launch(takePictureIntent)
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), 
                                "Error setting up camera: ${e.message}", 
                                Toast.LENGTH_SHORT).show()
                            Log.e("ItemBottomSheet", "Camera setup error: ${e.message}", e)
                        }
                    }
                } ?: run {
                    Toast.makeText(requireContext(), getString(R.string.no_camera_app), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), 
                getString(R.string.camera_error, e.message), 
                Toast.LENGTH_LONG).show()
            Log.e("ItemBottomSheet", "Camera dispatch error: ${e.message}", e)
        }
    }
    
    // Launch the gallery app to select an image
    private fun dispatchGalleryIntent() {
        try {
            val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            
            if (requireContext().checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), REQUEST_STORAGE_PERMISSION)
                return
            }
            
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryIntent.type = "image/*"
            try {
                _galleryLauncher?.launch(galleryIntent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), getString(R.string.no_gallery_app), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.gallery_error, e.message), Toast.LENGTH_LONG).show()
        }
    }
    
    // Upload the selected/cropped image to Firebase Storage with compression
    private fun uploadImageToFirebase(imageUri: Uri) {
        try {
            // Store the local URI as a fallback
            val localImageUri = imageUri.toString()
            
            // Show a loading indicator for the upload
            binding.itemImageView.alpha = 0.7f
            val progressOverlay = binding.progressOverlay
            progressOverlay?.visibility = View.VISIBLE
            
            // Try the upload in a safe way that won't crash if Firebase isn't configured
            try {
                // Create a reference to the file location with a unique name
                val fileName = "${UUID.randomUUID()}.jpg"
                val imageRef = storageRef.child(fileName)
                
                // Compress the image before uploading to reduce size - ADDED COMPRESSION
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(imageUri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    val rotatedBitmap = fixImageRotation(imageUri) ?: originalBitmap
                    
                    // First resize the image if it's too large (max dimensions of 1200px)
                    val maxSize = 1200
                    val resizedBitmap = compressImage(rotatedBitmap, maxSize)
                    
                    // Then compress the quality to reduce file size
                    val baos = ByteArrayOutputStream()
                    // Use quality of 50% for good balance of quality and size
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
                    val uploadData = baos.toByteArray()
                    
                    // Upload the compressed data
                    val uploadTask = imageRef.putBytes(uploadData)
                    uploadTask
                        .addOnSuccessListener { taskSnapshot ->
                            // Get the download URL
                            imageRef.downloadUrl.addOnSuccessListener { uri ->
                                imageUrl = uri.toString()
                                
                                // Restore the image view to normal
                                binding.itemImageView.alpha = 1.0f
                                progressOverlay?.visibility = View.GONE
                                
                                Toast.makeText(requireContext(), getString(R.string.image_upload_success), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { exception ->
                            // Upload failed, use local URI
                            imageUrl = localImageUri
                            
                            // Restore the image view to normal
                            binding.itemImageView.alpha = 1.0f
                            progressOverlay?.visibility = View.GONE
                            
                            // Log error but don't show to user unless in debug mode
                            android.util.Log.e("FirebaseUpload", "Upload failed: ${exception.message}", exception)
                            
                            // If this is a permissions error, attempt to work without Firebase
                            handleUploadError(exception)
                        }
                } catch (e: Exception) {
                    // Error with compression
                    binding.itemImageView.alpha = 1.0f
                    progressOverlay?.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error compressing image: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ItemBottomSheet", "Image compression error", e)
                }
            } catch (e: Exception) {
                // Firebase might not be configured or initialized
                imageUrl = localImageUri
                binding.itemImageView.alpha = 1.0f
                progressOverlay?.visibility = View.GONE
                
                // Log error but proceed with local image
                android.util.Log.e("Firebase", "Firebase initialization error", e)
            }
        } catch (e: Exception) {
            // Handle any other errors
            binding.itemImageView.alpha = 1.0f
            binding.progressOverlay?.visibility = View.GONE
            Toast.makeText(requireContext(), "Image handling error: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("ItemBottomSheet", "Image handling error", e)
        }
    }

    private fun compressImage(bitmap: Bitmap, maxSize: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        
        // Only resize if image is larger than max dimensions
        if (width > maxSize || height > maxSize) {
            val ratio = width.toFloat() / height.toFloat()
            if (ratio > 1) {
                // Width is greater than height
                width = maxSize
                height = (width / ratio).toInt()
            } else {
                // Height is greater than or equal to width
                height = maxSize
                width = (height * ratio).toInt()
            }
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        return bitmap
    }

    private fun fixImageRotation(uri: Uri): Bitmap? {
        try {
            // Get input stream from URI
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null

            // First decode image bounds
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Decode full image
            val newInputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(newInputStream)
            newInputStream.close()

            // Get orientation from EXIF data
            val inputStream2 = requireContext().contentResolver.openInputStream(uri) ?: return null
            val exif = ExifInterface(inputStream2)
            inputStream2.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )

            // Rotate bitmap according to EXIF orientation
            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            Log.e("ItemBottomSheet", "Error fixing image rotation: ${e.message}", e)
            return null
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun handleUploadError(exception: Exception) {
        // Check if this is a permissions issue with Firebase Storage rules
        if (exception.message?.contains("does not have access") == true) {
            // This likely means the Firebase Storage rules need to be updated
            Toast.makeText(
                requireContext(),
                "Firebase Storage permissions issue. Using local image for now.",
                Toast.LENGTH_LONG
            ).show()
            
            // Log detailed instructions for fixing the issue
            android.util.Log.e("FirebaseUpload", """
                Firebase Storage permission denied. To fix this:
                1. Go to Firebase Console > Storage > Rules
                2. Update the rules to allow writes. For testing, you can use:
                   service firebase.storage {
                     match /b/{bucket}/o {
                       match /{allPaths=**} {
                         allow read, write: if true;
                       }
                     }
                   }
                WARNING: The rule above is for development only! 
                For production, use proper authentication.
            """.trimIndent(), exception)
        } else {
            // Generic error message
            Toast.makeText(
                requireContext(),
                "Upload failed: ${exception.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, retry camera
                    dispatchTakePictureIntent()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.camera_permission_required), Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, retry gallery
                    dispatchGalleryIntent()
                } else {
                    // Handle case where permission is denied
                    val permissionName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        "photo library"
                    } else {
                        "storage"
                    }
                    Toast.makeText(
                        requireContext(), 
                        getString(R.string.storage_permission_required), 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        // Cancel all coroutine jobs
        coroutineJobs.forEach { it.cancel() }
        coroutineJobs.clear()
        
        // Remove all text watchers to prevent memory leaks
        textWatchers.forEach { (view, watcher) ->
            view.removeTextChangedListener(watcher)
        }
        textWatchers.clear()
        
        // Clear the existing watchers that might be tracked with tags
        val fields = listOf(
            binding.displayNameEditText,
            binding.grossWeightEditText,
            binding.netWeightEditText,
            binding.wastageEditText,
            binding.wastageTypeDropdown,
            binding.purityEditText,
            binding.stockEditText,
            binding.categoryDropdown
        )
        
        fields.forEach { view ->
            val existingWatchers = view?.getTag(TAG_TEXT_WATCHERS) as? ArrayList<TextWatcher>
            existingWatchers?.forEach { watcher ->
                view.removeTextChangedListener(watcher)
            }
        }
        
        // Clear image references
        imageUri = null
        currentImagePath = null
        
        // Clear binding
        _binding = null
        
        super.onDestroyView()
    }

    override fun onDestroy() {
        // Unregister activity result launchers
        _cameraLauncher = null
        _galleryLauncher = null
        super.onDestroy()
    }

}


