package com.jewelrypos.swarnakhatabook.BottomSheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jewelrypos.swarnakhatabook.Adapters.ItemSelectionAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.databinding.BottomsheetitemselectionBinding

// ItemSelectionBottomSheet.kt
class ItemSelectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetitemselectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ItemSelectionAdapter
    private val selectedItems = mutableListOf<SelectedItem>()

    private var listener: OnItemsSelectedListener? = null

    interface OnItemsSelectedListener {
        fun onItemsSelected(items: List<SelectedItem>)
    }

    fun setOnItemsSelectedListener(listener: OnItemsSelectedListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetitemselectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSearchView()
        setupRecyclerView()
        setupButtons()
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchItems(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchItems(newText)
                return true
            }
        })
    }

    private fun searchItems(query: String?) {
        // Implement search functionality here
    }

    private fun setupRecyclerView() {
        adapter = ItemSelectionAdapter(emptyList(), object : ItemSelectionAdapter.OnItemClickListener {
            override fun onItemClick(item: JewelleryItem, isSelected: Boolean) {
                if (isSelected) {
                    selectedItems.add(SelectedItem(item, 1))
                } else {
                    selectedItems.removeIf { it.item.id == item.id }
                }
                updateSelectedCount()
            }

            override fun onQuantityChanged(item: JewelleryItem, quantity: Int) {
                selectedItems.find { it.item.id == item.id }?.quantity = quantity
            }
        })

        binding.recyclerViewItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ItemSelectionBottomSheet.adapter
        }
    }

    private fun updateSelectedCount() {
        val count = selectedItems.size
        binding.selectedItemsCount.text = "$count items selected"
        binding.confirmButton.isEnabled = count > 0
    }

    private fun setupButtons() {
        binding.createNewItemButton.setOnClickListener {
            showNewItemBottomSheet()
        }

        binding.confirmButton.setOnClickListener {
            listener?.onItemsSelected(selectedItems)
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun showNewItemBottomSheet() {
        val newItemSheet = ModifiedItemBottomSheetFragment.newInstance()
        newItemSheet.setOnItemAddedListener(object : ItemBottomSheetFragment.OnItemAddedListener {
            override fun onItemAdded(item: JewelleryItem) {
                // Add the newly created item to selected items
                selectedItems.add(SelectedItem(item, 1))
                updateSelectedCount()

                // Refresh the recyclerview
                loadInventoryItems()
            }

            // Add this method if the interface requires it
            override fun onItemUpdated(item: JewelleryItem) {
                // Not needed for this implementation
            }
        })
        newItemSheet.show(parentFragmentManager, "NewItemBottomSheet")
    }

    private fun loadInventoryItems() {
        // Implement loading inventory items from repository
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): ItemSelectionBottomSheet {
            return ItemSelectionBottomSheet()
        }
    }

    data class SelectedItem(
        val item: JewelleryItem,
        var quantity: Int
    )
}