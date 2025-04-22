package com.jewelrypos.swarnakhatabook.Factorys

import android.app.Application
import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.RecycledItemsRepository
import com.jewelrypos.swarnakhatabook.ViewModle.RecyclingBinViewModel

class RecyclingBinViewModelFactory(
    private val application: Application,
    private val recycledItemsRepository: RecycledItemsRepository,
    private val invoiceRepository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecyclingBinViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecyclingBinViewModel(
                application,
                recycledItemsRepository,
                invoiceRepository,
                connectivityManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}