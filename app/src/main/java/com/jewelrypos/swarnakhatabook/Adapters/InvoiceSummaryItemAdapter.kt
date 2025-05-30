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
import java.util.Locale

class InvoiceSummaryItemAdapter(
    private var items: List<InvoiceItem>
) : RecyclerView.Adapter<InvoiceSummaryItemAdapter.InvoiceSummaryItemViewHolder>() {

    class InvoiceSummaryItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val summaryItemName: TextView = itemView.findViewById(R.id.summaryItemName)
        val summaryItemTotalValue: TextView = itemView.findViewById(R.id.summaryItemTotalValue)
        val summaryItemPurityRate: TextView = itemView.findViewById(R.id.summaryItemPurityRate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceSummaryItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice_summary_line, parent, false)
        return InvoiceSummaryItemViewHolder(view)
    }

    // In InvoiceSummaryItemAdapter.kt, inside onBindViewHolder
    override fun onBindViewHolder(holder: InvoiceSummaryItemViewHolder, position: Int) {
        val item = items[position]
        val formatter = DecimalFormat("#,##,##0")

        // Calculate item's total net weight (if quantity > 1) and total value
        val itemNetWeight = item.itemDetails.netWeight * item.quantity
        val itemTotalValue = item.price * item.quantity

        // Set item name and net weight
        holder.summaryItemName.text = "${item.itemDetails.displayName} (${formatter.format(itemNetWeight.toInt())} gm)"
        holder.summaryItemTotalValue.text = "₹${formatter.format(itemTotalValue.toInt())}"

        // Set purity and gold rate
        val purity = if (item.itemDetails.purity.isNotEmpty()) item.itemDetails.purity else "N/A"
        val metalRate = if (item.itemDetails.metalRate > 0) formatter.format(item.itemDetails.metalRate) else "N/A"
        // CORRECTED LINE: Remove .toInt() from metalRate
        holder.summaryItemPurityRate.text = "$purity% @ ₹${metalRate}/gm"
    }
    override fun getItemCount() = items.size

    fun updateItems(newItems: List<InvoiceItem>) {
        val diffCallback = InvoiceItemDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    // Using the same DiffUtil.Callback from previous InvoiceItemAdapter for efficiency
    private class InvoiceItemDiffCallback(
        private val oldList: List<InvoiceItem>,
        private val newList: List<InvoiceItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem == newItem // Data class equality
        }
    }
}