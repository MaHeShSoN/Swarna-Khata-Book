package com.jewelrypos.swarnakhatabook.Factorys

import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.PaymentsRepository
import com.jewelrypos.swarnakhatabook.ViewModle.PaymentsViewModel

class PaymentsViewModelFactory(
    private val repository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PaymentsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PaymentsViewModel(repository, connectivityManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}