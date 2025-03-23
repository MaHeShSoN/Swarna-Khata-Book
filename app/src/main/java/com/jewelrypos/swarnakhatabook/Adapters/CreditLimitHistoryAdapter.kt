package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.DataClasses.CreditLimitChange
import com.jewelrypos.swarnakhatabook.R
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

class CreditLimitHistoryAdapter(
    private var changes: List<CreditLimitChange>
) : RecyclerView.Adapter<CreditLimitHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val changeDate: TextView = view.findViewById(R.id.changeDate)
        val changedBy: TextView = view.findViewById(R.id.changedBy)
        val previousLimit: TextView = view.findViewById(R.id.previousLimit)
        val newLimit: TextView = view.findViewById(R.id.newLimit)
        val changeReason: TextView = view.findViewById(R.id.changeReason)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_credit_limit_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val change = changes[position]
        val formatter = DecimalFormat("#,##,##0.00")

        // Format date
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        holder.changeDate.text = change.changeDate?.let { dateFormat.format(it) } ?: "Unknown date"

        // Set changed by
        holder.changedBy.text = "By: ${change.changedBy}"

        // Format currency values
        holder.previousLimit.text = "₹${formatter.format(change.previousLimit)}"
        holder.newLimit.text = "₹${formatter.format(change.newLimit)}"

        // Set reason for change
        holder.changeReason.text = change.reason.ifEmpty { "No reason provided" }
    }

    override fun getItemCount() = changes.size

    fun updateChanges(newChanges: List<CreditLimitChange>) {
        val diffCallback = CreditLimitDiffCallback(changes, newChanges)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        changes = newChanges
        diffResult.dispatchUpdatesTo(this)
    }

    private class CreditLimitDiffCallback(
        private val oldList: List<CreditLimitChange>,
        private val newList: List<CreditLimitChange>
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