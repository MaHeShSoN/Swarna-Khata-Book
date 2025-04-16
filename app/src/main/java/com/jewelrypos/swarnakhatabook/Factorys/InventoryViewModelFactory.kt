package com.jewelrypos.swarnakhatabook.Factorys

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel

class InventoryViewModelFactory(
    private val repository: InventoryRepository,
    private val connectivityManager: ConnectivityManager,
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InventoryViewModel(repository, connectivityManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}