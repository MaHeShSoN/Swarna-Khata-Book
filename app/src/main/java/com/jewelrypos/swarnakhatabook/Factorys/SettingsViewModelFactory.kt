package com.jewelrypos.swarnakhatabook.Factorys

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.ViewModle.SettingsViewModel

/**
 * Factory for creating a [SettingsViewModel] with a dependency on [UserSubscriptionManager]
 */
class SettingsViewModelFactory(
    private val application: Application,
    private val subscriptionManager: UserSubscriptionManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application, subscriptionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 