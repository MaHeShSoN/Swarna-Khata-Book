package com.jewelrypos.swarnakhatabook.BottomSheet

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.ItemSelectionAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Factorys.InventoryViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.databinding.BottomsheetitemselectionBinding

class ItemSelectionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetitemselectionBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ItemSelectionAdapter
    private val selectedItems = mutableListOf<SelectedItem>()

    private var listener: OnItemsSelectedListener? = null

    // ViewModel for accessing inventory data
    private val inventoryViewModel: InventoryViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        InventoryViewModelFactory(repository, connectivityManager)
    }

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
        setupObservers()

        // Load inventory items
        inventoryViewModel.refreshData()
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
        inventoryViewModel.searchItems(query ?: "")
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

            // Add scroll listener for pagination
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    // Load more items when user approaches the end of the list
                    if (!inventoryViewModel.isLoading.value!! &&
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 &&
                        firstVisibleItemPosition >= 0
                    ) {
                        inventoryViewModel.loadNextPage()
                    }
                }
            })
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

    private fun setupObservers() {
        inventoryViewModel.jewelleryItems.observe(viewLifecycleOwner) { items ->
            adapter.updateItems(items)
            updateEmptyState(items.isEmpty())
        }

        inventoryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        inventoryViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerViewItems.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerViewItems.visibility = View.VISIBLE
        }
    }

    private fun showNewItemBottomSheet() {
        val newItemSheet = ModifiedItemBottomSheetFragment.newInstance()
        newItemSheet.setOnItemAddedListener(object : ItemBottomSheetFragment.OnItemAddedListener {
            override fun onItemAdded(item: JewelleryItem) {
                // Add the newly created item to inventory
                inventoryViewModel.addJewelleryItem(item)

                // Add the newly created item to selected items
                selectedItems.add(SelectedItem(item, 1))
                updateSelectedCount()
            }

            override fun onItemUpdated(item: JewelleryItem) {
                // Update the item in inventory
                inventoryViewModel.updateJewelleryItem(item)

                // Update in selected items if it exists
                val selected = selectedItems.find { it.item.id == item.id }
                if (selected != null) {
                    // Keep the quantity but update the item details
                    val quantity = selected.quantity
                    selectedItems.removeIf { it.item.id == item.id }
                    selectedItems.add(SelectedItem(item, quantity))
                }
            }
        })
        newItemSheet.show(parentFragmentManager, "NewItemBottomSheet")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ItemSelectionBottomSheet"

        fun newInstance(): ItemSelectionBottomSheet {
            return ItemSelectionBottomSheet()
        }
    }

    data class SelectedItem(
        val item: JewelleryItem,
        var quantity: Int
    )
}