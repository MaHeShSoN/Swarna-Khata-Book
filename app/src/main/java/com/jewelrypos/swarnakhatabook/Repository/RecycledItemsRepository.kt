package com.jewelrypos.swarnakhatabook.Repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.ExtraCharge
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
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
        const val RETENTION_DAYS = 30L // Number of days items are kept in recycling bin
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

    suspend fun addRecycledItem(item: RecycledItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getUserCollection(COLLECTION_NAME)
                .document(item.id) // Use the original item ID as the document ID here too
                .set(item)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding item to recycle bin collection: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- Add this function ---
    suspend fun restoreJewelleryItem(recycledItemId: String): Result<JewelleryItem> = withContext(Dispatchers.IO) {
        try {
            // Get the recycled item document
            val document = getUserCollection(COLLECTION_NAME)
                .document(recycledItemId)
                .get()
                .await()

            if (!document.exists()) {
                return@withContext Result.failure(Exception("Recycled item not found"))
            }

            val recycledItem = document.toObject<RecycledItem>()
                ?: return@withContext Result.failure(Exception("Failed to convert document to RecycledItem"))

            if (!recycledItem.itemType.equals("JEWELLERYITEM", ignoreCase = true)) {
                return@withContext Result.failure(Exception("Item is not a Jewellery Item"))
            }

            // Deserialize the JewelleryItem data
            val jewelleryItem = deserializeJewelleryItem(recycledItem.itemData)

            // Save the JewelleryItem back to the inventory collection
            getUserCollection("inventory") // Use the correct collection name "inventory"
                .document(jewelleryItem.id) // Use the item's original ID
                .set(jewelleryItem)
                .await()

            // Delete from recycling bin
            getUserCollection(COLLECTION_NAME)
                .document(recycledItemId)
                .delete()
                .await()

            Result.success(jewelleryItem)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring Jewellery Item: ${e.message}", e)
            Result.failure(e)
        }
    }


    // --- Add this helper function ---
    private fun deserializeJewelleryItem(data: Map<String, Any>): JewelleryItem {
        // Handle potential type mismatches during deserialization
        val extraChargesList = (data["listOfExtraCharges"] as? List<Map<String, Any>>)
            ?.mapNotNull { chargeMap ->
                try {
                    ExtraCharge(
                        name = chargeMap["name"] as? String ?: "",
                        amount = (chargeMap["amount"] as? Number)?.toDouble() ?: 0.0
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error deserializing extra charge: $chargeMap", e)
                    null // Skip invalid charges
                }
            } ?: emptyList()

        return JewelleryItem(
            id = data["id"] as? String ?: "",
            displayName = data["displayName"] as? String ?: "",
            jewelryCode = data["jewelryCode"] as? String ?: "",
            itemType = data["itemType"] as? String ?: "",
            category = data["category"] as? String ?: "",
            grossWeight = (data["grossWeight"] as? Number)?.toDouble() ?: 0.0,
            netWeight = (data["netWeight"] as? Number)?.toDouble() ?: 0.0,
            wastage = (data["wastage"] as? Number)?.toDouble() ?: 0.0,
            purity = data["purity"] as? String ?: "",
            makingCharges = (data["makingCharges"] as? Number)?.toDouble() ?: 0.0,
            makingChargesType = data["makingChargesType"] as? String ?: "",
            stock = (data["stock"] as? Number)?.toDouble() ?: 0.0,
            stockUnit = data["stockUnit"] as? String ?: "",
            location = data["location"] as? String ?: "",
            diamondPrice = (data["diamondPrice"] as? Number)?.toDouble() ?: 0.0,
            metalRate = (data["metalRate"] as? Number)?.toDouble() ?: 0.0,
            metalRateOn = data["metalRateOn"] as? String ?: "",
            taxRate = (data["taxRate"] as? Number)?.toDouble() ?: 0.0,
            totalTax = (data["totalTax"] as? Number)?.toDouble() ?: 0.0,
            listOfExtraCharges = extraChargesList
            // Add other fields similarly with safe casting and defaults
        )
    }



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

    /**
     * Moves a customer to the recycling bin instead of permanently deleting it
     */
    suspend fun moveCustomerToRecycleBin(customer: Customer): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Moving customer to recycling bin: ${customer.id}")

            // Calculate expiration date (30 days from now)
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, RETENTION_DAYS.toInt())
            val expirationTime = calendar.timeInMillis

            // Create recycled item
            val recycledItem = RecycledItem(
                id = customer.id,
                itemId = customer.id,
                itemType = "CUSTOMER",
                itemName = "${customer.firstName} ${customer.lastName}",
                itemData = serializeCustomer(customer),
                expiresAt = expirationTime,
                userId = getCurrentUserPhoneNumber()
            )

            // Save to recycledItems collection
            getUserCollection(COLLECTION_NAME)
                .document(customer.id)
                .set(recycledItem)
                .await()

            Log.d(TAG, "Successfully moved customer to recycling bin: ${customer.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error moving customer to recycling bin: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Helper function to serialize Customer to Map
     */
    private fun serializeCustomer(customer: Customer): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        // Add all customer fields to map
        map["id"] = customer.id
        map["customerType"] = customer.customerType
        map["firstName"] = customer.firstName
        map["lastName"] = customer.lastName
        map["phoneNumber"] = customer.phoneNumber
        map["email"] = customer.email ?: ""
        map["streetAddress"] = customer.streetAddress
        map["city"] = customer.city
        map["state"] = customer.state
        map["postalCode"] = customer.postalCode
        map["country"] = customer.country
        map["balanceType"] = customer.balanceType
        map["openingBalance"] = customer.openingBalance
        map["currentBalance"] = customer.currentBalance
        map["balanceNotes"] = customer.balanceNotes
        map["businessName"] = customer.businessName ?: ""
        map["gstNumber"] = customer.gstNumber ?: ""
        map["taxId"] = customer.taxId ?: ""
        map["customerSince"] = customer.customerSince
        map["referredBy"] = customer.referredBy
        map["birthday"] = customer.birthday
        map["anniversary"] = customer.anniversary
        map["notes"] = customer.notes
        map["createdAt"] = customer.createdAt
        map["lastUpdatedAt"] = customer.lastUpdatedAt

        return map
    }

    /**
     * Helper function to deserialize Map to Customer
     */
    private fun deserializeCustomer(data: Map<String, Any>): Customer {
        return Customer(
            id = data["id"] as? String ?: "",
            customerType = data["customerType"] as? String ?: "",
            firstName = data["firstName"] as? String ?: "",
            lastName = data["lastName"] as? String ?: "",
            phoneNumber = data["phoneNumber"] as? String ?: "",
            email = data["email"] as? String ?: "",
            streetAddress = data["streetAddress"] as? String ?: "",
            city = data["city"] as? String ?: "",
            state = data["state"] as? String ?: "",
            postalCode = data["postalCode"] as? String ?: "",
            country = data["country"] as? String ?: "India",
            balanceType = data["balanceType"] as? String ?: "Credit",
            openingBalance = (data["openingBalance"] as? Number)?.toDouble() ?: 0.0,
            currentBalance = (data["currentBalance"] as? Number)?.toDouble() ?: 0.0,
            balanceNotes = data["balanceNotes"] as? String ?: "",
            businessName = data["businessName"] as? String ?: "",
            gstNumber = data["gstNumber"] as? String ?: "",
            taxId = data["taxId"] as? String ?: "",
            customerSince = data["customerSince"] as? String ?: "",
            referredBy = data["referredBy"] as? String ?: "",
            birthday = data["birthday"] as? String ?: "",
            anniversary = data["anniversary"] as? String ?: "",
            notes = data["notes"] as? String ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastUpdatedAt = (data["lastUpdatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    /**
     * Restores a customer from the recycling bin
     */
    suspend fun restoreCustomer(recycledItemId: String): Result<Customer> = withContext(Dispatchers.IO) {
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

            if (recycledItem.itemType != "CUSTOMER") {
                return@withContext Result.failure(Exception("Item is not a customer"))
            }

            // Deserialize the customer data
            val customer = deserializeCustomer(recycledItem.itemData)

            // Save the customer back to the customers collection
            getUserCollection("customers")
                .document(customer.id)
                .set(customer)
                .await()

            // Delete from recycling bin
            getUserCollection(COLLECTION_NAME)
                .document(recycledItemId)
                .delete()
                .await()

            Result.success(customer)
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring customer: ${e.message}", e)
            Result.failure(e)
        }
    }
}