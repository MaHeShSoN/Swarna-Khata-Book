package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.ReportType
import com.jewelrypos.swarnakhatabook.R

class ReportTypeAdapter(
    private val onItemClick: (ReportType) -> Unit
) : ListAdapter<ReportType, ReportTypeAdapter.ReportTypeViewHolder>(ReportTypeDiffCallback) {

    var isUserPremium: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportTypeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_type, parent, false)
        return ReportTypeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportTypeViewHolder, position: Int) {
        val reportType = getItem(position)
        holder.bind(reportType)

        if (isUserPremium) {
            holder.itemView.setOnClickListener {
                onItemClick(reportType)
            }
            holder.itemView.isClickable = true
            holder.itemView.alpha = 1.0f
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
            holder.itemView.alpha = 0.5f
        }
    }

    class ReportTypeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.reportIconView)
        private val titleView: TextView = itemView.findViewById(R.id.reportTitle)
        private val descriptionView: TextView = itemView.findViewById(R.id.reportDescription)

        fun bind(reportType: ReportType) {
            iconView.setImageResource(reportType.iconResId)
            titleView.text = reportType.title
            descriptionView.text = reportType.description
        }
    }

    companion object {
        private val ReportTypeDiffCallback = object : DiffUtil.ItemCallback<ReportType>() {
            override fun areItemsTheSame(oldItem: ReportType, newItem: ReportType): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ReportType, newItem: ReportType): Boolean {
                return oldItem == newItem
            }
        }
    }
}