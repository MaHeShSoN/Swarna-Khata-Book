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

class CustomerStatementAdapter :
    ListAdapter<Any, RecyclerView.ViewHolder>(CustomerStatementDiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val currencyFormatter = DecimalFormat("#,##,##0.00")

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
                return when {
                    oldItem is Invoice && newItem is Invoice -> oldItem == newItem
                    oldItem is Payment && newItem is Payment -> oldItem == newItem
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
                InvoiceViewHolder(view)
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

    inner class InvoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val invoiceNumberText: TextView = itemView.findViewById(R.id.invoiceNumberText)
        private val debitText: TextView = itemView.findViewById(R.id.debitText)
        private val creditText: TextView = itemView.findViewById(R.id.creditText)

        fun bind(invoice: Invoice) {
            // Format the date
            dateText.text = dateFormat.format(Date(invoice.invoiceDate))

            // Set invoice information
            descriptionText.text = "Invoice"
            invoiceNumberText.text = invoice.invoiceNumber

            // Calculate unpaid amount (as of invoice creation)
            val unpaidAmount = invoice.totalAmount - invoice.paidAmount

            // For an invoice, we typically show it as a debit (amount customer owes)
            debitText.text = "₹${currencyFormatter.format(unpaidAmount)}"
            creditText.text = "-"
        }
    }

    inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val methodText: TextView = itemView.findViewById(R.id.methodText)
        private val debitText: TextView = itemView.findViewById(R.id.debitText)
        private val creditText: TextView = itemView.findViewById(R.id.creditText)

        fun bind(payment: Payment) {
            // Format the date
            dateText.text = dateFormat.format(Date(payment.date))

            // Set payment information
            descriptionText.text = "Payment"
            methodText.text = "via ${payment.method}"

            // For a payment, we typically show it as a credit (amount paid by customer)
            debitText.text = "-"
            creditText.text = "₹${currencyFormatter.format(payment.amount)}"
        }
    }
}