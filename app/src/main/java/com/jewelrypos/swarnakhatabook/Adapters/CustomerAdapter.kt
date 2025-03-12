package com.jewelrypos.swarnakhatabook.Adapters


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.R

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
        holder.customerName.text = "${currentCustomer.firstName} ${currentCustomer.lastName}"
        holder.customerType.text = currentCustomer.customerType

        val balanceText = when {
            currentCustomer.balanceType == "Credit" && currentCustomer.openingBalance > 0 ->
                "To Receive: ₹${currentCustomer.openingBalance}"
            currentCustomer.balanceType == "Debit" && currentCustomer.openingBalance > 0 ->
                "To Pay: ₹${currentCustomer.openingBalance}"
            else -> "Balance: ₹0.00"
        }
        holder.customerBalance.text = balanceText

        val address = if (currentCustomer.streetAddress.isNotEmpty()) {
            "${currentCustomer.streetAddress}, ${currentCustomer.city}"
        } else {
            "${currentCustomer.city}, ${currentCustomer.state}"
        }

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

        holder.itemView.setOnClickListener {
            itemClickListener?.onCustomerClick(currentCustomer)
        }

        // Apply animation
        setAnimation(holder.itemView, position)
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