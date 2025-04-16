package com.jewelrypos.swarnakhatabook.Factorys

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.ViewModle.SalesViewModel

class SalesViewModelFactory(
    private val repository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager,
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SalesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SalesViewModel(repository, connectivityManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}