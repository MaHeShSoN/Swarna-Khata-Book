package com.jewelrypos.swarnakhatabook.Adapters


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.ViewModle.PaymentsViewModel.PaymentWithContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentWithContextAdapter(
    private var payments: List<PaymentWithContext>
) : RecyclerView.Adapter<PaymentWithContextAdapter.PaymentWithContextViewHolder>() {

    interface OnPaymentClickListener {
        fun onPaymentClick(payment: PaymentWithContext)
    }

    private var clickListener: OnPaymentClickListener? = null

    fun setOnPaymentClickListener(listener: OnPaymentClickListener) {
        this.clickListener = listener
    }

    class PaymentWithContextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val invoiceNumberText: TextView = itemView.findViewById(R.id.invoiceNumberText)
        val customerNameText: TextView = itemView.findViewById(R.id.customerNameText)
        val paymentAmountText: TextView = itemView.findViewById(R.id.paymentAmountText)
        val paymentMethodText: TextView = itemView.findViewById(R.id.paymentMethodText)
        val paymentDateText: TextView = itemView.findViewById(R.id.paymentDateText)
        val paymentIcon: ImageView = itemView.findViewById(R.id.paymentIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentWithContextViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment_with_context, parent, false)
        return PaymentWithContextViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentWithContextViewHolder, position: Int) {
        val paymentContext = payments[position]
        val payment = paymentContext.payment

        // Format amount
        val amountFormatter = DecimalFormat("#,##,##0.00")
        holder.paymentAmountText.text = "₹${amountFormatter.format(payment.amount)}"

        // Set invoice number
        holder.invoiceNumberText.text = paymentContext.invoiceNumber

        // Set customer name
        holder.customerNameText.text = paymentContext.customerName

        // Set payment method
        holder.paymentMethodText.text = payment.method

        // Format date
        val dateFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        holder.paymentDateText.text = dateFormatter.format(Date(payment.date))

        // Set payment method icon
        val iconResId = when (payment.method.lowercase()) {
            "cash" -> R.drawable.mdi__cash
            "upi" -> R.drawable.material_symbols__upi_pay
            "card" -> R.drawable.ic_payment_card
            "bank transfer" -> R.drawable.mdi__bank
            else -> R.drawable.mdi__currency_inr
        }
        holder.paymentIcon.setImageResource(iconResId)

        // Set click listener
        holder.itemView.setOnClickListener {
            clickListener?.onPaymentClick(paymentContext)
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
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}