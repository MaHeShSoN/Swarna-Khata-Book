package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.ShopDetails
import com.jewelrypos.swarnakhatabook.databinding.ItemShopSelectionBinding

class ShopSelectionAdapter(
    private val onShopSelected: (String) -> Unit
) : ListAdapter<ShopDetails, ShopSelectionAdapter.ShopViewHolder>(ShopDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val binding = ItemShopSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ShopViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ShopViewHolder(
        private val binding: ItemShopSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val shop = getItem(position)
                    onShopSelected(shop.shopId)
                }
            }
        }

        fun bind(shop: ShopDetails) {
            binding.textViewShopName.text = shop.shopName
            binding.textViewShopAddress.text = shop.address
            
            // If this shop has GST, show the GST number
            if (shop.hasGst && !shop.gstNumber.isNullOrEmpty()) {
                binding.textViewGstNumber.text = "GSTIN: ${shop.gstNumber}"
            } else {
                binding.textViewGstNumber.text = "No GST"
            }
        }
    }

    private class ShopDiffCallback : DiffUtil.ItemCallback<ShopDetails>() {
        override fun areItemsTheSame(oldItem: ShopDetails, newItem: ShopDetails): Boolean {
            return oldItem.shopId == newItem.shopId
        }

        override fun areContentsTheSame(oldItem: ShopDetails, newItem: ShopDetails): Boolean {
            return oldItem == newItem
        }
    }
} 