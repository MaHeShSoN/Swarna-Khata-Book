package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.jewelrypos.swarnakhatabook.DataClasses.RecycledItem
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.ViewModle.RecyclingBinViewModel

class RecycledItemsAdapter(
    private val viewModel: RecyclingBinViewModel
) : ListAdapter<RecycledItem, RecycledItemsAdapter.RecycledItemViewHolder>(RecycledItemDiffCallback) {

    interface OnItemActionListener {
        fun onRestoreItem(item: RecycledItem)
        fun onDeleteItem(item: RecycledItem)
    }

    private var actionListener: OnItemActionListener? = null

    fun setOnItemActionListener(listener: OnItemActionListener) {
        this.actionListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecycledItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recycled, parent, false)
        return RecycledItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecycledItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class RecycledItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemNameTextView: TextView = itemView.findViewById(R.id.recycledItemName)
        private val itemTypeTextView: TextView = itemView.findViewById(R.id.recycledItemType)
        private val deletedDateTextView: TextView = itemView.findViewById(R.id.deletedDate)
        private val expiryTextView: TextView = itemView.findViewById(R.id.expiryDate)
        private val restoreButton: MaterialButton = itemView.findViewById(R.id.restoreButton)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)

        fun bind(item: RecycledItem) {
            itemNameTextView.text = item.itemName

            // Display item type with icon
            val typeText = when (item.itemType) {
                "INVOICE" -> "Invoice"
                "CUSTOMER" -> "Customer"
                "INVENTORY" -> "Inventory Item"
                else -> item.itemType
            }
            itemTypeTextView.text = typeText

            // Format and display dates
            deletedDateTextView.text = "Deleted: ${viewModel.formatDeletedDate(item.deletedAt)}"
            expiryTextView.text = "Expires: ${viewModel.calculateExpiryTimeRemaining(item.expiresAt)}"

            // Set click listeners
            restoreButton.setOnClickListener {
                actionListener?.onRestoreItem(item)
            }

            deleteButton.setOnClickListener {
                actionListener?.onDeleteItem(item)
            }
        }
    }

    companion object {
        private val RecycledItemDiffCallback = object : DiffUtil.ItemCallback<RecycledItem>() {
            override fun areItemsTheSame(oldItem: RecycledItem, newItem: RecycledItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RecycledItem, newItem: RecycledItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}