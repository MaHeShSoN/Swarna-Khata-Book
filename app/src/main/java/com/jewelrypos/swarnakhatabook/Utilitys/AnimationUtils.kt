package com.jewelrypos.swarnakhatabook.Utilitys

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.FragmentNavigator
import androidx.recyclerview.widget.RecyclerView
import com.jewelrypos.swarnakhatabook.R

/**
 * Utility class for animations throughout the app
 */
object AnimationUtils {

    // Duration for most animations
    private const val DEFAULT_DURATION = 300L

    /**
     * Apply fade in animation to a view
     */
    fun fadeIn(view: View, duration: Long = DEFAULT_DURATION) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setListener(null)
    }

    /**
     * Apply fade out animation to a view
     */
    fun fadeOut(view: View, duration: Long = DEFAULT_DURATION) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                }
            })
    }

    /**
     * Apply slide up animation to a view
     */
    fun slideUp(view: View) {
        val animation = android.view.animation.AnimationUtils.loadAnimation(
            view.context,
            R.anim.slide_up
        )
        view.visibility = View.VISIBLE
        view.startAnimation(animation)
    }

    /**
     * Apply slide down animation to a view
     */
    fun slideDown(view: View) {
        val animation = android.view.animation.AnimationUtils.loadAnimation(
            view.context,
            R.anim.slide_down
        )
        animation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                view.visibility = View.GONE
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        view.startAnimation(animation)
    }

    /**
     * Apply scale animation to a view
     */
    fun scaleView(view: View, startScale: Float, endScale: Float, duration: Long = DEFAULT_DURATION) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, startScale, endScale)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, startScale, endScale)
        
        scaleX.duration = duration
        scaleY.duration = duration
        
        scaleX.interpolator = AccelerateDecelerateInterpolator()
        scaleY.interpolator = AccelerateDecelerateInterpolator()
        
        scaleX.start()
        scaleY.start()
    }

    /**
     * Apply pulse animation to a view (useful for buttons)
     */
    fun pulse(view: View) {
        scaleView(view, 1f, 0.9f, 100)
        view.postDelayed({
            scaleView(view, 0.9f, 1f, 100)
        }, 100)
    }

    /**
     * Apply shake animation to a view (useful for error feedback)
     */
    fun shake(view: View) {
        val animation = android.view.animation.AnimationUtils.loadAnimation(
            view.context,
            R.anim.shake_animation
        )
        view.startAnimation(animation)
    }

    /**
     * Apply animation to RecyclerView items
     */
    fun animateRecyclerView(recyclerView: RecyclerView) {
        val context = recyclerView.context
        val controller = androidx.recyclerview.widget.DefaultItemAnimator()
        controller.addDuration = DEFAULT_DURATION
        controller.removeDuration = DEFAULT_DURATION
        controller.moveDuration = DEFAULT_DURATION
        controller.changeDuration = DEFAULT_DURATION
        recyclerView.itemAnimator = controller
    }

    /**
     * Get NavOptions for forward navigation with slide animations
     */
    fun getSlideNavOptions(): NavOptions {
        return NavOptions.Builder()
            .setEnterAnim(R.anim.slide_in_right)
            .setExitAnim(R.anim.slide_out_left)
            .setPopEnterAnim(R.anim.slide_in_left)
            .setPopExitAnim(R.anim.slide_out_right)
            .build()
    }

    /**
     * Get NavOptions for forward navigation with fade animations
     */
    fun getFadeNavOptions(): NavOptions {
        return NavOptions.Builder()
            .setEnterAnim(R.anim.fade_in)
            .setExitAnim(R.anim.fade_out)
            .setPopEnterAnim(R.anim.fade_in)
            .setPopExitAnim(R.anim.fade_out)
            .build()
    }

    /**
     * Get NavOptions for forward navigation with scale animations
     */
    fun getScaleNavOptions(): NavOptions {
        return NavOptions.Builder()
            .setEnterAnim(R.anim.scale_in)
            .setExitAnim(R.anim.fade_out)
            .setPopEnterAnim(R.anim.fade_in)
            .setPopExitAnim(R.anim.scale_out)
            .build()
    }
}
