package com.jewelrypos.swarnakhatabook

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.ItemUsageStats
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Enums.InventoryType
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.ItemDetailViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.ViewModle.ItemDetailViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentItemDetailBinding
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Scale
import coil3.transform.CircleCropTransformation
import com.jewelrypos.swarnakhatabook.Adapters.JewelleryAdapter
import coil3.load
import coil3.request.crossfade

class ItemDetailFragment : Fragment() {

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    private val args: ItemDetailFragmentArgs by navArgs()

    private val viewModel: ItemDetailViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth, requireContext())
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        ItemDetailViewModelFactory(repository, connectivityManager)
    }

    private var imageUri: Uri? = null // This can be removed if not used for anything else. The captured image URI is handled in currentImagePath.
    private var currentImagePath: String? = null

    // ActivityResultLauncher for camera
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>

    // ActivityResultLauncher for gallery
    private lateinit var galleryLauncher: ActivityResultLauncher<String>

    // ActivityResultLauncher for requesting camera permission
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>

    // ActivityResultLauncher for requesting gallery/storage permission
    private lateinit var requestGalleryPermissionLauncher: ActivityResultLauncher<String>


    // For continuous adjustment on long press
    private var continuousHandler: android.os.Handler? = android.os.Handler(android.os.Looper.getMainLooper())
    private var adjustmentRunnable: Runnable? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActivityResultLaunchers()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupObservers()
        setupClickListeners()

        binding.topAppBar.overflowIcon =
            ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)

        // Load item data based on passed ID
        viewModel.loadItem(args.itemId)
    }

    private fun setupActivityResultLaunchers() {
        // Initialize camera launcher
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                currentImagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        // Create URI from file
                        val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.provider",
                            file
                        )
                        displaySelectedImage(uri)
                        uploadImageToFirebase(file)
                    } else {
                        Toast.makeText(
                            context,
                            getString(R.string.error_image_file_not_found), // Add this string to your strings.xml
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(context, getString(R.string.image_capture_cancelled), Toast.LENGTH_SHORT).show() // Add this string
            }
        }

        // Initialize gallery launcher
        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                displaySelectedImage(uri)
                val file = createFileFromUri(uri)
                if (file != null) {
                    uploadImageToFirebase(file)
                } else {
                    Toast.makeText(context, getString(R.string.error_processing_image), Toast.LENGTH_SHORT).show() // Add this string
                }
            } else {
                Toast.makeText(context, getString(R.string.image_selection_cancelled), Toast.LENGTH_SHORT).show() // Add this string
            }
        }

        // Initialize permission launcher for camera
        requestCameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(context, getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show() // Add this string
            }
        }

        // Initialize permission launcher for gallery/storage
        requestGalleryPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                dispatchGalleryIntent()
            } else {
                Toast.makeText(context, getString(R.string.gallery_permission_denied), Toast.LENGTH_SHORT).show() // Add this string
            }
        }
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    editItem()
                    true
                }

                R.id.action_delete -> {
                    confirmDeleteItem()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupObservers() {
        // Observe item details
        viewModel.jewelryItem.observe(viewLifecycleOwner) { item ->
            item?.let { updateItemUI(it) }
        }

        // Observe item usage statistics
        viewModel.itemUsageStats.observe(viewLifecycleOwner) { stats ->
            updateUsageStatsUI(stats)
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun updateItemUI(item: JewelleryItem) {
        val formatter = DecimalFormat("#,##,##0.00")

        // Update toolbar title with item name
        binding.topAppBar.title = item.displayName

        // Basic item information
        binding.categoryValue.text = item.category
        binding.itemTypeValue.text = item.itemType
        binding.purityValue.text = item.purity

        // Set type indicator color based on item type
        val typeIndicatorColor = when (item.itemType.lowercase()) {
            "gold" -> R.color.star_color
            "silver" -> R.color.shape_image_silver
            else -> R.color.my_light_primary
        }
        binding.itemTypeIndicator.setBackgroundColor(
            ContextCompat.getColor(requireContext(), typeIndicatorColor)
        )

        // Weight information
        binding.grossWeightValue.text = "${item.grossWeight}g"
        binding.netWeightValue.text = "${item.netWeight}g"
        binding.wastageValue.text = "${item.wastage}g"

        // Handle inventory type specific UI
        when (item.inventoryType) {
            InventoryType.IDENTICAL_BATCH -> {
                // Stock information for IDENTICAL_BATCH
                binding.stockManagementCard.visibility = View.VISIBLE
                binding.stockStatusText.visibility = View.VISIBLE
                binding.stockStatusIndicator.visibility = View.VISIBLE
                binding.currentStockValue.text = "${item.stock} ${item.stockUnit}"
                binding.stockUnitText.text = item.stockUnit
                binding.stockAdjustmentValue.text = "0"
                binding.finalStockValue.text = "Final Stock: ${item.stock} ${item.stockUnit}"
                updateStockStatusIndicator(item.stock)
                setupQuantityBasedStockUI()
            }

            InventoryType.BULK_STOCK -> {
                // For bulk stock, show total weight instead of stock count
                binding.stockManagementCard.visibility = View.VISIBLE
                binding.stockStatusText.visibility = View.GONE
                binding.stockStatusIndicator.visibility = View.GONE
                binding.currentStockValue.text = "${item.totalWeightGrams}g"
                binding.stockUnitText.text = "g"
                binding.stockAdjustmentValue.text = "0.0"
                binding.finalStockValue.text =
                    "Final Weight: ${String.format("%.1f", item.totalWeightGrams)}g"
                setupWeightBasedStockUI()
            }
        }
        // Price information
        if (item.metalRate > 0) {
            binding.goldRateValue.text =
                "₹${formatter.format(item.metalRate)}/g (on ${item.metalRateOn})"
            binding.goldRateLayout.visibility = View.VISIBLE
        } else {
            binding.goldRateLayout.visibility = View.GONE
        }

        if (item.makingCharges > 0) {
            binding.makingChargesValue.text = when (item.makingChargesType.uppercase()) {
                "PER GRAM" -> "₹${formatter.format(item.makingCharges)}/g"
                "FIX" -> "₹${formatter.format(item.makingCharges)}"
                else -> "₹${formatter.format(item.makingCharges)}"
            }
            binding.makingChargesLayout.visibility = View.VISIBLE
        } else {
            binding.makingChargesLayout.visibility = View.GONE
        }

        // Load item image if available
        if (item.imageUrl.isNotEmpty()) {
            binding.itemImageCard?.visibility = View.VISIBLE

            // Show replace button, hide camera and gallery buttons
            binding.replaceImageButton.visibility = View.VISIBLE
            binding.cameraButton.visibility = View.GONE
            binding.galleryButton.visibility = View.GONE

            // Show progress overlay while loading image
            binding.progressOverlay.visibility = View.VISIBLE
            binding.itemImage.alpha = 0.7f

            // Load image with Coil directly onto the ImageView
            binding.itemImage.load(item.imageUrl) {
                // Match the same size as in the adapter for cache consistency
                scale(Scale.FILL)
                crossfade(true)

                // Add loading and error handling
                listener(
                    onStart = {
                        binding.progressOverlay.visibility = View.VISIBLE
                        binding.itemImage.alpha = 0.7f
                    },
                    onSuccess = { _, _ ->
                        binding.progressOverlay.visibility = View.GONE
                        binding.itemImage.alpha = 1.0f
                    },
                    onError = { _, _ ->
                        // Show placeholder on error
                        binding.itemImage.setImageResource(R.drawable.image_placeholder)
                        binding.progressOverlay.visibility = View.GONE
                        binding.itemImage.alpha = 1.0f
                    }
                )
            }
        } else {
            // No image available
            binding.itemImageCard?.visibility = View.VISIBLE
            binding.itemImage.setImageResource(R.drawable.image_placeholder)
            binding.progressOverlay.visibility = View.GONE
            binding.itemImage.alpha = 1.0f

            // Hide replace button, show camera and gallery buttons
            binding.replaceImageButton.visibility = View.GONE
            binding.cameraButton.visibility = View.VISIBLE
            binding.galleryButton.visibility = View.VISIBLE
        }

        // Extra charges
        updateExtraChargesSection(item)
    }

    private fun updateStockStatusIndicator(stock: Double) {
        val statusColor = when {
            stock <= 0 -> R.color.status_unpaid // Out of stock
            stock < 5 -> R.color.status_partial // Low stock
            else -> R.color.status_paid // In stock
        }

        val statusText = when {
            stock <= 0 -> "OUT OF STOCK"
            stock < 5 -> "LOW STOCK"
            else -> "IN STOCK"
        }

        binding.stockStatusIndicator.setBackgroundColor(
            ContextCompat.getColor(requireContext(), statusColor)
        )
        binding.stockStatusText.text = statusText
    }

    private fun updateExtraChargesSection(item: JewelleryItem) {
        // Clear any existing charges
        binding.extraChargesContainer.removeAllViews()

        // If there are no extra charges, hide the section
        if (item.listOfExtraCharges.isEmpty()) {
            binding.extraChargesCard.visibility = View.GONE
            return
        }

        // Show the section and add each charge
        binding.extraChargesCard.visibility = View.VISIBLE
        val formatter = DecimalFormat("#,##,##0.00")

        for (charge in item.listOfExtraCharges) {
            val chargeView = layoutInflater.inflate(
                R.layout.item_extra_charge_layout,
                binding.extraChargesContainer,
                false
            )

            val nameTextView =
                chargeView.findViewById<android.widget.TextView>(R.id.extraChargeNameText)
            val amountTextView =
                chargeView.findViewById<android.widget.TextView>(R.id.extraChargeAmountText)

            nameTextView.text = charge.name
            amountTextView.text = "₹${formatter.format(charge.amount)}"

            binding.extraChargesContainer.addView(chargeView)
        }
    }

    private fun updateUsageStatsUI(stats: ItemUsageStats) {
        val formatter = DecimalFormat("#,##,##0.00")

        // Update usage statistics
        binding.invoiceCountValue.text = stats.totalInvoicesUsed.toString()
        binding.quantitySoldValue.text = stats.totalQuantitySold.toString()
        binding.totalRevenueValue.text = "₹${formatter.format(stats.totalRevenue)}"

        // Last sold date
        if (stats.lastSoldDate > 0) {
            val dateFormat =
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            binding.lastSoldDateValue.text = dateFormat.format(java.util.Date(stats.lastSoldDate))
        } else {
            binding.lastSoldDateValue.text = "Never sold"
        }

        // Top customer
        if (stats.topCustomerName.isNotEmpty()) {
            binding.topCustomerValue.text =
                "${stats.topCustomerName} (${stats.topCustomerQuantity} units)"
            
            // Add click listener to copy customer name
            binding.topCustomerValue.setOnClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Customer Name", stats.topCustomerName)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Customer name copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            
            // Make the text view look clickable
            binding.topCustomerValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_primary))
            binding.topCustomerValue.isClickable = true
            binding.topCustomerValue.isFocusable = true
        } else {
            binding.topCustomerValue.text = "N/A"
            binding.topCustomerValue.setOnClickListener(null)
            binding.topCustomerValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.my_light_on_surface))
            binding.topCustomerValue.isClickable = false
            binding.topCustomerValue.isFocusable = false
        }

        // Update performance indicator
        val performanceColor = when {
            stats.totalInvoicesUsed == 0 -> R.color.status_unpaid // No sales
            stats.totalInvoicesUsed < 3 -> R.color.status_partial // Low sales
            else -> R.color.status_paid // Good sales
        }

        binding.performanceIndicator.setBackgroundColor(
            ContextCompat.getColor(requireContext(), performanceColor)
        )
    }

    private fun setupClickListeners() {
        // Apply stock adjustment button
        binding.applyStockButton.setOnClickListener {
            applyStockAdjustment()
        }

        // Image related button listeners
        binding.replaceImageButton.setOnClickListener {
            showImageEditOptions()
        }

        binding.cameraButton.setOnClickListener {
            checkCameraPermissionAndDispatchIntent()
        }

        binding.galleryButton.setOnClickListener {
            checkGalleryPermissionAndDispatchIntent()
        }

        // Item image click should also show image options
        binding.itemImage.setOnClickListener {
            if (viewModel.jewelryItem.value?.imageUrl?.isNotEmpty() == true) {
                // If we have an image, show replace options
                showImageEditOptions()
            } else {
                // If no image, default to camera
                checkCameraPermissionAndDispatchIntent()
            }
        }
    }

    private fun applyStockAdjustment() {
        val inventoryType = viewModel.jewelryItem.value?.inventoryType ?: return

        when (inventoryType) {
            InventoryType.IDENTICAL_BATCH -> {
                val adjustment = binding.stockAdjustmentValue.text.toString().toIntOrNull() ?: 0
                if (adjustment == 0) {
                    Toast.makeText(context, "No adjustment to apply", Toast.LENGTH_SHORT).show()
                    return
                }

                // For batch items, adjust count
                viewModel.updateStock(adjustment) { success ->
                    handleStockUpdateResult(success)
                }
            }

            InventoryType.BULK_STOCK -> {
                val adjustment =
                    binding.stockAdjustmentValue.text.toString().toDoubleOrNull() ?: 0.0
                if (adjustment == 0.0) {
                    Toast.makeText(context, "No adjustment to apply", Toast.LENGTH_SHORT).show()
                    return
                }

                // For bulk stock, adjust weight directly
                viewModel.updateBulkWeight(adjustment) { success ->
                    handleStockUpdateResult(success)
                }
            }
        }
    }

    private fun handleStockUpdateResult(success: Boolean) {
        val inventoryType = viewModel.jewelryItem.value?.inventoryType

        if (success) {
            Toast.makeText(context, "Stock updated successfully", Toast.LENGTH_SHORT).show()

            // Set the correct initial value based on inventory type
            if (inventoryType == InventoryType.BULK_STOCK) {
                binding.stockAdjustmentValue.text = "0.0"
            } else {
                binding.stockAdjustmentValue.text = "0"
            }

            binding.applyStockButton.visibility = View.GONE
            EventBus.postInvoiceUpdated()
        } else {
            Toast.makeText(context, "Failed to update stock", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editItem() {
        viewModel.jewelryItem.value?.let { item ->
            val bottomSheet = ItemBottomSheetFragment.newInstance()
            bottomSheet.setItemForEdit(item)

            bottomSheet.setOnItemAddedListener(object :
                ItemBottomSheetFragment.OnItemAddedListener {
                override fun onItemAdded(item: JewelleryItem) {
                    // This won't be called during editing
                }

                override fun onItemUpdated(updatedItem: JewelleryItem) {
                    viewModel.updateItem(updatedItem) { success ->
                        if (success) {
                            EventBus.postInventoryUpdated()
                            Toast.makeText(context, "Item updated successfully", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(context, "Failed to update item", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            })

            bottomSheet.show(parentFragmentManager, "ItemEditBottomSheet")
        }
    }

    private fun confirmDeleteItem() {
        val item = viewModel.jewelryItem.value ?: return

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete ${item.displayName}? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.checkItemUsage { usageCount ->
                    if (usageCount > 0) {
                        showItemInUseWarning(usageCount)
                    } else {
                        deleteItem()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showItemInUseWarning(usageCount: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Item In Use")
            .setMessage("This item is used in $usageCount ${if (usageCount == 1) "invoice" else "invoices"}. Moving it to the recycling bin might affect invoice details if restored later. Do you want to proceed?") // Updated message
            .setPositiveButton("Move to Bin") { _, _ -> // Updated button text
                deleteItem() // Calls the function which now uses moveItemToRecycleBin
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteItem() { // Rename this function if desired
        // Change this call:
        // viewModel.deleteItem { success -> ... }
        // To this:
        viewModel.moveItemToRecycleBin { success -> // Call the new function
            if (success) {
                // EventBus is posted from ViewModel now
                Toast.makeText(context, "Item moved to recycling bin", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } else {
                Toast.makeText(context, "Failed to move item to recycling bin", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun showImageEditOptions() {
        val item = viewModel.jewelryItem.value ?: return

        val options = arrayOf(
            getString(R.string.camera),
            getString(R.string.gallery)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(if (item.imageUrl.isEmpty()) getString(R.string.add_image) else getString(R.string.replace))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndDispatchIntent()
                    1 -> checkGalleryPermissionAndDispatchIntent()
                }
            }
            .show()
    }

    // --- Permission and Intent Dispatching with ActivityResultLauncher ---

    private fun checkCameraPermissionAndDispatchIntent() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            dispatchTakePictureIntent()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun checkGalleryPermissionAndDispatchIntent() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13 (API 33) and above, use READ_MEDIA_IMAGES
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // For older versions, use READ_EXTERNAL_STORAGE
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            dispatchGalleryIntent()
        } else {
            requestGalleryPermissionLauncher.launch(permission)
        }
    }

    private fun dispatchTakePictureIntent() {
        try {
            val photoFile = try {
                createImageFile()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    getString(R.string.error_creating_image_file),
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("ItemDetailFragment", "Error creating image file for camera: ${e.message}", e)
                return
            }

            val photoURI: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                photoFile
            )
            cameraLauncher.launch(photoURI)

        } catch (e: Exception) {
            Toast.makeText(context, "Error launching camera: ${e.message}", Toast.LENGTH_SHORT)
                .show()
            Log.e("ItemDetailFragment", "Error launching camera: ${e.message}", e)
        }
    }

    private fun dispatchGalleryIntent() {
        try {
            galleryLauncher.launch("image/*") // Use GetContent contract which directly provides URI
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching gallery: ${e.message}", Toast.LENGTH_SHORT)
                .show()
            Log.e("ItemDetailFragment", "Error launching gallery: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create image file name
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir =
            requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)

        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).apply {
            currentImagePath = absolutePath // Store the path for later use
        }
    }

    // Removed onActivityResult as it's replaced by ActivityResultLauncher
    // override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { ... }

    private fun createFileFromUri(uri: Uri): File? {
        return try {
            val timeStamp =
                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())
            val imageFileName = "IMG_${timeStamp}"
            val storageDir =
                requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val file = File.createTempFile(imageFileName, ".jpg", storageDir)

            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val outputStream = java.io.FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            Log.e("ItemDetailFragment", "Error creating file from URI", e)
            null
        }
    }

    private fun uploadImageToFirebase(file: File) {
        // Show both progress indicators
        binding.progressBar.visibility = View.VISIBLE
        // Show the image upload progress overlay
        binding.itemImage.alpha = 0.7f
        binding.progressOverlay.visibility = View.VISIBLE

        val currentItem = viewModel.jewelryItem.value ?: return

        viewModel.uploadItemImage(file) { success, imageUrl ->
            // Hide progress indicators
            binding.progressBar.visibility = View.GONE
            binding.itemImage.alpha = 1.0f
            binding.progressOverlay.visibility = View.GONE

            if (success && imageUrl.isNotEmpty()) {
                // Update the item with the new image URL
                val updatedItem = currentItem.copy(imageUrl = imageUrl)

                // Update UI to show the replace button instead of camera/gallery
                binding.replaceImageButton.visibility = View.VISIBLE
                binding.cameraButton.visibility = View.GONE
                binding.galleryButton.visibility = View.GONE

                viewModel.updateItem(updatedItem) { updateSuccess ->
                    if (updateSuccess) {
                        Toast.makeText(
                            context,
                            getString(R.string.image_upload_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            getString(R.string.image_uploaded_but_failed_to_update_item), // Add this string
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                Toast.makeText(context, getString(R.string.failed_to_upload_image), Toast.LENGTH_SHORT).show() // Add this string
            }
        }
    }

    /**
     * Displays the selected image in the ImageView and updates the UI elements
     * to show the proper buttons based on having an image
     */
    private fun displaySelectedImage(uri: Uri?) {
        if (uri == null) return

        try {
            // Show replace button and hide camera/gallery buttons
            binding.replaceImageButton.visibility = View.VISIBLE
            binding.cameraButton.visibility = View.GONE
            binding.galleryButton.visibility = View.GONE

            // Temporary show progress until image loads
            binding.progressOverlay.visibility = View.VISIBLE
            binding.itemImage.alpha = 0.7f

            // Display the image using Coil
            binding.itemImage.load(uri) {
                scale(Scale.FILL)
                crossfade(true)

                listener(
                    onSuccess = { _, _ ->
                        binding.progressOverlay.visibility = View.GONE
                        binding.itemImage.alpha = 1.0f
                    },
                    onError = { _, _ ->
                        binding.progressOverlay.visibility = View.GONE
                        binding.itemImage.alpha = 1.0f
                        binding.itemImage.setImageResource(R.drawable.image_placeholder)
                        Toast.makeText(context, getString(R.string.failed_to_load_image), Toast.LENGTH_SHORT).show() // Add this string
                    }
                )
            }
        } catch (e: Exception) {
            binding.progressOverlay.visibility = View.GONE
            binding.itemImage.alpha = 1.0f
            binding.itemImage.setImageResource(R.drawable.image_placeholder)

            // If error, revert to camera/gallery buttons
            binding.replaceImageButton.visibility = View.GONE
            binding.cameraButton.visibility = View.VISIBLE
            binding.galleryButton.visibility = View.VISIBLE

            Toast.makeText(context, "Error displaying image: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("ItemDetailFragment", "Error displaying image", e)
        }
    }

    private fun setupQuantityBasedStockUI() {
        // Setup integer-based adjustment for quantity
        binding.stockAdjustmentValue.text = "0"

        // Use different step values for quantity
        binding.decreaseStockButton.setOnClickListener {
            adjustStockQuantity(-1)
        }

        // Set up long press listeners for continuous adjustment
        binding.decreaseStockButton.setOnLongClickListener {
            startContinuousAdjustment(-1, true)
            true
        }

        binding.decreaseStockButton.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL
            ) {
                stopContinuousAdjustment()
            }
            false
        }

        binding.increaseStockButton.setOnClickListener {
            adjustStockQuantity(1)
        }

        binding.increaseStockButton.setOnLongClickListener {
            startContinuousAdjustment(1, true)
            true
        }

        binding.increaseStockButton.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL
            ) {
                stopContinuousAdjustment()
            }
            false
        }

        // Add new buttons for larger adjustments
        binding.decreaseFastButton?.setOnClickListener {
            adjustStockQuantity(-5)
        }

        binding.decreaseFastButton?.setOnLongClickListener {
            startContinuousAdjustment(-5, true)
            true
        }

        binding.decreaseFastButton?.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL
            ) {
                stopContinuousAdjustment()
            }
            false
        }

        binding.increaseFastButton?.setOnClickListener {
            adjustStockQuantity(5)
        }

        binding.increaseFastButton?.setOnLongClickListener {
            startContinuousAdjustment(5, true)
            true
        }

        binding.increaseFastButton?.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL
            ) {
                stopContinuousAdjustment()
            }
            false
        }
    }

    private fun setupWeightBasedStockUI() {
        // Setup decimal-based adjustment for weight
        binding.stockAdjustmentValue.text = "0.0"

        // Use appropriate step values for weight
        binding.decreaseStockButton.setOnClickListener {
            adjustStockWeight(-0.5)
        }

        binding.decreaseStockButton.setOnLongClickListener {
            startContinuousAdjustment(-0.5, false)
            true
        }

        binding.decreaseStockButton.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL
            ) {
                stopContinuousAdjustment()
            }
            false
        }

        binding.increaseStockButton.setOnClickListener {
            adjustStockWeight(0.1)
        }

        binding.increaseStockButton.setOnLongClickListener {
            startContinuousAdjustment(0.1, false)
            true
        }

        binding.increaseStockButton.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL
            ) {
                stopContinuousAdjustment()
            }
            false
        }

        // Add new buttons for larger weight adjustments
        binding.decreaseFastButton?.setOnClickListener {
            adjustStockWeight(-5.0)
        }

        binding.decreaseFastButton?.setOnLongClickListener {
            startContinuousAdjustment(-5.0, false)
            true
        }

        binding.decreaseFastButton?.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL
            ) {
                stopContinuousAdjustment()
            }
            false
        }

        binding.increaseFastButton?.setOnClickListener {
            adjustStockWeight(5.0)
        }

        binding.increaseFastButton?.setOnLongClickListener {
            startContinuousAdjustment(5.0, false)
            true
        }

        binding.increaseFastButton?.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP ||
                event.action == android.view.MotionEvent.ACTION_CANCEL
            ) {
                stopContinuousAdjustment()
            }
            false
        }
    }

    private fun startContinuousAdjustment(value: Number, isQuantity: Boolean) {
        stopContinuousAdjustment() // Stop any existing adjustment

        adjustmentRunnable = object : Runnable {
            override fun run() {
                if (isQuantity) {
                    adjustStockQuantity(value.toInt())
                } else {
                    adjustStockWeight(value.toDouble())
                }
                continuousHandler?.postDelayed(this, 150) // Adjust every 150ms
            }
        }

        // Start after a small delay
        continuousHandler?.postDelayed(adjustmentRunnable!!, 500)
    }

    private fun stopContinuousAdjustment() {
        adjustmentRunnable?.let {
            continuousHandler?.removeCallbacks(it)
            adjustmentRunnable = null
        }
    }

    private fun adjustStockQuantity(change: Int) {
        val currentValue = binding.stockAdjustmentValue.text.toString().toIntOrNull() ?: 0
        val newValue = currentValue + change
        binding.stockAdjustmentValue.text = newValue.toString()

        // Update button states
        updateQuantityAdjustmentButtonsState(newValue)
    }

    private fun adjustStockWeight(change: Double) {
        val currentValue = binding.stockAdjustmentValue.text.toString().toDoubleOrNull() ?: 0.0
        val newValue = currentValue + change
        // Format to one decimal place
        binding.stockAdjustmentValue.text = String.format("%.1f", newValue)

        // Update button states
        updateWeightAdjustmentButtonsState(newValue)
    }

    private fun updateQuantityAdjustmentButtonsState(adjustment: Int) {
        // Current stock from the viewModel
        val currentItem = viewModel.jewelryItem.value ?: return
        val currentStock = currentItem.stock

        // Enable/disable decrease button based on potential final stock value
        binding.decreaseStockButton.isEnabled = adjustment > -currentStock.toInt()
        binding.decreaseFastButton?.isEnabled = adjustment >= 5 - currentStock.toInt()

        // Update the preview of the adjustment result
        val finalStock = currentStock + adjustment
        binding.finalStockValue.text = "Final Stock: $finalStock ${currentItem.stockUnit}"

        // Show/hide apply button
        binding.applyStockButton.visibility = if (adjustment != 0) View.VISIBLE else View.GONE
    }

    private fun updateWeightAdjustmentButtonsState(adjustment: Double) {
        // Current weight from the viewModel
        val currentItem = viewModel.jewelryItem.value ?: return
        val currentWeight = currentItem.totalWeightGrams

        // Enable/disable decrease button based on potential final weight value
        binding.decreaseStockButton.isEnabled = adjustment > -currentWeight
        binding.decreaseFastButton?.isEnabled = adjustment >= 5.0 - currentWeight

        // Update the preview of the adjustment result
        val finalWeight = currentWeight + adjustment
        binding.finalStockValue.text = "Final Weight: ${String.format("%.1f", finalWeight)}g"

        // Show/hide apply button
        binding.applyStockButton.visibility = if (adjustment != 0.0) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Properly clean up handler to prevent memory leaks
        stopContinuousAdjustment()
        continuousHandler?.removeCallbacksAndMessages(null)
        continuousHandler = null
        _binding = null
    }

    // No longer needed constants as we are using ActivityResultLauncher
    // companion object {
    //     private const val REQUEST_IMAGE_CAPTURE = 1001
    //     private const val REQUEST_GALLERY_PICK = 1002
    // }
}