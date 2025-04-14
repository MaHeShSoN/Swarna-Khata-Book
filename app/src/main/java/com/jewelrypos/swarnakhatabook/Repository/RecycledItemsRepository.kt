package com.jewelrypos.swarnakhatabook.Repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.RecycledItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RecycledItemsRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val COLLECTION_NAME = "recycledItems"
        private const val TAG = "RecycledItemsRepository"
        private const val RETENTION_DAYS = 30L // Number of days items are kept in recycling bin
    }

    // Get authenticated user's phone number
    private fun getCurrentUserPhoneNumber(): String {
        val currentUser = auth.currentUser
            ?: throw Exception("User not authenticated.")
        return currentUser.phoneNumber?.replace("+", "")
            ?: throw Exception("User phone number not available.")
    }

    // Get user collection reference
    private fun getUserCollection(collectionName: String) = firestore.collection("users")
        .document(getCurrentUserPhoneNumber())
        .collection(collectionName)

    /**
     * Moves an invoice to the recycling bin instead of permanently deleting it
     */
    suspend fun moveInvoiceToRecycleBin(invoice: Invoice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Moving invoice to recycling bin: ${invoice.invoiceNumber}")

            // Calculate expiration date (30 days from now)
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, RETENTION_DAYS.toInt())
            val expirationTime = calendar.timeInMillis

            // Create recycled item
            val recycledItem = RecycledItem(
                id = invoice.id,
                itemId = invoice.invoiceNumber,
                itemType = "INVOICE",
                itemName = "Invoice #${invoice.invoiceNumber} - ${invoice.customerName}",
                itemData = serializeInvoice(invoice),
                expiresAt = expirationTime,
                userId = getCurrentUserPhoneNumber()
            )

            // Save to recycledItems collection
            getUserCollection(COLLECTION_NAME)
                .document(invoice.invoiceNumber)
                .set(recycledItem)
                .await()

            Log.d(TAG, "Successfully moved invoice to recycling bin: ${invoice.invoiceNumber}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error moving invoice to recycling bin: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Gets all items in the recycling bin
     */
    suspend fun getRecycledItems(): Result<List<RecycledItem>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = getUserCollection(COLLECTION_NAME)
                .orderBy("deletedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val items = snapshot.toObjects(RecycledItem::class.java)
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recycled items: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Gets recycled items of a specific type
     */
    suspend fun getRecycledItemsByType(itemType: String): Result<List<RecycledItem>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = getUserCollection(COLLECTION_NAME)
                .whereEqualTo("itemType", itemType)
                .orderBy("deletedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val items = snapshot.toObjects(RecycledItem::class.java)
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recycled items by type: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Restores an invoice from the recycling bin
     */
    suspend fun restoreInvoice(recycledItemId: String): Result<Invoice> = withContext(Dispatchers.IO) {
        try {
            // Get the recycled item
            val document = getUserCollection(COLLECTION_NAME)
                .document(recycledItemId)
                .get()
                .await()

            if (!document.exists()) {
                return@withContext Result.failure(Exception("Recycled item not found"))
            }

            val recycledItem = document.toObject<RecycledItem>()
                ?: return@withContext Result.failure(Exception("Failed to convert document to RecycledItem"))

            if (recycledItem.itemType != "INVOICE") {
                return@withContext Result.failure(Exception("Item is not an invoice"))
            }

            // Deserialize the invoice data
            val invoice = deserializeInvoice(recycledItem.itemData)

            // Save the invoice back to the invoices collection
            getUserCollection("invoices")
                .document(invoice.invoiceNumber)
                .set(invoice)
                .await()

            // Delete from recycling bin
            getUserCollection(COLLECTION_NAME)
                .document(recycledItemId)
                .delete()
                .await()

            Result.success(invoice)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring invoice: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes an item from the recycling bin
     */
    suspend fun permanentlyDeleteItem(recycledItemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getUserCollection(COLLECTION_NAME)
                .document(recycledItemId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error permanently deleting item: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Cleans up expired items (older than retention period)
     */
    suspend fun cleanupExpiredItems(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()

            val snapshot = getUserCollection(COLLECTION_NAME)
                .whereLessThan("expiresAt", currentTime)
                .get()
                .await()

            val batch = firestore.batch()
            var count = 0

            for (document in snapshot.documents) {
                batch.delete(document.reference)
                count++
            }

            if (count > 0) {
                batch.commit().await()
                Log.d(TAG, "Cleaned up $count expired items")
            }

            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up expired items: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Helper function to serialize Invoice to Map
     */
    private fun serializeInvoice(invoice: Invoice): Map<String, Any> {
        // Using Firebase's automatic serialization
        val map = mutableMapOf<String, Any>()
        map["id"] = invoice.id
        map["invoiceNumber"] = invoice.invoiceNumber
        map["customerId"] = invoice.customerId
        map["customerName"] = invoice.customerName
        map["customerPhone"] = invoice.customerPhone
        map["customerAddress"] = invoice.customerAddress
        map["invoiceDate"] = invoice.invoiceDate
        map["items"] = invoice.items
        map["payments"] = invoice.payments
        map["totalAmount"] = invoice.totalAmount
        map["paidAmount"] = invoice.paidAmount
        map["notes"] = invoice.notes
        return map
    }

    /**
     * Helper function to deserialize Map to Invoice
     */
    private fun deserializeInvoice(data: Map<String, Any>): Invoice {
        // Note: In a real implementation, you might need more complex deserialization
        // This is a simplified version
        return Invoice(
            id = data["id"] as? String ?: "",
            invoiceNumber = data["invoiceNumber"] as? String ?: "",
            customerId = data["customerId"] as? String ?: "",
            customerName = data["customerName"] as? String ?: "",
            customerPhone = data["customerPhone"] as? String ?: "",
            customerAddress = data["customerAddress"] as? String ?: "",
            invoiceDate = (data["invoiceDate"] as? Long) ?: System.currentTimeMillis(),
            items = data["items"] as? List<com.jewelrypos.swarnakhatabook.DataClasses.InvoiceItem> ?: listOf(),
            payments = data["payments"] as? List<com.jewelrypos.swarnakhatabook.DataClasses.Payment> ?: listOf(),
            totalAmount = (data["totalAmount"] as? Number)?.toDouble() ?: 0.0,
            paidAmount = (data["paidAmount"] as? Number)?.toDouble() ?: 0.0,
            notes = data["notes"] as? String ?: ""
        )
    }
}