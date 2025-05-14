package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.LowStockItem
import com.jewelrypos.swarnakhatabook.R
import java.text.SimpleDateFormat
import java.util.Locale

class LowStockAdapter :
    ListAdapter<LowStockItem, LowStockAdapter.LowStockViewHolder>(LowStockDiffCallback) {

    private var currentItems = listOf<LowStockItem>()
    private var filteredItems = listOf<LowStockItem>()
    private var currentFilter = ""
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LowStockViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_low_stock, parent, false)
        return LowStockViewHolder(view)
    }

    override fun onBindViewHolder(holder: LowStockViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun submitList(list: List<LowStockItem>?) {
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

    fun getFilteredItems(): List<LowStockItem> {
        return filteredItems
    }

    inner class LowStockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemName: TextView = itemView.findViewById(R.id.itemName)
        private val itemCode: TextView = itemView.findViewById(R.id.itemCode)
        private val itemType: TextView = itemView.findViewById(R.id.itemType)
        private val currentStock: TextView = itemView.findViewById(R.id.currentStock)
        private val reorderLevel: TextView = itemView.findViewById(R.id.reorderLevel)
        private val lastSoldDate: TextView = itemView.findViewById(R.id.lastSoldDate)

        fun bind(item: LowStockItem) {
            itemName.text = item.name
            itemType.text = item.itemType

            currentStock.text = "${item.currentStock} ${item.stockUnit}"
            reorderLevel.text = "${item.reorderLevel} ${item.stockUnit}"

            // Format last sold date if available
            lastSoldDate.text = item.lastSoldDate?.let {
                "Last Sold: ${dateFormat.format(it)}"
            } ?: "Last Sold: N/A"

            // Set background color based on stock level
            val stockRatio = item.currentStock / item.reorderLevel
            val backgroundResId = when {
                stockRatio <= 0.25 -> R.drawable.low_stock_critical_background
                stockRatio <= 0.5 -> R.drawable.low_stock_warning_background
                else -> R.drawable.low_stock_caution_background
            }
            itemView.setBackgroundResource(backgroundResId)
        }
    }

    companion object {
        private val LowStockDiffCallback = object : DiffUtil.ItemCallback<LowStockItem>() {
            override fun areItemsTheSame(oldItem: LowStockItem, newItem: LowStockItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: LowStockItem, newItem: LowStockItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}