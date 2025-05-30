package com.jewelrypos.swarnakhatabook.Factorys

import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel

class CustomerViewModelFactory(
    private val repository: CustomerRepository,
    private val connectivityManager: ConnectivityManager,
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CustomerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CustomerViewModel(repository, connectivityManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}