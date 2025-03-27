package com.jewelrypos.swarnakhatabook.Factorys

import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import com.jewelrypos.swarnakhatabook.ViewModle.ItemDetailViewModel

class ItemDetailViewModelFactory(
    private val repository: InventoryRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ItemDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ItemDetailViewModel(repository, connectivityManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}