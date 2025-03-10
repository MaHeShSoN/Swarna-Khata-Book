package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemBottomSheetFragment
import com.jewelrypos.swarnakhatabook.BottomSheet.JewelleryItem
import com.jewelrypos.swarnakhatabook.databinding.FragmentInventoryBinding

class InventoryFragment : Fragment(), ItemBottomSheetFragment.OnItemAddedListener {




    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentInventoryBinding.inflate(inflater,container,false)

        binding.addItemFab.setOnClickListener {
            addItemButton()
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    private fun addItemButton() {
        //make a bottom sheet that appear and show inputs

    }

    override fun onItemAdded(item: JewelleryItem) {
        // Add the new item to the list
        jewelleryItemsList.add(item)

        // Notify the adapter about the new item
        jewelleryAdapter.notifyItemInserted(jewelleryItemsList.size - 1)

        // Scroll to the newly added item
        binding.recyclerViewInventory.smoothScrollToPosition(jewelleryItemsList.size - 1)

        // Show confirmation toast
        Toast.makeText(
            context,
            "${item.displayName} added successfully",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showBottomSheetDialog() {
        val bottomSheetFragment = ItemBottomSheetFragment.newInstance()
        bottomSheetFragment.setOnItemAddedListener(this)
        bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
    }


}