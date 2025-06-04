package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import coil3.load
import coil3.request.crossfade
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import coil3.size.Scale
import android.graphics.drawable.Drawable

import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Enums.InventoryType
import com.jewelrypos.swarnakhatabook.R
import androidx.paging.PagingDataAdapter

class JewelleryAdapter(private val itemClickListener: OnItemClickListener? = null) :
    PagingDataAdapter<JewelleryItem, JewelleryAdapter.JewelleryViewHolder>(JewelleryDiffCallback()) {

    // Define target image dimensions for efficient loading
    companion object {
        // Target width and height for images (matching the view size)
        const val TARGET_WIDTH = 120  // 40dp * 3 (density)
        const val TARGET_HEIGHT = 120 // 40dp * 3 (density)
    }

    class JewelleryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val jewelryTypeIndicator: ShapeableImageView = itemView.findViewById(R.id.jewelryTypeIndicator)
        val jewelryTypeInitial: TextView = itemView.findViewById(R.id.jewelryTypeInitial)
        val jewelryTitle: TextView = itemView.findViewById(R.id.jewelryTitle)
        val jewelryCode: TextView = itemView.findViewById(R.id.jewelryCode)
        val stockValue: TextView = itemView.findViewById(R.id.stockValue)
        val purity: TextView = itemView.findViewById(R.id.purity)
        val divider: View = itemView.findViewById(R.id.divider)
        // Add other views here
    }

    // Define an interface for click events
    interface OnItemClickListener {
        fun onItemClick(item: JewelleryItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JewelleryViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.inventory_item_jewelry, parent, false)
        return JewelleryViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: JewelleryViewHolder, position: Int) {
        val currentItem = getItem(position)

        currentItem?.let { item ->
            holder.jewelryTitle.text = "${item.displayName}"

            // Handle jewelry code display differently based on inventory type
            when (item.inventoryType) {
                InventoryType.IDENTICAL_BATCH -> {
                    holder.jewelryCode.visibility = View.VISIBLE
                    holder.jewelryCode.text = "Gr. Weight: ${item.grossWeight}g"
                    holder.purity.text = "Purity: ${item.purity}%"
                    holder.purity.visibility = View.VISIBLE
                    holder.divider.visibility = View.VISIBLE
                }
                InventoryType.BULK_STOCK -> {
                    // For bulk stock, hide the code and show category instead
                    holder.jewelryCode.visibility = View.VISIBLE
                    holder.jewelryCode.text = "Category: ${item.category}"
                    holder.purity.visibility = View.GONE
                    holder.divider.visibility = View.GONE
                }
            }

            // Handle stock/weight display differently based on inventory type
            when (item.inventoryType) {
                InventoryType.IDENTICAL_BATCH -> {
                    holder.stockValue.text = "${item.stock} ${item.stockUnit}"
                }
                InventoryType.BULK_STOCK -> {
                    holder.stockValue.text = "Total: ${item.totalWeightGrams}g"
                }
            }



            // Check if item has image URL, if so load it
            if (item.imageUrl.isNotEmpty()) {
                // Hide the initial letter
                holder.jewelryTypeInitial.visibility = View.GONE

                // Load image with Coil
                holder.jewelryTypeIndicator.load(item.imageUrl) {
                    // Apply circle crop transformation
                    transformations(CircleCropTransformation())

                    // Enforce efficient sizing
                    size(TARGET_WIDTH, TARGET_HEIGHT)
                    scale(Scale.FILL)

                    // Add crossfade for smooth transitions
                    crossfade(true)

                    // Use a simpler approach for error handling
                    listener(
                        onStart = {
                            // Reset background at start of loading
                            holder.jewelryTypeIndicator.background = null
                            holder.jewelryTypeInitial.visibility = View.GONE
                        },
                        onSuccess = { _, _ ->
                            // On success, ensure initial is hidden and background is clear
                            holder.jewelryTypeInitial.visibility = View.GONE
                            holder.jewelryTypeIndicator.background = null
                        },
                        onError = { _, _ ->
                            // Error loading image, show the initial instead
                            holder.jewelryTypeIndicator.setImageDrawable(null) // Also good to clear here on error before setting background
                            holder.jewelryTypeInitial.visibility = View.VISIBLE
                            holder.jewelryTypeInitial.text = item.itemType.firstOrNull()?.toString()?.uppercase() ?: ""

                            // Set the appropriate background based on item type
                            when (item.itemType.lowercase()) {
                                "gold" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_gold_background)
                                "silver" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_silver_background)
                                "other" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_other_background)
                                else -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_gold_background)
                            }
                        }
                    )
                }
            } else {
                // No image, show the standard circle with initial
                holder.jewelryTypeIndicator.setImageDrawable(null) // <--- ADD THIS LINE
                // Or, alternatively: holder.jewelryTypeIndicator.setImageResource(0)

                when (item.itemType.lowercase()) {
                    "gold" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_gold_background)
                    "silver" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_silver_background)
                    "other" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_other_background)
                    else -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_gold_background)
                }

                holder.jewelryTypeInitial.visibility = View.VISIBLE
                holder.jewelryTypeInitial.text = item.itemType.firstOrNull()?.toString()?.uppercase() ?: ""
            }
            holder.itemView.setOnClickListener {
                itemClickListener?.onItemClick(item)
            }
        }
    }
}

// DiffUtil Callback class for efficient updates
class JewelleryDiffCallback : DiffUtil.ItemCallback<JewelleryItem>() {
    
    override fun areItemsTheSame(oldItem: JewelleryItem, newItem: JewelleryItem) =
        oldItem.id == newItem.id
    
    override fun areContentsTheSame(oldItem: JewelleryItem, newItem: JewelleryItem) =
        oldItem == newItem
}