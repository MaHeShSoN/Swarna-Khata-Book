package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.CustomerSalesData
import com.jewelrypos.swarnakhatabook.databinding.ItemTopCustomerBinding
import java.text.DecimalFormat


class TopCustomersAdapter : ListAdapter<CustomerSalesData, TopCustomersAdapter.TopCustomerViewHolder>(CustomerSalesDiffCallback()) {

    private val currencyFormatter = DecimalFormat("₹#,##,##0") // Simple format for reports

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopCustomerViewHolder {
        val binding = ItemTopCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TopCustomerViewHolder(binding, currencyFormatter)
    }

    override fun onBindViewHolder(holder: TopCustomerViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1) // Pass rank
    }

    class TopCustomerViewHolder(
        private val binding: ItemTopCustomerBinding,
        private val formatter: DecimalFormat
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(customer: CustomerSalesData, rank: Int) {
            binding.rankTextView.text = "$rank."
            binding.customerNameTextView.text = customer.customerName
            binding.customerInvoicesTextView.text = "Inv: ${customer.invoiceCount}"
            binding.customerRevenueTextView.text = formatter.format(customer.totalPurchaseValue)
        }
    }

    class CustomerSalesDiffCallback : DiffUtil.ItemCallback<CustomerSalesData>() {
        override fun areItemsTheSame(oldItem: CustomerSalesData, newItem: CustomerSalesData): Boolean {
            // Use name as identifier, assuming it's reasonably unique for top customer context
            return oldItem.customerName == newItem.customerName
        }

        override fun areContentsTheSame(oldItem: CustomerSalesData, newItem: CustomerSalesData): Boolean {
            return oldItem == newItem
        }
    }
}
