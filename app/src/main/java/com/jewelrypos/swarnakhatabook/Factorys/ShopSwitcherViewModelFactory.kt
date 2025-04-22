package com.jewelrypos.swarnakhatabook.Factorys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.ViewModle.ShopSwitcherViewModel

/**
 * Factory for creating a ShopSwitcherViewModel with the necessary dependencies
 */
class ShopSwitcherViewModelFactory : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShopSwitcherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShopSwitcherViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 