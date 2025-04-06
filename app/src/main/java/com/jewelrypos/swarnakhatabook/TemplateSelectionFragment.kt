package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceTemplate
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings
import com.jewelrypos.swarnakhatabook.Enums.TemplateType
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator

import com.jewelrypos.swarnakhatabook.databinding.FragmentTempleteSelectionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TemplateSelectionFragment : Fragment() {

    private var _binding: FragmentTempleteSelectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var pdfSettingsManager: PdfSettingsManager
    private var pdfSettings: PdfSettings? = null
    private var selectedTemplate: InvoiceTemplate? = null

    // Template buttons
    private lateinit var templateButtons: Map<TemplateType, CardView>
    private lateinit var templateLabels: Map<TemplateType, TextView>

    // Color options
    private val colorOptions = listOf(
        R.color.black,
        R.color.green,
        R.color.blue,
        R.color.purple,
        R.color.red,
        R.color.indigo,
        R.color.gold
    )

    private var selectedColorIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTempleteSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize managers
        pdfSettingsManager = PdfSettingsManager(requireContext())
        ShopManager.initialize(requireContext())

        // Setup toolbar
        setupToolbar()

        // Initialize template buttons map
        initializeTemplateButtons()

        // Load settings
        loadSettings()

        // Setup color selection
        setupColorSelection()

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save -> {
                    saveSettings()
                    true
                }
//                R.id.action_notifications -> {
//                    navigateToNotifications()
//                    true
//                }
                else -> false
            }
        }
       
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = binding.topAppBar
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
        toolbar.title = "Theme & Color"
    }

    private fun initializeTemplateButtons() {
        // Associate each template type with its button view
        templateButtons = mapOf(
            TemplateType.SIMPLE to binding.templateSimple,
            TemplateType.STYLISH to binding.templateStylish,
            TemplateType.ADVANCE_GST to binding.templateAdvanceGst,
            TemplateType.ADVANCE_GST_TALLY to binding.templateAdvanceGstTally,
            TemplateType.BILLBOOK to binding.templateBillbook
        )

        templateLabels = mapOf(
            TemplateType.SIMPLE to binding.templateSimpleText,
            TemplateType.STYLISH to binding.templateStylishText,
            TemplateType.ADVANCE_GST to binding.templateAdvanceGstText,
            TemplateType.ADVANCE_GST_TALLY to binding.templateAdvanceGstTallyText,
            TemplateType.BILLBOOK to binding.templateBillbookText
        )

        // Set click listeners for all template buttons
        for ((type, button) in templateButtons) {
            button.setOnClickListener {
                selectTemplate(type)
            }
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            // Show loading
            binding.previewProgressBar.visibility = View.VISIBLE

            // Load PDF settings
            pdfSettings = withContext(Dispatchers.IO) {
                pdfSettingsManager.loadSettings()
            }

            // Find the selected color index
            pdfSettings?.let { settings ->
                colorOptions.forEachIndexed { index, colorRes ->
                    if (colorRes == settings.primaryColorRes) {
                        selectedColorIndex = index
                    }
                }
            }

            // Update color selection UI
            updateColorSelection()

            // Update template selection
            pdfSettings?.let { settings ->
                selectTemplate(settings.templateType, false)
            }

            // Load all available templates
            val templates = PdfSettings.getAvailableTemplates()

            // Mark premium templates
            for (template in templates) {
                if (template.isPremium) {
                    val templateType = template.templateType
                    templateLabels[templateType]?.let { label ->
                        label.text = "${label.text} 👑"
                    }
                }
            }

            // Load PDF preview
            updatePreview()

            // Hide loading
            binding.previewProgressBar.visibility = View.GONE
        }
    }

    private fun selectTemplate(templateType: TemplateType, updatePreview: Boolean = true) {
        // Reset all template buttons
        for (button in templateButtons.values) {
            button.setBackgroundResource(R.drawable.template_button_unselected)
        }

        // Set selected button
        templateButtons[templateType]?.setBackgroundResource(R.drawable.template_button_selected)

        // Update pdf settings
        pdfSettings?.templateType = templateType

        // Update preview if needed
        if (updatePreview) {
            updatePreview()
        }
    }

    private fun setupColorSelection() {
        // Setup color selection buttons
        setupColorButton(binding.colorBlack, colorOptions[0], 0)
        setupColorButton(binding.colorGreen, colorOptions[1], 1)
        setupColorButton(binding.colorBlue, colorOptions[2], 2)
        setupColorButton(binding.colorPurple, colorOptions[3], 3)
        setupColorButton(binding.colorRed, colorOptions[4], 4)
        setupColorButton(binding.colorIndigo, colorOptions[5], 5)
        setupColorButton(binding.colorGold, colorOptions[6], 6)
    }

    private fun setupColorButton(buttonView: View, colorRes: Int, index: Int) {
        // Set button background color
        buttonView.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), colorRes)
        )

        // Setup click listener
        buttonView.setOnClickListener {
            selectedColorIndex = index
            updateColorSelection()
            updateColorSettings()
        }
    }

    private fun updateColorSelection() {
        // Hide all checkmarks
        binding.checkBlack.visibility = View.GONE
        binding.checkGreen.visibility = View.GONE
        binding.checkBlue.visibility = View.GONE
        binding.checkPurple.visibility = View.GONE
        binding.checkRed.visibility = View.GONE
        binding.checkIndigo.visibility = View.GONE
        binding.checkGold.visibility = View.GONE

        // Show selected checkmark
        when (selectedColorIndex) {
            0 -> binding.checkBlack.visibility = View.VISIBLE
            1 -> binding.checkGreen.visibility = View.VISIBLE
            2 -> binding.checkBlue.visibility = View.VISIBLE
            3 -> binding.checkPurple.visibility = View.VISIBLE
            4 -> binding.checkRed.visibility = View.VISIBLE
            5 -> binding.checkIndigo.visibility = View.VISIBLE
            6 -> binding.checkGold.visibility = View.VISIBLE
        }
    }

    private fun updateColorSettings() {
        pdfSettings?.primaryColorRes = colorOptions[selectedColorIndex]
        updatePreview()
    }

    private fun updatePreview() {
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
                    generator.generateInvoicePdf(sampleInvoice, shopDetails, "template_preview")
                }

                // Load PDF into viewer
                binding.pdfView.fromFile(pdfFile)
                    .enableSwipe(true)
                    .swipeHorizontal(false)
                    .enableDoubletap(true)
                    .defaultPage(0)
                    .onError { t ->
                        Log.e("TemplateSelection", "Error loading PDF: ${t.message}")
                        Toast.makeText(
                            requireContext(),
                            "Error loading PDF preview",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onLoad {
                        binding.previewProgressBar.visibility = View.GONE
                    }
                    .load()

            } catch (e: Exception) {
                Log.e("TemplateSelection", "Error generating PDF preview: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.previewProgressBar.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Error generating preview: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                        Toast.makeText(
                            requireContext(),
                            "Template settings saved",
                            Toast.LENGTH_SHORT
                        ).show()
                        requireActivity().onBackPressed()
                    }
                } catch (e: Exception) {
                    Log.e("TemplateSelection", "Error saving settings: ${e.message}")

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

    private fun createSampleInvoice(): Invoice {
        // Create a new sample invoice
        return Invoice(
            id = "sample",
            invoiceNumber = "2024-001",
            customerId = "cust123",
            customerName = "Rakesh Enterprises",
            customerPhone = "9999XXXXXX",
            customerAddress = "2nd Floor, 123 Main Street, Sector 5, Mysore 570001",
            invoiceDate = System.currentTimeMillis(),
            items = emptyList(),
            payments = emptyList(),
            totalAmount = 226713.0,
            paidAmount = 226713.0,
            notes = "Sample invoice for preview"
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}