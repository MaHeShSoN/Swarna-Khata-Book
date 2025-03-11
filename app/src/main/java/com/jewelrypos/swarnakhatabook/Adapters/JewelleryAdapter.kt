package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.R

class JewelleryAdapter(private var jewelleryList: List<JewelleryItem>,   private val itemClickListener: OnItemClickListener? = null  ) :
    RecyclerView.Adapter<JewelleryAdapter.JewelleryViewHolder>() {

    class JewelleryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val jewelryTypeIndicator: ShapeableImageView = itemView.findViewById(R.id.jewelryTypeIndicator)
        val jewelryTypeInitial: TextView = itemView.findViewById(R.id.jewelryTypeInitial)
        val jewelryTitle: TextView = itemView.findViewById(R.id.jewelryTitle)
        val jewelryCode: TextView = itemView.findViewById(R.id.jewelryCode)
        val grossWeightValue: TextView = itemView.findViewById(R.id.grossWeightValue)
        val locationValue: TextView = itemView.findViewById(R.id.locationValue)
        val stockValue: TextView = itemView.findViewById(R.id.stockValue)
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
        holder.jewelryTitle.text = "${currentItem.purity} ${currentItem.displayName}"
        holder.jewelryCode.text = "Code: ${currentItem.jewelryCode}"
        holder.grossWeightValue.text = "${currentItem.grossWeight} g"
        holder.locationValue.text = currentItem.location
        holder.stockValue.text = "${currentItem.stock} ${currentItem.stockUnit}"
        holder.jewelryTypeInitial.text = currentItem.itemType.firstOrNull()?.toString()?.uppercase() ?: ""

        //Change color based on itemType
        when (currentItem.itemType.lowercase()) {
            "gold" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_gold_background)
            "silver" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_silver_background)
            "other" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_other_background)
            else -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_gold_background)
        }

        holder.itemView.setOnClickListener {
            itemClickListener?.onItemClick(currentItem)
        }

        // Apply animation
        setAnimation(holder.itemView, position)
        // Apply FallDown animation

    }

    private var lastPosition = -1

    private fun setAnimation(view: View, position: Int) {
        // If this position hasn't been displayed yet
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(view.context, R.anim.animation_item_enter)
            view.startAnimation(animation)
            lastPosition = position
        }
    }


    // Reset animation when data changes
    override fun onViewDetachedFromWindow(holder: JewelleryViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewDetachedFromWindow(holder)
    }

    override fun getItemCount() = jewelleryList.size

    fun updateList(newList: List<JewelleryItem>) {
        val diffCallback = JewelleryDiffCallback(jewelleryList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        jewelleryList = newList
        diffResult.dispatchUpdatesTo(this)
    }
}
class JewelleryDiffCallback(
    private val oldList: List<JewelleryItem>,
    private val newList: List<JewelleryItem>
) : DiffUtil.Callback() {
    override fun getOldListSize() = oldList.size
    override fun getNewListSize() = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Use a unique identifier (assuming jewelryCode is unique)
        return oldList[oldItemPosition].jewelryCode == newList[newItemPosition].jewelryCode
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // Compare all relevant fields
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}