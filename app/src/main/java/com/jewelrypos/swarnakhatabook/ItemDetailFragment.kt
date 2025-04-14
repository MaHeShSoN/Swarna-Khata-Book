package com.jewelrypos.swarnakhatabook

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemSelectionBottomSheet
import com.jewelrypos.swarnakhatabook.DataClasses.ItemUsageStats
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Factorys.ItemDetailViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.ViewModle.ItemDetailViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentItemDetailBinding
import java.text.DecimalFormat

class ItemDetailFragment : Fragment() {

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    private val args: ItemDetailFragmentArgs by navArgs()

    private val viewModel: ItemDetailViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        ItemDetailViewModelFactory(repository, connectivityManager)
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

        binding.topAppBar.overflowIcon = ResourcesCompat.getDrawable(resources, R.drawable.entypo__dots_three_vertical, null)

        // Load item data based on passed ID
        viewModel.loadItem(args.itemId)
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
        // Format currency values
        val formatter = DecimalFormat("#,##,##0.00")

        // Update toolbar title with item name
        binding.topAppBar.title = item.displayName

        // Basic item information
        binding.itemCodeValue.text = item.jewelryCode
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

        // Stock information
        binding.currentStockValue.text = "${item.stock} ${item.stockUnit}"
        binding.stockUnitText.text = item.stockUnit
        binding.stockAdjustmentValue.text = "0"
        updateStockStatusIndicator(item.stock)

        // Location information
        binding.locationValue.text = if (item.location.isNotEmpty()) item.location else "Not specified"

        // Price information
        if (item.metalRate > 0) {
            binding.goldRateValue.text = "₹${formatter.format(item.metalRate)}/g (on ${item.metalRateOn})"
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

            val nameTextView = chargeView.findViewById<android.widget.TextView>(R.id.extraChargeNameText)
            val amountTextView = chargeView.findViewById<android.widget.TextView>(R.id.extraChargeAmountText)

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
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            binding.lastSoldDateValue.text = dateFormat.format(java.util.Date(stats.lastSoldDate))
        } else {
            binding.lastSoldDateValue.text = "Never sold"
        }

        // Top customer
        if (stats.topCustomerName.isNotEmpty()) {
            binding.topCustomerValue.text = "${stats.topCustomerName} (${stats.topCustomerQuantity} units)"
        } else {
            binding.topCustomerValue.text = "N/A"
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
        // Stock adjustment buttons
        binding.decreaseStockButton.setOnClickListener {
            adjustStockValue(-1)
        }

        binding.increaseStockButton.setOnClickListener {
            adjustStockValue(1)
        }

        // Apply stock adjustment button
        binding.applyStockButton.setOnClickListener {
            applyStockAdjustment()
        }
    }

    private fun adjustStockValue(change: Int) {
        val currentValue = binding.stockAdjustmentValue.text.toString().toIntOrNull() ?: 0
        val newValue = currentValue + change
        binding.stockAdjustmentValue.text = newValue.toString()

        // Update button states
        updateAdjustmentButtonsState(newValue)
    }

    private fun updateAdjustmentButtonsState(adjustment: Int) {
        // Current stock from the viewModel
        val currentStock = viewModel.jewelryItem.value?.stock ?: 0.0

        // Enable/disable decrease button based on potential final stock value
        binding.decreaseStockButton.isEnabled = adjustment > -currentStock.toInt()

        // Update the preview of the adjustment result
        val finalStock = currentStock + adjustment
        binding.finalStockValue.text = "Final: $finalStock ${viewModel.jewelryItem.value?.stockUnit ?: ""}"

        // Show/hide apply button
        binding.applyStockButton.visibility = if (adjustment != 0) View.VISIBLE else View.GONE
    }

    private fun applyStockAdjustment() {
        val adjustment = binding.stockAdjustmentValue.text.toString().toIntOrNull() ?: 0

        if (adjustment == 0) {
            Toast.makeText(context, "No adjustment to apply", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.updateStock(adjustment) { success ->
            if (success) {
                Toast.makeText(context, "Stock updated successfully", Toast.LENGTH_SHORT).show()
                binding.stockAdjustmentValue.text = "0"
                binding.applyStockButton.visibility = View.GONE
                EventBus.postInvoiceUpdated()
            } else {
                Toast.makeText(context, "Failed to update stock", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun editItem() {
        viewModel.jewelryItem.value?.let { item ->
            val bottomSheet = ItemSelectionBottomSheet.newInstance()
            bottomSheet.setItemForEdit(item)

            bottomSheet.setOnItemSelectedListener(object : ItemSelectionBottomSheet.OnItemSelectedListener {
                override fun onItemSelected(newItem: JewelleryItem, price: Double) {
                    // This won't be called during editing
                }

                override fun onItemUpdated(updatedItem: JewelleryItem, price: Double) {
                    viewModel.updateItem(updatedItem) { success ->
                        if (success) {
                            EventBus.postInventoryUpdated()
                            Toast.makeText(context, "Item updated successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to update item", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Failed to move item to recycling bin", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}