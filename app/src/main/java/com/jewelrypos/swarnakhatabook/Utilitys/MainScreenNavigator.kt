package com.jewelrypos.swarnakhatabook.Utilitys

import androidx.navigation.fragment.findNavController
import com.jewelrypos.swarnakhatabook.MainScreenFragment
import com.jewelrypos.swarnakhatabook.R
import nl.joery.animatedbottombar.AnimatedBottomBar


class MainScreenNavigator(private val mainScreenFragment: MainScreenFragment) {

    fun navigateToSalesTab() {
        // Find the animated bottom bar
        val bottomNav = mainScreenFragment.requireView().findViewById<AnimatedBottomBar>(R.id.bottom_navigation)
        // Select the sales tab (index should match your menu order)
        bottomNav.selectTabAt(1) // Assuming Sales is the second item (index 1)

        // The tab selection listener is already set in MainScreenFragment
        // This will trigger the navigation to the appropriate fragment
        val navController = mainScreenFragment.childFragmentManager
            .findFragmentById(R.id.inner_nav_host_fragment)
            ?.findNavController()
        navController?.navigate(R.id.salesFragment)
    }

    fun navigateToInventoryTab() {
        // Find the animated bottom bar
        val bottomNav = mainScreenFragment.requireView().findViewById<AnimatedBottomBar>(R.id.bottom_navigation)
        // Select the inventory tab
        bottomNav.selectTabAt(2) // Assuming Inventory is the third item (index 2)

        // The tab selection listener is already set in MainScreenFragment
        // This will trigger the navigation to the appropriate fragment
        val navController = mainScreenFragment.childFragmentManager
            .findFragmentById(R.id.inner_nav_host_fragment)
            ?.findNavController()
        navController?.navigate(R.id.inventoryFragment)
    }

    fun navigateToCustomersTab() {
        // Find the animated bottom bar
        val bottomNav = mainScreenFragment.requireView().findViewById<AnimatedBottomBar>(R.id.bottom_navigation)
        // Select the customers tab
        bottomNav.selectTabAt(3) // Assuming Customers is the fourth item (index 3)

        // The tab selection listener is already set in MainScreenFragment
        // This will trigger the navigation to the appropriate fragment
        val navController = mainScreenFragment.childFragmentManager
            .findFragmentById(R.id.inner_nav_host_fragment)
            ?.findNavController()
        navController?.navigate(R.id.customerFragment)
    }
}