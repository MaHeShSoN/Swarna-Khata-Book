package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.GstReportItem
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat

class GstReportAdapter :
    ListAdapter<GstReportItem, GstReportAdapter.GstViewHolder>(GstReportDiffCallback) {

    private val currencyFormatter = DecimalFormat("#,##,##0.00")
    private val percentFormatter = DecimalFormat("#0.00")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GstViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gst_report, parent, false)
        return GstViewHolder(view)
    }

    override fun onBindViewHolder(holder: GstViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class GstViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val taxRateText: TextView = itemView.findViewById(R.id.taxRateText)
        private val taxableAmountText: TextView = itemView.findViewById(R.id.taxableAmountText)
        private val cgstText: TextView = itemView.findViewById(R.id.cgstText)
        private val sgstText: TextView = itemView.findViewById(R.id.sgstText)
        private val totalTaxText: TextView = itemView.findViewById(R.id.totalTaxText)

        fun bind(item: GstReportItem) {
            taxRateText.text = "${percentFormatter.format(item.taxRate)}%"
            taxableAmountText.text = "₹${currencyFormatter.format(item.taxableAmount)}"
            cgstText.text = "₹${currencyFormatter.format(item.cgst)}"
            sgstText.text = "₹${currencyFormatter.format(item.sgst)}"
            totalTaxText.text = "₹${currencyFormatter.format(item.totalTax)}"
        }
    }

    companion object {
        private val GstReportDiffCallback = object : DiffUtil.ItemCallback<GstReportItem>() {
            override fun areItemsTheSame(oldItem: GstReportItem, newItem: GstReportItem): Boolean {
                return oldItem.taxRate == newItem.taxRate
            }

            override fun areContentsTheSame(oldItem: GstReportItem, newItem: GstReportItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}