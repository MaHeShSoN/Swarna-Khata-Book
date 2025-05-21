package com.jewelrypos.swarnakhatabook.Adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter // Import ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Extend ListAdapter<Invoice, InvoicesAdapter.InvoiceViewHolder>
// Pass the DiffUtil.ItemCallback object to the constructor
class InvoiceAdapter : ListAdapter<Invoice, InvoiceAdapter.InvoiceViewHolder>(InvoiceDiffCallback) {

    // Click listener remains the same
    var onItemClickListener: ((Invoice) -> Unit)? = null

    // ViewHolder remains the same
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

    // onCreateViewHolder remains the same
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice, parent, false)
        return InvoiceViewHolder(view)
    }

    // onBindViewHolder now uses getItem(position)
    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        // Use getItem(position) provided by ListAdapter
        val invoice = getItem(position)

        holder.invoiceNumber.text = invoice.invoiceNumber
        holder.customerName.text = invoice.customerName
        holder.itemsCount.text = "${invoice.items.size} items"

        // Format invoice date
        val formatter1 = SimpleDateFormat("dd MMM yy", Locale.getDefault()) // Consistent date format
        holder.invoiceDate.text = formatter1.format(Date(invoice.invoiceDate))

        // Format currency with no decimal places
        val formatter = DecimalFormat("#,##,##0")
        holder.totalAmount.text = "₹${formatter.format(invoice.totalAmount)}"

        val balanceDue = invoice.totalAmount - invoice.paidAmount
        holder.balanceAmount.text = "₹${formatter.format(balanceDue)}"

        // Set payment status text and background color
        val paymentStatusText = if (balanceDue <= 0) "Paid" else if (invoice.paidAmount > 0) "Partial" else "Unpaid"
        holder.paymentStatus.text = paymentStatusText

        val statusColor = when (paymentStatusText.lowercase()) {
            "paid" -> ContextCompat.getColor(holder.itemView.context, R.color.status_paid)
            "partial" -> ContextCompat.getColor(holder.itemView.context, R.color.status_partial)
            "unpaid" -> ContextCompat.getColor(holder.itemView.context, R.color.status_unpaid)
            else -> ContextCompat.getColor(holder.itemView.context, R.color.my_light_primary) // Fallback color
        }
        holder.paymentStatus.backgroundTintList = ColorStateList.valueOf(statusColor)

        // Set click listener
        holder.card.setOnClickListener {
            // Pass the specific invoice object obtained from getItem(position)
            onItemClickListener?.invoke(invoice)
        }
    }

    // No need to override getItemCount() - ListAdapter handles it.

    // Remove the updateInvoices function - use submitList() from the Fragment/Activity instead.

    // Define the DiffUtil.ItemCallback as a companion object or top-level object
    // This compares items to efficiently update the RecyclerView
    companion object {
        private val InvoiceDiffCallback = object : DiffUtil.ItemCallback<Invoice>() {
            override fun areItemsTheSame(oldItem: Invoice, newItem: Invoice): Boolean {
                // Check if items represent the same entity (e.g., by unique ID)
                return oldItem.id == newItem.id // Assuming Invoice has a unique 'id' field
            }

            override fun areContentsTheSame(oldItem: Invoice, newItem: Invoice): Boolean {
                // Check if the item contents have changed
                return oldItem == newItem // Relies on Invoice being a data class or implementing equals()
            }
        }
    }

    // Remove the old inner InvoiceDiffCallback class
}