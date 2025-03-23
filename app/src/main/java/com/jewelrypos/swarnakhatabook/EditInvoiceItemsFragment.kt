package com.jewelrypos.swarnakhatabook



import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.EditableInvoiceItemAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemSelectionBottomSheet
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Factorys.InvoiceDetailViewModelFactory
import com.jewelrypos.swarnakhatabook.ViewModle.InvoiceDetailViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentEditInvoiceItemsBinding
import java.text.DecimalFormat

class EditInvoiceItemsFragment : Fragment() {

    private var _binding: FragmentEditInvoiceItemsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InvoiceDetailViewModel by viewModels {
        InvoiceDetailViewModelFactory(requireActivity().application)
    }

    private lateinit var adapter: EditableInvoiceItemAdapter
    private var originalItems: List<InvoiceItem> = emptyList()
    private var editedItems: MutableList<InvoiceItem> = mutableListOf()

    // Flag to track if we've shown the preview toast
    private var hasShownPreviewToast = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditInvoiceItemsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()

        // Load invoice data
        val invoiceId = arguments?.getString("invoiceId") ?: return
        viewModel.loadInvoice(invoiceId)

        observeViewModel()
        setupButtons()

        // Handle back button press
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPress()
                }
            }
        )
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            handleBackPress()
        }
    }

    private fun setupRecyclerView() {
        adapter = EditableInvoiceItemAdapter(emptyList())

        adapter.setOnItemActionListener(object : EditableInvoiceItemAdapter.OnItemActionListener {
            override fun onRemoveItem(item: InvoiceItem) {
                removeItem(item)
            }

            override fun onEditItem(item: InvoiceItem) {
                openItemEditor(item)
            }

            override fun onQuantityChanged(item: InvoiceItem, newQuantity: Int) {
                updateItemQuantity(item, newQuantity)
            }
        })

        binding.itemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@EditInvoiceItemsFragment.adapter
        }
    }

    private fun observeViewModel() {
        viewModel.invoice.observe(viewLifecycleOwner) { invoice ->
            invoice?.let {
                originalItems = it.items
                // Clone the items list so we can modify it
                editedItems = originalItems.toMutableList()
                adapter.updateItems(editedItems)

                updateTotals()
                updateEmptyState()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.saveChangesButton.isEnabled = !isLoading && haveItemsChanged()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupButtons() {
        binding.addItemButton.setOnClickListener {
            openItemSelector()
        }

        binding.saveChangesButton.setOnClickListener {
            saveChanges()
        }

        binding.cancelButton.setOnClickListener {
            handleBackPress()
        }
    }

    private fun removeItem(item: InvoiceItem) {
        editedItems.remove(item)
        adapter.updateItems(editedItems)
        updateTotals()
        updateEmptyState()
        showLivePreviewNote()
    }

    private fun openItemEditor(item: InvoiceItem) {
        val bottomSheet = ItemSelectionBottomSheet.newInstance()
        bottomSheet.setItemForEdit(item.itemDetails)

        bottomSheet.setOnItemSelectedListener(object : ItemSelectionBottomSheet.OnItemSelectedListener {
            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
                // This shouldn't happen during editing
            }

            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
                // Find and update the item
                val index = editedItems.indexOfFirst { it.itemId == item.itemId }
                if (index != -1) {
                    editedItems[index] = InvoiceItem(
                        itemId = item.itemId,
                        quantity = item.quantity,
                        itemDetails = updatedItem,
                        price = price
                    )
                    adapter.updateItems(editedItems)
                    updateTotals()
                    showLivePreviewNote()
                }
            }
        })

        bottomSheet.show(parentFragmentManager, "ItemEditorBottomSheet")
    }

    private fun openItemSelector() {
        val bottomSheet = ItemSelectionBottomSheet.newInstance()

        bottomSheet.setOnItemSelectedListener(object : ItemSelectionBottomSheet.OnItemSelectedListener {
            override fun onItemSelected(newItem: JewelleryItem, price: Double) {
                // Add new item with quantity 1
                val newInvoiceItem = InvoiceItem(
                    itemId = newItem.id,
                    quantity = 1,
                    itemDetails = newItem,
                    price = price
                )

                editedItems.add(newInvoiceItem)
                adapter.updateItems(editedItems)
                updateTotals()
                updateEmptyState()
                showLivePreviewNote()
            }

            override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
                // This shouldn't happen during adding a new item
            }
        })

        bottomSheet.show(parentFragmentManager, "ItemSelectorBottomSheet")
    }

    private fun updateItemQuantity(item: InvoiceItem, newQuantity: Int) {
        if (newQuantity <= 0) {
            removeItem(item)
            return
        }

        val index = editedItems.indexOfFirst { it.itemId == item.itemId }
        if (index != -1) {
            editedItems[index] = item.copy(quantity = newQuantity)
            adapter.updateItems(editedItems)
            updateTotals()
            showLivePreviewNote()
        }
    }

    private fun updateTotals() {
        // Calculate subtotal
        val subtotal = editedItems.sumOf { it.price * it.quantity }

        // Format currency
        val formatter = DecimalFormat("#,##,##0.00")
        binding.subtotalValue.text = "₹${formatter.format(subtotal)}"

        // Update item count
        binding.itemCount.text = "${editedItems.size} items"

        // Show save button if changes were made
        val hasChanges = haveItemsChanged()
        binding.saveChangesButton.isEnabled = hasChanges

        // Update the title to show asterisk if there are unsaved changes
        binding.topAppBar.title = if (hasChanges) "Edit Invoice Items*" else "Edit Invoice Items"
    }

    private fun updateEmptyState() {
        if (editedItems.isEmpty()) {
            binding.emptyStateText.visibility = View.VISIBLE
            binding.itemsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateText.visibility = View.GONE
            binding.itemsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showLivePreviewNote() {
        // Show a toast message on first edit
        if (!hasShownPreviewToast && haveItemsChanged()) {
            Toast.makeText(
                requireContext(),
                "Changes are shown in real-time. Remember to save your changes.",
                Toast.LENGTH_SHORT
            ).show()
            hasShownPreviewToast = true
        }

        // Highlight the preview note if there are changes
        binding.livePreviewNote.visibility = if (haveItemsChanged()) View.VISIBLE else View.GONE
    }

    // Method to check if items have changed compared to original
    private fun haveItemsChanged(): Boolean {
        if (originalItems.size != editedItems.size) return true

        // Create a map of original items by ID for easier comparison
        val originalItemsMap = originalItems.associateBy { it.itemId }

        // Check if any item is different from the original
        return editedItems.any { editedItem ->
            val originalItem = originalItemsMap[editedItem.itemId]

            // If original item doesn't exist, this is a new item
            if (originalItem == null) return@any true

            // Compare quantity and price
            editedItem.quantity != originalItem.quantity ||
                    editedItem.price != originalItem.price
        }
    }

    private fun saveChanges() {
        binding.progressBar.visibility = View.VISIBLE
        binding.saveChangesButton.isEnabled = false

        viewModel.updateInvoiceItems(editedItems) { success ->
            binding.progressBar.visibility = View.GONE

            if (success) {
                Toast.makeText(context, "Items updated successfully", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } else {
                Toast.makeText(context, "Failed to update items", Toast.LENGTH_SHORT).show()
                binding.saveChangesButton.isEnabled = haveItemsChanged()
            }
        }
    }

    // Handle back button press with confirmation if there are unsaved changes
    private fun handleBackPress() {
        if (haveItemsChanged()) {
            // Show confirmation dialog
            AlertDialog.Builder(requireContext())
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Do you want to save before leaving?")
                .setPositiveButton("Save") { _, _ ->
                    saveChanges()
                }
                .setNegativeButton("Discard") { _, _ ->
                    // Just navigate back without saving
                    findNavController().navigateUp()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            // No changes, just go back
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
