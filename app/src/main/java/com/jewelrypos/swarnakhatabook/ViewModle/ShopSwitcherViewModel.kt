package com.jewelrypos.swarnakhatabook.ViewModle

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jewelrypos.swarnakhatabook.DataClasses.ShopDetails
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing shop switching functionality across the app
 * This ViewModel is scoped to the MainActivity to share shop state across fragments
 */
class ShopSwitcherViewModel : ViewModel() {

    // LiveData to hold the list of shops managed by the user
    private val _managedShops = MutableLiveData<List<ShopDetails>>(emptyList())
    val managedShops: LiveData<List<ShopDetails>> = _managedShops

    // LiveData to hold the currently active shop
    private val _activeShop = MutableLiveData<ShopDetails?>(null)
    val activeShop: LiveData<ShopDetails?> = _activeShop

    // LiveData to indicate loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData to report errors
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    /**
     * Load initial shop data for the user
     * @param userId The ID of the current user
     * @param context Context needed for SessionManager
     */
    fun loadInitialData(userId: String, context: Context) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                // Get the current active shop ID from SessionManager
                val activeShopId = SessionManager.getActiveShopId(context)

                // Fetch all managed shops
                val managedShopsResult = withContext(Dispatchers.IO) {
                    ShopManager.getManagedShops(userId)
                }
                
                // Check if fetching managed shops was successful
                if (managedShopsResult.isFailure) {
                    throw managedShopsResult.exceptionOrNull() ?: Exception("Failed to fetch managed shops")
                }
                
                // Get the shop IDs from the map keys
                val shopIds = managedShopsResult.getOrNull()?.keys ?: emptySet()

                // Fetch details for each shop
                val shopDetailsList = mutableListOf<ShopDetails>()
                for (shopId in shopIds) {
                    try {
                        val shopDetailsResult = withContext(Dispatchers.IO) {
                            ShopManager.getShopDetails(shopId)
                        }
                        
                        // Check if fetching shop details was successful
                        if (shopDetailsResult.isSuccess) {
                            val shopDetails = shopDetailsResult.getOrNull()
                            if (shopDetails != null) {
                                shopDetailsList.add(shopDetails)
                            }
                        }
                    } catch (e: Exception) {
                        // Continue with other shops even if one fails
                        continue
                    }
                }

                // Update managed shops list
                _managedShops.value = shopDetailsList

                // Set active shop based on stored ID or default to first shop
                if (activeShopId != null && shopDetailsList.any { it.shopId == activeShopId }) {
                    // Find and set the active shop
                    _activeShop.value = shopDetailsList.find { it.shopId == activeShopId }
                } else if (shopDetailsList.isNotEmpty()) {
                    // Default to first shop if no valid active ID
                    _activeShop.value = shopDetailsList.first()
                    // Store this as active shop
                    withContext(Dispatchers.IO) {
                        SessionManager.setActiveShopId(context, shopDetailsList.first().shopId)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load shops: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Switch the active shop
     * @param selectedShop The shop to switch to
     * @param context Context needed for SessionManager
     */
    fun switchActiveShop(selectedShop: ShopDetails, context: Context) {
        // Launch explicitly on the Main dispatcher from the start
        viewModelScope.launch(Dispatchers.Main) {
            Log.d("ShopSwitcherVM", "Switching shop on thread: ${Thread.currentThread().name}") // Should be Main
            try {
                // Perform the IO operation in a separate context
                val success = withContext(Dispatchers.IO) {
                    Log.d("ShopSwitcherVM", "Updating SessionManager on thread: ${Thread.currentThread().name}") // Should be IO
                    try {
                        SessionManager.setActiveShopId(context, selectedShop.shopId)
                        true // Indicate success
                    } catch (e: Exception) {
                        Log.e("ShopSwitcherVM", "Error setting active shop ID in SessionManager", e)
                        false // Indicate failure
                    }
                }

                // Back on the Main thread here (guaranteed by initial launch context)
                Log.d("ShopSwitcherVM", "Returned to Main thread after IO: ${Thread.currentThread().name}") // Should be Main
                if (success) {
                    Log.d("ShopSwitcherVM", "Updating _activeShop LiveData")
                    _activeShop.value = selectedShop // Now definitely on Main thread
                    Log.d("ShopSwitcherVM", "Shop switched successfully to: ${selectedShop.shopName}")
                } else {
                    _error.value = "Failed to update shop preference."
                }

            } catch (e: Exception) {
                // Catch any other exceptions during context switching etc.
                Log.e("ShopSwitcherVM", "Error during shop switch coroutine", e)
                // Ensure error update is also on Main thread
                _error.value = "Failed to switch shop: ${e.message}"
            }
        }
    }    /**
     * Clear any error messages
     */
    fun clearError() {
        _error.value = null
    }
} 