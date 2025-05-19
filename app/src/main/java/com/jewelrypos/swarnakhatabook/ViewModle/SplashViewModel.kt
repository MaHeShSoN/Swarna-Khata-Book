package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
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
                // First check if there is a previously saved active shop ID from the last session
                val activeShopId = SessionManager.getActiveShopId(getApplication())
                
                if (activeShopId != null) {
                    // If an active shop ID exists from the previous session,
                    // immediately navigate to the dashboard to improve startup performance
                    Log.d("SplashViewModel", "Found activeShopId in SessionManager: $activeShopId, navigating to Dashboard.")
                    _navigationEvent.value = NavigationEvent.NavigateToDashboard
                    
                    // Optionally verify shop access in the background for future sessions
                    // but don't wait for the result to speed up the startup
                    verifyShopAccessInBackground(userId, activeShopId)
                    return@launch
                }

                // No active shop in session, need to fetch from network
                val managedShopsResult = ShopManager.getManagedShops(userId, preferCache = true)

                if (managedShopsResult.isSuccess) {
                    val managedShops = managedShopsResult.getOrNull() ?: emptyMap()
                    Log.d("SplashViewModel", "Fetched managed shops successfully. Count: ${managedShops.size}")

                    when {
                        managedShops.isEmpty() -> {
                            Log.d("SplashViewModel", "No shops found, navigating to CreateShop.")
                            _navigationEvent.value = NavigationEvent.NavigateToCreateShop
                        }
                        managedShops.size == 1 -> {
                            val shopId = managedShops.keys.first()
                            Log.d("SplashViewModel", "Found single shop: $shopId. Setting active and navigating to Dashboard.")
                            SessionManager.setActiveShopId(getApplication(), shopId)
                            _navigationEvent.value = NavigationEvent.NavigateToDashboard
                        }
                        else -> {
                            Log.d("SplashViewModel", "Found multiple shops. Navigating to ShopSelection.")
                            // Even with multiple shops, check if one is already active
                            val currentActiveShopId = SessionManager.getActiveShopId(getApplication())
                            if (currentActiveShopId != null && managedShops.containsKey(currentActiveShopId)) {
                                Log.d("SplashViewModel", "Multiple shops, but active ID ($currentActiveShopId) is valid. Navigating to Dashboard.")
                                _navigationEvent.value = NavigationEvent.NavigateToDashboard
                            } else {
                                Log.d("SplashViewModel", "Multiple shops, no valid active ID. Navigating to ShopSelection.")
                                _navigationEvent.value = NavigationEvent.NavigateToShopSelection(true)
                            }
                        }
                    }
                } else {
                    // Failed to get shops, navigate to registration
                    Log.e("SplashViewModel", "Failed to fetch managed shops: ${managedShopsResult.exceptionOrNull()?.message}")
                    _navigationEvent.value = NavigationEvent.NavigateToRegistration
                }
            } catch (e: Exception) {
                Log.e("SplashViewModel", "Error checking user shops: ${e.message}", e)
                _navigationEvent.value = NavigationEvent.NavigateToRegistration
            }
        }
    }
    
    // Verify shop access in background without blocking UI
    private fun verifyShopAccessInBackground(userId: String, shopId: String) {
        viewModelScope.launch {
            try {
                // Check if the user still has access to this shop, but don't wait for the result
                val managedShopsResult = ShopManager.getManagedShops(userId, preferCache = false)
                if (managedShopsResult.isSuccess) {
                    val managedShops = managedShopsResult.getOrNull() ?: emptyMap()
                    if (!managedShops.containsKey(shopId)) {
                        // User no longer has access to this shop, but we won't interrupt the current flow
                        // This will be handled on the next app start
                        Log.w("SplashViewModel", "Background check: User no longer has access to shop: $shopId")
                        // Optionally clear the active shop ID
                        // SessionManager.clearActiveShopId(getApplication())
                    }
                }
            } catch (e: Exception) {
                Log.e("SplashViewModel", "Error in background shop verification: ${e.message}", e)
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
        data class NavigateToShopSelection(val fromLogin: Boolean = false) : NavigationEvent()
        object NoInternet : NavigationEvent()
    }
}