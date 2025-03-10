package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.R


class JewelleryAdapter(private var jewelleryList: List<JewelleryItem>) :
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JewelleryViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_jewelry, parent, false)
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
            "diamond" -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_diamond_background)
            else -> holder.jewelryTypeIndicator.setBackgroundResource(R.drawable.circle_default_background)
        }
    }

    override fun getItemCount() = jewelleryList.size

    fun updateList(newList: List<JewelleryItem>) {
        jewelleryList = newList
        notifyDataSetChanged()
    }
}