package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.RecycledItem
import com.jewelrypos.swarnakhatabook.Enums.InventoryType
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

    companion object {
        internal const val TAG = "InventoryRepository"
        const val PAGE_SIZE = 20 // Define page size for PagingSource
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

    // New method to provide the PagingSource
    fun getInventoryPagingSource(
        searchQuery: String,
        activeFilters: Set<String>, // Filters are passed as uppercase strings from ViewModel
        source: Source = Source.DEFAULT // Allow specifying data source
    ): InventoryPagingSource {
        return InventoryPagingSource(firestore, getCurrentShopId(), searchQuery, activeFilters, source)
    }



    suspend fun updateJewelleryItem(item: JewelleryItem): Result<Unit> {
        return try {
            val shopId = getCurrentShopId()

            // Use shopData collection path
            firestore.collection("shopData")
                .document(shopId)
                .collection("inventory")
                .document(item.id)
                .set(item)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InventoryRepository", "Error updating jewelry item", e)
            Result.failure(e)
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

    /**
     * Update the total weight of a bulk stock item
     */
    suspend fun updateItemBulkWeight(itemId: String, newWeight: Double): Result<Unit> {
        return try {
            val shopId = getCurrentShopId()

            firestore
                .collection("shopData")
                .document(shopId)
                .collection("inventory")
                .document(itemId)
                .update("totalWeightGrams", newWeight)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("InventoryRepository", "Error updating item weight", e)
            Result.failure(e)
        }
    }

    /**
     * Updates stock for an inventory item based on its type (weight-based or quantity-based)
     * @param itemId The ID of the item to update
     * @param quantityChange The change in quantity/weight (positive to add, negative to subtract)
     * @return Result indicating success or failure
     */
    suspend fun updateInventoryStock(itemId: String, quantityChange: Double): Result<Unit> {
        return try {
            val shopId = getCurrentShopId()
            
            // First get the item to determine its inventory type
            val itemDoc = firestore.collection("shopData")
                .document(shopId)
                .collection("inventory")
                .document(itemId)
                .get()
                .await()
            
            if (!itemDoc.exists()) {
                Log.e(TAG, "Item not found for stock update: $itemId")
                return Result.failure(Exception("Item not found: $itemId"))
            }
            
            val item = itemDoc.toObject(JewelleryItem::class.java)
                ?: return Result.failure(Exception("Failed to parse item data"))
            
            Log.d(TAG, "Updating inventory for item $itemId: ${item.displayName}, inventoryType=${item.inventoryType}, change=$quantityChange")
            
            // Update based on inventory type
            when (item.inventoryType) {
                InventoryType.BULK_STOCK -> {
                    // For weight-based items, update totalWeightGrams
                    val currentWeight = item.totalWeightGrams
                    val currentStock = item.stock
                    
                    // Determine the most reliable current weight value
                    val effectiveCurrentWeight = when {
                        // If totalWeightGrams is set, use it
                        currentWeight > 0.0 -> currentWeight
                        // Otherwise fall back to stock (older schema might use this)
                        currentStock > 0.0 -> currentStock
                        // If neither is set, start at zero
                        else -> 0.0
                    }
                    
                    val newWeight = maxOf(0.0, effectiveCurrentWeight + quantityChange) // Prevent negative weight
                    
                    Log.d(TAG, "Updating weight-based item $itemId: totalWeightGrams=$currentWeight, stock=$currentStock, " +
                          "effectiveWeight=$effectiveCurrentWeight, change=$quantityChange -> newWeight=$newWeight grams")
                    
                    // Update both totalWeightGrams and stock to ensure consistency
                    val updates = mutableMapOf<String, Any>().apply {
                        put("totalWeightGrams", newWeight)
                        put("stock", newWeight)
                        
                        // If grossWeight is zero but we're using totalWeightGrams for calculations, 
                        // set a default grossWeight for future calculations based on totalWeightGrams
                        if (item.grossWeight <= 0.0 && currentWeight > 0.0) {
                            // If we have stock quantity information, divide by that to get per-item weight
                            val estimatedItemWeight = if (item.stock > 1.0) {
                                currentWeight / item.stock
                            } else {
                                // Default to total weight if we can't determine per-item weight
                                currentWeight
                            }
                            Log.d(TAG, "Setting default grossWeight=$estimatedItemWeight for weight-based item $itemId")
                            put("grossWeight", estimatedItemWeight)
                        }
                    }
                    
                    firestore.collection("shopData")
                        .document(shopId)
                        .collection("inventory")
                        .document(itemId)
                        .update(updates)
                        .await()
                    
                    Log.d(TAG, "Successfully updated weight-based item $itemId to $newWeight grams")
                }
                InventoryType.IDENTICAL_BATCH -> {
                    // For quantity-based items, update stock
                    val currentStock = item.stock
                    val newStock = maxOf(0.0, currentStock + quantityChange) // Prevent negative stock
                    
                    Log.d(TAG, "Updating quantity-based item $itemId: $currentStock -> $newStock units")
                    
                    firestore.collection("shopData")
                        .document(shopId)
                        .collection("inventory")
                        .document(itemId)
                        .update("stock", newStock)
                        .await()
                    
                    Log.d(TAG, "Successfully updated quantity-based item $itemId to $newStock units")
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating inventory stock for item $itemId with change $quantityChange: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Updates stock for a weight-based inventory item when used in an invoice
     * @param itemId The ID of the item
     * @param grossWeight The gross weight used in the invoice
     * @return Result indicating success or failure
     */
    suspend fun updateWeightBasedStock(itemId: String, grossWeight: Double): Result<Unit> {
        // For weight-based items, we subtract the gross weight from total weight
        return updateInventoryStock(itemId, -grossWeight)
    }
    
    /**
     * Updates stock for a quantity-based inventory item when used in an invoice
     * @param itemId The ID of the item
     * @param quantity The quantity used in the invoice (integer)
     * @return Result indicating success or failure
     */
    suspend fun updateQuantityBasedStock(itemId: String, quantity: Int): Result<Unit> {
        // For quantity-based items, we subtract the quantity from stock
        return updateInventoryStock(itemId, -quantity.toDouble())
    }

    // Add this method to provide items for the dropdown in ItemSelectionBottomSheet
    // This is a separate, non-paginated fetch for the specific dropdown use case.
    suspend fun getInventoryItemsForDropdown(): Result<List<JewelleryItem>> {
        return try {
            val shopId = getCurrentShopId()

            val snapshot = firestore.collection("shopData")
                .document(shopId)
                .collection("inventory")
                // Optionally add sorting here if needed for the dropdown
                .get(Source.CACHE) // Try cache first for speed
                .await()

            val items = snapshot.toObjects(JewelleryItem::class.java)
            Log.d(TAG, "Loaded ${items.size} inventory items for dropdown (from cache)")
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading inventory items for dropdown from cache: ${e.message}", e)
            // If cache failed, try from server
            try {
                val shopId = getCurrentShopId()

                val snapshot = firestore.collection("shopData")
                    .document(shopId)
                    .collection("inventory")
                    // Optionally add sorting here if needed for the dropdown
                    .get(Source.SERVER) // Try server
                    .await()

                val items = snapshot.toObjects(JewelleryItem::class.java)
                Log.d(TAG, "Loaded ${items.size} inventory items for dropdown (from server)")
                Result.success(items)
            } catch (serverError: Exception) {
                Log.e(TAG, "Error loading inventory items for dropdown from server: ${serverError.message}", serverError)
                Result.failure(serverError) // Return server error
            }
        }
    }



    // Custom exceptions
    class UserNotAuthenticatedException(message: String) : Exception(message)
    class PhoneNumberInvalidException(message: String) : Exception(message)  
    class ShopNotSelectedException(message: String) : Exception(message)
}