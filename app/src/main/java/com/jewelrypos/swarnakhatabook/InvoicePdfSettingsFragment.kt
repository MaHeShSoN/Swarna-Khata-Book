package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.listener.OnTapListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.google.android.material.textfield.TextInputEditText
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoicePdfSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class InvoicePdfSettingsFragment : Fragment(), SignatureDialogFragment.OnSignatureCapturedListener,
    OnPageChangeListener, OnLoadCompleteListener, OnTapListener {

    private var _binding: FragmentInvoicePdfSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var pdfView: PDFView
    private lateinit var pdfSettingsManager: PdfSettingsManager
    private var pdfSettings: PdfSettings? = null

    private var logoUri: Uri? = null
    private var watermarkUri: Uri? = null
    private var signatureUri: Uri? = null

    // Add these properties
    private var pageNumber = 0
    private var isZoomedIn = false

    private lateinit var userSubscriptionManager: UserSubscriptionManager // Add this


    // Flag to prevent multiple concurrent preview generations
    private var isGeneratingPreview = false

    private var hasUnsavedChanges = false

    // Add a flag to track if we're currently loading settings
    private var isLoadingSettings = false

    private val getLogoContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { originalUri ->
                // Copy the image to app storage
                val copiedUri = copyImageToAppStorage(requireContext(), originalUri)

                copiedUri?.let {
                    logoUri = it
                    binding.logoPreviewLayout.visibility = View.VISIBLE
                    binding.logoImageView.setImageURI(it)

                    // Save the logo URI to settings
                    pdfSettings?.logoUri = it.toString()
                    hasUnsavedChanges = true
                    updatePdfPreview()
                } ?: run {
                    // Show error if copying failed
                    Toast.makeText(
                        requireContext(),
                        "Failed to process selected image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val getWatermarkContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { originalUri ->

                val copiedUri = copyImageToAppStorage(requireContext(), originalUri)

                copiedUri?.let {
                    watermarkUri = it
                    binding.watermarkPreviewLayout.visibility = View.VISIBLE
                    binding.watermarkImageView.setImageURI(it)

                    // Save the watermark URI to settings
                    pdfSettings?.watermarkUri = it.toString()
                    hasUnsavedChanges = true
                    updatePdfPreview()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoicePdfSettingsBinding.inflate(inflater, container, false)

        binding.topAppBar.overflowIcon =
            ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)

        userSubscriptionManager = UserSubscriptionManager(requireContext()) // Initialize
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize managers
        pdfSettingsManager = PdfSettingsManager(requireContext())
        ShopManager.initialize(requireContext())

        // Initialize PDF viewer
        pdfView = binding.pdfView

        // Setup toolbar
        setupToolbar()

        // Setup UI components before loading settings
        setupBrandingSection()
        setupContentSettings()
        setupBackHandler()
        setupColorPickers()

        // Load existing settings
        loadSettings()

        // Generate initial preview
        updatePdfPreview()
        checkForPremiumStatus()
    }

    private fun setupBrandingSection() {
        // Logo section
        setupLogoSection()

        // Watermark section
        setupWatermarkSection()

        // Signature section
        setupSignatureSection()
    }
    private fun setupLogoSection() {
        // Logo toggle switch
        binding.showLogoSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateLogoVisibility(isChecked)
            if (!isLoadingSettings) {
                pdfSettings?.showLogo = isChecked
                hasUnsavedChanges = true
                updatePdfPreview()
            }

            if (isChecked) {
                binding.showLogoSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.my_light_primary))
                binding.showLogoSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.my_light_primary_container))
            } else {
                binding.showLogoSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.material_gray))
                binding.showLogoSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.material_gray_light))
            }
        }

        // Upload logo button
        binding.uploadLogoButton.setOnClickListener {
            getLogoContent.launch("image/*")
        }

        // Replace logo button
        binding.replaceLogoButton.setOnClickListener {
            getLogoContent.launch("image/*")
        }

        // Delete logo button
        binding.deleteLogoButton.setOnClickListener {
            showDeleteConfirmationDialog("logo") {
                logoUri = null
                pdfSettings?.logoUri = null
                updateLogoVisibility(binding.showLogoSwitch.isChecked)
                hasUnsavedChanges = true
                updatePdfPreview()
            }
        }
    }

    private fun updateLogoVisibility(isEnabled: Boolean) {
       if (logoUri != null) {
            // If logo exists
            binding.uploadLogoButton.visibility = View.GONE
            binding.logoPreviewLayout.visibility = if (isEnabled) View.VISIBLE else View.GONE
        } else {
            // If no logo exists
            binding.uploadLogoButton.visibility = if (isEnabled) View.VISIBLE else View.GONE
            binding.logoPreviewLayout.visibility = View.GONE
        }
    }

    private fun setupWatermarkSection() {
        // Watermark toggle switch
        binding.showWatermarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateWatermarkVisibility(isChecked)
            if (!isLoadingSettings) {
                pdfSettings?.showWatermark = isChecked
                hasUnsavedChanges = true
                updatePdfPreview()
            }

            if (isChecked) {
                binding.showWatermarkSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.my_light_primary))
                binding.showWatermarkSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.my_light_primary_container))
            } else {
                binding.showWatermarkSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.material_gray))
                binding.showWatermarkSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.material_gray_light))
            }
        }

        // Upload watermark button
        binding.uploadWatermarkButton.setOnClickListener {
            getWatermarkContent.launch("image/*")
        }

        // Replace watermark button
        binding.replaceWatermarkButton.setOnClickListener {
            getWatermarkContent.launch("image/*")
        }

        // Delete watermark button
        binding.deleteWatermarkButton.setOnClickListener {
            showDeleteConfirmationDialog("watermark") {
                watermarkUri = null
                pdfSettings?.watermarkUri = null
                updateWatermarkVisibility(binding.showWatermarkSwitch.isChecked)
                hasUnsavedChanges = true
                updatePdfPreview()
            }
        }
    }

    private fun updateWatermarkVisibility(isEnabled: Boolean) {
        if (watermarkUri != null) {
            // If watermark exists
            binding.uploadWatermarkButton.visibility = View.GONE
            binding.watermarkPreviewLayout.visibility = if (isEnabled) View.VISIBLE else View.GONE
        } else {
            // If no watermark exists
            binding.uploadWatermarkButton.visibility = if (isEnabled) View.VISIBLE else View.GONE
            binding.watermarkPreviewLayout.visibility = View.GONE
        }
    }

    private fun setupSignatureSection() {
        // Signature toggle switch
        binding.showSignatureSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateSignatureVisibility(isChecked)
            if (!isLoadingSettings) {
                pdfSettings?.showSignature = isChecked
                hasUnsavedChanges = true
                updatePdfPreview()
            }

            if (isChecked) {
                binding.showSignatureSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.my_light_primary))
                binding.showSignatureSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.my_light_primary_container))
            } else {
                binding.showSignatureSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.material_gray))
                binding.showSignatureSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.material_gray_light))
            }
        }

        // Upload signature button
        binding.uploadSignatureButton.setOnClickListener {
            showSignatureDialog()
        }

        // Recreate signature button
        binding.recreateSignatureButton.setOnClickListener {
            showSignatureDialog()
        }

        // Delete signature button
        binding.deleteSignatureButton.setOnClickListener {
            showDeleteConfirmationDialog("signature") {
                signatureUri = null
                pdfSettings?.signatureUri = null
                updateSignatureVisibility(binding.showSignatureSwitch.isChecked)
                hasUnsavedChanges = true
                updatePdfPreview()
            }
        }
    }

    private fun updateSignatureVisibility(isEnabled: Boolean) {
        if (signatureUri != null) {
            // If signature exists
            binding.uploadSignatureButton.visibility = View.GONE
            binding.signaturePreviewLayout.visibility = if (isEnabled) View.VISIBLE else View.GONE
        } else {
            // If no signature exists
            binding.uploadSignatureButton.visibility = if (isEnabled) View.VISIBLE else View.GONE
            binding.signaturePreviewLayout.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmationDialog(itemType: String, onConfirm: () -> Unit) {
        val capitalizedItemType = itemType.capitalize(Locale.getDefault())

        AlertDialog.Builder(requireContext())
            .setTitle("Delete $capitalizedItemType")
            .setMessage("Are you sure you want to delete this $itemType?")
            .setPositiveButton("Delete") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupContentSettings() {
        // QR Code toggle
        binding.showQrCodeSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.upiIdInputLayout.isEnabled = isChecked
            if (!isLoadingSettings) {
                pdfSettings?.showQrCode = isChecked
                hasUnsavedChanges = true
                updatePdfPreview()
            }

            if (isChecked) {
                binding.showQrCodeSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.my_light_primary))
                binding.showQrCodeSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.my_light_primary_container))
            } else {
                binding.showQrCodeSwitch.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.material_gray))
                binding.showQrCodeSwitch.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.material_gray_light))
            }

        }

        // Setup text input fields with debounced preview updates
        setupDebouncedTextListener(binding.upiIdEditText) { text ->
            pdfSettings?.upiId = text
            hasUnsavedChanges = true
            updatePdfPreview()
        }

        setupDebouncedTextListener(binding.termsEditText) { text ->
            pdfSettings?.termsAndConditions = text
            hasUnsavedChanges = true
            updatePdfPreview()
        }
    }


    private fun setupDebouncedTextListener(editText: TextInputEditText, onTextChanged: (String) -> Unit) {
        var debounceJob: Job? = null

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isLoadingSettings) return

                hasUnsavedChanges = true

                // Cancel previous debounce job
                debounceJob?.cancel()

                // Create new debounce job with 500ms delay
                debounceJob = lifecycleScope.launch {
                    delay(500) // 500ms debounce delay
                    onTextChanged(s.toString())
                }
            }
        })
    }



    private fun setupBackHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (hasUnsavedChanges) {
                        showUnsavedChangesDialog()
                    } else {
                        findNavController().navigateUp()
                    }
                }
            }
        )
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to save them before leaving?")
            .setPositiveButton("Save") { _, _ ->
                saveSettings()
                findNavController().navigateUp()
            }
            .setNegativeButton("Discard") { _, _ ->
                findNavController().navigateUp()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    override fun onSignatureCaptured(signatureUri: Uri) {
        this.signatureUri = signatureUri
        binding.signatureImageView.setImageURI(signatureUri)

        // Update UI to show the signature
        updateSignatureVisibility(binding.showSignatureSwitch.isChecked)

        // Save the signature URI to settings
        pdfSettings?.signatureUri = signatureUri.toString()
        hasUnsavedChanges = true
        updatePdfPreview()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            isLoadingSettings = true

            try {
                // Load PDF settings
                pdfSettings = withContext(Dispatchers.IO) {
                    pdfSettingsManager.loadSettings()
                }

                // Apply settings to UI
                pdfSettings?.let { settings ->
                    // Apply switch settings
                    binding.showLogoSwitch.isChecked = settings.showLogo
                    binding.showWatermarkSwitch.isChecked = settings.showWatermark
                    binding.showQrCodeSwitch.isChecked = settings.showQrCode
                    binding.showSignatureSwitch.isChecked = settings.showSignature

                    // Apply text settings
                    binding.termsEditText.setText(settings.termsAndConditions)
                    binding.upiIdEditText.setText(settings.upiId)

                    // Load images if available
                    settings.logoUri?.let { uri ->
                        try {
                            logoUri = Uri.parse(uri)
                            binding.logoImageView.setImageURI(logoUri)
                        } catch (e: Exception) {
                            Log.e("InvoicePdfSettings", "Error loading logo: ${e.message}")
                            logoUri = null
                        }
                    }

                    settings.watermarkUri?.let { uri ->
                        try {
                            watermarkUri = Uri.parse(uri)
                            binding.watermarkImageView.setImageURI(watermarkUri)
                        } catch (e: Exception) {
                            Log.e("InvoicePdfSettings", "Error loading watermark: ${e.message}")
                            watermarkUri = null
                        }
                    }

                    settings.signatureUri?.let { uri ->
                        try {
                            signatureUri = Uri.parse(uri)
                            binding.signatureImageView.setImageURI(signatureUri)
                        } catch (e: Exception) {
                            Log.e("InvoicePdfSettings", "Error loading signature: ${e.message}")
                            signatureUri = null
                        }
                    }

                    // Update UI visibility based on loaded settings
                    updateLogoVisibility(settings.showLogo)
                    updateWatermarkVisibility(settings.showWatermark)
                    updateSignatureVisibility(settings.showSignature)
                }
            } catch (e: Exception) {
                Log.e("InvoicePdfSettings", "Error loading settings: ${e.message}")
                Toast.makeText(
                    requireContext(),
                    "Error loading settings: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()

                // Create default settings if loading fails
                pdfSettings = PdfSettings()
            }

            // Reset the flag and unsaved changes tracker after loading
            isLoadingSettings = false
            hasUnsavedChanges = false

            // Generate preview after loading is complete
            updatePdfPreview()
        }
    }

    private fun updatePdfPreview() {
        // Prevent multiple concurrent preview generations
        if (isGeneratingPreview) return
        isGeneratingPreview = true

        binding.previewProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Generate a sample invoice for preview
                val sampleInvoice = createSampleInvoice()

                // Create temporary file for preview
                val pdfFile = withContext(Dispatchers.IO) {
                    // Use the InvoicePdfGenerator with current settings
                    val generator = InvoicePdfGenerator(requireContext())

                    // Apply current settings to generator
                    pdfSettings?.let { settings ->
                        generator.applySettings(settings)
                    }

                    // Get shop details from ShopManager
                    val shopDetails = ShopManager.getShopDetails(requireContext())

                    // Generate PDF with sample data
                    generator.generateInvoicePdf(
                        sampleInvoice,
                        shopDetails,
                        "sample_invoice_preview"
                    )
                }

                // Load PDF into the viewer with enhanced settings
                pdfView.fromFile(pdfFile)
                    .defaultPage(pageNumber)
                    .onPageChange(this@InvoicePdfSettingsFragment)
                    .onLoad(this@InvoicePdfSettingsFragment)
                    .onTap(this@InvoicePdfSettingsFragment)
                    .enableSwipe(true)
                    .swipeHorizontal(true)
                    .enableDoubletap(true)
                    .enableAnnotationRendering(true)
                    .scrollHandle(DefaultScrollHandle(requireContext()))
                    .spacing(10)
                    .autoSpacing(true)
                    .pageFitPolicy(FitPolicy.WIDTH)
                    .pageSnap(true)
                    .pageFling(true)
                    .fitEachPage(true)
                    .enableAntialiasing(true)
                    .nightMode(false)
                    .onError { t ->
                        Log.e("InvoicePdfSettings", "Error loading PDF: ${t.message}")
                        isGeneratingPreview = false
                        binding.previewProgressBar.visibility = View.GONE

                        // Show error toast
                        Toast.makeText(
                            requireContext(),
                            "Error loading PDF preview",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .load()

            } catch (e: Exception) {
                Log.e("InvoicePdfSettings", "Error generating PDF preview: ${e.message}")
                withContext(Dispatchers.Main) {
                    isGeneratingPreview = false
                    binding.previewProgressBar.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Error generating PDF preview: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun loadComplete(nbPages: Int) {
        isGeneratingPreview = false
        binding.previewProgressBar.visibility = View.GONE
    }

    override fun onPageChanged(page: Int, pageCount: Int) {
        pageNumber = page
    }

    override fun onTap(e: MotionEvent?): Boolean {
        // Get the tap position
        val tapX = e?.x ?: return false
        val tapY = e.y

        // Calculate zoom center point based on tap location
        if (isZoomedIn) {
            // Reset zoom
            pdfView.resetZoom()
            isZoomedIn = false
        } else {
            // Zoom to tap location
            pdfView.zoomWithAnimation(
                2.0f, // zoom level
                tapX,  // center X
                tapY   // center Y
            )
            isZoomedIn = true
        }
        return true
    }

    private fun saveSettings() {
        // Show loading indicator
        val loadingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Saving Settings")
            .setMessage("Please wait...")
            .setCancelable(false)
            .show()

        // Save current settings
        pdfSettings?.let { settings ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    pdfSettingsManager.saveSettings(settings)

                    withContext(Dispatchers.Main) {
                        hasUnsavedChanges = false
                        loadingDialog.dismiss()

                        // Show success message
                        Toast.makeText(
                            requireContext(),
                            "Settings saved successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("InvoicePdfSettings", "Error saving settings: ${e.message}")

                    withContext(Dispatchers.Main) {
                        loadingDialog.dismiss()

                        // Show error message
                        Toast.makeText(
                            requireContext(),
                            "Error saving settings: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun resetSettings() {
        // Confirm with user before resetting
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Settings")
            .setMessage("Are you sure you want to reset all invoice PDF settings to defaults?")
            .setPositiveButton("Reset") { _, _ ->
                // Show loading indicator during reset
                val loadingDialog = AlertDialog.Builder(requireContext())
                    .setTitle("Resetting Settings")
                    .setMessage("Please wait...")
                    .setCancelable(false)
                    .show()

                lifecycleScope.launch {
                    try {
                        // Create default settings
                        pdfSettings = PdfSettings()

                        // Save default settings
                        withContext(Dispatchers.IO) {
                            pdfSettingsManager.saveSettings(pdfSettings!!)
                        }

                        // Clear all image references
                        logoUri = null
                        watermarkUri = null
                        signatureUri = null

                        // Reload UI with default settings
                        isLoadingSettings = true

                        // Apply default values to UI components
                        binding.showLogoSwitch.isChecked = pdfSettings!!.showLogo
                        binding.showWatermarkSwitch.isChecked = pdfSettings!!.showWatermark
                        binding.showQrCodeSwitch.isChecked = pdfSettings!!.showQrCode
                        binding.showSignatureSwitch.isChecked = pdfSettings!!.showSignature

                        binding.termsEditText.setText(pdfSettings!!.termsAndConditions)
                        binding.upiIdEditText.setText(pdfSettings!!.upiId)

                        // Update visibility states
                        updateLogoVisibility(pdfSettings!!.showLogo)
                        updateWatermarkVisibility(pdfSettings!!.showWatermark)
                        updateSignatureVisibility(pdfSettings!!.showSignature)

                        isLoadingSettings = false
                        hasUnsavedChanges = false

                        // Dismiss loading dialog
                        loadingDialog.dismiss()

                        // Update preview
                        updatePdfPreview()

                        Toast.makeText(
                            requireContext(),
                            "Settings reset to defaults",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Log.e("InvoicePdfSettings", "Error resetting settings: ${e.message}")

                        withContext(Dispatchers.Main) {
                            loadingDialog.dismiss()

                            Toast.makeText(
                                requireContext(),
                                "Error resetting settings: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Helper function to copy a picked image to app's internal storage with optimization
     */
    private fun copyImageToAppStorage(context: Context, uri: Uri): Uri? {
        try {
            // Create a unique file name
            val fileName = "img_${System.currentTimeMillis()}.jpg"

            // Get the input stream from the uri
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Create a file in your app's private storage
            val outputFile = File(context.filesDir, fileName)

            // Decode the bitmap with options to check size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Get new input stream
            val newInputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Calculate sample size for reasonable image size (max 1000px width/height)
            val maxDimension = 1000
            val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, maxDimension)

            // Decode the bitmap with scaling
            val decodingOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeStream(newInputStream, null, decodingOptions)
            newInputStream.close()

            if (bitmap != null) {
                // Compress and save the image with reasonable quality
                FileOutputStream(outputFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                }

                // Recycle the bitmap
                bitmap.recycle()

                // Return a Uri to the copied file
                return Uri.fromFile(outputFile)
            } else {
                // Fallback to direct copy if bitmap processing failed
                val finalInputStream = context.contentResolver.openInputStream(uri) ?: return null

                finalInputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                return Uri.fromFile(outputFile)
            }
        } catch (e: Exception) {
            Log.e("ImageCopyHelper", "Error copying image: ${e.message}")
            return null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1

        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested maxDimension
            while ((halfWidth / sampleSize) >= maxDimension ||
                (halfHeight / sampleSize) >= maxDimension) {
                sampleSize *= 2
            }
        }

        return sampleSize
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


















    private fun checkForPremiumStatus() {
        lifecycleScope.launch { // Launch coroutine inside the listener
            val isPremium = userSubscriptionManager.isPremiumUser()
            withContext(Dispatchers.Main) { // Switch back to main thread for UI actions
                if (isPremium) {
                    binding.premiumBadge.visibility = View.GONE
                } else {
                    binding.premiumBadge.visibility = View.VISIBLE
                }
            }
        }

    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (hasUnsavedChanges) {
                // If editing notes, ask to save changes
                showUnsavedChangesDialog()
            } else {
                // Normal back behavior
                findNavController().navigateUp()
            }
        }
    }



    private fun setupToolbar() {
        val toolbar: Toolbar = binding.topAppBar


        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save -> {
                    saveSettings()
                    true
                }

                R.id.action_reset -> {
                    resetSettings()
                    true
                }

                else -> false
            }
        }
    }





    private fun setupColorPickers() {
        binding.selectTemplateButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    // Force refresh subscription status before checking
                    userSubscriptionManager.refreshSubscriptionStatus()
                    val isPremium = userSubscriptionManager.isPremiumUser()

                    withContext(Dispatchers.Main) {
                        if (isPremium) {
                            navigateToTemplateSelection()
                        } else {
                            showPremiumFeatureDialog("Advanced invoice templates & colors")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("InvoicePdfSettings", "Error checking premium status: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Error checking subscription status. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // Setup color picker views
        val colorOptions = listOf(
            R.color.my_light_primary,
            R.color.my_light_secondary,
            R.color.status_paid,
            R.color.status_partial,
            R.color.status_unpaid,
            android.R.color.black
        )
    }

    private fun showPremiumFeatureDialog(featureName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("✨ Unlock Premium ✨")
            .setMessage("Unlock powerful features like '$featureName' by upgrading to Premium!")
            .setPositiveButton("Upgrade Now") { _, _ ->
                startActivity(Intent(requireContext(), UpgradeActivity::class.java))
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    private fun setupSwitches() {
        // Set up toggle switches
        binding.showLogoSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.uploadLogoButton.isEnabled = isChecked
            binding.logoPreviewLayout.visibility =
                if (isChecked && logoUri != null) View.VISIBLE else View.GONE
            pdfSettings?.showLogo = isChecked

            // Only mark as unsaved if this is a user action, not initial loading
            if (!isLoadingSettings) {
                hasUnsavedChanges = true
                updatePdfPreview()
            }
        }

        binding.showWatermarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.uploadWatermarkButton.isEnabled = isChecked
            binding.watermarkPreviewLayout.visibility =
                if (isChecked && watermarkUri != null) View.VISIBLE else View.GONE
            pdfSettings?.showWatermark = isChecked

            // Only mark as unsaved if this is a user action, not initial loading
            if (!isLoadingSettings) {
                hasUnsavedChanges = true
                updatePdfPreview()
            }
        }

        binding.showQrCodeSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.upiIdInputLayout.isEnabled = isChecked
            pdfSettings?.showQrCode = isChecked

            // Only mark as unsaved if this is a user action, not initial loading
            if (!isLoadingSettings) {
                hasUnsavedChanges = true
                updatePdfPreview()
            }
        }

        binding.showSignatureSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.uploadSignatureButton.isEnabled = isChecked
            binding.signaturePreviewLayout.visibility =
                if (isChecked && signatureUri != null) View.VISIBLE else View.GONE
            pdfSettings?.showSignature = isChecked

            // Only mark as unsaved if this is a user action, not initial loading
            if (!isLoadingSettings) {
                hasUnsavedChanges = true
                updatePdfPreview()
            }
        }
    }

    private fun setupButtons() {
        // Set up buttons
        binding.uploadLogoButton.setOnClickListener {
            getLogoContent.launch("image/*")
        }

        binding.uploadWatermarkButton.setOnClickListener {
            getWatermarkContent.launch("image/*")
        }

        binding.uploadSignatureButton.setOnClickListener {
            showSignatureDialog()
        }
    }

    private fun showSignatureDialog() {
        val signatureDialog = SignatureDialogFragment.newInstance()
        signatureDialog.show(childFragmentManager, "signature_dialog")
    }



    private fun navigateToTemplateSelection() {
        val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
        mainNavController.navigate(R.id.action_invoicePdfSettingsFragment_to_templateSelectionFragment)
    }

    private fun setupTextInputs() {
        // Add text change listeners
        binding.termsEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                pdfSettings?.termsAndConditions = s.toString()
                // Only mark as unsaved if this is a user action, not initial loading
                if (!isLoadingSettings) {
                    hasUnsavedChanges = true
                }
                // Not updating preview on every keystroke for performance
            }
        })

        binding.upiIdEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                pdfSettings?.upiId = s.toString()
                // Only mark as unsaved if this is a user action, not initial loading
                if (!isLoadingSettings) {
                    hasUnsavedChanges = true
                }
                // Not updating preview on every keystroke for performance
            }
        })
    }

    private fun createSampleInvoice(): Invoice {
        // Create a new sample invoice since we can't directly call the method
        return Invoice(
            id = "sample",
            invoiceNumber = "2024-001",
            customerId = "cust123",
            customerName = "Rahul Sharma",
            customerPhone = "9876543210",
            customerAddress = "123 Main St, Bangalore, Karnataka",
            invoiceDate = System.currentTimeMillis(),
            items = emptyList(), // Simplified for demonstration
            payments = emptyList(),
            totalAmount = 72600.0,
            paidAmount = 30000.0,
            notes = "Sample invoice for preview"
        )
    }

    // Add this method to refresh premium status when fragment resumes
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                userSubscriptionManager.refreshSubscriptionStatus()
                checkForPremiumStatus() // This will update the premium badge visibility
            } catch (e: Exception) {
                Log.e("InvoicePdfSettings", "Error refreshing subscription status: ${e.message}")
            }
        }
    }

}