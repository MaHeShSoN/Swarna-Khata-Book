package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.TemplateAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceTemplate
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings
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
    private lateinit var templateAdapter: TemplateAdapter
    private var selectedTemplate: InvoiceTemplate? = null

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

        // Load settings
        loadSettings()

        // Setup save button
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = binding.topAppBar
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
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

            // Setup template selection
            setupTemplateSelection()

            // Setup color picker
            setupColorPicker()

            // Generate preview with current settings
            updatePreview()

            // Hide loading
            binding.previewProgressBar.visibility = View.GONE
        }
    }

    private fun setupTemplateSelection() {
        val templates = PdfSettings.getAvailableTemplates()

        // Set selected template based on current settings
        selectedTemplate = templates.find { it.templateType == pdfSettings?.templateType }

        // Create template adapter
        templateAdapter = TemplateAdapter(
            templates,
            pdfSettings?.templateType ?: templates.first().templateType
        ) { template ->
            selectedTemplate = template
            updatePreview()
        }

        // Set up RecyclerView
        binding.templateRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = templateAdapter
        }
    }

    private fun setupColorPicker() {
        // Set initial color
        binding.colorPicker.setSelectedColor(pdfSettings?.primaryColorRes ?: R.color.my_light_primary)

        // Set color change listener
        binding.colorPicker.onColorSelected = { colorRes ->
            pdfSettings?.primaryColorRes = colorRes
            updatePreview()
        }
    }

    private fun updatePreview() {
        binding.previewProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Generate a sample invoice for preview
                val sampleInvoice = createSampleInvoice()

                // Apply template type if selected
                selectedTemplate?.let {
                    pdfSettings?.templateType = it.templateType
                }

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
                        Toast.makeText(requireContext(), "Error loading PDF preview", Toast.LENGTH_SHORT).show()
                    }
                    .onLoad {
                        binding.previewProgressBar.visibility = View.GONE
                    }
                    .load()

            } catch (e: Exception) {
                Log.e("TemplateSelection", "Error generating PDF preview: ${e.message}")
                withContext(Dispatchers.Main) {
                    binding.previewProgressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error generating preview: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(requireContext(), "Template settings saved", Toast.LENGTH_SHORT).show()
                        requireActivity().onBackPressed()
                    }
                } catch (e: Exception) {
                    Log.e("TemplateSelection", "Error saving settings: ${e.message}")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error saving settings", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}