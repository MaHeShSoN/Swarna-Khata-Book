package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object SessionManager {
    private const val PREF_NAME = "session_preferences"
    private const val KEY_ACTIVE_SHOP_ID = "active_shop_id"
    private const val KEY_PHONE_NUMBER = "phone_number"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"

    private val _activeShopIdLiveData = MutableLiveData<String?>()
    open val activeShopIdLiveData: LiveData<String?> = _activeShopIdLiveData

    private lateinit var sharedPreferences: SharedPreferences

    // Initialize the SessionManager
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        // Load the active shop ID and update the LiveData
        val activeShopId = getActiveShopId(context)
        _activeShopIdLiveData.value = activeShopId
    }

    // Create a login session
    fun createLoginSession(context: Context, phoneNumber: String) {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }
        
        val editor = sharedPreferences.edit()
        editor.putString(KEY_PHONE_NUMBER, phoneNumber)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    // Check if user is logged in
    fun isLoggedIn(context: Context): Boolean {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }
        
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    // Get the stored phone number
    fun getPhoneNumber(context: Context): String? {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }
        
        return sharedPreferences.getString(KEY_PHONE_NUMBER, null)
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

    // Clear the active shop ID
    fun clearActiveShopId(context: Context) {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }
        
        sharedPreferences.edit().remove(KEY_ACTIVE_SHOP_ID).apply()
        _activeShopIdLiveData.value = null
    }

    // Get the current user ID from Firebase Auth
    fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    // Get all shop IDs associated with the current user
    fun getShopIds(context: Context): Array<String> {
        // First try to get the active shop ID
        val activeShopId = getActiveShopId(context)
        
        // If we have an active shop ID, return it as a single-element array
        if (!activeShopId.isNullOrEmpty()) {
            return arrayOf(activeShopId)
        }
        
        // If no active shop ID, return an empty array
        // The NotificationWorker will handle fetching shop IDs from Firestore directly
        return emptyArray()
    }

    // Clear the active shop ID and other session data on logout
    fun clearSession(context: Context) {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }
        
        val editor = sharedPreferences.edit()
        editor.remove(KEY_ACTIVE_SHOP_ID)
        editor.remove(KEY_PHONE_NUMBER)
        editor.putBoolean(KEY_IS_LOGGED_IN, false)
        editor.apply()
        
        _activeShopIdLiveData.value = null
    }
}