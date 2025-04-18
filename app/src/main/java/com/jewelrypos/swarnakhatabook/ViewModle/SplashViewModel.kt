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
                // --- PROPOSED CHANGE START ---
                // Check if there is a previously saved active shop ID from the last session
                val activeShopId = SessionManager.getActiveShopId(getApplication())
                if (activeShopId != null) {
                    // If an active shop ID exists from the previous session,
                    // optimistically navigate to the dashboard.
                    // The dashboard screen can handle verifying this ID and loading shop data.
                    Log.d("SplashViewModel", "Found activeShopId in SessionManager: $activeShopId, navigating to Dashboard.")
                    _navigationEvent.value = NavigationEvent.NavigateToDashboard
                    return@launch // Stop further checks in this coroutine
                }
                // --- PROPOSED CHANGE END ---


                // Proceed only if no active shop ID was found in the session
                Log.d("SplashViewModel", "No activeShopId in SessionManager, fetching managed shops from Firestore.")
                val managedShopsResult = ShopManager.getManagedShops(userId)

                if (managedShopsResult.isSuccess) {
                    val managedShops = managedShopsResult.getOrNull() ?: emptyMap()
                    Log.d("SplashViewModel", "Fetched managed shops successfully. Count: ${managedShops.size}")

                    when {
                        // Only navigate to CreateShop if BOTH session is empty AND fetch returns no shops.
                        managedShops.isEmpty() -> {
                            Log.d("SplashViewModel", "No shops found after fetch, navigating to CreateShop.")
                            _navigationEvent.value = NavigationEvent.NavigateToCreateShop
                        }
                        managedShops.size == 1 -> {
                            val shopId = managedShops.keys.first()
                            Log.d("SplashViewModel", "Found single shop: $shopId. Setting active and navigating to Dashboard.")
                            SessionManager.setActiveShopId(getApplication(), shopId) // Set session for next time
                            _navigationEvent.value = NavigationEvent.NavigateToDashboard
                        }
                        else -> { // Multiple shops
                            Log.d("SplashViewModel", "Found multiple shops. Navigating to ShopSelection.")
                            // Note: The activeShopId check here is less critical now,
                            // as the initial check handles the main dashboard case.
                            // But keeping it doesn't hurt.
                            val currentActiveShopId = SessionManager.getActiveShopId(getApplication())
                            if (currentActiveShopId != null && managedShops.containsKey(currentActiveShopId)) {
                                Log.d("SplashViewModel", "Multiple shops found, but active ID ($currentActiveShopId) is valid. Navigating to Dashboard.")
                                _navigationEvent.value = NavigationEvent.NavigateToDashboard
                            } else {
                                Log.d("SplashViewModel", "Multiple shops found, no valid active ID. Navigating to ShopSelection.")
                                _navigationEvent.value = NavigationEvent.NavigateToShopSelection
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