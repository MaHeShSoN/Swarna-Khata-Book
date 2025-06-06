package com.jewelrypos.swarnakhatabook.Adapters
//
import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat


// Change constructor, extend PagingDataAdapter
class CustomerAdapter(
    private val itemClickListener: OnCustomerClickListener? = null
) : PagingDataAdapter<Customer, CustomerAdapter.CustomerViewHolder>(CustomerDiffCallback) { // Use the DiffUtil.ItemCallback

    class CustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val customerCard: MaterialCardView = itemView.findViewById(R.id.customerCard)
        val customerName: TextView = itemView.findViewById(R.id.customerName)
        val customerType: TextView = itemView.findViewById(R.id.customerType)
        val customerBalance: TextView = itemView.findViewById(R.id.customerBalance)
        // Add any other views accessed here
    }

    interface OnCustomerClickListener {
        fun onCustomerClick(customer: Customer)
        // Add other callbacks if needed, e.g., onEditClick, onDeleteClick
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_customer, parent, false)
        return CustomerViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
        val currentCustomer = getItem(position) // Use getItem(position) for PagingDataAdapter
        Log.d("CustomerAdapter", "Binding customer at position $position: ${currentCustomer?.firstName} (ID: ${currentCustomer?.id})")


        currentCustomer?.let { customer ->
            val formatter = DecimalFormat("#,##,##0.00")

            holder.customerName.text = "${customer.firstName} ${customer.lastName}"
            holder.customerType.text = customer.customerType

            val balanceText = when (customer.balanceType) {
                "Baki" -> holder.itemView.context.getString(R.string.baki_amount, formatter.format(customer.currentBalance))
                "Jama" -> holder.itemView.context.getString(R.string.jama_amount, formatter.format(customer.currentBalance))
                else -> holder.itemView.context.getString(R.string.settled_amount, "0.00") // Assuming 0 balance is settled
            }
            holder.customerBalance.text = balanceText

            if (customer.customerType.equals("Wholesaler", ignoreCase = true)) {
                holder.customerCard.setCardBackgroundColor(
                    holder.itemView.context.getColor(R.color.my_light_secondary_container)
                )
            } else {
                holder.customerCard.setCardBackgroundColor(
                    holder.itemView.context.getColor(R.color.my_light_surface)
                )
            }

            holder.customerCard.transitionName = "customer_${customer.id}"

            holder.itemView.setOnClickListener {
                itemClickListener?.onCustomerClick(customer)
            }
            // Animation: PagingDataAdapter might recycle views differently.
            // Test if this animation still works as expected or if it needs adjustment.
            setAnimation(holder.itemView, position)
        }
    }

    // Remove updateList method, PagingDataAdapter handles data submission via submitData()

    // DiffUtil.ItemCallback object for PagingDataAdapter
    companion object CustomerDiffCallback : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Customer, newItem: Customer): Boolean {
            return oldItem == newItem // Relies on Customer being a data class
        }
    }

    private var lastPosition = -1 // Keep for animation if needed

    private fun setAnimation(view: View, position: Int) {
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(view.context, R.anim.animation_item_enter)
            view.startAnimation(animation)
            lastPosition = position
        }
    }

    // getPositionForCustomer will not work reliably with PagingDataAdapter as items can be null or not yet loaded.
    // If you need to find an item, you might need to iterate through snapshots or query the PagingSource differently.
    // fun getPositionForCustomer(customer: Customer): Int { ... } // Remove or rethink
}





