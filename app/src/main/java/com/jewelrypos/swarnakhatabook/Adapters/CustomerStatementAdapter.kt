package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Define the OnInvoiceClickListener interface
class CustomerStatementAdapter(private val onInvoiceClickListener: OnInvoiceClickListener) :
    ListAdapter<Any, RecyclerView.ViewHolder>(CustomerStatementDiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val currencyFormatter = DecimalFormat("#,##,##0.00")

    // Interface for invoice click events
    interface OnInvoiceClickListener {
        fun onInvoiceClick(invoiceId: String)
    }

    companion object {
        private const val TYPE_INVOICE = 0
        private const val TYPE_PAYMENT = 1

        private object CustomerStatementDiffCallback : DiffUtil.ItemCallback<Any>() {
            override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
                return when {
                    oldItem is Invoice && newItem is Invoice -> oldItem.id == newItem.id
                    oldItem is Payment && newItem is Payment -> oldItem.id == newItem.id
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
                return when (oldItem) {
                    is Invoice -> oldItem == newItem
                    is Payment -> oldItem == newItem
                    else -> false
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Invoice -> TYPE_INVOICE
            is Payment -> TYPE_PAYMENT
            else -> throw IllegalArgumentException("Unknown view type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_INVOICE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_customer_statement_invoice, parent, false)
                InvoiceViewHolder(view, onInvoiceClickListener) // Pass listener to ViewHolder
            }
            TYPE_PAYMENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_customer_statement_payment, parent, false)
                PaymentViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is InvoiceViewHolder -> {
                val invoice = getItem(position) as Invoice
                holder.bind(invoice)
            }
            is PaymentViewHolder -> {
                val payment = getItem(position) as Payment
                holder.bind(payment)
            }
        }
    }

    inner class InvoiceViewHolder(itemView: View, private val onInvoiceClickListener: OnInvoiceClickListener) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val invoiceNumberText: TextView = itemView.findViewById(R.id.invoiceNumberText)
        private val bakiText: TextView = itemView.findViewById(R.id.bakiText) // Changed from debitText
        private val jamaText: TextView = itemView.findViewById(R.id.jamaText) // Changed from creditText

        fun bind(invoice: Invoice) {
            dateText.text = dateFormat.format(Date(invoice.invoiceDate))
            descriptionText.text = "Invoice"
            invoiceNumberText.text = invoice.invoiceNumber

            val unpaidAmount = invoice.totalAmount - invoice.paidAmount

            // Display unpaid amount under "Baki" (customer owes)
            bakiText.text = "₹${currencyFormatter.format(unpaidAmount)}"
            jamaText.text = "-" // Invoices don't typically represent "Jama" directly in this context

            itemView.setOnClickListener {
                onInvoiceClickListener.onInvoiceClick(invoice.id) // Pass invoice ID for navigation
            }
        }
    }

    inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val methodText: TextView = itemView.findViewById(R.id.methodText)
        private val bakiText: TextView = itemView.findViewById(R.id.bakiText) // Changed from debitText
        private val jamaText: TextView = itemView.findViewById(R.id.jamaText) // Changed from creditText

        fun bind(payment: Payment) {
            dateText.text = dateFormat.format(Date(payment.date))
            descriptionText.text = "Payment"
            methodText.text = "via ${payment.method}"

            // Payments typically reduce the customer's "Baki" or increase their "Jama"
            // For a payment, we show it as "Jama" (amount received by shop from customer)
            bakiText.text = "-"
            jamaText.text = "₹${currencyFormatter.format(payment.amount)}"
        }
    }
}