package com.jewelrypos.swarnakhatabook.Adapters



import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.SelectedItemWithPrice
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat

// SelectedItemsAdapter.kt
class SelectedItemsAdapter(
    private var items: List<SelectedItemWithPrice>
) : RecyclerView.Adapter<SelectedItemsAdapter.ItemViewHolder>() {

    interface OnItemActionListener {
        fun onRemoveItem(item: SelectedItemWithPrice)
        fun onEditItem(item: SelectedItemWithPrice)
        fun onQuantityChanged(item: SelectedItemWithPrice, quantity: Int)
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

        holder.itemName.text = "${item.purity} ${item.displayName}"
        holder.itemDetails.text = "Weight: ${item.grossWeight}g | Code: ${item.jewelryCode}"

        // Format price
        val formatter = DecimalFormat("#,##,##0.00")
        holder.price.text = "₹${formatter.format(selectedItem.price)}"

        // Set quantity
        holder.quantity.text = selectedItem.quantity.toString()

        // Handle quantity adjustment
        holder.decreaseButton.setOnClickListener {
            if (selectedItem.quantity > 1) {
                val newQty = selectedItem.quantity - 1
                listener?.onQuantityChanged(selectedItem, newQty)
            }
        }

        holder.increaseButton.setOnClickListener {
            if (selectedItem.quantity < 99) {
                val newQty = selectedItem.quantity + 1
                listener?.onQuantityChanged(selectedItem, newQty)
            }
        }

        // Handle edit and remove
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

    class SelectedItemDiffCallback(
        private val oldList: List<SelectedItemWithPrice>,
        private val newList: List<SelectedItemWithPrice>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].item.id == newList[newItemPosition].item.id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }


}