//class   CustomerAdapter(
//    private var customerList: List<Customer>,
//    private val itemClickListener: OnCustomerClickListener? = null
//) : RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder>() {
//
//    class CustomerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val customerCard: MaterialCardView = itemView.findViewById(R.id.customerCard)
//        val customerName: TextView = itemView.findViewById(R.id.customerName)
//        val customerType: TextView = itemView.findViewById(R.id.customerType)
//        val customerBalance: TextView = itemView.findViewById(R.id.customerBalance)
//
//
//    }
//
//    interface OnCustomerClickListener {
//        fun onCustomerClick(customer: Customer)
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomerViewHolder {
//        val itemView = LayoutInflater.from(parent.context)
//            .inflate(R.layout.item_customer, parent, false)
//        return CustomerViewHolder(itemView)
//    }
//
//    override fun onBindViewHolder(holder: CustomerViewHolder, position: Int) {
//        val currentCustomer = customerList[position]
//        Log.d("CustomerAdapter", "Binding customer at position $position: ${currentCustomer.firstName} ${currentCustomer.lastName} (ID: ${currentCustomer.id})")
//
//        val formatter = DecimalFormat("#,##,##0.00")
//
//        holder.customerName.text = "${currentCustomer.firstName} ${currentCustomer.lastName}"
//        holder.customerType.text = currentCustomer.customerType
//
//        // New balance display logic using currentBalance as the single source of truth
//        val balanceText = when (currentCustomer.balanceType) {
//            "Baki" -> {
//                holder.itemView.context.getString(R.string.baki_amount, formatter.format(currentCustomer.currentBalance))
//            }
//            "Jama" -> {
//                holder.itemView.context.getString(R.string.jama_amount, formatter.format(currentCustomer.currentBalance))
//            }
//            else -> {
//                holder.itemView.context.getString(R.string.settled_amount, "0.00")
//            }
//        }
//
//        Log.d("CustomerAdapter", "Customer ${currentCustomer.firstName} balance display:")
//        Log.d("CustomerAdapter", "Current Balance: ${currentCustomer.currentBalance}")
//        Log.d("CustomerAdapter", "Balance Type: ${if (currentCustomer.currentBalance < 0) "Baki" else if (currentCustomer.currentBalance > 0) "Jama" else "Settled"}")
//        Log.d("CustomerAdapter", "Display Text: $balanceText")
//
//        holder.customerBalance.text = balanceText
//
//        // Set different background color based on customer type
//        if (currentCustomer.customerType.equals("Wholesaler", ignoreCase = true)) {
//            holder.customerCard.setCardBackgroundColor(
//                holder.itemView.context.getColor(R.color.my_light_secondary_container)
//            )
//        } else {
//            holder.customerCard.setCardBackgroundColor(
//                holder.itemView.context.getColor(R.color.my_light_surface)
//            )
//        }
//
//        // Set transition name for shared element transitions
//        holder.customerCard.transitionName = "customer_${currentCustomer.id}"
//
//        holder.itemView.setOnClickListener {
//            itemClickListener?.onCustomerClick(currentCustomer)
//        }
//        setAnimation(holder.itemView, position)
//    }
//
//    /**
//     * Calculate the percentage of credit limit used
//     * @return Percentage of credit used (0-100)
//     */
//
//    private var lastPosition = -1
//
//    private fun setAnimation(view: View, position: Int) {
//        // If this position hasn't been displayed yet
//        if (position > lastPosition) {
//            val animation = AnimationUtils.loadAnimation(view.context, R.anim.animation_item_enter)
//            view.startAnimation(animation)
//            lastPosition = position
//        }
//    }
//
//    override fun getItemCount() = customerList.size
//
//    override fun onViewDetachedFromWindow(holder: CustomerViewHolder) {
//        holder.itemView.clearAnimation()
//        super.onViewDetachedFromWindow(holder)
//    }
//
//    fun updateList(newList: List<Customer>) {
//        Log.d("CustomerAdapter", "Updating list. Old size: ${customerList.size}, New size: ${newList.size}")
//        Log.d("CustomerAdapter", "New customer IDs: ${newList.map { it.id }}")
//        Log.d("CustomerAdapter", "New customer Names: ${newList.map { "${it.firstName} ${it.lastName}" }}")
//
//        val diffCallback = CustomerDiffCallback(customerList, newList)
//        val diffResult = DiffUtil.calculateDiff(diffCallback)
//        customerList = newList
//        diffResult.dispatchUpdatesTo(this)
//    }
//
//    class CustomerDiffCallback(
//        private val oldList: List<Customer>,
//        private val newList: List<Customer>
//    ) : DiffUtil.Callback() {
//        override fun getOldListSize() = oldList.size
//        override fun getNewListSize() = newList.size
//
//        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
//            return oldList[oldItemPosition].id == newList[newItemPosition].id
//        }
//
//        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
//            return oldList[oldItemPosition] == newList[newItemPosition]
//        }
//    }
//
//    // Helper method to get position for a specific customer by ID
//    fun getPositionForCustomer(customer: Customer): Int {
//        return customerList.indexOfFirst { it.id == customer.id }
//    }
//}