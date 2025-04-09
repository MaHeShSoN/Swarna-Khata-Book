package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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

        // Payment details fields
        val paymentDetailsContainer: LinearLayout = itemView.findViewById(R.id.paymentDetailsContainer)
        val primaryDetailContainer: LinearLayout = itemView.findViewById(R.id.primaryDetailContainer)
        val primaryDetailLabel: TextView = itemView.findViewById(R.id.primaryDetailLabel)
        val primaryDetailValue: TextView = itemView.findViewById(R.id.primaryDetailValue)
        val secondaryDetailContainer: LinearLayout = itemView.findViewById(R.id.secondaryDetailContainer)
        val secondaryDetailLabel: TextView = itemView.findViewById(R.id.secondaryDetailLabel)
        val secondaryDetailValue: TextView = itemView.findViewById(R.id.secondaryDetailValue)

        // Reference field
        val referenceContainer: LinearLayout = itemView.findViewById(R.id.referenceContainer)
        val referenceText: TextView = itemView.findViewById(R.id.referenceText)
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

        // Set up payment-specific details
        setupPaymentDetails(holder, payment)

        // Show reference if available
        if (payment.reference.isNotEmpty()) {
            holder.referenceContainer.visibility = View.VISIBLE
            holder.referenceText.text = payment.reference
        } else {
            holder.referenceContainer.visibility = View.GONE
        }

        // Set click listener for the whole item
        holder.itemView.setOnClickListener {
            listener?.onPaymentClick(paymentContext)
        }
    }

    private fun setupPaymentDetails(holder: PaymentViewHolder, payment: com.jewelrypos.swarnakhatabook.DataClasses.Payment) {
        // Default is to hide the details container
        holder.paymentDetailsContainer.visibility = View.GONE

        // Check if there are any details to show
        if (payment.details.isEmpty()) {
            return
        }

        when (payment.method.lowercase()) {
            "upi" -> setupUpiDetails(holder, payment)
            "card" -> setupCardDetails(holder, payment)
            "bank transfer" -> setupBankDetails(holder, payment)
            "gold exchange" -> setupGoldDetails(holder, payment)
            "silver exchange" -> setupSilverDetails(holder, payment)
        }
    }

    private fun setupUpiDetails(holder: PaymentViewHolder, payment: com.jewelrypos.swarnakhatabook.DataClasses.Payment) {
        val details = payment.details
        val upiId = details["upiID"] as? String
        val upiApp = details["upiApp"] as? String

        if (upiId.isNullOrEmpty() && upiApp.isNullOrEmpty()) {
            holder.paymentDetailsContainer.visibility = View.GONE
            return
        }

        holder.paymentDetailsContainer.visibility = View.VISIBLE

        // Set UPI ID if available
        if (!upiId.isNullOrEmpty()) {
            holder.primaryDetailContainer.visibility = View.VISIBLE
            holder.primaryDetailLabel.text = "UPI ID:"
            holder.primaryDetailValue.text = upiId
        } else {
            holder.primaryDetailContainer.visibility = View.GONE
        }

        // Set UPI App if available
        if (!upiApp.isNullOrEmpty()) {
            holder.secondaryDetailContainer.visibility = View.VISIBLE
            holder.secondaryDetailLabel.text = "App:"
            holder.secondaryDetailValue.text = upiApp
        } else {
            holder.secondaryDetailContainer.visibility = View.GONE
        }
    }

    private fun setupCardDetails(holder: PaymentViewHolder, payment: com.jewelrypos.swarnakhatabook.DataClasses.Payment) {
        val details = payment.details
        val cardType = details["cardType"] as? String
        val last4Digits = details["last4Digits"] as? String

        if (cardType.isNullOrEmpty() && last4Digits.isNullOrEmpty()) {
            holder.paymentDetailsContainer.visibility = View.GONE
            return
        }

        holder.paymentDetailsContainer.visibility = View.VISIBLE

        // Set card type if available
        if (!cardType.isNullOrEmpty()) {
            holder.primaryDetailContainer.visibility = View.VISIBLE
            holder.primaryDetailLabel.text = "Card Type:"
            holder.primaryDetailValue.text = cardType
        } else {
            holder.primaryDetailContainer.visibility = View.GONE
        }

        // Set last 4 digits if available
        if (!last4Digits.isNullOrEmpty()) {
            holder.secondaryDetailContainer.visibility = View.VISIBLE
            holder.secondaryDetailLabel.text = "Card Number:"
            holder.secondaryDetailValue.text = "xxxx-xxxx-xxxx-$last4Digits"
        } else {
            holder.secondaryDetailContainer.visibility = View.GONE
        }
    }

    private fun setupBankDetails(holder: PaymentViewHolder, payment: com.jewelrypos.swarnakhatabook.DataClasses.Payment) {
        val details = payment.details
        val bankName = details["bankName"] as? String
        val accountNumber = details["accountNumber"] as? String

        if (bankName.isNullOrEmpty() && accountNumber.isNullOrEmpty()) {
            holder.paymentDetailsContainer.visibility = View.GONE
            return
        }

        holder.paymentDetailsContainer.visibility = View.VISIBLE

        // Set bank name if available
        if (!bankName.isNullOrEmpty()) {
            holder.primaryDetailContainer.visibility = View.VISIBLE
            holder.primaryDetailLabel.text = "Bank:"
            holder.primaryDetailValue.text = bankName
        } else {
            holder.primaryDetailContainer.visibility = View.GONE
        }

        // Set account number if available
        if (!accountNumber.isNullOrEmpty()) {
            holder.secondaryDetailContainer.visibility = View.VISIBLE
            holder.secondaryDetailLabel.text = "Account:"

            // Mask account number for privacy
            val maskedNumber = if (accountNumber.length > 4) {
                "xxxx${accountNumber.takeLast(4)}"
            } else {
                accountNumber
            }

            holder.secondaryDetailValue.text = maskedNumber
        } else {
            holder.secondaryDetailContainer.visibility = View.GONE
        }
    }

    private fun setupGoldDetails(holder: PaymentViewHolder, payment: com.jewelrypos.swarnakhatabook.DataClasses.Payment) {
        val details = payment.details
        val weight = details["weight"] as? Double
        val purity = details["purity"] as? Double
        val rate = details["rate"] as? Double

        if (weight == null && purity == null && rate == null) {
            holder.paymentDetailsContainer.visibility = View.GONE
            return
        }

        holder.paymentDetailsContainer.visibility = View.VISIBLE

        // Set weight if available
        if (weight != null) {
            holder.primaryDetailContainer.visibility = View.VISIBLE
            holder.primaryDetailLabel.text = "Weight:"
            holder.primaryDetailValue.text = "${weight}g"
        } else {
            holder.primaryDetailContainer.visibility = View.GONE
        }

        // Set purity if available
        if (purity != null) {
            holder.secondaryDetailContainer.visibility = View.VISIBLE
            holder.secondaryDetailLabel.text = "Purity:"
            holder.secondaryDetailValue.text = "${purity}%"
        } else if (rate != null) {
            // If no purity but rate is available, show that instead
            holder.secondaryDetailContainer.visibility = View.VISIBLE
            holder.secondaryDetailLabel.text = "Rate:"
            holder.secondaryDetailValue.text = "₹${DecimalFormat("#,##,##0.00").format(rate)}/g"
        } else {
            holder.secondaryDetailContainer.visibility = View.GONE
        }
    }

    private fun setupSilverDetails(holder: PaymentViewHolder, payment: com.jewelrypos.swarnakhatabook.DataClasses.Payment) {
        val details = payment.details
        val weight = details["weight"] as? Double
        val purity = details["purity"] as? Double
        val rate = details["rate"] as? Double

        if (weight == null && purity == null && rate == null) {
            holder.paymentDetailsContainer.visibility = View.GONE
            return
        }

        holder.paymentDetailsContainer.visibility = View.VISIBLE

        // Set weight if available
        if (weight != null) {
            holder.primaryDetailContainer.visibility = View.VISIBLE
            holder.primaryDetailLabel.text = "Weight:"
            holder.primaryDetailValue.text = "${weight}g"
        } else {
            holder.primaryDetailContainer.visibility = View.GONE
        }

        // Set purity if available
        if (purity != null) {
            holder.secondaryDetailContainer.visibility = View.VISIBLE
            holder.secondaryDetailLabel.text = "Purity:"
            holder.secondaryDetailValue.text = "${purity}%"
        } else if (rate != null) {
            // If no purity but rate is available, show that instead
            holder.secondaryDetailContainer.visibility = View.VISIBLE
            holder.secondaryDetailLabel.text = "Rate:"
            holder.secondaryDetailValue.text = "₹${DecimalFormat("#,##,##0.00").format(rate)}/g"
        } else {
            holder.secondaryDetailContainer.visibility = View.GONE
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