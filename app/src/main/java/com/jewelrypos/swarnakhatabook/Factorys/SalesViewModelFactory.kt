package com.jewelrypos.swarnakhatabook.Factorys

import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel

class SalesViewModelFactory(
    private val repository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SalesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SalesViewModel(repository, connectivityManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}