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

    private val ordersFragment = OrdersFragment()
    private val invoicesFragment = InvoicesFragment()

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> ordersFragment
            1 -> invoicesFragment
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }

    fun getOrdersFragment(): OrdersFragment = ordersFragment

    fun getInvoicesFragment(): InvoicesFragment = invoicesFragment
}