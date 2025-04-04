package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.github.barteksc.pdfviewer.PDFView
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.jewelrypos.swarnakhatabook.databinding.FragmentInvoicePdfSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class InvoicePdfSettingsFragment : Fragment(), SignatureDialogFragment.OnSignatureCapturedListener {

    private var _binding: FragmentInvoicePdfSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var pdfView: PDFView
    private lateinit var pdfSettingsManager: PdfSettingsManager
    private var pdfSettings: PdfSettings? = null

    private var logoUri: Uri? = null
    private var watermarkUri: Uri? = null
    private var signatureUri: Uri? = null

    private val getLogoContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            logoUri = it
            binding.logoPreviewLayout.visibility = View.VISIBLE
            binding.logoImageView.setImageURI(it)

            // Save the logo URI to settings
            pdfSettings?.logoUri = it.toString()
            updatePdfPreview()
        }
    }

    private val getWatermarkContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            watermarkUri = it
            binding.watermarkPreviewLayout.visibility = View.VISIBLE
            binding.watermarkImageView.setImageURI(it)

            // Save the watermark URI to settings
            pdfSettings?.watermarkUri = it.toString()
            updatePdfPreview()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInvoicePdfSettingsBinding.inflate(inflater, container, false)
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

        // Load existing settings
        loadSettings()

        // Setup UI components
        setupColorPickers()
        setupSwitches()
        setupButtons()
        setupTextInputs()

        // Generate initial preview
        updatePdfPreview()
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
            // Load PDF settings
            pdfSettings = withContext(Dispatchers.IO) {
                pdfSettingsManager.loadSettings()
            }

            // Apply settings to UI
            pdfSettings?.let { settings ->
                // Apply color settings
                binding.primaryColorPicker.setBackgroundColor(ContextCompat.getColor(requireContext(), settings.primaryColorRes))
                binding.secondaryColorPicker.setBackgroundColor(ContextCompat.getColor(requireContext(), settings.secondaryColorRes))

                // Apply switch settings
                binding.showLogoSwitch.isChecked = settings.showLogo
                binding.showWatermarkSwitch.isChecked = settings.showWatermark
                binding.showQrCodeSwitch.isChecked = settings.showQrCode
                binding.showSignatureSwitch.isChecked = settings.showSignature

                // Apply text settings
                binding.termsEditText.setText(settings.termsAndConditions)
                binding.invoicePrefixEditText.setText(settings.invoicePrefix)
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
            }
        }
    }

    private fun setupColorPickers() {
        // New template selection button
        binding.selectTemplateButton.setOnClickListener {
            navigateToTemplateSelection()
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

        binding.primaryColorPicker.setOnClickListener {
            showColorPickerDialog(colorOptions) { colorRes ->
                binding.primaryColorPicker.setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
                pdfSettings?.primaryColorRes = colorRes
                updatePdfPreview()
            }
        }

        binding.secondaryColorPicker.setOnClickListener {
            showColorPickerDialog(colorOptions) { colorRes ->
                binding.secondaryColorPicker.setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
                pdfSettings?.secondaryColorRes = colorRes
                updatePdfPreview()
            }
        }
    }

    private fun setupSwitches() {
        // Set up toggle switches
        binding.showLogoSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.uploadLogoButton.isEnabled = isChecked
            binding.logoPreviewLayout.visibility = if (isChecked && logoUri != null) View.VISIBLE else View.GONE
            pdfSettings?.showLogo = isChecked
            updatePdfPreview()
        }

        binding.showWatermarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.uploadWatermarkButton.isEnabled = isChecked
            binding.watermarkPreviewLayout.visibility = if (isChecked && watermarkUri != null) View.VISIBLE else View.GONE
            pdfSettings?.showWatermark = isChecked
            updatePdfPreview()
        }

        binding.showQrCodeSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.upiIdInputLayout.isEnabled = isChecked
            pdfSettings?.showQrCode = isChecked
            updatePdfPreview()
        }

        binding.showSignatureSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.uploadSignatureButton.isEnabled = isChecked
            binding.signaturePreviewLayout.visibility = if (isChecked && signatureUri != null) View.VISIBLE else View.GONE
            pdfSettings?.showSignature = isChecked
            updatePdfPreview()
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

        binding.generatePreviewButton.setOnClickListener {
            updatePdfPreview()
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
                // Not updating preview on every keystroke for performance
            }
        })

        binding.invoicePrefixEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                pdfSettings?.invoicePrefix = s.toString()
                // Not updating preview on every keystroke for performance
            }
        })

        binding.upiIdEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                pdfSettings?.upiId = s.toString()
                // Not updating preview on every keystroke for performance
            }
        })
    }

    private fun showColorPickerDialog(colorOptions: List<Int>, onColorSelected: (Int) -> Unit) {
        // Create a simple dialog with color options
        val colorNames = colorOptions.map {
            resources.getResourceEntryName(it).replace("_", " ").capitalize()
        }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Color")
            .setItems(colorNames) { _, which ->
                onColorSelected(colorOptions[which])
            }
            .show()
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
                    generator.generateInvoicePdf(sampleInvoice, shopDetails, "sample_invoice_preview")
                }

                // Load PDF into the viewer
                pdfView.fromFile(pdfFile)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .onError { t ->
                        Log.e("InvoicePdfSettings", "Error loading PDF: ${t.message}")
                    }
                    .onLoad {
                        binding.previewProgressBar.visibility = View.GONE
                    }
                    .load()

            } catch (e: Exception) {
                Log.e("InvoicePdfSettings", "Error generating PDF preview: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.previewProgressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error generating PDF preview: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveSettings() {
        // Save current settings
        pdfSettings?.let { settings ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    pdfSettingsManager.saveSettings(settings)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Settings saved successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("InvoicePdfSettings", "Error saving settings: ${e.message}")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error saving settings", Toast.LENGTH_SHORT).show()
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

                    Toast.makeText(requireContext(), "Settings reset to defaults", Toast.LENGTH_SHORT).show()
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