package com.jewelrypos.swarnakhatabook.Repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.toObject
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

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
    private fun getCurrentUserPhoneNumber(): String {
        return auth.currentUser?.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")
    }

    // Centralized method for getting Firestore collections
    private fun getUserCollection(collectionName: String) = firestore.collection("users")
        .document(getCurrentUserPhoneNumber())
        .collection(collectionName)

    // Enhanced save invoice method with improved error handling and concurrency
// Fixed save invoice method with proper payment handling for balance updates
    suspend fun saveInvoice(invoice: Invoice): Result<Unit> = try {
        // Prepare invoice with ID
        val invoiceWithId = if (invoice.id.isEmpty()) {
            invoice.copy(id = invoice.invoiceNumber)
        } else {
            invoice
        }

        // Get the existing invoice to calculate the balance difference (for updates)
        val existingInvoice = if (invoiceWithId.id.isNotEmpty()) {
            try {
                getUserCollection("invoices")
                    .document(invoiceWithId.id)
                    .get()
                    .await()
                    .toObject(Invoice::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "Could not retrieve existing invoice: ${e.message}")
                null
            }
        } else {
            null
        }

        // Calculate how this affects the customer's balance - THIS IS THE KEY FIX
        // We need to compare the unpaid amounts (total - paid) before and after
        val unpaidAmountBefore = existingInvoice?.let { it.totalAmount - it.paidAmount } ?: 0.0
        val unpaidAmountAfter = invoice.totalAmount - invoice.paidAmount
        val balanceChange = unpaidAmountAfter - unpaidAmountBefore

        Log.d(TAG, "Invoice save calculation: id=${invoice.invoiceNumber}, " +
                "totalBefore=${existingInvoice?.totalAmount ?: 0.0}, paidBefore=${existingInvoice?.paidAmount ?: 0.0}, " +
                "totalAfter=${invoice.totalAmount}, paidAfter=${invoice.paidAmount}, " +
                "unpaidBefore=$unpaidAmountBefore, unpaidAfter=$unpaidAmountAfter, balanceChange=$balanceChange")

        // 1. Save the invoice first
        getUserCollection("invoices")
            .document(invoiceWithId.id)
            .set(invoiceWithId)
            .await()

        // 2. Update customer balance if needed
        if (balanceChange != 0.0 && invoice.customerId.isNotEmpty()) {
            try {
                val customerDoc = getUserCollection("customers")
                    .document(invoice.customerId)
                    .get()
                    .await()

                val customer = customerDoc.toObject(Customer::class.java)

                customer?.let {

                    val isWholesaler = it.customerType.equals("Wholesaler", ignoreCase = true)


                    // Apply balance change based on customer type
                    val finalBalanceChange = if (isWholesaler) {
                        // For wholesalers (suppliers), the balance change is reversed
                        // We're buying from them, so we owe them money (or reduce what they owe us)
                        if (it.balanceType.uppercase() == "DEBIT") {
                            balanceChange  // For debit wholesalers: positive means we owe them
                        } else {
                            -balanceChange // For credit wholesalers: negative means they owe us less
                        }
                    } else {
                        // Standard consumer logic (existing code)
                        if (it.balanceType.uppercase() == "DEBIT") {
                            -balanceChange  // Inverse for debit customers
                        } else {
                            balanceChange   // Normal for credit customers
                        }
                    }
                    val newBalance = it.currentBalance + finalBalanceChange
                    Log.d(TAG, "Customer balance update: customer=${it.firstName} ${it.lastName}, " +
                            "oldBalance=${it.currentBalance}, change=$finalBalanceChange, newBalance=$newBalance, type=${it.balanceType}")

                    getUserCollection("customers")
                        .document(invoice.customerId)
                        .update("currentBalance", newBalance)
                        .await()
                }
            } catch (e: Exception) {
                // Log but don't fail the whole operation
                Log.e(TAG, "Error updating customer balance", e)
            }
        } else {
            Log.d(TAG, "No balance change needed: change=$balanceChange, customerId=${invoice.customerId}")
        }

        // 3. Update inventory stock if needed
        if (existingInvoice != null) {
            updateInventoryStock(existingInvoice)
        }

        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Error saving invoice", e)
        Result.failure(e)
    }

    // Helper method to update inventory stock in a transaction
    private fun updateInventoryStockInTransaction(
        transaction: com.google.firebase.firestore.Transaction,
        newInvoice: Invoice,
        oldInvoice: Invoice?
    ) {
        // Only process items that have stock management
        newInvoice.items.forEach { invoiceItem ->
            if (invoiceItem.itemDetails.stock > 0) {
                // Check if item existed in old invoice
                val oldItemQuantity = oldInvoice?.items?.find { it.itemId == invoiceItem.itemId }?.quantity ?: 0
                val quantityDifference = invoiceItem.quantity - oldItemQuantity

                // Only update stock if there's a change in quantity
                if (quantityDifference != 0) {
                    val inventoryRef = getUserCollection("inventory").document(invoiceItem.itemId)

                    try {
                        val itemSnapshot = transaction.get(inventoryRef)
                        if (itemSnapshot.exists()) {
                            val currentItem = itemSnapshot.toObject(JewelleryItem::class.java)
                            currentItem?.let {
                                val newStock = maxOf(0.0, it.stock - quantityDifference)
                                transaction.update(inventoryRef, "stock", newStock)
                                Log.d(TAG, "Transaction updating stock for ${it.displayName}: $newStock")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in stock transaction for item ${invoiceItem.itemId}", e)
                        // Continue with other items even if one fails
                    }
                }
            }
        }
    }

    // Update customer balance by the difference amount
    private suspend fun updateCustomerBalanceWithDifference(customerId: String, balanceChange: Double) {
        if (balanceChange == 0.0) return // No change needed - early exit for optimization

        try {
            val customerDoc = getUserCollection("customers")
                .document(customerId)
                .get()
                .await()

            val customer = customerDoc.toObject<Customer>()

            customer?.let {
                // The balance change effect depends on the customer's balance type
                val finalBalanceChange = when (it.balanceType.uppercase()) {
                    "CREDIT" -> balanceChange  // For Credit customers: positive means they owe more
                    "DEBIT" -> -balanceChange  // For Debit customers: negative means they owe more
                    else -> balanceChange      // Default to Credit behavior
                }

                val newBalance = it.currentBalance + finalBalanceChange

                Log.d(TAG, "Customer balance update: customerId=$customerId, type=${it.balanceType}, " +
                        "oldBalance=${it.currentBalance}, newBalance=$newBalance, change=$finalBalanceChange")

                getUserCollection("customers")
                    .document(customerId)
                    .update("currentBalance", newBalance)
                    .await()
            } ?: run {
                Log.e(TAG, "Customer not found for balance update: $customerId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating customer balance", e)
            throw e  // Re-throw to allow the calling code to handle the error
        }
    }

    // Separate method for inventory stock update
    private suspend fun updateInventoryStock(invoice: Invoice) {

        // Get the customer to check if they're a wholesaler
        val customerDoc = getUserCollection("customers")
            .document(invoice.customerId)
            .get()
            .await()

        val customer = customerDoc.toObject<Customer>()
        val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

        invoice.items.forEach { invoiceItem ->
            if (invoiceItem.itemDetails.stock > 0 || isWholesaler) {
                try {
                    val inventoryItemDoc = getUserCollection("inventory")
                        .document(invoiceItem.itemId)
                        .get()
                        .await()

                    val currentItem = inventoryItemDoc.toObject<JewelleryItem>()

                    currentItem?.let {
                        val newStock = if (isWholesaler) {
                            it.stock + invoiceItem.quantity  // Add to stock for wholesalers
                        } else {
                            maxOf(0.0, it.stock - invoiceItem.quantity)  // Regular logic for consumers
                        }

                        getUserCollection("inventory")
                            .document(invoiceItem.itemId)
                            .update("stock", newStock)
                            .await()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating stock for item ${invoiceItem.itemId}", e)
                }
            }
        }
    }

    // Separate method for customer balance update
    private suspend fun updateCustomerBalance(invoice: Invoice) {
        try {
            val unpaidAmount = invoice.totalAmount - invoice.paidAmount

            val customerDoc = getUserCollection("customers")
                .document(invoice.customerId)
                .get()
                .await()

            val customer = customerDoc.toObject<Customer>()

            customer?.let {
                val newBalance = it.currentBalance + unpaidAmount
                getUserCollection("customers")
                    .document(invoice.customerId)
                    .update("currentBalance", newBalance)
                    .await()

                Log.d(TAG, "Updated customer balance: $newBalance")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating customer balance", e)
        }
    }

    // Enhanced delete invoice method
    // In InvoiceRepository.kt:

    suspend fun deleteInvoice(invoiceNumber: String): Result<Unit> {
        try {
            // Log deletion attempt for debugging
            Log.d(TAG, "Attempting to delete invoice: $invoiceNumber")

            // Get the invoice first to properly update customer balance and inventory
            val invoiceDoc = getUserCollection("invoices")
                .document(invoiceNumber)
                .get()
                .await()

            val invoice = invoiceDoc.toObject<Invoice>()
            if (invoice == null) {
                Log.w(TAG, "Invoice not found: $invoiceNumber")
                return Result.failure(Exception("Invoice not found"))
            }

            // Get customer to determine type (consumer vs wholesaler)
            val customerDoc = getUserCollection("customers")
                .document(invoice.customerId)
                .get()
                .await()

            val customer = customerDoc.toObject<Customer>()
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

            Log.d(TAG, "Deleting invoice: $invoiceNumber, customer type: ${if (isWholesaler) "Wholesaler" else "Consumer"}")

            // Update inventory based on customer type
            updateInventoryOnDeletion(invoice, isWholesaler)

            // Update customer balance based on customer type and balance type
            if (invoice.customerId.isNotEmpty() && customer != null) {
                updateCustomerBalanceOnDeletion(invoice, customer, isWholesaler)
            }

            // Delete the invoice document
            getUserCollection("invoices")
                .document(invoiceNumber)
                .delete()
                .await()

            Log.d(TAG, "Successfully deleted invoice: $invoiceNumber")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting invoice: ${e.message}", e)
            return Result.failure(e)
        }
    }

    // Update inventory differently based on customer type
    private suspend fun updateInventoryOnDeletion(invoice: Invoice, isWholesaler: Boolean) {
        invoice.items.forEach { item ->
            try {
                val inventoryItemDoc = getUserCollection("inventory")
                    .document(item.itemId)
                    .get()
                    .await()

                val currentItem = inventoryItemDoc.toObject<JewelleryItem>()

                currentItem?.let {
                    // For consumer: ADD back to inventory
                    // For wholesaler: REMOVE from inventory (since we added when created)
                    val newStock = if (isWholesaler) {
                        // Wholesaler purchase gets removed from inventory
                        maxOf(0.0, it.stock - item.quantity)
                    } else {
                        // Consumer sale gets added back to inventory
                        it.stock + item.quantity
                    }

                    getUserCollection("inventory")
                        .document(item.itemId)
                        .update("stock", newStock)
                        .await()

                    Log.d(TAG, "Updated inventory for item ${item.itemId}: new stock = $newStock")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating inventory for item ${item.itemId}", e)
            }
        }
    }
    // Update customer balance based on customer type and balance type
    private suspend fun updateCustomerBalanceOnDeletion(invoice: Invoice, customer: Customer, isWholesaler: Boolean) {
        try {
            val unpaidAmount = invoice.totalAmount - invoice.paidAmount

            // The balance change depends on:
            // 1. Customer type (consumer vs wholesaler)
            // 2. Balance type (credit vs debit)

            val finalBalanceChange = if (isWholesaler) {
                // For wholesaler:
                if (customer.balanceType.uppercase() == "DEBIT") {
                    -unpaidAmount  // Decrease what we owe them
                } else {
                    unpaidAmount   // Increase what they owe us
                }
            } else {
                // For consumer:
                if (customer.balanceType.uppercase() == "DEBIT") {
                    unpaidAmount  // Increase what we owe them
                } else {
                    -unpaidAmount // Decrease what they owe us
                }
            }

            val newBalance = customer.currentBalance + finalBalanceChange

            Log.d(TAG, "Updating balance on delete: customerType=${if (isWholesaler) "Wholesaler" else "Consumer"}, " +
                    "balanceType=${customer.balanceType}, oldBalance=${customer.currentBalance}, " +
                    "newBalance=$newBalance, change=$finalBalanceChange")

            getUserCollection("customers")
                .document(invoice.customerId)
                .update("currentBalance", newBalance)
                .await()

        } catch (e: Exception) {
            Log.e(TAG, "Error updating customer balance during invoice deletion", e)
        }
    }
    // Separate method to revert customer balance during invoice deletion
//    private suspend fun updateCustomerBalanceOnDeletion(invoice: Invoice) {
//        try {
//            val unpaidAmount = invoice.totalAmount - invoice.paidAmount
//
//            val customerDoc = getUserCollection("customers")
//                .document(invoice.customerId)
//                .get()
//                .await()
//
//            val customer = customerDoc.toObject<Customer>()
//
//            customer?.let {
//                val newBalance = it.currentBalance - unpaidAmount
//                getUserCollection("customers")
//                    .document(invoice.customerId)
//                    .update("currentBalance", newBalance)
//                    .await()
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error updating customer balance during invoice deletion", e)
//        }
//    }

    // Optimized paginated invoice fetching
    suspend fun fetchInvoicesPaginated(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<Invoice>> {
        // Reset pagination if not loading next page
        if (!loadNextPage) {
            lastDocumentSnapshot = null
            isLastPage = false
        }

        // Return empty list if we've reached the last page
        if (isLastPage) {
            return Result.success(emptyList())
        }

        return try {
            // Build query with pagination
            var query = getUserCollection("invoices")
                .orderBy("invoiceDate", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())

            // Add start after clause if we have a previous page
            if (loadNextPage && lastDocumentSnapshot != null) {
                query = query.startAfter(lastDocumentSnapshot!!)
            }

            // Get data with the specified source
            val snapshot = query.get(source).await()

            // Update pagination state
            isLastPage = snapshot.documents.size < PAGE_SIZE

            // Save the last document for next page query
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
            }

            val invoices = snapshot.toObjects(Invoice::class.java)
            Result.success(invoices)
        } catch (e: Exception) {
            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                fetchInvoicesPaginated(loadNextPage, Source.SERVER)
            } else {
                Log.e(TAG, "Error fetching invoices", e)
                Result.failure(e)
            }
        }
    }

    // Get invoice by number with improved error handling
    suspend fun getInvoiceByNumber(invoiceNumber: String): Result<Invoice> = try {
        val document = getUserCollection("invoices")
            .document(invoiceNumber)
            .get()
            .await()

        if (document.exists()) {
            document.toObject<Invoice>()
                ?.let { Result.success(it) }
                ?: Result.failure(Exception("Failed to convert document to Invoice"))
        } else {
            Result.failure(Exception("Invoice not found"))
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error getting invoice by number", e)
        Result.failure(e)
    }
}