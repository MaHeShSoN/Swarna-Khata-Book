package com.jewelrypos.swarnakhatabook

import android.app.Application
import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.jewelrypos.swarnakhatabook.Repository.BillingManager
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.Services.FirebaseNotificationService
import com.jewelrypos.swarnakhatabook.Utilitys.AppUpdateManager
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationChannelManager

class SwarnaKhataBook : Application() {

    // Use lazy initialization to defer creation until first use
    private val _userSubscriptionManager by lazy { 
        UserSubscriptionManager(applicationContext).also {
            // Record first use if needed (moved inside lazy init)
            it.recordFirstUseIfNeeded()
        }
    }
    
    private val _appUpdateManager by lazy { AppUpdateManager(applicationContext) }
    private val _billingManager by lazy { BillingManager(applicationContext) }

    companion object {
        // Use weak reference to application context or provide access via function
        // that requires a context parameter
        private lateinit var instance: SwarnaKhataBook

        // Public accessors that don't expose context-holding objects as static fields
        fun getUserSubscriptionManager() = instance._userSubscriptionManager
        fun getAppUpdateManager() = instance._appUpdateManager
        fun getBillingManager() = instance._billingManager
    }

    override fun onCreate() {
        super.onCreate()

        // Store instance of application
        instance = this

        // Configure Firestore only once at app startup - this is still needed early
        // but we'll optimize the settings
        configureCacheSettings()

        // Create notification channels on app startup
        // This is important for Android 8.0+ and should be done at startup
        NotificationChannelManager.createNotificationChannels(applicationContext)
        
        // Note: No longer initializing managers here - they will be initialized on demand
    }
    
    private fun configureCacheSettings() {
        // Configure Firestore settings to prioritize cached content and offline support
        val cacheSettings = PersistentCacheSettings.newBuilder()
            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            // Note: When using setLocalCacheSettings(), neither setCacheSizeBytes() 
            // nor setPersistenceEnabled() should be used as they're mutually exclusive
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}