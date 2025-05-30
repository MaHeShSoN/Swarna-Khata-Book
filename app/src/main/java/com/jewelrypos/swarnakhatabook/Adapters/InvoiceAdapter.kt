package com.jewelrypos.swarnakhatabook.Adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.databinding.ItemInvoiceBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InvoiceAdapter : PagingDataAdapter<Invoice, InvoiceAdapter.InvoiceViewHolder>(InvoiceDiffCallback()) {

    var onItemClickListener: ((Invoice) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val binding = ItemInvoiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InvoiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        getItem(position)?.let { invoice ->
            holder.bind(invoice)
        }
    }

    inner class InvoiceViewHolder(
        private val binding: ItemInvoiceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let { invoice ->
                        onItemClickListener?.invoke(invoice)
                    }
                }
            }
        }

        fun bind(invoice: Invoice) {
            binding.apply {
                // Bind your invoice data to the views
                invoiceNumber.text = invoice.invoiceNumber
                customerName.text = invoice.customerName
                itemsCount.text = "${invoice.items.size} items"

                val formatter1 = SimpleDateFormat("dd MMM yy", Locale.getDefault()) // Consistent date format
                invoiceDate.text = formatter1.format(Date(invoice.invoiceDate))

                val formatter = DecimalFormat("#,##,##0")
                totalAmount.text = "₹${formatter.format(invoice.totalAmount)}"

                val balanceDue = invoice.totalAmount - invoice.paidAmount
                balanceAmount.text = "₹${formatter.format(balanceDue)}"

                // Set payment status text and background color
                val paymentStatusText = if (balanceDue <= 0) "Paid" else if (invoice.paidAmount > 0) "Partial" else "Unpaid"
                paymentStatus.text = paymentStatusText

                val statusColor = when (paymentStatusText.lowercase()) {
                    "paid" -> ContextCompat.getColor(itemView.context, R.color.status_paid)
                    "partial" -> ContextCompat.getColor(itemView.context, R.color.status_partial)
                    "unpaid" -> ContextCompat.getColor(itemView.context, R.color.status_unpaid)
                    else -> ContextCompat.getColor(itemView.context, R.color.my_light_primary) // Fallback color
                }
                paymentStatus.backgroundTintList = ColorStateList.valueOf(statusColor)

                
            }
        }
    }

    private class InvoiceDiffCallback : DiffUtil.ItemCallback<Invoice>() {
        override fun areItemsTheSame(oldItem: Invoice, newItem: Invoice): Boolean {
            return oldItem.invoiceNumber == newItem.invoiceNumber
        }

        override fun areContentsTheSame(oldItem: Invoice, newItem: Invoice): Boolean {
            return oldItem == newItem
        }
    }
}