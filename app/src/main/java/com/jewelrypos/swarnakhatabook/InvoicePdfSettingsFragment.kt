package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoicePdfSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        setupColorPickers()
        setupSwitches()
        setupButtons()
        setupTextInputs()

        // Load existing settings
        loadSettings()

        // Generate initial preview
        updatePdfPreview()
        checkForPremiumStatus()

        // Add the callback
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )
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
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = binding.topAppBar
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

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

    private fun loadSettings() {
        lifecycleScope.launch {
            isLoadingSettings = true

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
                        binding.logoPreviewLayout.visibility = View.VISIBLE
                        binding.logoImageView.setImageURI(logoUri)
                    } catch (e: Exception) {
                        Log.e("InvoicePdfSettings", "Error loading logo: ${e.message}")
                    }
                }

                settings.watermarkUri?.let { uri ->
                    try {
                        watermarkUri = Uri.parse(uri)
                        binding.watermarkPreviewLayout.visibility = View.VISIBLE
                        binding.watermarkImageView.setImageURI(watermarkUri)
                    } catch (e: Exception) {
                        Log.e("InvoicePdfSettings", "Error loading watermark: ${e.message}")
                    }
                }

                settings.signatureUri?.let { uri ->
                    try {
                        signatureUri = Uri.parse(uri)
                        binding.signaturePreviewLayout.visibility = View.VISIBLE
                        binding.signatureImageView.setImageURI(signatureUri)
                    } catch (e: Exception) {
                        Log.e("InvoicePdfSettings", "Error loading signature: ${e.message}")
                    }
                }

                // Generate preview automatically after loading settings
                updatePdfPreview()
            }

            // Reset the flag and unsaved changes tracker after loading
            isLoadingSettings = false
            hasUnsavedChanges = false
        }
    }

    /**
     * Helper function to copy a picked image to app's internal storage
     * @param context The context
     * @param uri The uri from the picker
     * @return A new Uri pointing to the copied file, or null if copying failed
     */
    fun copyImageToAppStorage(context: Context, uri: Uri): Uri? {
        try {
            // Create a unique file name
            val fileName = "img_${System.currentTimeMillis()}.jpg"

            // Get the input stream from the uri
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Create a file in your app's private storage
            val outputFile = File(context.filesDir, fileName)

            // Copy the file
            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Return a Uri to the copied file
            return Uri.fromFile(outputFile)
        } catch (e: Exception) {
            Log.e("ImageCopyHelper", "Error copying image: ${e.message}")
            return null
        }
    }

    private fun setupColorPickers() {
        // New template selection button

        binding.selectTemplateButton.setOnClickListener {
            lifecycleScope.launch { // Launch coroutine inside the listener
                val isPremium = userSubscriptionManager.isPremiumUser()
                withContext(Dispatchers.Main) { // Switch back to main thread for UI actions
                    if (isPremium) {
                        navigateToTemplateSelection()
                    } else {
                        showPremiumFeatureDialog("Advanced invoice templates & colors") // Updated message slightly
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
        ThemedM3Dialog(requireContext()).setTitle("✨ Unlock Premium ✨")
            .setLayout(R.layout.dialog_confirmation) // Assuming you have a simple layout
            .apply {
                findViewById<TextView>(R.id.confirmationMessage)?.text =
                    "Unlock powerful features like '$featureName' by upgrading to Premium!"
            }
            .setPositiveButton("Upgrade Now") { dialog, _ ->
                startActivity(Intent(requireContext(), UpgradeActivity::class.java))
                dialog.dismiss()
            }
            .setNegativeButton("Maybe Later") { dialog ->
                dialog.dismiss()
            }
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

    override fun onSignatureCaptured(signatureUri: Uri) {
        this.signatureUri = signatureUri
        binding.signaturePreviewLayout.visibility = View.VISIBLE
        binding.signatureImageView.setImageURI(signatureUri)

        // Save the signature URI to settings
        pdfSettings?.signatureUri = signatureUri.toString()
        hasUnsavedChanges = true
        updatePdfPreview()
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

                // Load PDF into the viewer with enhanced settings for smooth zooming and scrolling
                pdfView.fromFile(pdfFile)
                    .defaultPage(pageNumber)
                    .onPageChange(this@InvoicePdfSettingsFragment)
                    .onLoad(this@InvoicePdfSettingsFragment)
                    .onTap(this@InvoicePdfSettingsFragment)
                    .enableSwipe(true)
                    .swipeHorizontal(true) // Enable horizontal swiping
                    .enableDoubletap(true) // Enable double tap to zoom
                    .enableAnnotationRendering(true)
                    .scrollHandle(DefaultScrollHandle(requireContext()))
                    .spacing(10) // Add spacing between pages
                    .autoSpacing(true) // Add auto spacing based on screen
                    .pageFitPolicy(FitPolicy.WIDTH) // Fit page to width
                    .pageSnap(true) // Snap pages to screen boundaries
                    .pageFling(true) // Make fling change pages
                    .fitEachPage(true) // Fit each page to the view
                    .enableAntialiasing(true) // Improve rendering quality
                    .nightMode(false) // Disable night mode
                    .onError { t ->
                        Log.e("InvoicePdfSettings", "Error loading PDF: ${t.message}")
                        isGeneratingPreview = false
                        binding.previewProgressBar.visibility = View.GONE
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

    override fun onPageChanged(page: Int, pageCount: Int) {
        pageNumber = page
    }

    override fun loadComplete(nbPages: Int) {
        isGeneratingPreview = false
        binding.previewProgressBar.visibility = View.GONE
    }

    override fun onTap(e: MotionEvent?): Boolean {
        // Toggle between zoomed and normal view on tap
        if (isZoomedIn) {
            pdfView.resetZoom()
            isZoomedIn = false
        } else {
            // You can set a specific zoom level or use a multiplier
            pdfView.zoomTo(2.0f) // Zoom to 2x
            isZoomedIn = true
        }
        return true
    }

    private fun saveSettings() {
        // Save current settings
        pdfSettings?.let { settings ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    pdfSettingsManager.saveSettings(settings)

                    withContext(Dispatchers.Main) {
                        hasUnsavedChanges = false
                        Toast.makeText(
                            requireContext(),
                            "Settings saved successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("InvoicePdfSettings", "Error saving settings: ${e.message}")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Error saving settings",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun resetSettings() {
        // Confirm with user before resetting
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Reset Settings")
            .setMessage("Are you sure you want to reset all invoice PDF settings to defaults?")
            .setPositiveButton("Reset") { _, _ ->
                lifecycleScope.launch {
                    // Create default settings
                    pdfSettings = PdfSettings()

                    // Save default settings
                    withContext(Dispatchers.IO) {
                        pdfSettingsManager.saveSettings(pdfSettings!!)
                    }

                    // Reload UI with default settings
                    loadSettings()

                    // Update preview
                    updatePdfPreview()

                    Toast.makeText(
                        requireContext(),
                        "Settings reset to defaults",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}