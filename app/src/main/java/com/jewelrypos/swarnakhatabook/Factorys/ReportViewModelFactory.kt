package com.jewelrypos.swarnakhatabook.Factorys

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel

class ReportViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    private val TAG = "ReportViewModelFactory"

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        Log.d(TAG, "create: Creating ViewModel for class: ${modelClass.simpleName}")
        val startTime = System.currentTimeMillis()
        
        return try {
            if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
                Log.d(TAG, "create: Creating ReportViewModel instance")
                @Suppress("UNCHECKED_CAST")
                ReportViewModel(application).also {
                    Log.d(TAG, "create: ReportViewModel created successfully in ${System.currentTimeMillis() - startTime}ms")
                } as T
            } else {
                Log.e(TAG, "create: Unknown ViewModel class: ${modelClass.simpleName}")
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        } catch (e: Exception) {
            Log.e(TAG, "create: Error creating ViewModel", e)
            throw e
        }
    }
}