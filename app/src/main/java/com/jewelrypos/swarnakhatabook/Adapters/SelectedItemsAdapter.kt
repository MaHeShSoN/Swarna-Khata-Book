package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.SelectedItemWithPrice
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat

class SelectedItemsAdapter(
    private var items: List<SelectedItemWithPrice>,
) : RecyclerView.Adapter<SelectedItemsAdapter.ItemViewHolder>() {

    interface OnItemActionListener {
        fun onRemoveItem(item: SelectedItemWithPrice)
        fun onEditItem(item: SelectedItemWithPrice)
        fun onQuantityChanged(item: SelectedItemWithPrice, newQuantity: Int)
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
            .inflate(R.layout.item_selected_jewelry, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val selectedItem = items[position]
        val item = selectedItem.item
        val formatter = DecimalFormat("#,##,##0.00")

        // Set basic item details
        holder.itemName.text = "${item.purity} ${item.displayName}"
        holder.itemDetails.text = "Weight: ${item.grossWeight}g"

        // Set price
        val totalPrice = selectedItem.price * selectedItem.quantity
        holder.price.text = "₹${formatter.format(totalPrice)}"

        // Set quantity
        holder.quantity.text = selectedItem.quantity.toString()

        // Handle quantity adjustment
        holder.decreaseButton.setOnClickListener {
            if (selectedItem.quantity > 1) {
                val newQuantity = selectedItem.quantity - 1
                listener?.onQuantityChanged(selectedItem, newQuantity)
            }
        }

        holder.increaseButton.setOnClickListener {
            val newQuantity = selectedItem.quantity + 1
            if (newQuantity > selectedItem.item.stock && selectedItem.item.stock > 0) {
                // Show warning toast
                Toast.makeText(holder.itemView.context,
                    "Warning: Requested quantity exceeds available stock (${selectedItem.item.stock})",
                    Toast.LENGTH_SHORT).show()
            }
            listener?.onQuantityChanged(selectedItem, newQuantity)
        }

        // Handle edit and remove actions
        holder.editButton.setOnClickListener {
            listener?.onEditItem(selectedItem)
        }

        holder.removeButton.setOnClickListener {
            listener?.onRemoveItem(selectedItem)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<SelectedItemWithPrice>) {
        val diffCallback = SelectedItemDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    // Getter for the current items list
    fun getItems(): List<SelectedItemWithPrice> = items

    private class SelectedItemDiffCallback(
        private val oldList: List<SelectedItemWithPrice>,
        private val newList: List<SelectedItemWithPrice>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].item.id == newList[newItemPosition].item.id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.item.id == newItem.item.id &&
                    oldItem.quantity == newItem.quantity &&
                    oldItem.price == newItem.price
        }
    }
}