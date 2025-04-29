package com.jewelrypos.swarnakhatabook.Animations

import android.view.View
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.FragmentNavigatorExtras
import android.os.Bundle

/**
 * Manages shared element transitions between fragments
 * Makes it easier to implement beautiful transitions between screens
 */
class SharedElementTransitionManager {

    companion object {
        /**
         * Setup a shared element transition from a list item to a detail screen
         * 
         * @param sourceView The view in the source fragment to animate from
         * @param transitionName A unique transition name to identify this element
         * @param fragment The current fragment
         * @param navController The navigation controller
         * @param destinationId The destination fragment ID
         * @param args Any arguments to pass to the destination fragment
         */
        fun navigateWithSharedElement(
            sourceView: View,
            transitionName: String,
            fragment: Fragment,
            navController: NavController,
            destinationId: Int,
            args: Bundle? = null
        ) {
            // Set the transition name on the view
            ViewCompat.setTransitionName(sourceView, transitionName)
            
            // Create the extras for the transition
            val extras = FragmentNavigatorExtras(sourceView to transitionName)
            
            // Navigate with the shared element
            navController.navigate(
                destinationId,
                args,
                null,
                extras
            )
        }
        
        /**
         * Setup the shared element transition in the receiving fragment
         * Call this method in onCreate() of the destination fragment
         * 
         * @param fragment The destination fragment
         */
        fun setupEnterTransition(fragment: Fragment) {
            // Postpone the transition until all views are loaded and measured
            fragment.postponeEnterTransition()
            
            // Set the duration of the shared element transition
            fragment.sharedElementEnterTransition = androidx.transition.TransitionInflater.from(
                fragment.requireContext()
            ).inflateTransition(android.R.transition.move)
                .setDuration(300L)
                
            fragment.sharedElementReturnTransition = androidx.transition.TransitionInflater.from(
                fragment.requireContext()
            ).inflateTransition(android.R.transition.move)
                .setDuration(250L)
        }
        
        /**
         * Start the postponed transition once the destination view is ready
         * Call this method once your destination view is fully initialized
         * 
         * @param fragment The destination fragment
         * @param targetView The target view in the destination fragment (optional)
         */
        fun startPostponedTransition(fragment: Fragment, targetView: View? = null) {
            if (targetView != null) {
                // Wait for the target view to be fully laid out
                targetView.post {
                    fragment.startPostponedEnterTransition()
                }
            } else {
                // Just start the transition
                fragment.startPostponedEnterTransition()
            }
        }
    }
} 