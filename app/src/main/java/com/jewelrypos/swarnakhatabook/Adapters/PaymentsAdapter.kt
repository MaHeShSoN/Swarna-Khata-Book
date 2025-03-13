package com.jewelrypos.swarnakhatabook.Adapters



import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// PaymentsAdapter.kt
class PaymentsAdapter(
    private var payments: List<Payment>
) : RecyclerView.Adapter<PaymentsAdapter.PaymentViewHolder>() {

    class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val paymentAmount: TextView = itemView.findViewById(R.id.paymentAmount)
        val paymentMethod: TextView = itemView.findViewById(R.id.paymentMethod)
        val paymentDate: TextView = itemView.findViewById(R.id.paymentDate)
        val paymentIcon: ImageView = itemView.findViewById(R.id.paymentIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = payments[position]

        // Format payment amount
        val formatter = DecimalFormat("#,##,##0.00")
        holder.paymentAmount.text = "₹${formatter.format(payment.amount)}"

        // Set payment method
        holder.paymentMethod.text = payment.method

        // Format payment date
        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.paymentDate.text = dateFormatter.format(Date(payment.date))

        // Set payment icon based on method
        val iconResId = when (payment.method.lowercase()) {
            "cash" -> R.drawable.mdi__cash
            "card" -> R.drawable.ic_payment_card
            "upi" -> R.drawable.material_symbols__upi_pay
            "bank transfer" -> R.drawable.mdi__bank
            "old gold" -> R.drawable.uil__gold
            else -> R.drawable.mdi__currency_inr
        }
        holder.paymentIcon.setImageResource(iconResId)
    }

    override fun getItemCount() = payments.size

    fun updatePayments(newPayments: List<Payment>) {
        val diffCallback = PaymentDiffCallback(payments, newPayments)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        payments = newPayments
        diffResult.dispatchUpdatesTo(this)
    }

    class PaymentDiffCallback(
        private val oldList: List<Payment>,
        private val newList: List<Payment>
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