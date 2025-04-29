package com.jewelrypos.swarnakhatabook.Animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.CopyOnWriteArrayList // <-- Import this

/**
 * Custom RecyclerView item animator that provides a slide-in-up animation
 * with fade and scale effects for a polished look.
 * Uses CopyOnWriteArrayList for better thread safety against ConcurrentModificationException.
 */
class SlideInUpItemAnimator : DefaultItemAnimator() {

    // Use CopyOnWriteArrayList for thread safety during iteration/modification
    private val pendingAdds = CopyOnWriteArrayList<RecyclerView.ViewHolder>()
    private val addAnimations = CopyOnWriteArrayList<Animator>()
    private val removeAnimations = CopyOnWriteArrayList<Animator>()
    private val changeAnimations = CopyOnWriteArrayList<Animator>() // Keep track even if not customized
    private val moveAnimations = CopyOnWriteArrayList<Animator>()   // Keep track even if not customized

    // Helper function to cleanly end animations for a specific holder
    private fun endAnimationInternal(holder: RecyclerView.ViewHolder) {
        holder.itemView.animate().cancel() // Cancel any ongoing property animations

        // Check and remove from pending adds
        if (pendingAdds.remove(holder)) {
            clearAnimatedValues(holder.itemView)
            dispatchAddFinished(holder)
        }

        // Check and cancel/remove from active animations
        // Iterate over copies to avoid modifying while iterating internally
        ArrayList(addAnimations).filter { it is AnimatorSet && targetsViewHolder(it, holder) }.forEach {
            it.cancel() // Cancel should trigger listener to remove from addAnimations
        }
        ArrayList(removeAnimations).filter { it is AnimatorSet && targetsViewHolder(it, holder) }.forEach {
            it.cancel() // Cancel should trigger listener to remove from removeAnimations
        }
        ArrayList(changeAnimations).filter { it is AnimatorSet && targetsViewHolder(it, holder) }.forEach {
            it.cancel() // Cancel should trigger listener to remove from changeAnimations (if applicable)
        }
        ArrayList(moveAnimations).filter { it is AnimatorSet && targetsViewHolder(it, holder) }.forEach {
            it.cancel() // Cancel should trigger listener to remove from moveAnimations (if applicable)
        }

        // Fallback cleanup in case listener doesn't remove immediately or animation wasn't running
        addAnimations.removeIf { it is AnimatorSet && targetsViewHolder(it, holder) }
        removeAnimations.removeIf { it is AnimatorSet && targetsViewHolder(it, holder) }
        changeAnimations.removeIf { it is AnimatorSet && targetsViewHolder(it, holder) }
        moveAnimations.removeIf { it is AnimatorSet && targetsViewHolder(it, holder) }

        // Dispatch appropriate finished state if necessary (redundant calls are handled by base class)
        dispatchChangeFinished(holder, false) // Example for change
        dispatchMoveFinished(holder)    // Example for move
        dispatchRemoveFinished(holder)  // Example for remove

        dispatchFinishedWhenDone() // Check if all animations are done
    }

    // Helper to check if an AnimatorSet targets a specific ViewHolder's item view
    // Note: This is a basic check based on ObjectAnimator targets. Adapt if your AnimatorSet structure is different.
    private fun targetsViewHolder(animatorSet: AnimatorSet, holder: RecyclerView.ViewHolder): Boolean {
        return animatorSet.childAnimations?.any { animator ->
            (animator is ObjectAnimator) && animator.target == holder.itemView
        } ?: false
    }


    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        resetAnimation(holder) // Ensure clean state before starting
        holder.itemView.translationY = holder.itemView.height.toFloat() * 0.5f // Start slightly lower
        holder.itemView.alpha = 0f
        holder.itemView.scaleX = 0.95f // Subtle scale
        holder.itemView.scaleY = 0.95f

