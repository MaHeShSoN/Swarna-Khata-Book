package com.jewelrypos.swarnakhatabook.Factorys

import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationViewModel

class NotificationViewModelFactory(
    private val repository: NotificationRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotificationViewModel(repository, connectivityManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}