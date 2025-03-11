package com.jewelrypos.swarnakhatabook.Animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import androidx.recyclerview.widget.RecyclerView


object FallDownAnimator {
    fun animateFallDown(holder: RecyclerView.ViewHolder) {
        val itemView = holder.itemView

        // Start the animation off-screen and rotated.
        itemView.translationY = -itemView.height.toFloat()
        itemView.rotationX = -90f
        itemView.alpha = 0f

        // Animate the view to its original position and rotation.
        itemView.animate()
            .translationY(0f)
            .rotationX(0f)
            .alpha(1f)
            .setDuration(500) // Adjust duration as needed
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Optional: Perform actions after the animation finishes
                }
            })
            .start()
    }

    fun animateFallDown(itemView: View) {
        // Start the animation off-screen and rotated.
        itemView.translationY = -itemView.height.toFloat()
        itemView.rotationX = -90f
        itemView.alpha = 0f

        // Animate the view to its original position and rotation.
        itemView.animate()
            .translationY(0f)
            .rotationX(0f)
            .alpha(1f)
            .setDuration(500) // Adjust duration as needed
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Optional: Perform actions after the animation finishes
                }
            })
            .start()
    }
}