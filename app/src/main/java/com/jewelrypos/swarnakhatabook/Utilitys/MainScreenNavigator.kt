package com.jewelrypos.swarnakhatabook.Utilitys

import androidx.navigation.fragment.findNavController
import com.jewelrypos.swarnakhatabook.MainScreenFragment
import com.jewelrypos.swarnakhatabook.R
import me.ibrahimsn.lib.SmoothBottomBar


class MainScreenNavigator(private val mainScreenFragment: MainScreenFragment) {

    fun navigateToSalesTab() {
        // Find the smooth bottom bar
        val bottomNav = mainScreenFragment.requireView().findViewById<SmoothBottomBar>(R.id.bottom_navigation)
        // Select the sales tab (index should match your menu order)
        bottomNav.itemActiveIndex = 1 // Assuming Sales is the second item (index 1)

        // Update the onItemSelected with a lambda
        bottomNav.onItemSelected = { selectedIndex ->
            if (selectedIndex == 1) {
                // Update inner navigation
                val navController = mainScreenFragment.childFragmentManager
                    .findFragmentById(R.id.inner_nav_host_fragment)
                    ?.findNavController()
                navController?.navigate(R.id.salesFragment)
            }
        }
    }

    fun navigateToInventoryTab() {
        // Find the smooth bottom bar
        val bottomNav = mainScreenFragment.requireView().findViewById<SmoothBottomBar>(R.id.bottom_navigation)
        // Select the inventory tab
        bottomNav.itemActiveIndex = 2 // Assuming Inventory is the third item (index 2)

        // Update the onItemSelected with a lambda
        bottomNav.onItemSelected = { selectedIndex ->
            if (selectedIndex == 2) {
                // Update inner navigation
                val navController = mainScreenFragment.childFragmentManager
                    .findFragmentById(R.id.inner_nav_host_fragment)
                    ?.findNavController()
                navController?.navigate(R.id.inventoryFragment)
            }
        }
    }

    fun navigateToCustomersTab() {
        // Find the smooth bottom bar
        val bottomNav = mainScreenFragment.requireView().findViewById<SmoothBottomBar>(R.id.bottom_navigation)
        // Select the customers tab
        bottomNav.itemActiveIndex = 3 // Assuming Customers is the fourth item (index 3)

        // Update the onItemSelected with a lambda
        bottomNav.onItemSelected = { selectedIndex ->
            if (selectedIndex == 3) {
                // Update inner navigation
                val navController = mainScreenFragment.childFragmentManager
                    .findFragmentById(R.id.inner_nav_host_fragment)
                    ?.findNavController()
                navController?.navigate(R.id.customerFragment)
            }
        }
    }
}