package com.jewelrypos.swarnakhatabook.Animations

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.jewelrypos.swarnakhatabook.R

/**
 * Enhanced animations for SwarnaKhataBook app
 * Provides animation utilities for various UI components
 */
class EnhancedAnimations {

    companion object {
        /**
         * Apply bounce animation to a view
         * Useful for emphasizing important UI elements
         */
        fun bounce(view: View) {
            val bounceY = ObjectAnimator.ofFloat(view, "translationY", 0f, -20f, 0f)
            bounceY.duration = 500
            bounceY.interpolator = DecelerateInterpolator()
            bounceY.start()
        }

        /**
         * Apply shimmer loading effect to a view during data loading
         * Useful as a placeholder while content loads
         */
        fun applyShimmerEffect(view: View, context: Context) {
            val shimmerAnimation = AnimationUtils.loadAnimation(context, R.anim.shimmer_animation)
            view.startAnimation(shimmerAnimation)
        }

        /**
         * Apply shared element transition setup for RecyclerView items
         * Identifies views for transitions between screens
         */
        fun setupSharedElementTransition(view: View, uniqueTransitionName: String) {
            view.transitionName = uniqueTransitionName
        }

        /**
         * Apply staggered animation to RecyclerView items
         * Creates a cascading effect when items appear
         */
        fun applyRecyclerViewLayoutAnimation(recyclerView: RecyclerView, context: Context) {
            val layoutAnimation = AnimationUtils.loadAnimation(context, R.anim.item_animation_from_bottom)
            recyclerView.layoutAnimation = AnimationUtils.loadLayoutAnimation(context, R.anim.layout_animation_from_bottom)
            recyclerView.scheduleLayoutAnimation()
        }

        /**
         * Animate numeric changes in TextViews
         * Useful for counter displays like totals
         */
        fun animateNumericChange(textView: TextView, startValue: Float, endValue: Float, duration: Long = 1000) {
            val animator = ObjectAnimator.ofFloat(startValue, endValue)
            animator.duration = duration
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                textView.text = value.toInt().toString()
            }
            animator.start()
        }

        /**
         * Apply attention-grabbing animation for notifications or alerts
         */
        fun applyAttentionAnimation(view: View) {
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f, 1f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f, 1f)
            val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1f, 0.7f, 1f)

            val animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY, alpha)
            animator.duration = 800
            animator.repeatCount = 1
            animator.start()
        }

        /**
         * Animate the entry of bar charts
         */
        fun animateBarChart(chart: BarChart) {
            chart.animateY(1000)
        }

        /**
         * Animate the entry of line charts with path drawing
         */
        fun animateLineChart(chart: LineChart) {
            chart.animateX(1500)
        }

        /**
         * Animate pie chart with rotation and alpha
         */
        fun animatePieChart(chart: PieChart) {
            chart.animateXY(1000, 1000)
        }

        /**
         * Apply sequential item animations to a LinearLayout or similar ViewGroup
         */
        fun animateItemsSequentially(viewGroup: android.view.ViewGroup, delay: Long = 100) {
            for (i in 0 until viewGroup.childCount) {
                val child = viewGroup.getChildAt(i)
                child.alpha = 0f
                child.translationY = 50f
                
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * delay)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }

        /**
         * Button click animation that provides satisfying feedback
         */
        fun animateButtonClick(button: View) {
            val scaleDown = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.9f)
            val scaleUp = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f)
            
            val downAnimator = ObjectAnimator.ofPropertyValuesHolder(button, scaleDown, scaleUp)
            downAnimator.duration = 100
            
            val scaleDownBack = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1f)
            val scaleUpBack = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1f)
            
            val upAnimator = ObjectAnimator.ofPropertyValuesHolder(button, scaleDownBack, scaleUpBack)
            upAnimator.duration = 100
            
            val set = AnimatorSet()
            set.playSequentially(downAnimator, upAnimator)
            set.start()
        }

    }
} 