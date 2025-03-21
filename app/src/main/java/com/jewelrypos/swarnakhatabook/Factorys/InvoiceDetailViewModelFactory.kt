package com.jewelrypos.swarnakhatabook.Factorys



import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.ViewModle.InvoiceDetailViewModel

class InvoiceDetailViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InvoiceDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InvoiceDetailViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}