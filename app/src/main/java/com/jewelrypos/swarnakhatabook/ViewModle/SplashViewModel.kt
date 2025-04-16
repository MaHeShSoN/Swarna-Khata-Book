package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.launch

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
            // User is authenticated, check for shops
            checkUserShops(currentUser.uid)
        } else {
            // User is not authenticated, navigate to registration
            _navigationEvent.value = NavigationEvent.NavigateToRegistration
        }
    }

    private fun checkUserShops(userId: String) {
        viewModelScope.launch {
            try {
                // Get the user's managed shops
                val managedShopsResult = ShopManager.getManagedShops(userId)
                
                if (managedShopsResult.isSuccess) {
                    val managedShops = managedShopsResult.getOrNull() ?: emptyMap()
                    
                    when {
                        // No shops, navigate to create shop
                        managedShops.isEmpty() -> {
                            _navigationEvent.value = NavigationEvent.NavigateToCreateShop
                        }
                        
                        // Single shop, set it as active and navigate to dashboard
                        managedShops.size == 1 -> {
                            val shopId = managedShops.keys.first()
                            SessionManager.setActiveShopId(getApplication(), shopId)
                            _navigationEvent.value = NavigationEvent.NavigateToDashboard
                        }
                        
                        // Multiple shops, navigate to shop selection
                        else -> {
                            // Check if there's already an active shop ID
                            val activeShopId = SessionManager.getActiveShopId(getApplication())
                            
                            if (activeShopId != null && managedShops.containsKey(activeShopId)) {
                                // If active shop exists and is in user's shops, go to dashboard
                                _navigationEvent.value = NavigationEvent.NavigateToDashboard
                            } else {
                                // Otherwise, let user select a shop
                                _navigationEvent.value = NavigationEvent.NavigateToShopSelection
                            }
                        }
                    }
                } else {
                    // Failed to get shops, default to registration
                    _navigationEvent.value = NavigationEvent.NavigateToRegistration
                }
            } catch (e: Exception) {
                // Error occurred, default to registration
                _navigationEvent.value = NavigationEvent.NavigateToRegistration
            }
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
        object NavigateToCreateShop : NavigationEvent()
        object NavigateToShopSelection : NavigationEvent()
        object NoInternet : NavigationEvent()
    }
}