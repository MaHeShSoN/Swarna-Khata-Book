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
import androidx.recyclerview.widget.SimpleItemAnimator
import com.jewelrypos.swarnakhatabook.R
import androidx.navigation.navOptions

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
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * Apply fade out animation to a view
     */
    fun fadeOut(view: View, duration: Long = DEFAULT_DURATION) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                }
            })
            .start()
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
    fun pulse(view: View, duration: Long = 150) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f, 1f)
        
        scaleX.duration = duration
        scaleY.duration = duration
        
        scaleX.interpolator = AccelerateDecelerateInterpolator()
        scaleY.interpolator = AccelerateDecelerateInterpolator()
        
        scaleX.start()
        scaleY.start()
    }

    /**
     * Apply shake animation to a view (useful for error feedback)
     */
    fun shake(view: View, duration: Long = 500) {
        val animator = ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f)
        animator.duration = duration
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }

    /**
     * Apply animation to RecyclerView items
     */
    fun animateRecyclerView(recyclerView: RecyclerView) {
        // Disable default animations for smoother performance
        val animator = recyclerView.itemAnimator as SimpleItemAnimator?
        animator?.supportsChangeAnimations = false
    }

    /**
     * Get NavOptions for forward navigation with slide animations
     */
    fun getSlideNavOptions(): NavOptions {
        return navOptions {
            anim {
                enter = R.anim.slide_in_right
                exit = R.anim.slide_out_left
                popEnter = R.anim.slide_in_left
                popExit = R.anim.slide_out_right
            }
        }
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
