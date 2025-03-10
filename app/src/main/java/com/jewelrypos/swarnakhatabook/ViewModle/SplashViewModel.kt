package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

// SplashViewModel.kt
class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val _navigationEvent = MutableLiveData<NavigationEvent>()
    val navigationEvent: LiveData<NavigationEvent> = _navigationEvent

    private val auth = FirebaseAuth.getInstance()

    fun checkInternetAndAuth() {
        if (isNetworkAvailable()) {
            // Network available, check authentication
            checkAuthState()
        } else {
            // No network, notify UI
            _navigationEvent.value = NavigationEvent.NoInternet
        }
    }

    private fun checkAuthState() {
        // Check if user is already logged in
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // User is authenticated, navigate to dashboard
            _navigationEvent.value = NavigationEvent.NavigateToDashboard
        } else {
            // User is not authenticated, navigate to registration
            _navigationEvent.value = NavigationEvent.NavigateToRegistration
        }
    }

    // Check if device is connected to internet
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }

    // Sealed class to represent navigation events
    sealed class NavigationEvent {
        object NavigateToDashboard : NavigationEvent()
        object NavigateToRegistration : NavigationEvent()
        object NoInternet : NavigationEvent()
    }
}