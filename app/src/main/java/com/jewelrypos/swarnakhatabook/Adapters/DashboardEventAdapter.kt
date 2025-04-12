package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.R
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter for upcoming events on the dashboard
 * Updated to use AppNotification instead of PaymentNotification
 */
class DashboardEventAdapter(
    private var events: List<AppNotification>
) : RecyclerView.Adapter<DashboardEventAdapter.EventViewHolder>() {

    interface OnEventClickListener {
        fun onEventClick(notification: AppNotification)
    }

    private var listener: OnEventClickListener? = null

    fun setOnEventClickListener(listener: OnEventClickListener) {
        this.listener = listener
    }

    class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val eventIcon: ImageView = itemView.findViewById(R.id.eventIcon)
        val eventTitle: TextView = itemView.findViewById(R.id.eventTitle)
        val eventDate: TextView = itemView.findViewById(R.id.eventDate)
        val customerName: TextView = itemView.findViewById(R.id.customerName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]

        // Set event title
        holder.eventTitle.text = when (event.type) {
            NotificationType.BIRTHDAY -> "Birthday"
            NotificationType.ANNIVERSARY -> "Anniversary"
            NotificationType.PAYMENT_DUE -> "Payment Due"
            NotificationType.PAYMENT_OVERDUE -> "Payment Overdue"
            else -> event.title
        }

        // Set event icon
        val iconResource = when (event.type) {
            NotificationType.BIRTHDAY -> R.drawable.mingcute__birthday_2_fill
            NotificationType.ANNIVERSARY -> R.drawable.mingcute__anniversary_fill
            NotificationType.PAYMENT_DUE, NotificationType.PAYMENT_OVERDUE -> R.drawable.mdi__currency_inr
            else -> R.drawable.mingcute__notification_fill
        }
        holder.eventIcon.setImageResource(iconResource)

        // Set event color based on type
        val iconColor = when (event.type) {
            NotificationType.BIRTHDAY, NotificationType.ANNIVERSARY -> R.color.my_light_primary
            NotificationType.PAYMENT_DUE -> R.color.status_partial
            NotificationType.PAYMENT_OVERDUE -> R.color.status_unpaid
            else -> R.color.my_light_primary
        }
        holder.eventIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, iconColor))

        // Set customer name
        holder.customerName.text = event.customerName

        // Format date if available
        if (event.createdAt != null) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            holder.eventDate.text = dateFormat.format(event.createdAt)
        } else {
            holder.eventDate.text = "Upcoming"
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            listener?.onEventClick(event)
        }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<AppNotification>) {
        val diffCallback = EventDiffCallback(events, newEvents)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        events = newEvents
        diffResult.dispatchUpdatesTo(this)
    }

    private class EventDiffCallback(
        private val oldList: List<AppNotification>,
        private val newList: List<AppNotification>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old.id == new.id &&
                    old.status == new.status &&
                    old.actionTaken == new.actionTaken
        }
    }
}