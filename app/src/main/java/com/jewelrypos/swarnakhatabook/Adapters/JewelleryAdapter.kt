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

class JewelleryAdapter(private var jewelleryList: List<JewelleryItem>, private val itemClickListener: OnItemClickListener? = null) :
    RecyclerView.Adapter<JewelleryAdapter.JewelleryViewHolder>() {

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
        val currentItem = jewelleryList[position]
        holder.jewelryTitle.text = "${currentItem.displayName}"
        
        // Handle jewelry code display differently based on inventory type
        when (currentItem.inventoryType) {
            InventoryType.IDENTICAL_BATCH -> {
                holder.jewelryCode.visibility = View.VISIBLE
                holder.jewelryCode.text = "Gr. Weight: ${currentItem.grossWeight}g"
                holder.purity.text = "Purity: ${currentItem.purity}%"


            }
            InventoryType.BULK_STOCK -> {
                // For bulk stock, hide the code and show category instead
                holder.jewelryCode.visibility = View.VISIBLE
                holder.jewelryCode.text = "Category: ${currentItem.category}"
                holder.purity.visibility = View.GONE
                holder.divider.visibility = View.GONE
            }
        }
        
        // Handle stock/weight display differently based on inventory type
        when (currentItem.inventoryType) {
            InventoryType.IDENTICAL_BATCH -> {
                holder.stockValue.text = "${currentItem.stock} ${currentItem.stockUnit}"
            }
            InventoryType.BULK_STOCK -> {
                holder.stockValue.text = "Total: ${currentItem.totalWeightGrams}g"
            }
        }
        


        // Check if item has image URL, if so load it
        if (currentItem.imageUrl.isNotEmpty()) {
            // Hide the initial letter
            holder.jewelryTypeInitial.visibility = View.GONE
            
            // Load image with Coil
            holder.jewelryTypeIndicator.load(currentItem.imageUrl) {
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
                        holder.jewelryTypeInitial.visibility = View.VISIBLE
                        holder.jewelryTypeInitial.text = currentItem.itemType.firstOrNull()?.toString()?.uppercase() ?: ""
                        
                        // Set the appropriate background based on item type
                        when (currentItem.itemType.lowercase()) {
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
            when (currentItem.itemType.lowercase()) {
                "gold" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_gold_background)
                "silver" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_silver_background)
                "other" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_other_background)
                else -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_gold_background)
            }

            holder.jewelryTypeInitial.visibility = View.VISIBLE
            holder.jewelryTypeInitial.text = currentItem.itemType.firstOrNull()?.toString()?.uppercase() ?: ""
        }

        holder.itemView.setOnClickListener {
            itemClickListener?.onItemClick(currentItem)
        }

        // Apply animation
        setAnimation(holder.itemView, position)
    }

    private var lastPosition = -1

    private fun setAnimation(view: View, position: Int) {
        // Only animate if scrolling forward
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(view.context, R.anim.animation_item_enter)
            view.startAnimation(animation)
            lastPosition = position
        }
    }

    // Reset animation tracking when data changes
    override fun onViewDetachedFromWindow(holder: JewelleryViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewDetachedFromWindow(holder)
    }

    override fun getItemCount() = jewelleryList.size

    // Helper function to safely access items
    fun getItem(position: Int): JewelleryItem? {
        return if (position >= 0 && position < jewelleryList.size) {
            jewelleryList[position]
        } else {
            null
        }
    }

    fun updateList(newList: List<JewelleryItem>) {
        val diffCallback = JewelleryDiffCallback(jewelleryList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        jewelleryList = newList
        diffResult.dispatchUpdatesTo(this)
    }
}

// DiffUtil Callback class for efficient updates
class JewelleryDiffCallback(
    private val oldList: List<JewelleryItem>,
    private val newList: List<JewelleryItem>
) : DiffUtil.Callback() {
    
    override fun getOldListSize() = oldList.size
    
    override fun getNewListSize() = newList.size
    
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition].id == newList[newItemPosition].id
    
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
}