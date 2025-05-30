package com.jewelrypos.swarnakhatabook.Factorys


import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.ViewModle.MetalItemViewModel
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Repository.MetalItemRepository



class MetalItemViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MetalItemViewModel::class.java)) {
            val firestore = FirebaseFirestore.getInstance()
            val auth = FirebaseAuth.getInstance()
            val repository = MetalItemRepository(firestore, auth)
            @Suppress("UNCHECKED_CAST")
            return MetalItemViewModel(application,repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}