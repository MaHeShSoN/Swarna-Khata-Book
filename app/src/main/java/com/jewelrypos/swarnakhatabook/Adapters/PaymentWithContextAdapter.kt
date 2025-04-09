package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.ViewModle.PaymentsViewModel.PaymentWithContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentWithContextAdapter(
    private var payments: List<PaymentWithContext>
) : RecyclerView.Adapter<PaymentWithContextAdapter.PaymentViewHolder>() {

    interface OnPaymentClickListener {
        fun onPaymentClick(payment: PaymentWithContext)
    }

    private var listener: OnPaymentClickListener? = null

    fun setOnPaymentClickListener(listener: OnPaymentClickListener) {
        this.listener = listener
    }

    class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val paymentIcon: ImageView = itemView.findViewById(R.id.paymentIcon)
        val invoiceNumberText: TextView = itemView.findViewById(R.id.invoiceNumberText)
        val customerNameText: TextView = itemView.findViewById(R.id.customerNameText)
        val paymentAmountText: TextView = itemView.findViewById(R.id.paymentAmountText)
        val paymentMethodText: TextView = itemView.findViewById(R.id.paymentMethodText)
        val paymentDateText: TextView = itemView.findViewById(R.id.paymentDateText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment_with_context, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val paymentContext = payments[position]
        val payment = paymentContext.payment
        val formatter = DecimalFormat("#,##,##0.00")

        // Set amount
        holder.paymentAmountText.text = "₹${formatter.format(payment.amount)}"

        // Set method
        holder.paymentMethodText.text = payment.method

        // Format date with time
        val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        holder.paymentDateText.text = dateFormatter.format(Date(payment.date))

        // Set customer name and invoice number
        holder.customerNameText.text = paymentContext.customerName ?: "Unknown Customer"
        holder.invoiceNumberText.text = paymentContext.invoiceNumber ?: "No Invoice"

        // Set icon based on payment method
        val iconResId = when (payment.method.lowercase()) {
            "cash" -> R.drawable.mdi__cash
            "card" -> R.drawable.ic_payment_card
            "upi" -> R.drawable.material_symbols__upi_pay
            "bank transfer" -> R.drawable.mdi__bank
            "gold exchange" -> R.drawable.uil__gold
            "silver exchange" -> R.drawable.uil__gold
            else -> R.drawable.mdi__currency_inr
        }
        holder.paymentIcon.setImageResource(iconResId)

        // Set click listener for the whole item
        holder.itemView.setOnClickListener {
            listener?.onPaymentClick(paymentContext)
        }
    }

    override fun getItemCount() = payments.size

    fun updatePayments(newPayments: List<PaymentWithContext>) {
        val diffCallback = PaymentDiffCallback(payments, newPayments)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        payments = newPayments
        diffResult.dispatchUpdatesTo(this)
    }

    private class PaymentDiffCallback(
        private val oldList: List<PaymentWithContext>,
        private val newList: List<PaymentWithContext>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].payment.id == newList[newItemPosition].payment.id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPayment = oldList[oldItemPosition]
            val newPayment = newList[newItemPosition]
            return oldPayment.payment.id == newPayment.payment.id &&
                    oldPayment.payment.amount == newPayment.payment.amount &&
                    oldPayment.payment.method == newPayment.payment.method &&
                    oldPayment.payment.date == newPayment.payment.date &&
                    oldPayment.customerName == newPayment.customerName &&
                    oldPayment.invoiceNumber == newPayment.invoiceNumber
        }
    }
}