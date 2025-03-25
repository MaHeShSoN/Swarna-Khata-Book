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
    suspend fun saveInvoice(invoice: Invoice): Result<Unit> = try {
        coroutineScope {
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
                    null
                }
            } else {
                null
            }

            // Calculate how this affects the customer's balance
            val unpaidAmountBefore = existingInvoice?.let { it.totalAmount - it.paidAmount } ?: 0.0
            val unpaidAmountAfter = invoice.totalAmount - invoice.paidAmount
            val balanceChange = unpaidAmountAfter - unpaidAmountBefore

            // Update stock (can run concurrently)
            val stockUpdateJob = async { updateInventoryStock(invoice) }

            // Update customer balance with the calculated difference
            val customerBalanceJob = async {
                updateCustomerBalanceWithDifference(invoice.customerId, balanceChange)
            }

            // Wait for both jobs to complete
            stockUpdateJob.await()
            customerBalanceJob.await()

            // Save invoice
            getUserCollection("invoices")
                .document(invoiceWithId.id)
                .set(invoiceWithId)
                .await()

            Result.success(Unit)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error saving invoice", e)
        Result.failure(e)
    }

    // Update customer balance by the difference amount
    private suspend fun updateCustomerBalanceWithDifference(customerId: String, balanceChange: Double) {
        try {
            if (balanceChange == 0.0) return // No change needed

            val customerDoc = getUserCollection("customers")
                .document(customerId)
                .get()
                .await()

            val customer = customerDoc.toObject<Customer>()

            customer?.let {
                val newBalance = it.currentBalance + balanceChange
                getUserCollection("customers")
                    .document(customerId)
                    .update("currentBalance", newBalance)
                    .await()

                Log.d(TAG, "Updated customer balance from ${it.currentBalance} to $newBalance")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating customer balance", e)
        }
    }

    // Separate method for inventory stock update
    private suspend fun updateInventoryStock(invoice: Invoice) {
        invoice.items.forEach { invoiceItem ->
            if (invoiceItem.itemDetails.stock > 0) {
                try {
                    val inventoryItemDoc = getUserCollection("inventory")
                        .document(invoiceItem.itemId)
                        .get()
                        .await()

                    val currentItem = inventoryItemDoc.toObject<JewelleryItem>()

                    currentItem?.let {
                        val newStock = maxOf(0.0, it.stock - invoiceItem.quantity)
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
    suspend fun deleteInvoice(invoiceNumber: String): Result<Unit> = try {
        coroutineScope {
            // Fetch invoice first
            val invoiceDoc = getUserCollection("invoices")
                .document(invoiceNumber)
                .get()
                .await()

            val invoice = invoiceDoc.toObject<Invoice>()

            // Revert customer balance
            invoice?.let {
                updateCustomerBalanceOnDeletion(it)
            }

            // Delete the invoice
            getUserCollection("invoices")
                .document(invoiceNumber)
                .delete()
                .await()

            Result.success(Unit)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error deleting invoice", e)
        Result.failure(e)
    }

    // Separate method to revert customer balance during invoice deletion
    private suspend fun updateCustomerBalanceOnDeletion(invoice: Invoice) {
        try {
            val unpaidAmount = invoice.totalAmount - invoice.paidAmount

            val customerDoc = getUserCollection("customers")
                .document(invoice.customerId)
                .get()
                .await()

            val customer = customerDoc.toObject<Customer>()

            customer?.let {
                val newBalance = it.currentBalance - unpaidAmount
                getUserCollection("customers")
                    .document(invoice.customerId)
                    .update("currentBalance", newBalance)
                    .await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating customer balance during invoice deletion", e)
        }
    }

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