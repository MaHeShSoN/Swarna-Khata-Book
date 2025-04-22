// main/java/com/jewelrypos/swarnakhatabook/Adapters/SettingsAdapter.kt
package com.jewelrypos.swarnakhatabook.Adapters

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.BadgeType
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.R

/**
 * Adapter for displaying settings items in a RecyclerView.
 * Optimized to cache ColorStateList objects in the ViewHolder.
 */
class SettingsAdapter(
    private val items: List<SettingsItem>,
    private val isPremium: Boolean, // Still needed for click handler logic in fragment
    private val onItemClick: (SettingsItem) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        // Pass context to ViewHolder for efficient color loading
        return SettingsViewHolder(view, parent.context)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        // Pass only the item to bind
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    // --- Optimized ViewHolder ---
    inner class SettingsViewHolder(itemView: View, private val context: Context) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.settingIconView)
        private val titleView: TextView = itemView.findViewById(R.id.settingTitle)
        private val subtitleView: TextView = itemView.findViewById(R.id.settingSubtitle)
        private val badgeView: TextView = itemView.findViewById(R.id.newBadge)

        // --- Cache ColorStateLists ---
        // Cache common colors/tints needed frequently
        private val whiteIconTint: ColorStateList? = ContextCompat.getColorStateList(context, R.color.white)
        private val premiumBadgeBackground: ColorStateList? = ContextCompat.getColorStateList(context, R.color.premium_color)
        private val newBadgeBackground: ColorStateList? = ContextCompat.getColorStateList(context, R.color.status_paid)
        private val daysLeftBadgeBackground: ColorStateList? = ContextCompat.getColorStateList(context, R.color.status_unpaid)
        private val defaultBadgeBackground: ColorStateList? = ContextCompat.getColorStateList(context, R.color.my_light_secondary)
        private val defaultIconBackgroundTint: ColorStateList? = ContextCompat.getColorStateList(context, R.color.my_background_light_grey_color)

        // Cache badge text colors
        private val blackTextColor: Int = ContextCompat.getColor(context, R.color.black)
        private val whiteTextColor: Int = ContextCompat.getColor(context, R.color.white)

        // Cache unique icon background tints (Load them once per ViewHolder creation)
        // Use a map for cleaner access based on item ID
        private val iconBackgroundTints: Map<String, ColorStateList?> = mapOf(
            "debug_subscription" to ContextCompat.getColorStateList(context, R.color.my_background_light_pink_color),
            "subscription_status" to ContextCompat.getColorStateList(context, R.color.my_background_light_gold_color),
            "shop_details" to ContextCompat.getColorStateList(context, R.color.my_background_light_green_color),
            "invoice_format" to ContextCompat.getColorStateList(context, R.color.my_background_light_purple_color),
            "invoice_template" to ContextCompat.getColorStateList(context, R.color.my_background_light_blue_color),
            "reports" to ContextCompat.getColorStateList(context, R.color.my_background_light_yellow_color),
            "recycling_bin" to ContextCompat.getColorStateList(context, R.color.my_background_light_red_color),
            "account_settings" to ContextCompat.getColorStateList(context, R.color.my_background_light_orange_color),
            "app_updates" to ContextCompat.getColorStateList(context, R.color.my_background_light_teal_color),
            "about_language" to ContextCompat.getColorStateList(context, R.color.my_background_light_pink_color)
        )
        // --- End Color Caching ---


        // Bind method now only takes the item
        fun bind(item: SettingsItem) {
            iconView.setImageResource(item.iconResId)
            
            // Resolve string resources for title and subtitle
            titleView.text = context.getString(item.titleResId)
            
            // Handle subtitle with potential format args
            subtitleView.text = if (item.subtitleResIdArgs != null) {
                context.getString(item.subtitleResId, *item.subtitleResIdArgs.toTypedArray())
            } else {
                context.getString(item.subtitleResId)
            }

            // Badge logic using BadgeType for more consistent styling
            if (item.badgeTextResId != null) {
                badgeView.visibility = View.VISIBLE
                
                // Resolve badge text with potential format args
                badgeView.text = if (item.badgeTextResIdArgs != null) {
                    context.getString(item.badgeTextResId, *item.badgeTextResIdArgs.toTypedArray())
                } else {
                    context.getString(item.badgeTextResId)
                }
                
                // Determine badge styling based on badge type rather than text
                val (badgeBackground, badgeTextColor) = when (item.badgeType) {
                    BadgeType.PREMIUM -> premiumBadgeBackground to whiteTextColor
                    BadgeType.NEW -> newBadgeBackground to whiteTextColor
                    BadgeType.DAYS_LEFT, BadgeType.EXPIRED -> daysLeftBadgeBackground to whiteTextColor
                    BadgeType.TRIAL -> defaultBadgeBackground to whiteTextColor
                    BadgeType.NONE -> defaultBadgeBackground to whiteTextColor
                }
                
                badgeView.backgroundTintList = badgeBackground
                badgeView.setTextColor(badgeTextColor)
            } else {
                badgeView.visibility = View.GONE
            }

            // --- Apply Icon Tint and Background Tint using cached values ---

            // Apply white tint to the icon itself (cached)
            iconView.imageTintList = whiteIconTint

            // Apply unique background tint based on ID using the cached map
            iconView.backgroundTintList = iconBackgroundTints[item.id] ?: defaultIconBackgroundTint

            // --- End Tint Logic ---

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(item) // isPremium is no longer needed here, handled in fragment
            }
        }
    }
}
