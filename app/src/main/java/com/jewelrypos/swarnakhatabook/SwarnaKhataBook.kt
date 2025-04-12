package com.jewelrypos.swarnakhatabook

import android.app.Application
import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.Services.FirebaseNotificationService
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationChannelManager

class SwarnaKhataBook : Application() {

    companion object {
        lateinit var userSubscriptionManager: UserSubscriptionManager
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Configure Firestore only once at app startup
        val cacheSettings = PersistentCacheSettings.newBuilder()
            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings

        userSubscriptionManager = UserSubscriptionManager(this)

        // Record first use if needed
        userSubscriptionManager.recordFirstUseIfNeeded()

        // Create notification channels on app startup (moved from FirebaseNotificationService)
        NotificationChannelManager.createNotificationChannels(this)
    }
}