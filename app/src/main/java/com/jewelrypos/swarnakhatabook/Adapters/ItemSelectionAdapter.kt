package com.jewelrypos.swarnakhatabook.Adapters



import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.R

// ItemSelectionAdapter.kt
class ItemSelectionAdapter(
    private var items: List<JewelleryItem>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<ItemSelectionAdapter.ItemViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(item: JewelleryItem, isSelected: Boolean)
        fun onQuantityChanged(item: JewelleryItem, quantity: Int)
    }

    // Keep track of selected items and their quantities
    private val selectedItems = mutableMapOf<String, Int>()

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkbox: CheckBox = itemView.findViewById(R.id.selectCheckbox)
        val itemName: TextView = itemView.findViewById(R.id.itemName)
        val itemCode: TextView = itemView.findViewById(R.id.itemCode)
        val weightValue: TextView = itemView.findViewById(R.id.weightValue)
        val purityValue: TextView = itemView.findViewById(R.id.purityValue)
        val quantityContainer: LinearLayout = itemView.findViewById(R.id.quantityContainer)
        val decreaseButton: ImageButton = itemView.findViewById(R.id.decreaseButton)
        val quantityText: TextView = itemView.findViewById(R.id.quantityText)
        val increaseButton: ImageButton = itemView.findViewById(R.id.increaseButton)
        val card: MaterialCardView = itemView.findViewById(R.id.itemCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selectable_jewelry, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]

        holder.itemName.text = "${item.purity} ${item.displayName}"
        holder.itemCode.text = "Code: ${item.jewelryCode}"
        holder.weightValue.text = "${item.grossWeight}g"
        holder.purityValue.text = item.purity

        // Check if this item is selected
        val isSelected = selectedItems.containsKey(item.id)
        holder.checkbox.isChecked = isSelected
        holder.quantityContainer.visibility = if (isSelected) View.VISIBLE else View.GONE

        // Set quantity if selected
        val quantity = selectedItems[item.id] ?: 1
        holder.quantityText.text = quantity.toString()

        // Handle clicks
        holder.checkbox.setOnClickListener {
            val checked = holder.checkbox.isChecked
            if (checked) {
                selectedItems[item.id] = 1
                holder.quantityContainer.visibility = View.VISIBLE
                holder.quantityText.text = "1"
            } else {
                selectedItems.remove(item.id)
                holder.quantityContainer.visibility = View.GONE
            }
            listener.onItemClick(item, checked)
        }

        // Card click selects/deselects the item
        holder.card.setOnClickListener {
            holder.checkbox.performClick()
        }

        // Handle quantity adjustment
        holder.decreaseButton.setOnClickListener {
            val currentQty = selectedItems[item.id] ?: 1
            if (currentQty > 1) {
                val newQty = currentQty - 1
                selectedItems[item.id] = newQty
                holder.quantityText.text = newQty.toString()
                listener.onQuantityChanged(item, newQty)
            }
        }

        holder.increaseButton.setOnClickListener {
            val currentQty = selectedItems[item.id] ?: 1
            // Assuming max quantity is 99
            if (currentQty < 99) {
                val newQty = currentQty + 1
                selectedItems[item.id] = newQty
                holder.quantityText.text = newQty.toString()
                listener.onQuantityChanged(item, newQty)
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<JewelleryItem>) {
        val diffCallback = ItemDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    fun getSelectedItemsWithQuantity(): Map<JewelleryItem, Int> {
        return items.filter { selectedItems.containsKey(it.id) }
            .associateWith { selectedItems[it.id] ?: 1 }
    }

    class ItemDiffCallback(
        private val oldList: List<JewelleryItem>,
        private val newList: List<JewelleryItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}