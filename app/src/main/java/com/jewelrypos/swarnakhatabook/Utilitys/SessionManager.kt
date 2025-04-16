package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth

object SessionManager {
    private const val PREF_NAME = "session_preferences"
    private const val KEY_ACTIVE_SHOP_ID = "active_shop_id"

    private val _activeShopIdLiveData = MutableLiveData<String?>()
    val activeShopIdLiveData: LiveData<String?> = _activeShopIdLiveData

    private lateinit var sharedPreferences: SharedPreferences

    // Initialize the SessionManager
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Load the active shop ID and update the LiveData
        val activeShopId = getActiveShopId(context)
        _activeShopIdLiveData.value = activeShopId
    }

    // Set the active shop ID
    fun setActiveShopId(context: Context, shopId: String) {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }
        
        sharedPreferences.edit().putString(KEY_ACTIVE_SHOP_ID, shopId).apply()
        _activeShopIdLiveData.value = shopId
    }

    // Get the active shop ID
    fun getActiveShopId(context: Context): String? {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }
        
        return sharedPreferences.getString(KEY_ACTIVE_SHOP_ID, null)
    }

    // Get the current user ID from Firebase Auth
    fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    // Clear the active shop ID and other session data on logout
    fun clearSession(context: Context) {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }
        
        sharedPreferences.edit().remove(KEY_ACTIVE_SHOP_ID).apply()
        _activeShopIdLiveData.value = null
    }
} 