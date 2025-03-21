package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat

class InvoiceItemAdapter(
    private var items: List<InvoiceItem>
) : RecyclerView.Adapter<InvoiceItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemName: TextView = itemView.findViewById(R.id.itemName)
        val itemDetails: TextView = itemView.findViewById(R.id.itemDetails)
        val price: TextView = itemView.findViewById(R.id.price)
        val quantity: TextView = itemView.findViewById(R.id.quantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice_detail, parent, false)
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
        holder.quantity.text = "Qty: ${item.quantity}"
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
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}