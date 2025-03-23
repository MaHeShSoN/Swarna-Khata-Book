package com.jewelrypos.swarnakhatabook.Adapters


import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.jewelrypos.swarnakhatabook.CustomerDashboardFragment
import com.jewelrypos.swarnakhatabook.CustomerInvoicesFragment
import com.jewelrypos.swarnakhatabook.DataClasses.Customer

class CustomerDetailViewPagerAdapter(
    fragment: Fragment,
    private val customer: Customer
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CustomerDashboardFragment.newInstance(customer)
            1 -> CustomerInvoicesFragment.newInstance(customer.id)
            else -> CustomerDashboardFragment.newInstance(customer)
        }
    }
}