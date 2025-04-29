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

        // Determine the balance text - always use currentBalance for consistency
        val balanceText = when {
            currentCustomer.currentBalance != 0.0 -> {
                when {
                    currentCustomer.balanceType == "Credit" && currentCustomer.currentBalance > 0 ->
                        "To Receive: ₹${formatter.format(currentCustomer.currentBalance)}"

                    currentCustomer.balanceType == "Debit" && currentCustomer.currentBalance > 0 ->
                        "To Pay: ₹${formatter.format(currentCustomer.currentBalance)}"

                    else -> "Balance: ₹${formatter.format(currentCustomer.currentBalance)}"
                }
            }

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

        // Set transition name for shared element transitions
        holder.customerCard.transitionName = "customer_${currentCustomer.id}"

        holder.itemView.setOnClickListener {
            itemClickListener?.onCustomerClick(currentCustomer)
        }
        setAnimation(holder.itemView, position)
    }

    /**
     * Calculate the percentage of credit limit used
     * @return Percentage of credit used (0-100)
     */

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

    // Helper method to get position for a specific customer by ID
    fun getPositionForCustomer(customer: Customer): Int {
        return customerList.indexOfFirst { it.id == customer.id }
    }
}