package com.jewelrypos.swarnakhatabook.Adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat

class CustomerAdapter(
    private var customerList: List<Customer>,
    private val itemClickListener: OnCustomerClickListener? = null
) : RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder>() {

    class CustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val customerCard: MaterialCardView = itemView.findViewById(R.id.customerCard)
        val customerName: TextView = itemView.findViewById(R.id.customerName)
        val customerType: TextView = itemView.findViewById(R.id.customerType)
        val customerBalance: TextView = itemView.findViewById(R.id.customerBalance)

        // New credit limit UI elements
        val creditLimitContainer: LinearLayout = itemView.findViewById(R.id.creditLimitContainer)
        val creditLimitProgress: LinearProgressIndicator = itemView.findViewById(R.id.creditLimitProgress)
        val creditLimitPercentage: TextView = itemView.findViewById(R.id.creditLimitPercentage)
    }

    interface OnCustomerClickListener {
        fun onCustomerClick(customer: Customer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer, parent, false)
        return CustomerViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val currentCustomer = customerList[position]
        val formatter = DecimalFormat("#,##,##0.00")

        holder.customerName.text = "${currentCustomer.firstName} ${currentCustomer.lastName}"
        holder.customerType.text = currentCustomer.customerType

        // Determine the balance text (now using both opening balance and current balance)
        val balanceText = when {
            // If there's a current balance that's different from opening balance, show both
            currentCustomer.currentBalance != 0.0 -> {
                when {
                    currentCustomer.balanceType == "Credit" && currentCustomer.currentBalance > 0 ->
                        "To Receive: ₹${formatter.format(currentCustomer.currentBalance)}"
                    currentCustomer.balanceType == "Debit" && currentCustomer.currentBalance > 0 ->
                        "To Pay: ₹${formatter.format(currentCustomer.currentBalance)}"
                    else -> "Balance: ₹${formatter.format(currentCustomer.currentBalance)}"
                }
            }
            // Otherwise fall back to showing just the opening balance
            currentCustomer.balanceType == "Credit" && currentCustomer.openingBalance > 0 ->
                "Opening: To Receive: ₹${formatter.format(currentCustomer.openingBalance)}"
            currentCustomer.balanceType == "Debit" && currentCustomer.openingBalance > 0 ->
                "Opening: To Pay: ₹${formatter.format(currentCustomer.openingBalance)}"
            else -> "Balance: ₹0.00"
        }
        holder.customerBalance.text = balanceText

        // Set different background color based on customer type
        if (currentCustomer.customerType.equals("Wholesaler", ignoreCase = true)) {
            holder.customerCard.setCardBackgroundColor(
                holder.itemView.context.getColor(R.color.my_light_secondary_container)
            )
        } else {
            holder.customerCard.setCardBackgroundColor(
                holder.itemView.context.getColor(R.color.my_light_surface)
            )
        }

        // Show credit limit information if applicable
        if (currentCustomer.balanceType == "Credit" &&
            currentCustomer.creditLimit > 0.0 &&
            currentCustomer.currentBalance > 0.0) {

            val percentage = calculateCreditUsagePercentage(currentCustomer)
            holder.creditLimitContainer.visibility = View.VISIBLE
            holder.creditLimitProgress.progress = percentage.toInt()
            holder.creditLimitPercentage.text = "$percentage%"

            // Set progress color based on percentage
            val progressColor = when {
                percentage >= 90 -> R.color.status_unpaid // Red for > 90%
                percentage >= 75 -> R.color.status_partial // Orange for > 75%
                else -> R.color.my_light_primary // Default gold color
            }

            holder.creditLimitProgress.setIndicatorColor(
                ContextCompat.getColor(holder.itemView.context, progressColor)
            )

            // Set percentage text color to match progress
            holder.creditLimitPercentage.setTextColor(
                ContextCompat.getColor(holder.itemView.context, progressColor)
            )
        } else {
            holder.creditLimitContainer.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            itemClickListener?.onCustomerClick(currentCustomer)
        }

        // Apply animation
        setAnimation(holder.itemView, position)
    }

    /**
     * Calculate the percentage of credit limit used
     * @return Percentage of credit used (0-100)
     */
    private fun calculateCreditUsagePercentage(customer: Customer): Int {
        if (customer.creditLimit <= 0.0 || customer.currentBalance <= 0.0) {
            return 0
        }

        val percentage = (customer.currentBalance / customer.creditLimit) * 100
        // Cap at 100% for display purposes
        return percentage.coerceAtMost(100.0).toInt()
    }

    private var lastPosition = -1

    private fun setAnimation(view: View, position: Int) {
        // If this position hasn't been displayed yet
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(view.context, R.anim.animation_item_enter)
            view.startAnimation(animation)
            lastPosition = position
        }
    }

    override fun getItemCount() = customerList.size

    override fun onViewDetachedFromWindow(holder: CustomerViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewDetachedFromWindow(holder)
    }

    fun updateList(newList: List<Customer>) {
        val diffCallback = CustomerDiffCallback(customerList, newList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        customerList = newList
        diffResult.dispatchUpdatesTo(this)
    }

    class CustomerDiffCallback(
        private val oldList: List<Customer>,
        private val newList: List<Customer>
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