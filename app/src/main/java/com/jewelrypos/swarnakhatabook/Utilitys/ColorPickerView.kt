package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.R

/**
 * Custom view for color grid selection
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var recyclerView: RecyclerView
    private var adapter: ColorAdapter
    private var selectedColorPosition = 0

    // List of default colors
    private val colorOptions = listOf(
        R.color.color_green,
        R.color.color_blue,
        R.color.color_purple,
        R.color.color_red,
        R.color.color_indigo,
        R.color.color_gold,
        R.color.color_orange,
        R.color.my_light_primary,
        R.color.my_light_secondary,
        R.color.status_paid,
        R.color.status_partial,
        R.color.status_unpaid,
        R.color.black
    )

    // Callback for color selection
    var onColorSelected: ((Int) -> Unit)? = null

    init {
        // Inflate the layout
        val view = LayoutInflater.from(context).inflate(R.layout.view_color_picker, this, true)

        // Get recycler view
        recyclerView = view.findViewById(R.id.colorRecyclerView)

        // Setup recycler view
        recyclerView.layoutManager = GridLayoutManager(context, 5)
        adapter = ColorAdapter(colorOptions)
        recyclerView.adapter = adapter
    }

    /**
     * Set the selected color
     */
    fun setSelectedColor(@ColorRes colorRes: Int) {
        val index = colorOptions.indexOf(colorRes)
        if (index != -1) {
            selectedColorPosition = index
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * Get the currently selected color resource
     */
    fun getSelectedColorRes(): Int {
        return colorOptions[selectedColorPosition]
    }

    /**
     * Inner class for color adapter
     */
    private inner class ColorAdapter(private val colors: List<Int>) :
        RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_color, parent, false)
            return ColorViewHolder(view)
        }

        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            holder.bind(colors[position], position == selectedColorPosition)
        }

        override fun getItemCount(): Int = colors.size

        inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val colorView: View = itemView.findViewById(R.id.colorView)
            private val checkmark: ImageView = itemView.findViewById(R.id.checkmark)

            fun bind(@ColorRes colorRes: Int, isSelected: Boolean) {
                // Set color
                colorView.setBackgroundColor(ContextCompat.getColor(context, colorRes))

                // Show checkmark if selected
                checkmark.visibility = if (isSelected) View.VISIBLE else View.GONE

                // Set click listener
                itemView.setOnClickListener {
                    val previousSelected = selectedColorPosition
                    selectedColorPosition = adapterPosition

                    // Update UI
                    notifyItemChanged(previousSelected)
                    notifyItemChanged(selectedColorPosition)

                    // Notify callback
                    onColorSelected?.invoke(colorRes)
                }
            }
        }
    }
}