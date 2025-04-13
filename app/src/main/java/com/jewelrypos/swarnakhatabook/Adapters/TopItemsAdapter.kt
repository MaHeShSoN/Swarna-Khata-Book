package com.jewelrypos.swarnakhatabook.Adapters // Or place inside SalesReportFragment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.CustomerSalesData
import com.jewelrypos.swarnakhatabook.DataClasses.ItemSalesData
import com.jewelrypos.swarnakhatabook.databinding.ItemTopCustomerBinding
import com.jewelrypos.swarnakhatabook.databinding.ItemTopSellingBinding
import java.text.DecimalFormat
import java.util.*

// --- Adapter for Top Selling Items ---

class TopItemsAdapter : ListAdapter<ItemSalesData, TopItemsAdapter.TopItemViewHolder>(ItemSalesDiffCallback()) {

    private val currencyFormatter = DecimalFormat("₹#,##,##0") // Simple format for reports

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopItemViewHolder {
        val binding = ItemTopSellingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopItemViewHolder(binding, currencyFormatter)
    }

    override fun onBindViewHolder(holder: TopItemViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1) // Pass rank (position + 1)
    }

    class TopItemViewHolder(
        private val binding: ItemTopSellingBinding,
        private val formatter: DecimalFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemSalesData, rank: Int) {
            binding.rankTextView.text = "$rank."
            binding.itemNameTextView.text = item.itemName
            binding.itemQuantityTextView.text = "Qty: ${item.quantitySold}"
            binding.itemRevenueTextView.text = formatter.format(item.totalRevenue)
        }
    }

    class ItemSalesDiffCallback : DiffUtil.ItemCallback<ItemSalesData>() {
        override fun areItemsTheSame(oldItem: ItemSalesData, newItem: ItemSalesData): Boolean {
            return oldItem.itemName == newItem.itemName // Assuming item name is unique identifier here
        }

        override fun areContentsTheSame(oldItem: ItemSalesData, newItem: ItemSalesData): Boolean {
            return oldItem == newItem
        }
    }
}