        pendingAdds.add(holder) // Add to thread-safe list
        return true
    }

    override fun runPendingAnimations() {
        val hasPendingAdds = pendingAdds.isNotEmpty()
        // Call super first to handle default pending animations (move, change, remove)
        super.runPendingAnimations()

        if (hasPendingAdds) {
            // Create a snapshot for iteration
            val addsToRun = ArrayList(pendingAdds)
            pendingAdds.clear() // Clear the original list

            for (i in addsToRun.indices.reversed()) { // Reverse for staggered effect
                val holder = addsToRun[i]

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(
                    ObjectAnimator.ofFloat(holder.itemView, View.TRANSLATION_Y, 0f),
                    ObjectAnimator.ofFloat(holder.itemView, View.ALPHA, 1f),
                    ObjectAnimator.ofFloat(holder.itemView, View.SCALE_X, 1f),
                    ObjectAnimator.ofFloat(holder.itemView, View.SCALE_Y, 1f)
                )

                animatorSet.duration = 300 // Adjust duration as needed
                animatorSet.startDelay = (addsToRun.size - 1 - i) * 50L // Staggered delay

                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        dispatchAddStarting(holder)
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        animation.removeAllListeners()
                        // Reset properties modified by the animation
                        clearAnimatedValues(holder.itemView) // Ensure final state is clean
                        dispatchAddFinished(holder)
                        addAnimations.remove(animation) // Remove from thread-safe list
                        dispatchFinishedWhenDone()
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        clearAnimatedValues(holder.itemView) // Clean up on cancel
                        // It might be necessary to remove from addAnimations here too if cancelled mid-run
                        if (addAnimations.remove(animation)) {
                            Log.d("SlideInUpAnimator", "Add animation cancelled and removed.")
                        }
                        dispatchAddFinished(holder) // Ensure state is consistent on cancel
                        dispatchFinishedWhenDone()
                    }
                })

                addAnimations.add(animatorSet) // Add to thread-safe list
                animatorSet.start()
            }
        }
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        resetAnimation(holder)
        val animator = AnimatorSet()
        animator.playTogether(
            ObjectAnimator.ofFloat(holder.itemView, View.ALPHA, 0f),
            ObjectAnimator.ofFloat(holder.itemView, View.TRANSLATION_X, -holder.itemView.width.toFloat()) // Slide left
            //ObjectAnimator.ofFloat(holder.itemView, View.SCALE_X, 0.8f), // Optional: scale down
            // ObjectAnimator.ofFloat(holder.itemView, View.SCALE_Y, 0.8f)
        )
        animator.duration = 300 // Adjust duration

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                dispatchRemoveStarting(holder)
            }

            override fun onAnimationEnd(animation: Animator) {
                animation.removeAllListeners()
                // Reset properties modified by the animation AFTER it's finished
                clearAnimatedValues(holder.itemView) // Ensure final state is clean
                dispatchRemoveFinished(holder)
                removeAnimations.remove(animation) // Remove from thread-safe list
                dispatchFinishedWhenDone()
            }

            override fun onAnimationCancel(animation: Animator) {
                clearAnimatedValues(holder.itemView) // Clean up on cancel
                if (removeAnimations.remove(animation)) {
                    Log.d("SlideInUpAnimator", "Remove animation cancelled and removed.")
                }
                dispatchRemoveFinished(holder) // Ensure state is consistent on cancel
                dispatchFinishedWhenDone()
            }
        })

        removeAnimations.add(animator) // Add to thread-safe list
        animator.start()
        return true
    }

    // --- IMPORTANT: Override other animate methods if you customize them ---
    // override fun animateChange(...) { ... add to changeAnimations ... return super.animateChange(...) }
    // override fun animateMove(...) { ... add to moveAnimations ... return super.animateMove(...) }

    private fun clearAnimatedValues(view: View) {
        // Reset all potentially animated properties to their default state
        view.alpha = 1f
        view.translationY = 0f
        view.translationX = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun resetAnimation(holder: RecyclerView.ViewHolder) {
        // Cancel any framework animations and clear potentially stale listener references
        holder.itemView.animate().cancel()
        // Might need to clear other animation types if used (e.g., ViewPropertyAnimator)
        clearAnimatedValues(holder.itemView) // Reset properties immediately
    }

    // Called by RecyclerView when animations should be finished for a specific item
    override fun endAnimation(holder: RecyclerView.ViewHolder) {
        Log.d("SlideInUpAnimator", "endAnimation called for holder: ${holder.adapterPosition}")
        // Call internal helper to handle cleanup across all lists
        endAnimationInternal(holder)
        // Let the superclass handle its internal state cleanup as well
        super.endAnimation(holder)
    }

    // Called by RecyclerView when all animations should be finished (e.g., on detach)
    override fun endAnimations() {
        Log.d("SlideInUpAnimator", "endAnimations called")
        // --- Handle Pending Additions ---
        // Create a snapshot for iteration to avoid issues if modified externally
        val addsToCancel = ArrayList(pendingAdds)
        pendingAdds.clear() // Clear the original list immediately
        for (holder in addsToCancel) {
            clearAnimatedValues(holder.itemView) // Reset visual state
            dispatchAddFinished(holder) // Notify RecyclerView
        }
        if (addsToCancel.isNotEmpty()) {
            Log.d("SlideInUpAnimator", "Cleared ${addsToCancel.size} pending adds.")
        }

        // --- Handle Running Animations ---
        // Create a snapshot of all running animations
        val runningAnimations = ArrayList<Animator>()
        runningAnimations.addAll(addAnimations)
        runningAnimations.addAll(removeAnimations)
        runningAnimations.addAll(changeAnimations)
        runningAnimations.addAll(moveAnimations)

        // Cancel all running animations. Cancellation will trigger listeners
        // which should remove the animations from the CopyOnWriteArrayLists safely.
        runningAnimations.forEach { it.cancel() }

        if (runningAnimations.isNotEmpty()) {
            Log.d("SlideInUpAnimator", "Cancelled ${runningAnimations.size} running animations.")
        }

        // --- Cleanup & Final Dispatch ---
        // While listeners *should* clear the lists, clear them explicitly here
        // as a safeguard, especially if cancellation doesn't trigger listeners reliably.
        addAnimations.clear()
        removeAnimations.clear()
        changeAnimations.clear()
        moveAnimations.clear()

        // Call super.endAnimations() AFTER cancelling custom animations
        super.endAnimations()

        // Final check to dispatch finished if needed (super.endAnimations might do this)
        if (!isRunning()) {
            dispatchAnimationsFinished()
        }
    }


    override fun isRunning(): Boolean {
        // Check if any list still contains items OR if superclass says it's running
        return pendingAdds.isNotEmpty() ||
                addAnimations.isNotEmpty() ||
                removeAnimations.isNotEmpty() ||
                changeAnimations.isNotEmpty() ||
                moveAnimations.isNotEmpty() ||
                super.isRunning() // Important: include super's state
    }

    // Helper to dispatch finish event only when truly done
    private fun dispatchFinishedWhenDone() {
        if (!isRunning) {
            dispatchAnimationsFinished()
            Log.d("SlideInUpAnimator", "All animations finished, dispatched.")
        }
    }
}