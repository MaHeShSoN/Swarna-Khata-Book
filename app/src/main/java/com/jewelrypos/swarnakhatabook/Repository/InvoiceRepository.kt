package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.toObject
import com.jewelrypos.swarnakhatabook.DataClasses.Customer // Assuming needed
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem // Assuming needed
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.withContext // Import withContext
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

// Define custom exceptions if not already defined elsewhere
class UserNotAuthenticatedException(message: String) : Exception(message)
class PhoneNumberInvalidException(message: String) : Exception(message)
class ShopNotSelectedException(message: String) : Exception(message)


class InvoiceRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context
) {
    // Companion object for configuration
    companion object {
        private const val PAGE_SIZE = 10
        private const val TAG = "InvoiceRepository"
    }

    // Pagination state
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false

    // Get current active shop ID from SessionManager
    private fun getCurrentShopId(): String {
        return SessionManager.getActiveShopId(context)
            ?: throw ShopNotSelectedException("No active shop selected.")
    }

    // Get current user ID for validation
    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid
            ?: throw UserNotAuthenticatedException("User not authenticated.")
    }

    // Centralized method for getting Firestore collection references
    // This doesn't need Dispatchers.IO as it just builds a reference
    private fun getShopCollection(collectionName: String) = firestore.collection("shopData")
        .document(getCurrentShopId())
        .collection(collectionName)


    /**
     * Saves or updates an invoice, ensuring Firestore operations run on the IO dispatcher.
     * Also updates customer balance and inventory stock accordingly.
     */
    suspend fun saveInvoice(invoice: Invoice): Result<Unit> = withContext(Dispatchers.IO) {
        // All code within this block executes on the IO dispatcher
        try {
            // Prepare invoice with ID
            val invoiceWithId = if (invoice.id.isEmpty()) {
                invoice.copy(id = invoice.invoiceNumber) // Use invoiceNumber as ID if ID is missing
            } else {
                invoice
            }

            // Get existing invoice (if any) for comparison
            val existingInvoice = if (invoiceWithId.id.isNotEmpty()) {
                try {
                    getShopCollection("invoices")
                        .document(invoiceWithId.id)
                        .get()
                        .await()
                        .toObject(Invoice::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not retrieve existing invoice for update check: ${e.message}")
                    null /* Handle gracefully, treat as new if fetch fails */
                }
            } else { null }

            // Calculate balance change based on difference in unpaid amounts
            val unpaidAmountBefore = existingInvoice?.let { it.totalAmount - it.paidAmount } ?: 0.0
            val unpaidAmountAfter = invoiceWithId.totalAmount - invoiceWithId.paidAmount
            val balanceChange = unpaidAmountAfter - unpaidAmountBefore

            // --- Perform updates sequentially ---

            // 1. Save/Update the invoice document itself
            getShopCollection("invoices")
                .document(invoiceWithId.id)
                .set(invoiceWithId) // set() overwrites or creates
                .await()
            Log.d(TAG, "Invoice document saved/updated: ${invoiceWithId.id}")

            // 2. Update customer balance (if necessary and customer exists)
            if (balanceChange != 0.0 && invoiceWithId.customerId.isNotEmpty()) {
                // Encapsulate balance update logic - this call is already within withContext(Dispatchers.IO)
                updateCustomerBalanceForSave(invoiceWithId.customerId, balanceChange)
            } else {
                Log.d(TAG, "No balance change needed or customer ID missing: change=$balanceChange, customerId=${invoiceWithId.customerId}")
            }

            // 3. Update inventory stock (handle new vs existing invoice)
            // These calls are already within withContext(Dispatchers.IO)
            if (existingInvoice == null) {
                // This is a new invoice, update stock based on items sold/purchased
                updateInventoryStockForNewInvoice(invoiceWithId)
            } else {
                // This is an existing invoice being updated, handle stock differences
                handleInventoryStockChanges(existingInvoice, invoiceWithId)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving invoice ${invoice.invoiceNumber}", e)
            Result.failure(e)
        }
    }

    /**
     * Helper to update customer balance during saveInvoice.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun updateCustomerBalanceForSave(customerId: String, balanceChange: Double) {
        try {
            if (balanceChange == 0.0) return // Skip if no change

            // Get current customer document
            val customerDocRef = getShopCollection("customers").document(customerId)
            val customerDoc = customerDocRef.get().await()

            if (!customerDoc.exists()) {
                Log.w(TAG, "Customer $customerId not found for balance update")
                return
            }

            // Get current balance
            val currentBalance = customerDoc.getDouble("currentBalance") ?: 0.0
            val newBalance = currentBalance + balanceChange

            // Update balance
            customerDocRef.update("currentBalance", newBalance).await()
            Log.d(TAG, "Updated customer $customerId balance: $currentBalance -> $newBalance")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating customer balance for $customerId", e)
            // Could rethrow if critical, but usually better to log and continue
        }
    }


    /**
     * Handles calculating and applying stock changes when an existing invoice is updated.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun handleInventoryStockChanges(oldInvoice: Invoice, newInvoice: Invoice) {
        try {
            // Determine if the customer is a wholesaler (affects stock direction)
            val customerDoc = getShopCollection("customers").document(newInvoice.customerId).get().await()
            val customer = customerDoc.toObject<Customer>()
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

            // Create maps for efficient lookup
            val oldItemQuantities = oldInvoice.items.associate { it.itemId to it.quantity }
            val newItemQuantities = newInvoice.items.associate { it.itemId to it.quantity }

            // Combine all unique item IDs from both old and new invoices
            val allItemIds = oldItemQuantities.keys + newItemQuantities.keys

            // Process each unique item ID
            for (itemId in allItemIds) {
                val oldQuantity = oldItemQuantities[itemId] ?: 0
                val newQuantity = newItemQuantities[itemId] ?: 0
                val quantityDifference = newQuantity - oldQuantity // Positive means more items added/increased qty

                if (quantityDifference != 0) {
                    // Apply the stock change for this specific item
                    updateItemStockForInvoiceChange(itemId, quantityDifference, isWholesaler)
                }
            }
            Log.d(TAG, "Finished handling inventory stock changes for updated invoice ${newInvoice.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling inventory stock changes for invoice ${newInvoice.id}", e)
            // Decide on error handling: log, throw, etc.
        }
    }

    /**
     * Updates the stock for a single item based on quantity change and customer type.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun updateItemStockForInvoiceChange(itemId: String, quantityChange: Int, isWholesaler: Boolean) {
        if (quantityChange == 0 || itemId.isBlank()) return // No change or invalid item ID

        try {
            val inventoryRef = getShopCollection("inventory").document(itemId)
            val inventoryItemDoc = inventoryRef.get().await()
            val currentItem = inventoryItemDoc.toObject<JewelleryItem>()

            currentItem?.let {
                // Determine the actual change to apply to the stock value
                // If wholesaler: positive quantityChange means ADD to stock (buying from them)
                // If consumer: positive quantityChange means REMOVE from stock (selling to them)
                val effectiveStockChange = if (isWholesaler) quantityChange.toDouble() else -quantityChange.toDouble()
                val newStock = maxOf(0.0, it.stock + effectiveStockChange) // Ensure stock doesn't go negative

                // Only update if stock value actually changes
                if (newStock != it.stock) {
                    inventoryRef.update("stock", newStock).await()
                    Log.d(TAG, "Stock updated for item $itemId: old=${it.stock}, change=$effectiveStockChange, new=$newStock (Wholesaler: $isWholesaler)")
                } else {
                    Log.d(TAG, "Stock for item $itemId unchanged: old=${it.stock}, change=$effectiveStockChange, new=$newStock")
                }
            } ?: Log.w(TAG, "Inventory item $itemId not found for stock update.")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating stock for item $itemId during invoice change", e)
            // Decide on error handling
        }
    }

    /**
     * Updates inventory stock when a completely new invoice is created.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun updateInventoryStockForNewInvoice(invoice: Invoice) {
        try {
            // Determine if the customer is a wholesaler
            val customerDoc = getShopCollection("customers").document(invoice.customerId).get().await()
            val customer = customerDoc.toObject<Customer>()
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

            Log.d(TAG, "Updating inventory for NEW invoice ${invoice.id} (Wholesaler: $isWholesaler)")

            // Iterate through items and update stock individually
            invoice.items.forEach { invoiceItem ->
                // Apply the change for each item (negative quantity for consumers, positive for wholesalers)
                updateItemStockForInvoiceChange(invoiceItem.itemId, invoiceItem.quantity, isWholesaler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determining customer type or updating stock for new invoice ${invoice.id}", e)
            // Decide on error handling
        }
    }


    /**
     * Deletes an invoice, ensuring Firestore operations run on the IO dispatcher.
     * Also reverts customer balance and inventory stock changes.
     */
    suspend fun deleteInvoice(invoiceNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting to delete invoice: $invoiceNumber")

            // 1. Get the invoice to be deleted
            val invoiceRef = getShopCollection("invoices").document(invoiceNumber)
            val invoiceDoc = invoiceRef.get().await()
            val invoice = invoiceDoc.toObject<Invoice>()
                ?: return@withContext Result.failure(Exception("Invoice $invoiceNumber not found")) // Exit early if not found

            // 2. Get customer details for type checking
            val customerDoc = getShopCollection("customers").document(invoice.customerId).get().await()
            val customer = customerDoc.toObject<Customer>()
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)
            Log.d(TAG, "Deleting invoice $invoiceNumber, Customer Type: ${customer?.customerType ?: "Unknown"}")


            // 3. Revert inventory changes based on the deleted invoice items
            // This call is already within withContext(Dispatchers.IO)
            updateInventoryOnDeletion(invoice, isWholesaler)

            // 4. Revert customer balance change (if customer exists)
            if (invoice.customerId.isNotEmpty() && customer != null) {
                // This call is already within withContext(Dispatchers.IO)
                updateCustomerBalanceOnDeletion(invoice, customer, isWholesaler)
            } else {
                Log.w(TAG, "Customer not found or ID missing for balance reversion on delete: ${invoice.customerId}")
            }

            // 5. Delete the actual invoice document
            invoiceRef.delete().await()

            Log.d(TAG, "Successfully deleted invoice: $invoiceNumber")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting invoice $invoiceNumber", e)
            Result.failure(e)
        }
    }

    /**
     * Reverts inventory stock changes when an invoice is deleted.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun updateInventoryOnDeletion(invoice: Invoice, isWholesaler: Boolean) {
        Log.d(TAG, "Reverting inventory for deleted invoice ${invoice.id} (Wholesaler: $isWholesaler)")
        // Reverting means applying the *opposite* quantity change
        invoice.items.forEach { item ->
            // Pass the negative of the item quantity to reverse the stock change
            updateItemStockForInvoiceChange(item.itemId, -item.quantity, isWholesaler)
        }
    }

    /**
     * Reverts customer balance changes when an invoice is deleted.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun updateCustomerBalanceOnDeletion(invoice: Invoice, customer: Customer, isWholesaler: Boolean) {
        try {
            val unpaidAmount = invoice.totalAmount - invoice.paidAmount
            // To revert the balance change, we apply the negative of the original change
            val originalBalanceChange = calculateFinalBalanceChange(customer, unpaidAmount)
            val balanceReversion = -originalBalanceChange

            val newBalance = customer.currentBalance + balanceReversion

            getShopCollection("customers").document(invoice.customerId).update("currentBalance", newBalance).await()
            Log.d(TAG, "Reverted customer balance for ${invoice.customerId}: newBalance=$newBalance (reversion=$balanceReversion)")

        } catch (e: Exception) {
            Log.e(TAG, "Error reverting customer balance for ${invoice.customerId} during invoice deletion", e)
            // Decide on error handling
        }
    }

    /**
     * Fetches invoices with pagination, ensuring Firestore operations run on the IO dispatcher.
     */
    suspend fun fetchInvoicesPaginated(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<Invoice>> = withContext(Dispatchers.IO) {
        // Reset pagination state if needed
        if (!loadNextPage) {
            lastDocumentSnapshot = null
            isLastPage = false
            Log.d(TAG, "Pagination reset for fetching first page.")
        }
        if (isLastPage) {
            Log.d(TAG, "Already on the last page, returning empty list.")
            return@withContext Result.success(emptyList())
        }

        try {
            // Build query with pagination
            var query = getShopCollection("invoices")
                .orderBy("invoiceDate", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())

            // Add startAfter for pagination if needed
            if (loadNextPage && lastDocumentSnapshot != null) {
                query = query.startAfter(lastDocumentSnapshot)
            }

            // Get data with specified source
            val snapshot = query.get(source).await()

            // Update pagination state
            if (snapshot.documents.size < PAGE_SIZE) {
                isLastPage = true
                Log.d(TAG, "Reached last page with ${snapshot.documents.size} documents.")
            }

            // Save last document for next page query
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
                Log.d(TAG, "Last document ID for pagination: ${lastDocumentSnapshot?.id}")
            }

            // Convert to invoice objects
            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Invoice::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document to Invoice: ${e.message}")
                    null
                }
            }

            Log.d(TAG, "Fetched ${invoices.size} invoices.")
            Result.success(invoices)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching invoices: ${e.message}")

            // Try from server if cache source fails
            if (source == Source.CACHE) {
                Log.d(TAG, "Retrying fetch from server")
                return@withContext fetchInvoicesPaginated(loadNextPage, Source.SERVER)
            }

            Result.failure(e)
        }
    }

    /**
     * Gets a single invoice by its number (ID), running on the IO dispatcher.
     */
    suspend fun getInvoiceByNumber(invoiceNumber: String): Result<Invoice> = withContext(Dispatchers.IO) {
        if (invoiceNumber.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invoice number cannot be blank"))
        }
        try {
            val document = getShopCollection("invoices")
                .document(invoiceNumber)
                .get()
                .await()

            if (document.exists()) {
                document.toObject<Invoice>()
                    ?.let { Result.success(it) } // Successfully converted
                    ?: Result.failure(Exception("Failed to convert document to Invoice object for $invoiceNumber"))
            } else {
                Result.failure(Exception("Invoice $invoiceNumber not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting invoice by number $invoiceNumber", e)
            Result.failure(e)
        }
    }

    suspend fun getInvoicesBetweenDates(startDate: Date, endDate: Date): List<Invoice> = withContext(Dispatchers.IO) {
        try {
            // Get the time in milliseconds for comparison
            val startTime = startDate.time
            // Adjust end time to include the whole day if necessary (often needed for 'less than or equal to' logic)
            // Example: Set end date to the very end of the selected day
            val calendar = Calendar.getInstance()
            calendar.time = endDate
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis

            Log.d(TAG, "Fetching invoices between ${Date(startTime)} and ${Date(endTime)} (Long: $startTime to $endTime)")

            // Updated to use shop collection
            val snapshot = getShopCollection("invoices")
                .whereGreaterThanOrEqualTo("invoiceDate", startTime)
                .whereLessThanOrEqualTo("invoiceDate", endTime)
                .get()
                .await()

            return@withContext snapshot.toObjects(Invoice::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting invoices between dates", e)
            return@withContext emptyList<Invoice>()
        }
    }


    // --- Helper Methods ---

    /**
     * Calculates the final balance change to apply based on customer type and balance type.
     * This is pure calculation, does not need Dispatchers.IO itself.
     */
    private fun calculateFinalBalanceChange(customer: Customer, balanceChange: Double): Double {
        val isWholesaler = customer.customerType.equals("Wholesaler", ignoreCase = true)
        // Determine effect based on customer type and their balance convention
        return if (isWholesaler) {
            // For Wholesaler (Supplier):
            // If their balance is Debit (we owe them), positive change means we owe more.
            // If their balance is Credit (they owe us), positive change means they owe us less.
            if (customer.balanceType.uppercase() == "DEBIT") balanceChange else -balanceChange
        } else {
            // For Consumer:
            // If their balance is Debit (we owe them - unusual?), positive change means we owe less.
            // If their balance is Credit (they owe us), positive change means they owe more.
            if (customer.balanceType.uppercase() == "DEBIT") -balanceChange else balanceChange
        }
    }

    /**
     * Moves an invoice to the recycling bin instead of permanently deleting it.
     * This replaces the original deleteInvoice method.
     */
    suspend fun moveInvoiceToRecycleBin(invoiceNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Moving invoice to recycling bin: $invoiceNumber")

            // First, get the invoice to be "deleted"
            val invoiceRef = getShopCollection("invoices").document(invoiceNumber)
            val invoiceDoc = invoiceRef.get().await()

            if (!invoiceDoc.exists()) {
                return@withContext Result.failure(Exception("Invoice $invoiceNumber not found"))
            }

            val invoice = invoiceDoc.toObject<Invoice>()
                ?: return@withContext Result.failure(Exception("Failed to convert document to Invoice"))

            // Create a RecycledItemsRepository to handle the recycling bin operation
            val recycledItemsRepository = RecycledItemsRepository(firestore, auth,context)

            // Move to recycling bin
            val recycleResult = recycledItemsRepository.moveInvoiceToRecycleBin(invoice)

            // If successful, proceed with all the usual reversion of inventory and customer balance changes
            if (recycleResult.isSuccess) {
                // Get customer details for type checking
                val customerDoc = getShopCollection("customers").document(invoice.customerId).get().await()
                val customer = customerDoc.toObject<Customer>()
                val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

                // Revert inventory changes
                updateInventoryOnDeletion(invoice, isWholesaler)

                // Revert customer balance change
                if (invoice.customerId.isNotEmpty() && customer != null) {
                    updateCustomerBalanceOnDeletion(invoice, customer, isWholesaler)
                }

                // Delete the invoice from active invoices
                invoiceRef.delete().await()

                Log.d(TAG, "Successfully moved invoice to recycling bin: $invoiceNumber")
                Result.success(Unit)
            } else {
                // If recycling failed, propagate the error
                Result.failure(recycleResult.exceptionOrNull() ?: Exception("Unknown error recycling invoice"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving invoice to recycling bin: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes an invoice, bypassing the recycling bin.
     * This should only be used in specific cases or when deleting from the recycling bin.
     */
    suspend fun permanentlyDeleteInvoice(invoiceNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Permanently deleting invoice: $invoiceNumber")

            // This is the original deleteInvoice logic
            val invoiceRef = getShopCollection("invoices").document(invoiceNumber)
            val invoiceDoc = invoiceRef.get().await()

            if (!invoiceDoc.exists()) {
                return@withContext Result.failure(Exception("Invoice $invoiceNumber not found"))
            }

            val invoice = invoiceDoc.toObject<Invoice>()
                ?: return@withContext Result.failure(Exception("Failed to convert document to Invoice"))

            // Get customer details for type checking
            val customerDoc = getShopCollection("customers").document(invoice.customerId).get().await()
            val customer = customerDoc.toObject<Customer>()
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

            // Revert inventory changes
            updateInventoryOnDeletion(invoice, isWholesaler)

            // Revert customer balance change
            if (invoice.customerId.isNotEmpty() && customer != null) {
                updateCustomerBalanceOnDeletion(invoice, customer, isWholesaler)
            }

            // Delete the invoice
            invoiceRef.delete().await()

            Log.d(TAG, "Successfully deleted invoice permanently: $invoiceNumber")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error permanently deleting invoice: ${e.message}", e)
            Result.failure(e)
        }
    }

}
