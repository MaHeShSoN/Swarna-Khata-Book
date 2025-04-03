package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentsAdapter(
    private var payments: List<Payment>
) : RecyclerView.Adapter<PaymentsAdapter.PaymentViewHolder>() {

    interface OnPaymentActionListener {
        fun onRemovePayment(payment: Payment)
        fun onEditPayment(payment: Payment)
    }

    private var listener: OnPaymentActionListener? = null

    fun setOnPaymentActionListener(listener: OnPaymentActionListener) {
        this.listener = listener
    }

    class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val paymentAmount: TextView = itemView.findViewById(R.id.paymentAmount)
        val paymentMethod: TextView = itemView.findViewById(R.id.paymentMethod)
        val paymentDate: TextView = itemView.findViewById(R.id.paymentDate)
        val paymentIcon: ImageView = itemView.findViewById(R.id.paymentIcon)
        val removeButton: ImageButton? = itemView.findViewById(R.id.removeButton)
        val editButton: ImageButton? = itemView.findViewById(R.id.editButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment_editable, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = payments[position]
        val formatter = DecimalFormat("#,##,##0.00")

        // Set amount
        holder.paymentAmount.text = "₹${formatter.format(payment.amount)}"

        holder.paymentMethod.text = payment.method



        // Format date
        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.paymentDate.text = dateFormatter.format(Date(payment.date))

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

        // Set action button listeners if they exist
        holder.editButton?.setOnClickListener {
            listener?.onEditPayment(payment)
        }

        holder.removeButton?.setOnClickListener {
            listener?.onRemovePayment(payment)
        }
    }

    override fun getItemCount() = payments.size

    fun updatePayments(newPayments: List<Payment>) {
        val diffCallback = PaymentDiffCallback(payments, newPayments)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        payments = newPayments
        diffResult.dispatchUpdatesTo(this)
    }

    // Getter for the current payments list
    fun getPayments(): List<Payment> = payments

    private class PaymentDiffCallback(
        private val oldList: List<Payment>,
        private val newList: List<Payment>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldPayment = oldList[oldItemPosition]
            val newPayment = newList[newItemPosition]
            return oldPayment.id == newPayment.id &&
                    oldPayment.amount == newPayment.amount &&
                    oldPayment.method == newPayment.method &&
                    oldPayment.date == newPayment.date
        }
    }
}