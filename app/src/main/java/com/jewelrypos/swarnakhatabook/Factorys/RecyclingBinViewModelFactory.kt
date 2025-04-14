package com.jewelrypos.swarnakhatabook.Factorys

import android.net.ConnectivityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.RecycledItemsRepository
import com.jewelrypos.swarnakhatabook.ViewModle.RecyclingBinViewModel

class RecyclingBinViewModelFactory(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val connectivityManager: ConnectivityManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecyclingBinViewModel::class.java)) {
            val recycledItemsRepository = RecycledItemsRepository(firestore, auth)
            val invoiceRepository = InvoiceRepository(firestore, auth)

            @Suppress("UNCHECKED_CAST")
            return RecyclingBinViewModel(
                recycledItemsRepository,
                invoiceRepository,
                connectivityManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}