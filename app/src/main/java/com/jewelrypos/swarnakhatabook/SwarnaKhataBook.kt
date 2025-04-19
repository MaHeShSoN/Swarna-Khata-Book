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

    // Avoid using static fields that reference context
    private lateinit var _userSubscriptionManager: UserSubscriptionManager
    private lateinit var _appUpdateManager: AppUpdateManager
    private lateinit var _billingManager: BillingManager

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

        // Configure Firestore only once at app startup
        val cacheSettings = PersistentCacheSettings.newBuilder()
            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings

        // Initialize managers (not as static fields)
        _userSubscriptionManager = UserSubscriptionManager(applicationContext)
        _appUpdateManager = AppUpdateManager(applicationContext)
        _billingManager = BillingManager(applicationContext)

        // Record first use if needed
        _userSubscriptionManager.recordFirstUseIfNeeded()

        // Create notification channels on app startup
        NotificationChannelManager.createNotificationChannels(applicationContext)
    }
}