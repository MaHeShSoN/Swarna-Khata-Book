// main/java/com/jewelrypos/swarnakhatabook/Adapters/SettingsAdapter.kt
package com.jewelrypos.swarnakhatabook.Adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.R

/**
 * Adapter for displaying settings items in a RecyclerView.
 * Updated to give each item a unique light icon background color and set all icon tints to white.
 */
class SettingsAdapter(
    private val items: List<SettingsItem>,
    private val isPremium: Boolean, // Still needed for potential badge logic, but not icon tint
    private val onItemClick: (SettingsItem) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        // Pass isPremium status, though it's not used for tinting anymore
        holder.bind(items[position], isPremium)
    }

    override fun getItemCount(): Int = items.size

    inner class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.settingIconView)
        private val titleView: TextView = itemView.findViewById(R.id.settingTitle)
        private val subtitleView: TextView = itemView.findViewById(R.id.settingSubtitle)
        private val badgeView: TextView = itemView.findViewById(R.id.newBadge)

        fun bind(item: SettingsItem, isPremiumUser: Boolean) {
            iconView.setImageResource(item.iconResId)
            titleView.text = item.title
            subtitleView.text = item.subtitle

            // Badge logic remains the same
            if (item.badgeText != null) {
                badgeView.visibility = View.VISIBLE
                badgeView.text = item.badgeText
                val badgeColorRes = when (item.badgeText) {
                    "PREMIUM" -> R.color.premium_color
                    "NEW" -> R.color.status_paid
                    "DAYS LEFT", "EXPIRED" -> R.color.status_unpaid
                    else -> R.color.my_light_secondary
                }
                val badgeTextColorRes = when (item.badgeText) {
                    "PREMIUM" -> R.color.black
                    "DAYS LEFT", "EXPIRED" -> R.color.white
                    else -> R.color.white
                }
                badgeView.backgroundTintList = ContextCompat.getColorStateList(itemView.context, badgeColorRes)
                badgeView.setTextColor(ContextCompat.getColor(itemView.context, badgeTextColorRes))
            } else {
                badgeView.visibility = View.GONE
            }

            // --- Apply Unique Icon Background Tint and White Icon Tint ---
            val context = itemView.context

            // Define Icon Tint (White for all icons)
            val whiteIconTint = ContextCompat.getColorStateList(context, R.color.white)

            // Apply white tint to the icon itself for all items
            iconView.imageTintList = whiteIconTint

            // Define ColorStateLists for Background Tints using unique light colors
            // IMPORTANT: Ensure these color resources exist in your colors.xml!
            val defaultBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_grey_color) // Fallback/default
            val debugSubscriptionBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_pink_color) // Example Pink
            val subscriptionBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_gold_color) // Example Gold/Light Yellow
            val shopDetailsBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_green_color) // Example Green
            val invoiceFormatBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_purple_color) // Example Purple
            val invoiceTemplateBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_blue_color) // Blue
            val reportsBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_yellow_color) // Yellow
            val recyclingBinBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_red_color) // Red
            val accountSettingsBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_orange_color) // Example Orange
            val appUpdatesBackgroundTint = ContextCompat.getColorStateList(context, R.color.my_background_light_teal_color) // Example Teal


            // Apply unique background tint based on ID
            when (item.id) {
                "debug_subscription" -> iconView.backgroundTintList = debugSubscriptionBackgroundTint
                "subscription_status" -> iconView.backgroundTintList = subscriptionBackgroundTint
                "shop_details" -> iconView.backgroundTintList = shopDetailsBackgroundTint
                "invoice_format" -> iconView.backgroundTintList = invoiceFormatBackgroundTint
                "invoice_template" -> iconView.backgroundTintList = invoiceTemplateBackgroundTint
                "reports" -> iconView.backgroundTintList = reportsBackgroundTint
                "recycling_bin" -> iconView.backgroundTintList = recyclingBinBackgroundTint
                "account_settings" -> iconView.backgroundTintList = accountSettingsBackgroundTint
                "app_updates" -> iconView.backgroundTintList = appUpdatesBackgroundTint
                else -> iconView.backgroundTintList = defaultBackgroundTint // Use a default light color for any others
            }
            // --- End Tint Logic ---

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}