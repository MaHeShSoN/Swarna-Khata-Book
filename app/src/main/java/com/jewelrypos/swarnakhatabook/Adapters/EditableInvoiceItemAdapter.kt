
package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat

class EditableInvoiceItemAdapter(
    private var items: List<InvoiceItem>
) : RecyclerView.Adapter<EditableInvoiceItemAdapter.ItemViewHolder>() {

    interface OnItemActionListener {
        fun onRemoveItem(item: InvoiceItem)
        fun onEditItem(item: InvoiceItem)
        fun onQuantityChanged(item: InvoiceItem, newQuantity: Int)
    }

    private var listener: OnItemActionListener? = null

    fun setOnItemActionListener(listener: OnItemActionListener) {
        this.listener = listener
    }

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemName: TextView = itemView.findViewById(R.id.itemName)
        val itemDetails: TextView = itemView.findViewById(R.id.itemDetails)
        val price: TextView = itemView.findViewById(R.id.price)
        val quantity: TextView = itemView.findViewById(R.id.quantity)
        val decreaseButton: ImageButton = itemView.findViewById(R.id.decreaseButton)
        val increaseButton: ImageButton = itemView.findViewById(R.id.increaseButton)
        val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_editable_invoice, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        val formatter = DecimalFormat("#,##,##0.00")

        // Set item name and details
        holder.itemName.text = item.itemDetails.displayName
        holder.itemDetails.text = "Weight: ${item.itemDetails.grossWeight}g | Purity: ${item.itemDetails.purity}"

        // Set price
        val totalPrice = item.price * item.quantity
        holder.price.text = "₹${formatter.format(totalPrice)}"

        // Set quantity
        holder.quantity.text = item.quantity.toString()

        // Handle quantity adjustment
        holder.decreaseButton.setOnClickListener {
            if (item.quantity > 1) {
                listener?.onQuantityChanged(item, item.quantity - 1)
            }
        }

        holder.increaseButton.setOnClickListener {
            listener?.onQuantityChanged(item, item.quantity + 1)
        }

        // Handle edit and remove actions
        holder.editButton.setOnClickListener {
            listener?.onEditItem(item)
        }

        holder.removeButton.setOnClickListener {
            listener?.onRemoveItem(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<InvoiceItem>) {
        val diffCallback = ItemDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    class ItemDiffCallback(
        private val oldList: List<InvoiceItem>,
        private val newList: List<InvoiceItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].itemId == newList[newItemPosition].itemId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.itemId == newItem.itemId &&
                    oldItem.quantity == newItem.quantity &&
                    oldItem.price == newItem.price
        }
    }
}
