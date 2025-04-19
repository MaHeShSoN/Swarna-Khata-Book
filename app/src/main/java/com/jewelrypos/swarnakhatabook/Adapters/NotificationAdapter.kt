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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationPriority
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private var notifications: List<AppNotification>
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    interface OnNotificationActionListener {
        fun onNotificationClick(notification: AppNotification)
        fun onActionButtonClick(notification: AppNotification)
        fun onDismissButtonClick(notification: AppNotification)
    }

    private var listener: OnNotificationActionListener? = null

    fun setOnNotificationActionListener(listener: OnNotificationActionListener) {
        this.listener = listener
    }

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.notificationCard)
        val title: TextView = view.findViewById(R.id.notificationTitle)
        val message: TextView = view.findViewById(R.id.notificationMessage)
        val date: TextView = view.findViewById(R.id.notificationDate)
        val priorityIndicator: View = view.findViewById(R.id.priorityIndicator)
        val icon: ImageView = view.findViewById(R.id.notificationIcon)
        val actionButton: MaterialButton = view.findViewById(R.id.actionButton)
        val dismissButton: MaterialButton = view.findViewById(R.id.dismissButton)
        val detailsContainer: View = view.findViewById(R.id.detailsContainer)
        val currentBalanceValue: TextView = view.findViewById(R.id.currentBalanceValue)
        val creditLimitValue: TextView = view.findViewById(R.id.creditLimitValue)
        val usagePercentage: TextView = view.findViewById(R.id.usagePercentage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        val context = holder.itemView.context
        val formatter = DecimalFormat("#,##,##0.00")

        // Set notification title and message
        holder.title.text = notification.title
        holder.message.text = notification.message

        // Format date
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        holder.date.text = notification.createdAt?.let { dateFormat.format(it) } ?: "Unknown date"

        // Set priority indicator color
        val priorityColor = when (notification.priority) {
            NotificationPriority.HIGH -> R.color.status_unpaid
            NotificationPriority.NORMAL -> R.color.status_partial
            NotificationPriority.LOW -> R.color.my_light_primary
        }
        holder.priorityIndicator.setBackgroundColor(ContextCompat.getColor(context, priorityColor))

        // Set notification icon based on type
        val iconResource = when (notification.type) {
            NotificationType.APP_UPDATE -> R.drawable.mingcute__notification_fill
            NotificationType.CREDIT_LIMIT -> R.drawable.material_symbols_warning_rounded
            NotificationType.PAYMENT_DUE -> R.drawable.mdi__currency_inr
            NotificationType.PAYMENT_OVERDUE -> R.drawable.mdi__currency_inr
            NotificationType.BIRTHDAY -> R.drawable.mingcute__birthday_2_fill
            NotificationType.ANNIVERSARY -> R.drawable.mingcute__anniversary_fill
            NotificationType.GENERAL -> R.drawable.mingcute__notification_fill
        }
        holder.icon.setImageResource(iconResource)

        // Only show details for credit limit notifications
        if (notification.type == NotificationType.CREDIT_LIMIT) {
            holder.detailsContainer.visibility = View.VISIBLE
            notification.currentBalance?.let {
                holder.currentBalanceValue.text = "₹${formatter.format(it)}"
            }
            notification.creditLimit?.let {
                holder.creditLimitValue.text = "₹${formatter.format(it)}"
            }

            // Calculate usage percentage
            val usagePercentage = if (notification.creditLimit != null && notification.creditLimit > 0
                && notification.currentBalance != null) {
                ((notification.currentBalance / notification.creditLimit) * 100).toInt()
            } else {
                0
            }
            holder.usagePercentage.text = "$usagePercentage%"

            // Set percentage text color based on value
            val percentageColor = when {
                usagePercentage >= 90 -> R.color.status_unpaid
                usagePercentage >= 75 -> R.color.status_partial
                else -> R.color.my_light_primary
            }
            holder.usagePercentage.setTextColor(ContextCompat.getColor(context, percentageColor))
        } else {
            holder.detailsContainer.visibility = View.GONE
        }

        // Configure action button based on notification type
        val actionText = when (notification.type) {
            NotificationType.APP_UPDATE -> "Update"
            NotificationType.CREDIT_LIMIT -> "View Customer"
            NotificationType.PAYMENT_DUE, NotificationType.PAYMENT_OVERDUE -> "Add Payment"
            NotificationType.BIRTHDAY, NotificationType.ANNIVERSARY -> "Send Wishes"
            NotificationType.GENERAL -> {
                if (notification.relatedItemId != null) "View Item" else "View"
            }
        }
        holder.actionButton.text = actionText

        // Set card appearance based on read status
        if (notification.status == NotificationStatus.UNREAD) {
            holder.card.strokeWidth = 2
            holder.card.strokeColor = ContextCompat.getColor(context, priorityColor)
            holder.card.setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.my_light_surface)
            )
        } else {
            holder.card.strokeWidth = 1
            holder.card.strokeColor = ContextCompat.getColor(context, R.color.my_light_outline)
            holder.card.setCardBackgroundColor(
                ContextCompat.getColor(context, R.color.my_light_on_surface)
            )
        }

        // Click listeners
        holder.card.setOnClickListener {
            listener?.onNotificationClick(notification)
        }

        holder.actionButton.setOnClickListener {
            listener?.onActionButtonClick(notification)
        }

        holder.dismissButton.setOnClickListener {
            listener?.onDismissButtonClick(notification)
        }
    }

    override fun getItemCount() = notifications.size

    fun updateNotifications(newNotifications: List<AppNotification>) {
        val diffCallback = NotificationDiffCallback(notifications, newNotifications)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        notifications = newNotifications
        diffResult.dispatchUpdatesTo(this)
    }

    private class NotificationDiffCallback(
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