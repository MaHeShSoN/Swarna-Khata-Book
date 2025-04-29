package com.jewelrypos.swarnakhatabook.Animations

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.R

/**
 * Utility class to handle shimmer loading effects for RecyclerViews
 */
class ShimmerLoader(private val context: Context) {

    /**
     * Show shimmer placeholder items in a RecyclerView while content is loading
     *
     * @param recyclerView The RecyclerView to show shimmer placeholders in
     * @param shimmerLayoutResId The resource ID of the shimmer placeholder layout
     * @param itemCount Number of shimmer items to display
     */
    fun showShimmer(recyclerView: RecyclerView, shimmerLayoutResId: Int, itemCount: Int = 5) {
        recyclerView.adapter = ShimmerAdapter(context, shimmerLayoutResId, itemCount)
    }

    /**
     * Hide shimmer placeholders and restore the original adapter
     *
     * @param recyclerView The RecyclerView currently showing shimmer
     * @param originalAdapter The original adapter to restore
     */
    fun hideShimmer(recyclerView: RecyclerView, originalAdapter: RecyclerView.Adapter<*>) {
        recyclerView.adapter = originalAdapter
    }

    /**
     * RecyclerView adapter for displaying shimmer placeholder items
     */
    private class ShimmerAdapter(
        private val context: Context,
        private val shimmerLayoutResId: Int,
        private val itemCount: Int
    ) : RecyclerView.Adapter<ShimmerAdapter.ShimmerViewHolder>() {

        class ShimmerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShimmerViewHolder {
            val view = LayoutInflater.from(context).inflate(shimmerLayoutResId, parent, false)
            return ShimmerViewHolder(view)
        }

        override fun onBindViewHolder(holder: ShimmerViewHolder, position: Int) {
            // Apply shimmer animation to the item view
            val shimmerAnimation = AnimationUtils.loadAnimation(context, R.anim.shimmer_animation)
            holder.itemView.startAnimation(shimmerAnimation)
        }

        override fun getItemCount(): Int = itemCount
    }
} 