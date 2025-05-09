package com.jewelrypos.swarnakhatabook.Repository


import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.RecycledItem
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class InventoryRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context
) {

    // Add these properties to InventoryRepository class
    private val pageSize = 10
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false


    companion object {
        private const val TAG = "InventoryRepository"
    }

    // Get current active shop ID from SessionManager
    private fun getCurrentShopId(): String {
        return SessionManager.getActiveShopId(context)
            ?: throw ShopNotSelectedException("No active shop selected.")
    }

    // Get current user ID for validation
    fun getCurrentUserId(): String {
        return auth.currentUser?.uid
            ?: throw UserNotAuthenticatedException("User not authenticated.")
    }

    suspend fun addJewelleryItem(jewelleryItem: JewelleryItem): Result<Unit> = try {
        getCurrentUserId() // Validate user is authenticated

        // Create a reference with auto-generated ID
        val docRef = firestore.collection("shopData")
            .document(getCurrentShopId())
            .collection("inventory")
            .document()

        // Update the jewelleryItem with the document ID
        val itemWithId = jewelleryItem.copy(id = docRef.id)

        // Save to Firestore with the ID included
        docRef.set(itemWithId).await()

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error adding jewelry item", e)
        Result.failure(e)
    }

    suspend fun fetchJewelleryItemsPaginated(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<JewelleryItem>> {
        try {
            // Reset pagination if not loading next page
            if (!loadNextPage) {
                lastDocumentSnapshot = null
                isLastPage = false
            }

            // Return empty list if we've reached the last page
            if (isLastPage) {
                return Result.success(emptyList())
            }

            // Build query with pagination
            var query = firestore.collection("shopData")
                .document(getCurrentShopId())
                .collection("inventory")
                .limit(pageSize.toLong())

            // Add startAfter for pagination if needed
            if (loadNextPage && lastDocumentSnapshot != null) {
                query = query.startAfter(lastDocumentSnapshot!!)
            }

            // Execute query
            val snapshot = query.get(source).await()

            // Update pagination state
            if (snapshot.documents.size < pageSize) {
                isLastPage = true
            }

            // Save the last document for next page
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
            }

            val items = snapshot.toObjects(JewelleryItem::class.java)
            return Result.success(items)
        } catch (e: Exception) {
            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                return fetchJewelleryItemsPaginated(loadNextPage, Source.SERVER)
            }
            return Result.failure(e)
        }
    }


    suspend fun updateJewelleryItem(item: JewelleryItem) = suspendCoroutine<Unit> { continuation ->
        getCurrentUserId() // Validate user is authenticated
        
        try {
            val shopId = getCurrentShopId()
            
            // Use shopData collection path
            firestore.collection("shopData")
                .document(shopId)
                .collection("inventory")
                .document(item.id)
                .set(item)
                .addOnSuccessListener {
                    continuation.resume(Unit)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * Get a jewelry item by its ID
     */
    suspend fun getJewelleryItemById(itemId: String, source: Source = Source.DEFAULT): Result<JewelleryItem> {
        return try {
            val shopId = getCurrentShopId()

            val documentSnapshot = firestore
                .collection("shopData")
                .document(shopId)
                .collection("inventory")
                .document(itemId)
                .get(source)
                .await()

            if (documentSnapshot.exists()) {
                val item = documentSnapshot.toObject(JewelleryItem::class.java)
                if (item != null) {
                    Result.success(item)
                } else {
                    Result.failure(Exception("Failed to parse item data"))
                }
            } else {
                Result.failure(Exception("Item not found"))
            }
        } catch (e: Exception) {
            Log.e("InventoryRepository", "Error getting item by ID", e)
            Result.failure(e)
        }
    }

    /**
     * Update the stock quantity of a jewelry item
     */
    suspend fun updateItemStock(itemId: String, newStock: Double): Result<Unit> {
        return try {
            val shopId = getCurrentShopId()

            firestore
                .collection("shopData")
                .document(shopId)
                .collection("inventory")
                .document(itemId)
                .update("stock", newStock)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InventoryRepository", "Error updating item stock", e)
            Result.failure(e)
        }
    }

    suspend fun moveItemToRecycleBin(item: JewelleryItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val recycledItemsRepository = RecycledItemsRepository(firestore, auth, context)

            val calendar = Calendar.getInstance()
            // Use the constant directly from RecycledItemsRepository companion object
            calendar.add(Calendar.DAY_OF_YEAR, RecycledItemsRepository.RETENTION_DAYS.toInt())
            val expirationTime = calendar.timeInMillis

            // First, move to recycle bin
            val recycledItem = RecycledItem(
                id = item.id,
                itemId = item.id,
                itemType = "JEWELLERYITEM", 
                itemName = item.displayName,
                itemData = serializeJewelleryItem(item),
                expiresAt = expirationTime,
                userId = getCurrentUserId()
            )

            val result = recycledItemsRepository.addRecycledItem(recycledItem)
            if (result.isSuccess) {
                // Then delete from main inventory
                return@withContext deleteJewelleryItem(item.id)
            } else {
                return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Failed to move item to recycle bin"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving item to recycle bin", e)
            return@withContext Result.failure(e)
        }
    }

    // --- Add helper function to serialize JewelleryItem ---
    private fun serializeJewelleryItem(item: JewelleryItem): Map<String, Any> {
        // Create a mutable map first
        val map = mutableMapOf<String, Any?>()

        map["id"] = item.id
        map["displayName"] = item.displayName
        map["jewelryCode"] = item.jewelryCode
        map["itemType"] = item.itemType
        map["category"] = item.category
        map["grossWeight"] = item.grossWeight
        map["netWeight"] = item.netWeight
        map["wastage"] = item.wastage
        map["purity"] = item.purity
        map["makingCharges"] = item.makingCharges
        map["makingChargesType"] = item.makingChargesType
        map["stock"] = item.stock
        map["stockUnit"] = item.stockUnit
        map["location"] = item.location
        map["diamondPrice"] = item.diamondPrice
        map["metalRate"] = item.metalRate
        map["metalRateOn"] = item.metalRateOn
        map["taxRate"] = item.taxRate
        map["totalTax"] = item.totalTax
        // Serialize list of ExtraCharge
        map["listOfExtraCharges"] = item.listOfExtraCharges.map { mapOf("name" to it.name, "amount" to it.amount) }
        // Add any other relevant fields

        // Filter out null values before returning
        return map.filterValues { it != null } as Map<String, Any>
    }

    /**
     * Delete a jewelry item from inventory
     */
    suspend fun deleteJewelleryItem(itemId: String): Result<Unit> {
        return try {
            val shopId = getCurrentShopId()

            firestore
                .collection("shopData")
                .document(shopId)
                .collection("inventory")
                .document(itemId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InventoryRepository", "Error deleting item", e)
            Result.failure(e)
        }
    }

    suspend fun getAllInventoryItems(source: Source = Source.DEFAULT): Result<List<JewelleryItem>> {
        return try {
            val shopId = getCurrentShopId()

            // This query gets all items without pagination
            val snapshot = firestore.collection("shopData")
                .document(shopId)
                .collection("inventory")
                .get(source)
                .await()

            val items = snapshot.toObjects(JewelleryItem::class.java)
            Log.d(TAG, "Loaded all ${items.size} inventory items")
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all inventory items", e)

            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                return getAllInventoryItems(Source.SERVER)
            }

            Result.failure(e)
        }
    }

    /**
     * Get the total count of inventory items
     */
    suspend fun getTotalInventoryCount(): Result<Int> {
        return try {
            val shopId = getCurrentShopId()

            val snapshot = firestore.collection("shopData")
                .document(shopId)
                .collection("inventory")
                .get()
                .await()

            Result.success(snapshot.size())
        } catch (e: Exception) {
            Log.e(TAG, "Error getting inventory count", e)
            Result.failure(e)
        }
    }

    // Custom exceptions
    class UserNotAuthenticatedException(message: String) : Exception(message)
    class PhoneNumberInvalidException(message: String) : Exception(message)  
    class ShopNotSelectedException(message: String) : Exception(message)
}