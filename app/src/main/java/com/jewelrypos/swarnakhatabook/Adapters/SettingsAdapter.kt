package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.R

/**
 * Adapter for displaying settings items in a RecyclerView
 */
class SettingsAdapter(
    private val items: List<SettingsItem>,
    private val onItemClick: (SettingsItem) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return SettingsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingsViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class SettingsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.settingIconView)
        private val titleView: TextView = itemView.findViewById(R.id.settingTitle)
        private val subtitleView: TextView = itemView.findViewById(R.id.settingSubtitle)
        private val badgeView: TextView = itemView.findViewById(R.id.newBadge)

        fun bind(item: SettingsItem) {
            iconView.setImageResource(item.iconResId)
            titleView.text = item.title
            subtitleView.text = item.subtitle

            // Show badge if present
            if (item.badgeText != null) {
                badgeView.visibility = View.VISIBLE
                badgeView.text = item.badgeText
            } else {
                badgeView.visibility = View.GONE
            }

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}