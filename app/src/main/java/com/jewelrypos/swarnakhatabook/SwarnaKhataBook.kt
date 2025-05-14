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
import com.squareup.picasso.OkHttp3Downloader
import com.squareup.picasso.Picasso
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

        // Configure Picasso with optimized settings
        configurePicasso()

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
    
    private fun configurePicasso() {
        try {
            // Create custom OkHttp client with optimized settings
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)     // Connection timeout
                .readTimeout(10, TimeUnit.SECONDS)       // Read timeout
                .cache(okhttp3.Cache(
                    applicationContext.cacheDir,
                    50 * 1024 * 1024  // 50 MB disk cache
                ))
                .build()
            
            // Build custom Picasso instance
            val picasso = Picasso.Builder(applicationContext)
                .downloader(OkHttp3Downloader(okHttpClient))  // Use OkHttp with our settings
                .indicatorsEnabled(false)                     // Disable debug indicators
                .loggingEnabled(false)                        // Disable logging in production
                .defaultBitmapConfig(android.graphics.Bitmap.Config.RGB_565) // Less memory usage
                .build()
            
            // Set as the global instance
            Picasso.setSingletonInstance(picasso)
            
        } catch (e: Exception) {
            // If anything goes wrong, fall back to default Picasso behavior
            android.util.Log.e("SwarnaKhataBook", "Error configuring Picasso: ${e.message}")
        }
    }
}