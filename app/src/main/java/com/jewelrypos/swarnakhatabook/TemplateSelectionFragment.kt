package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceTemplate
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings
import com.jewelrypos.swarnakhatabook.Enums.TemplateType
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.PdfSettingsManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.InvoicePdfGenerator
import com.jewelrypos.swarnakhatabook.Utilitys.PremiumFeatureHelper
import android.content.Intent
import com.jewelrypos.swarnakhatabook.DataClasses.ExtraCharge
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.Payment

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
    private var hasUnsavedChanges = false

    // Template buttons
    private lateinit var templateButtons: Map<TemplateType, CardView>
    private lateinit var templateLabels: Map<TemplateType, TextView>

    private var isLoadingSettings = false

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

    // Track premium status
    private var isPremiumUser = false
    private var selectedColorIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTempleteSelectionBinding.inflate(inflater, container, false)

        binding.topAppBar.overflowIcon =
            ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)

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

        // Check premium status
        checkPremiumStatus()

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
                else -> false
            }
        }

        // Add the callback
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback
        )
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
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes to your template settings. Do you want to save them before leaving?")
            .setPositiveButton("Save") { _, _ ->
                saveSettings()
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
        toolbar.title = "Theme & Color"
    }

    private fun initializeTemplateButtons() {
        // Associate each template type with its button view
        templateButtons = mapOf(
            TemplateType.SIMPLE to binding.templateSimple,
            TemplateType.STYLISH to binding.templateStylish,
            TemplateType.GST_TALLY to binding.templateAdvanceGstTally,
            TemplateType.MODERN_MINIMAL to binding.templateModernMinimal,
        )

        templateLabels = mapOf(
            TemplateType.SIMPLE to binding.templateSimpleText,
            TemplateType.STYLISH to binding.templateStylishText,
            TemplateType.GST_TALLY to binding.templateAdvanceGstTallyText,
            TemplateType.MODERN_MINIMAL to binding.templateModernMinimalText,
        )

        // Set click listeners for all template buttons
        for ((type, button) in templateButtons) {
            button.setOnClickListener {
                selectTemplate(type)
            }
        }
    }

    private fun checkPremiumStatus() {
        // Use UserSubscriptionManager to check premium status
        lifecycleScope.launch {
            try {
                // Show loading if needed
                val subscriptionManager = com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager(requireContext())
                
                // Use IO dispatcher for the database operation
                val premium = withContext(Dispatchers.IO) {
                    subscriptionManager.isPremiumUser()
                }
                
                // Update UI on main thread
                isPremiumUser = premium
                
                // We're hiding the premiumBanner as requested, showing badges instead
                binding.premiumBanner?.visibility = View.GONE
                
                // Update UI with premium badges
                updatePremiumTemplateAccess()
                
            } catch (e: Exception) {
                Log.e("TemplateSelection", "Error checking premium status: ${e.message}")
                // Default to non-premium if there's an error
                isPremiumUser = false
            }
        }
    }

    private fun updatePremiumTemplateAccess() {
        // Add premium badges to premium template options but don't block selection
        for ((type, _) in templateButtons) {
            val template = PdfSettings.getAvailableTemplates().find { it.templateType == type }

            if (template != null && template.isPremium) {
                val label = templateLabels[type]
                // Only add premium badge if not already present
                if (label != null && !label.text.toString().contains("👑")) {
                    label.text = "${label.text} 👑"
                }
            }
        }
    }

    private fun showPremiumTemplateDialog() {
        PremiumFeatureHelper.showPremiumFeatureDialog(
            requireContext(),
            "Advanced invoice templates & color themes"
        )
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            // Show loading
            isLoadingSettings = true // Set flag before loading
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

            // Mark premium templates - this is now handled in updatePremiumTemplateAccess()
            // We're keeping this comment for clarity

            // Load PDF preview
            updatePreview()

            // Hide loading
            binding.previewProgressBar.visibility = View.GONE

            isLoadingSettings = false // Reset flag
            hasUnsavedChanges = false
        }
    }

    private fun selectTemplate(templateType: TemplateType, updatePreview: Boolean = true) {
        // Allow selecting any template, including premium ones
        // Premium check will happen at save time

        // Reset all template buttons
        for (button in templateButtons.values) {
            button.setBackgroundResource(R.drawable.template_button_unselected)
        }
        if (!isLoadingSettings) {
            hasUnsavedChanges = true
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
        if (!isLoadingSettings) {
            hasUnsavedChanges = true
        }
    }

    private fun updateColorSettings() {
        pdfSettings?.primaryColorRes = colorOptions[selectedColorIndex]
        updatePreview()
        // Only mark as unsaved if not during initial load
        if (!isLoadingSettings) {
            hasUnsavedChanges = true
        }
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

    private fun isPremiumTemplate(templateType: TemplateType): Boolean {
        return when (templateType) {
            TemplateType.SIMPLE -> false
            TemplateType.STYLISH -> true
            TemplateType.GST_TALLY -> true
            TemplateType.MODERN_MINIMAL -> true
            TemplateType.LUXURY_BOUTIQUE -> true
            // Add other template types here if needed
        }
    }

    private fun saveSettings() {
        // Check if user is trying to save a premium template without being a premium user
        val currentTemplateType = pdfSettings?.templateType
        if (currentTemplateType != null && isPremiumTemplate(currentTemplateType) && !isPremiumUser) {
            showPremiumTemplateDialog()
            return
        }

        // Save current settings
        pdfSettings?.let { settings ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    pdfSettingsManager.saveSettings(settings)

                    withContext(Dispatchers.Main) {
                        hasUnsavedChanges = false
                        Toast.makeText(
                            requireContext(),
                            "Template settings saved",
                            Toast.LENGTH_SHORT
                        ).show()
                        findNavController().navigateUp()
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
        val jewelleryItem_01 = JewelleryItem(
            id = "Ring",
            grossWeight = 11.667,
            netWeight = 10.0,
            jewelryCode = "4832",
            category = "Ring",
            wastage = 5.0,
            displayName = "Ring",
            wastageType = "Gram",
            purity = "91.667",
            makingCharges = 3550.0,
            makingChargesType = "FIX",
            taxRate = 3.0,
            itemType = "Gold",
            diamondPrice = 1200.0,
            metalRate = 9000.0,
            totalTax = 374.0,
            listOfExtraCharges = listOf(
                ExtraCharge(
                    id = "powai",
                    name = "BEADWORK",
                    amount = 500.0
                )
            )
        )
        val jewelleryItem_02 = JewelleryItem(
            id = "Aad",
            grossWeight = 110.667,
            netWeight = 100.0,
            jewelryCode = "4832",
            category = "Aad",
            wastage = 5.0,
            displayName = "Aad",
            wastageType = "Gram",
            purity = "91.667",
            makingCharges = 35500.0,
            makingChargesType = "FIX",
            taxRate = 3.0,
            itemType = "Silver",
            diamondPrice = 1200.0,
            metalRate = 9000.0,
            totalTax = 3704.0,
            listOfExtraCharges = listOf(
                ExtraCharge(
                    id = "powai",
                    name = "BEADWORK",
                    amount = 500.0
                )
            )
        )

        // Create InvoiceItems from JewelleryItems
        val item1 = InvoiceItem(
            id = "inv_item_1",
            itemDetails = jewelleryItem_01,
            quantity = 1,
            price = 94624.0,  // (Metal value + making charges + diamond price + extra charges)
        )
        
        val item2 = InvoiceItem(
            id = "inv_item_2",
            itemDetails = jewelleryItem_02,
            quantity = 1,
            price = 132089.0,  // (Metal value + making charges + diamond price + extra charges)
        )

        val items: List<InvoiceItem> = listOf(item1, item2)
        
        // Add a sample payment
        val payment = Payment(
            id = "pay_01",
            method = "Cash",
            amount = 226713.0,
            reference = "Cash payment"
        )

        // Create a new sample invoice with the items and payment
        return Invoice(
            id = "sample",
            invoiceNumber = "2024-001",
            customerId = "cust123",
            customerName = "Rakesh Enterprises",
            customerPhone = "9999XXXXXX",
            customerAddress = "2nd Floor, 123 Main Street, Sector 5, Mysore 570001",
            invoiceDate = System.currentTimeMillis(),
            items = items,
            payments = listOf(payment),
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