package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.MainActivity
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Manages the emergency data wipe functionality
 * Triggered by reverse PIN entry
 */
object DataWipeManager {

    private const val TAG = "DataWipeManager"

    /**
     * Performs a complete wipe of user data
     * This is silent and doesn't show any progress or confirmation
     */
    fun performEmergencyWipe(context: Context, onComplete: () -> Unit) {
        val coroutineScope = CoroutineScope(Dispatchers.IO)

        coroutineScope.launch {
            try {
                // Get user info before cleaning
                val auth = FirebaseAuth.getInstance()
                val userId = auth.currentUser?.phoneNumber?.replace("+", "")

                // Delete Firestore data if signed in
                if (!userId.isNullOrEmpty()) {
                    deleteCloudData(userId)
                }

                // Clean local data regardless of cloud deletion success
                clearLocalData(context)

                // Complete the operation
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during emergency wipe", e)
                // Still try to clean local data and complete
                clearLocalData(context)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    /**
     * Deletes all cloud data for the user
     */
    private suspend fun deleteCloudData(userId: String) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val userDoc = firestore.collection("users").document(userId)

            // Collections to delete
            val collections = listOf(
                "customers",
                "invoices",
                "inventory",
                "notifications",
                "payments",
                "settings"
            )

            // Process each collection
            for (collection in collections) {
                try {
                    // Get documents in collection
                    val querySnapshot = userDoc.collection(collection).get().await()

                    // Delete documents in batches
                    val chunks = querySnapshot.documents.chunked(450)
                    for (chunk in chunks) {
                        val batch = firestore.batch()
                        for (document in chunk) {
                            batch.delete(document.reference)
                        }
                        batch.commit().await()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting collection $collection", e)
                    // Continue with next collection
                }
            }

            // Delete the user document itself
            try {
                userDoc.delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting user document", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during cloud data deletion", e)
            throw e
        }
    }

    /**
     * Clears all local app data
     */
    private fun clearLocalData(context: Context) {
        try {
            // Clear SharedPreferences
            val allPrefs = listOf(
                "jewelry_pos_settings",
                "shop_preferences",
                "jewelry_pos_pdf_settings",
                "secure_jewelry_pos_settings",
                "app_update_preferences"
            )

            for (prefName in allPrefs) {
                try {
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing preferences $prefName", e)
                }
            }

            // Clear databases
            try {
                context.deleteDatabase("jewelry_app_database.db")
                context.deleteDatabase("jewelry_pos_cache.db")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting databases", e)
            }

            // Clear shop data
            try {
                ShopManager.clearLocalShop(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing shop manager data", e)
            }

            // Sign out from Firebase
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                Log.e(TAG, "Error signing out from Firebase", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during local data cleanup", e)
        }
    }

    /**
     * Returns to the main activity after wipe completes
     */
    fun restartApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}