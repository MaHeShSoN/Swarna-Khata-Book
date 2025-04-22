package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
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
        private val itemTypeIcon: ImageView = itemView.findViewById(R.id.itemTypeIcon)

        fun bind(item: RecycledItem) {
            itemNameTextView.text = item.itemName

            // Set item type text and icon
            val iconResId: Int
            val typeText = when (item.itemType.uppercase()) {
                "INVOICE" -> {
                    iconResId = R.drawable.tabler__file_invoice // Use your invoice icon
                    itemView.context.getString(R.string.item_type_invoice)
                }
                "CUSTOMER" -> {
                    iconResId = R.drawable.line_md__person_twotone // Use your customer icon
                    itemView.context.getString(R.string.item_type_customer)
                }
                "JEWELLERYITEM" -> { // *** ADD THIS CASE ***
                    iconResId = R.drawable.uil__gold // Use your inventory/jewelry icon
                    itemView.context.getString(R.string.item_type_inventory)
                }
                else -> {
                    iconResId = R.drawable.mingcute__question_line // Default icon
                    item.itemType // Display raw type if unknown
                }
            }
            itemTypeTextView.text = typeText
            itemTypeIcon.setImageResource(iconResId) // Set the icon

            // Format and display dates with localized prefixes
            deletedDateTextView.text = itemView.context.getString(
                R.string.deleted_prefix,
                viewModel.formatDeletedDate(item.deletedAt)
            )
            
            expiryTextView.text = itemView.context.getString(
                R.string.expires_prefix,
                viewModel.calculateExpiryTimeRemaining(item.expiresAt)
            )

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