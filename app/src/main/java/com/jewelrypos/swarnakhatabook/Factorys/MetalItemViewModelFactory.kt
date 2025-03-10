package com.jewelrypos.swarnakhatabook.Factorys


import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.ViewModle.MetalItemViewModel

class MetalItemViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MetalItemViewModel::class.java)) {
            return MetalItemViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}