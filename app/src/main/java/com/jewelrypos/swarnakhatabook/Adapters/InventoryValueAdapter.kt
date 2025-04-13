package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.InventoryValueItem
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat

class InventoryValueAdapter :
    ListAdapter<InventoryValueItem, InventoryValueAdapter.InventoryViewHolder>(
        InventoryValueDiffCallback
    ) {

    private var currentItems = listOf<InventoryValueItem>()
    private var filteredItems = listOf<InventoryValueItem>()
    private var currentFilter = ""
    private val currencyFormatter = DecimalFormat("#,##,##0.00")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_value, parent, false)
        return InventoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    // Add the override keyword and make the list parameter nullable
    override fun submitList(list: List<InventoryValueItem>?) {
        currentItems = list ?: emptyList()
        applyFilter()
    }

    fun filter(category: String) {
        currentFilter = category
        applyFilter()
    }

    private fun applyFilter() {
        filteredItems = if (currentFilter.isEmpty()) {
            currentItems
        } else {
            currentItems.filter { it.itemType.equals(currentFilter, ignoreCase = true) }
        }
        super.submitList(filteredItems)
    }

    fun getFilteredItems(): List<InventoryValueItem> {
        return filteredItems
    }

    inner class InventoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemName: TextView = itemView.findViewById(R.id.itemName)
        private val itemType: TextView = itemView.findViewById(R.id.itemType)
        private val itemCode: TextView = itemView.findViewById(R.id.itemCode)
        private val stockLevel: TextView = itemView.findViewById(R.id.stockLevel)
        private val metalValue: TextView = itemView.findViewById(R.id.metalValue)
        private val makingValue: TextView = itemView.findViewById(R.id.makingValue)
        private val diamondValue: TextView = itemView.findViewById(R.id.diamondValue)
        private val itemValue: TextView = itemView.findViewById(R.id.itemValue)
        private val totalValue: TextView = itemView.findViewById(R.id.totalValue)

        fun bind(item: InventoryValueItem) {
            itemName.text = item.name
            itemType.text = item.itemType
            itemCode.text = "Code: ${item.code}"
            stockLevel.text = "Stock: ${item.stock} ${item.stockUnit}"

            metalValue.text = "Metal: ₹${currencyFormatter.format(item.metalValue)}"
            makingValue.text = "Making: ₹${currencyFormatter.format(item.makingValue)}"
            diamondValue.text = "Diamond: ₹${currencyFormatter.format(item.diamondValue)}"

            itemValue.text = "Per Unit: ₹${currencyFormatter.format(item.totalItemValue)}"
            totalValue.text = "Total: ₹${currencyFormatter.format(item.totalStockValue)}"

            // Set background color based on item type
            val backgroundRes = when (item.itemType.uppercase()) {
                "GOLD" -> R.drawable.inventory_gold_background
                "SILVER" -> R.drawable.inventory_silver_background
                else -> R.drawable.inventory_other_background
            }
            itemView.setBackgroundResource(backgroundRes)
        }
    }

    companion object {
        private val InventoryValueDiffCallback =
            object : DiffUtil.ItemCallback<InventoryValueItem>() {
                override fun areItemsTheSame(
                    oldItem: InventoryValueItem,
                    newItem: InventoryValueItem
                ): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(
                    oldItem: InventoryValueItem,
                    newItem: InventoryValueItem
                ): Boolean {
                    return oldItem == newItem
                }
            }
    }
}