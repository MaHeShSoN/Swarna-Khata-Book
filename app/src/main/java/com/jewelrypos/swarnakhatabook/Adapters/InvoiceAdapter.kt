package com.jewelrypos.swarnakhatabook.Adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class InvoicesAdapter(
    private var invoices: List<Invoice>
) : RecyclerView.Adapter<InvoicesAdapter.InvoiceViewHolder>() {

    private var onItemClickListener: ((Invoice) -> Unit)? = null

    fun setOnItemClickListener(listener: (Invoice) -> Unit) {
        onItemClickListener = listener
    }

    class InvoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val invoiceNumber: TextView = itemView.findViewById(R.id.invoiceNumber)
        val paymentStatus: TextView = itemView.findViewById(R.id.paymentStatus)
        val customerName: TextView = itemView.findViewById(R.id.customerName)
        val itemsCount: TextView = itemView.findViewById(R.id.itemsCount)
        val invoiceDate: TextView = itemView.findViewById(R.id.invoiceDate)
        val totalAmount: TextView = itemView.findViewById(R.id.totalAmount)
        val balanceAmount: TextView = itemView.findViewById(R.id.balanceAmount)
        val card: MaterialCardView = itemView.findViewById(R.id.invoiceCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice, parent, false)
        return InvoiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        val invoice = invoices[position]

        holder.invoiceNumber.text = invoice.invoiceNumber
        holder.customerName.text = invoice.customerName
        holder.itemsCount.text = "${invoice.items.size} items"

        // Format invoice date
        val formatter1 = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.invoiceDate.text = formatter1.format(Date(invoice.invoiceDate))

        // Format currency
        val formatter = DecimalFormat("#,##,##0.00")
        holder.totalAmount.text = "₹${formatter.format(invoice.totalAmount)}"

        val balanceDue = invoice.totalAmount - invoice.paidAmount
        holder.balanceAmount.text = "₹${formatter.format(balanceDue)}"

        // Set payment status text and background color
        val paymentStatus = if (balanceDue <= 0) "Paid" else if (invoice.paidAmount > 0) "Partial" else "Unpaid"
        holder.paymentStatus.text = paymentStatus

        val statusColor = when (paymentStatus.lowercase()) {
            "paid" -> ContextCompat.getColor(holder.itemView.context, R.color.status_paid)
            "partial" -> ContextCompat.getColor(holder.itemView.context, R.color.status_partial)
            "unpaid" -> ContextCompat.getColor(holder.itemView.context, R.color.status_unpaid)
            else -> ContextCompat.getColor(holder.itemView.context, R.color.my_light_primary)
        }
        holder.paymentStatus.backgroundTintList = ColorStateList.valueOf(statusColor)

        // Set click listener
        holder.card.setOnClickListener {
            onItemClickListener?.invoke(invoice)
        }
    }

    override fun getItemCount() = invoices.size

// Continuing the InvoicesAdapter.kt implementation...

    fun updateInvoices(newInvoices: List<Invoice>) {
        val diffCallback = InvoiceDiffCallback(invoices, newInvoices)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        invoices = newInvoices
        diffResult.dispatchUpdatesTo(this)
    }

    class InvoiceDiffCallback(
        private val oldList: List<Invoice>,
        private val newList: List<Invoice>
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