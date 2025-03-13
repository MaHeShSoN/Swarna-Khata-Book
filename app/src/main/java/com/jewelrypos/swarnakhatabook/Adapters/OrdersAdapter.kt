package com.jewelrypos.swarnakhatabook.Adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.Order
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// OrdersAdapter.kt
class OrdersAdapter(
    private var orders: List<Order>
) : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {

    private var onItemClickListener: ((Order) -> Unit)? = null

    fun setOnItemClickListener(listener: (Order) -> Unit) {
        onItemClickListener = listener
    }

    class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val orderNumber: TextView = itemView.findViewById(R.id.orderNumber)
        val orderStatus: TextView = itemView.findViewById(R.id.orderStatus)
        val customerName: TextView = itemView.findViewById(R.id.customerName)
        val itemsCount: TextView = itemView.findViewById(R.id.itemsCount)
        val deliveryDate: TextView = itemView.findViewById(R.id.deliveryDate)
        val totalAmount: TextView = itemView.findViewById(R.id.totalAmount)
        val advanceAmount: TextView = itemView.findViewById(R.id.advanceAmount)
        val card: MaterialCardView = itemView.findViewById(R.id.orderCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]

        holder.orderNumber.text = order.orderNumber
        holder.customerName.text = order.customerName
        holder.itemsCount.text = "${order.items.size} items"

        // Format delivery date
        val formatter1 = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.deliveryDate.text = formatter1.format(Date(order.deliveryDate))

        // Format currency
        val formatter = DecimalFormat("#,##,##0.00")
        holder.totalAmount.text = "₹${formatter.format(order.totalAmount)}"
        holder.advanceAmount.text = "₹${formatter.format(order.advanceAmount)}"

        // Set status text and background color
        holder.orderStatus.text = order.status
        val statusColor = when (order.status.lowercase()) {
            "pending" -> ContextCompat.getColor(holder.itemView.context, R.color.status_pending)
            "in progress" -> ContextCompat.getColor(holder.itemView.context, R.color.status_in_progress)
            "completed" -> ContextCompat.getColor(holder.itemView.context, R.color.status_completed)
            "cancelled" -> ContextCompat.getColor(holder.itemView.context, R.color.status_cancelled)
            else -> ContextCompat.getColor(holder.itemView.context, R.color.my_light_primary)
        }
        holder.orderStatus.backgroundTintList = ColorStateList.valueOf(statusColor)

        // Set click listener
        holder.card.setOnClickListener {
            onItemClickListener?.invoke(order)
        }
    }

    override fun getItemCount() = orders.size

    fun updateOrders(newOrders: List<Order>) {
        val diffCallback = OrderDiffCallback(orders, newOrders)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        orders = newOrders
        diffResult.dispatchUpdatesTo(this)
    }

    class OrderDiffCallback(
        private val oldList: List<Order>,
        private val newList: List<Order>
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