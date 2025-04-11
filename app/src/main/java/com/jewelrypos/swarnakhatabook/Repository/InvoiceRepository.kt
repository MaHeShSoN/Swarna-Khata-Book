package com.jewelrypos.swarnakhatabook.Repository

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
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.withContext // Import withContext
import kotlinx.coroutines.tasks.await
import kotlin.math.max // For maxOf

// Define custom exceptions if not already defined elsewhere
class UserNotAuthenticatedException(message: String) : Exception(message)
class PhoneNumberInvalidException(message: String) : Exception(message)


class InvoiceRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    // Companion object for configuration
    companion object {
        private const val PAGE_SIZE = 10
        private const val TAG = "InvoiceRepository"
    }

    // Pagination state
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false

    // Helper method to get authenticated user's phone number
    // This doesn't need Dispatchers.IO as it's synchronous CPU work
    private fun getCurrentUserPhoneNumber(): String {
        val currentUser = auth.currentUser
            ?: throw UserNotAuthenticatedException("User not authenticated.")
        return currentUser.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")
    }

    // Centralized method for getting Firestore collection references
    // This doesn't need Dispatchers.IO as it just builds a reference
    private fun getUserCollection(collectionName: String) = firestore.collection("users")
        .document(getCurrentUserPhoneNumber())
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
                    getUserCollection("invoices")
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
            getUserCollection("invoices")
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
            val customerDoc = getUserCollection("customers").document(customerId).get().await()
            val customer = customerDoc.toObject(Customer::class.java)

            customer?.let {
                val finalBalanceChange = calculateFinalBalanceChange(it, balanceChange)
                val newBalance = it.currentBalance + finalBalanceChange
                getUserCollection("customers").document(customerId).update("currentBalance", newBalance).await()
                Log.d(TAG, "Customer balance updated for $customerId: newBalance=$newBalance (change=$finalBalanceChange)")
            } ?: Log.w(TAG, "Customer $customerId not found for balance update during save.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update customer balance for $customerId during save", e)
            // Decide if this error should propagate or just be logged
            // throw e // Uncomment to make the saveInvoice fail if balance update fails
        }
    }


    /**
     * Handles calculating and applying stock changes when an existing invoice is updated.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun handleInventoryStockChanges(oldInvoice: Invoice, newInvoice: Invoice) {
        try {
            // Determine if the customer is a wholesaler (affects stock direction)
            val customerDoc = getUserCollection("customers").document(newInvoice.customerId).get().await()
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
            val inventoryRef = getUserCollection("inventory").document(itemId)
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
            val customerDoc = getUserCollection("customers").document(invoice.customerId).get().await()
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
            val invoiceRef = getUserCollection("invoices").document(invoiceNumber)
            val invoiceDoc = invoiceRef.get().await()
            val invoice = invoiceDoc.toObject<Invoice>()
                ?: return@withContext Result.failure(Exception("Invoice $invoiceNumber not found")) // Exit early if not found

            // 2. Get customer details for type checking
            val customerDoc = getUserCollection("customers").document(invoice.customerId).get().await()
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

            getUserCollection("customers").document(invoice.customerId).update("currentBalance", newBalance).await()
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
            // Build query
            var query = getUserCollection("invoices")
                .orderBy("invoiceDate", Query.Direction.DESCENDING) // Order by date descending
                .limit(PAGE_SIZE.toLong())

            // Apply cursor for next page
            if (loadNextPage && lastDocumentSnapshot != null) {
                query = query.startAfter(lastDocumentSnapshot!!)
                Log.d(TAG, "Fetching next page starting after document: ${lastDocumentSnapshot?.id}")
            } else {
                Log.d(TAG, "Fetching first page or restarting pagination.")
            }

            // Fetch data from specified source (Cache or Server/Default)
            Log.d(TAG, "Executing fetch query with source: $source")
            val snapshot = query.get(source).await()

            // Update pagination state based on results
            isLastPage = snapshot.documents.size < PAGE_SIZE
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
            }
            Log.d(TAG, "Fetched ${snapshot.documents.size} invoices. Is last page: $isLastPage")


            val invoices = snapshot.toObjects(Invoice::class.java)
            Result.success(invoices)
        } catch (e: Exception) {
            // Handle specific cache unavailability error by retrying from server
            if (source == Source.CACHE && e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                Log.w(TAG, "Cache unavailable for pagination, retrying from server...")
                fetchInvoicesPaginated(loadNextPage, Source.SERVER) // Recursive call with Server source
            } else {
                // Log and fail for other errors
                Log.e(TAG, "Error fetching invoices paginated (source: $source)", e)
                Result.failure(e)
            }
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
            val document = getUserCollection("invoices")
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

}
