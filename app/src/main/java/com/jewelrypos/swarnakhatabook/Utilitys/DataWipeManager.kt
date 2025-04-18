package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.MainActivity
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
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
                val userId = auth.currentUser?.uid
                val shopIds = getShopIdsForCurrentUser(context)

                // Delete Firestore data if signed in
                if (!userId.isNullOrEmpty() && shopIds.isNotEmpty()) {
                    deleteCloudData(shopIds)
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
     * Gets all shop IDs associated with the current user
     */
    private suspend fun getShopIdsForCurrentUser(context: Context): List<String> {
        try {
            // First try to get the active shop ID from SessionManager
            val activeShopId = SessionManager.getActiveShopId(context)
            
            // If we have an active shop ID, use that
            if (!activeShopId.isNullOrEmpty()) {
                return listOf(activeShopId)
            }
            
            // Otherwise, try to get all shops for the current user from Firestore
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
            val firestore = FirebaseFirestore.getInstance()
            
            val querySnapshot = firestore.collection("userShops")
                .document(userId)
                .collection("shops")
                .get()
                .await()
                
            return querySnapshot.documents.mapNotNull { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting shop IDs for current user", e)
            return emptyList()
        }
    }

    /**
     * Deletes all cloud data for the user's shops
     */
    private suspend fun deleteCloudData(shopIds: List<String>) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            
            // Collections to delete for each shop
            val collections = listOf(
                "customers",
                "invoices",
                "inventory",
                "notifications",
                "payments",
                "settings",
                "metal_items",
                "recycled_items"
            )

            // Process each shop
            for (shopId in shopIds) {
                val shopDoc = firestore.collection("shopData").document(shopId)
                
                // Process each collection
                for (collection in collections) {
                    try {
                        // Get documents in collection
                        val querySnapshot = shopDoc.collection(collection).get().await()

                        // Delete documents in batches
                        val chunks = querySnapshot.documents.chunked(450)
                        for (chunk in chunks) {
                            val batch = firestore.batch()
                            for (document in chunk) {
                                batch.delete(document.reference)
                            }
                            batch.commit().await()
                        }
                        Log.d(TAG, "Deleted collection $collection for shop $shopId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting collection $collection for shop $shopId", e)
                        // Continue with next collection
                    }
                }
                
                // Delete the shop document itself
                try {
                    shopDoc.delete().await()
                    Log.d(TAG, "Deleted shop document $shopId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting shop document $shopId", e)
                }
            }
            
            // Delete user-shop associations
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (!userId.isNullOrEmpty()) {
                    val userShopsDoc = firestore.collection("userShops").document(userId)
                    
                    // Get all documents in the shops subcollection
                    val shopsSnapshot = userShopsDoc.collection("shops").get().await()
                    
                    // Delete all shop documents
                    val batch = firestore.batch()
                    for (document in shopsSnapshot.documents) {
                        batch.delete(document.reference)
                    }
                    batch.commit().await()
                    
                    // Delete the userShops document
                    userShopsDoc.delete().await()
                    Log.d(TAG, "Deleted user-shop associations for user $userId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting user-shop associations", e)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during cloud data deletion", e)
            throw e
        }
    }

    /**
     * Deletes data for a single shop without wiping all user data or logging out
     * Used when a user has multiple shops and wants to delete just one
     */
    suspend fun deleteSingleShopData(context: Context, shopId: String): Result<Boolean> {
        return try {
            // Delete Firestore data for this specific shop
            val firestore = FirebaseFirestore.getInstance()
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            
            if (userId.isNullOrEmpty() || shopId.isEmpty()) {
                return Result.failure(Exception("Invalid user ID or shop ID"))
            }
            
            // Collections to delete for the shop
            val collections = listOf(
                "customers",
                "invoices",
                "inventory",
                "notifications",
                "payments",
                "settings",
                "metal_items",
                "recycled_items"
            )
            
            // Process each collection for this shop
            val shopDoc = firestore.collection("shopData").document(shopId)
            
            for (collection in collections) {
                try {
                    // Get documents in collection
                    val querySnapshot = shopDoc.collection(collection).get().await()

                    // Delete documents in batches
                    val chunks = querySnapshot.documents.chunked(450)
                    for (chunk in chunks) {
                        val batch = firestore.batch()
                        for (document in chunk) {
                            batch.delete(document.reference)
                        }
                        batch.commit().await()
                    }
                    Log.d(TAG, "Deleted collection $collection for shop $shopId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting collection $collection for shop $shopId", e)
                    // Continue with next collection
                }
            }
            
            // Delete the shop document itself
            try {
                shopDoc.delete().await()
                Log.d(TAG, "Deleted shop document $shopId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting shop document $shopId", e)
            }
            
            // Delete shop association in userShops collection
            try {
                val userShopsDoc = firestore.collection("userShops").document(userId)
                userShopsDoc.collection("shops").document(shopId).delete().await()
                Log.d(TAG, "Deleted shop association for shop $shopId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting shop association for shop $shopId", e)
            }
            
            // Delete shop association in phone_shops collection
            try {
                val phoneNumber = SessionManager.getPhoneNumber(context)?.replace("+", "")
                if (!phoneNumber.isNullOrEmpty()) {
                    val phoneShopsDoc = firestore.collection("phone_shops").document(phoneNumber)
                    phoneShopsDoc.collection("shops").whereEqualTo("shopId", shopId).get().await().documents.forEach { doc ->
                        doc.reference.delete().await()
                    }
                    Log.d(TAG, "Deleted phone-shop association for shop $shopId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting phone-shop association for shop $shopId", e)
            }
            
            // Clear local data specific to this shop
            // This is a simplified version that just clears the active shop selection
            // if it matches the deleted shop
            val activeShopId = SessionManager.getActiveShopId(context)
            if (activeShopId == shopId) {
                SessionManager.clearActiveShopId(context)
                ShopManager.clearLocalShop(context)
            }
            
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error during single shop data deletion", e)
            Result.failure(e)
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
                "app_update_preferences",
                "session_preferences" // Added for SessionManager
            )

            for (prefName in allPrefs) {
                try {
                    val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing preferences $prefName", e)
                }
            }

            // Clear databases - include all possible database names
            val databaseNames = listOf(
                "jewelry_app_database.db",
                "jewelry_pos_cache.db",
                "metal_item_database.db",
                "swarna_khata_book.db"
            )
            
            for (dbName in databaseNames) {
                try {
                    context.deleteDatabase(dbName)
                    Log.d(TAG, "Deleted database $dbName")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting database $dbName", e)
                }
            }

            // Clear shop data
            try {
                ShopManager.clearLocalShop(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing shop manager data", e)
            }
            
            // Clear session data
//            try {
//                SessionManager.clearActiveShopId(context)
//            } catch (e: Exception) {
//                Log.e(TAG, "Error clearing session data", e)
//            }

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