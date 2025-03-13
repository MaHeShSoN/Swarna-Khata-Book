package com.jewelrypos.swarnakhatabook.Adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.jewelrypos.swarnakhatabook.TabFragment.InvoicesFragment
import com.jewelrypos.swarnakhatabook.TabFragment.OrdersFragment

// SalesViewPagerAdapter.kt
class SalesViewPagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> OrdersFragment()
            1 -> InvoicesFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
