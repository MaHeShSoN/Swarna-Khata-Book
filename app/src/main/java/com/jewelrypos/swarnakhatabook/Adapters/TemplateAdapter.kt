package com.jewelrypos.swarnakhatabook.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jewelrypos.swarnakhatabook.DataClasses.InvoiceTemplate
import com.jewelrypos.swarnakhatabook.Enums.TemplateType
import com.jewelrypos.swarnakhatabook.R

/**
 * Adapter for displaying invoice templates in a RecyclerView
 */
class TemplateAdapter(
    private val templates: List<InvoiceTemplate>,
    private val selectedTemplateType: TemplateType,
    private val onTemplateSelected: (InvoiceTemplate) -> Unit
) : RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder>() {

    private var selectedPosition = templates.indexOfFirst { it.templateType == selectedTemplateType }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_invoice_templete, parent, false)
        return TemplateViewHolder(view)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        holder.bind(templates[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = templates.size

    inner class TemplateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val templateCard: MaterialCardView = itemView.findViewById(R.id.templateCard)
        private val templateImage: ImageView = itemView.findViewById(R.id.templateImage)
        private val templateName: TextView = itemView.findViewById(R.id.templateName)
        private val templateDescription: TextView = itemView.findViewById(R.id.templateDescription)
        private val premiumBadge: TextView = itemView.findViewById(R.id.premiumBadge)
        private val selectedIndicator: View = itemView.findViewById(R.id.selectedIndicator)

        fun bind(template: InvoiceTemplate, isSelected: Boolean) {
            templateName.text = template.name
            templateDescription.text = template.description
            templateImage.setImageResource(template.previewResId)

            // Show premium badge if applicable
            premiumBadge.visibility = if (template.isPremium) View.VISIBLE else View.GONE

            // Update selected state
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            templateCard.strokeWidth = if (isSelected) 2 else 1

            // Set click listener
            itemView.setOnClickListener {
                val previousSelected = selectedPosition
                selectedPosition = adapterPosition

                // Update UI for previous selected item
                if (previousSelected != -1) {
                    notifyItemChanged(previousSelected)
                }

                // Update UI for newly selected item
                notifyItemChanged(selectedPosition)

                // Notify listener
                onTemplateSelected(template)
            }
        }
    }
}