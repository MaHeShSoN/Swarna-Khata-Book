package com.jewelrypos.swarnakhatabook.Repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class InvoiceRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    // For pagination
    private val pageSize = 10
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false


    suspend fun saveInvoice(invoice: Invoice): Result<Unit> = try {
        val currentUser = auth.currentUser
            ?: throw UserNotAuthenticatedException("User not authenticated.")
        val phoneNumber = currentUser.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")

        // Create invoice with ID matching invoice number if not already set
        val invoiceWithId = if (invoice.id.isEmpty()) {
            invoice.copy(id = invoice.invoiceNumber)
        } else {
            invoice
        }

        // Update inventory stock for each item in the invoice
        for (invoiceItem in invoice.items) {
            // Only attempt to update stock if current stock is greater than 0
            if (invoiceItem.itemDetails.stock > 0) {
                try {
                    // Get the current item to get the latest stock value
                    val inventoryItemDoc = firestore.collection("users")
                        .document(phoneNumber)
                        .collection("inventory")
                        .document(invoiceItem.itemId)
                        .get()
                        .await()

                    val currentItem = inventoryItemDoc.toObject(JewelleryItem::class.java)

                    if (currentItem != null) {
                        // Calculate new stock (prevent negative values)
                        val newStock = maxOf(0.0, currentItem.stock - invoiceItem.quantity)

                        // Update the stock
                        firestore.collection("users")
                            .document(phoneNumber)
                            .collection("inventory")
                            .document(invoiceItem.itemId)
                            .update("stock", newStock)
                            .await()
                    }
                } catch (e: Exception) {
                    // Log error but continue with other items
                    Log.e(
                        "InvoiceRepository",
                        "Error updating stock for item ${invoiceItem.itemId}",
                        e
                    )
                }
            }
        }

        // Save the invoice
        firestore.collection("users")
            .document(phoneNumber)
            .collection("invoices")
            .document(invoiceWithId.id)
            .set(invoiceWithId)
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }


    suspend fun deleteInvoice(invoiceNumber: String): Result<Unit> = try {
        val currentUser = auth.currentUser
            ?: throw UserNotAuthenticatedException("User not authenticated.")
        val phoneNumber = currentUser.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")

        // Delete from Firestore
        firestore.collection("users")
            .document(phoneNumber)
            .collection("invoices")
            .document(invoiceNumber)
            .delete()
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }


    suspend fun fetchInvoicesPaginated(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<Invoice>> {
        return try {
            val currentUser = auth.currentUser
                ?: throw UserNotAuthenticatedException("User not authenticated.")
            val phoneNumber = currentUser.phoneNumber?.replace("+", "")
                ?: throw PhoneNumberInvalidException("User phone number not available.")

            // Reset pagination if not loading next page
            if (!loadNextPage) {
                lastDocumentSnapshot = null
                isLastPage = false
            }

            // Return empty list if we've reached the last page
            if (isLastPage) {
                return Result.success(emptyList())
            }

            // Build query with pagination, ordering by invoice date descending (newest first)
            var query = firestore.collection("users")
                .document(phoneNumber)
                .collection("invoices")
                .orderBy("invoiceDate", Query.Direction.DESCENDING)
                .limit(pageSize.toLong())

            // Add start after clause if we have a previous page
            if (loadNextPage && lastDocumentSnapshot != null) {
                query = query.startAfter(lastDocumentSnapshot!!)
            }

            // Get data with the specified source
            val snapshot = query.get(source).await()

            // Update pagination state
            if (snapshot.documents.size < pageSize) {
                isLastPage = true
            }

            // Save the last document for next page query
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
            }

            val invoices = snapshot.toObjects(Invoice::class.java)
            Result.success(invoices)
        } catch (e: Exception) {
            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                return fetchInvoicesPaginated(loadNextPage, Source.SERVER)
            }
            Result.failure(e)
        }
    }

    suspend fun getInvoiceByNumber(invoiceNumber: String): Result<Invoice> = try {
        val currentUser = auth.currentUser
            ?: throw UserNotAuthenticatedException("User not authenticated.")
        val phoneNumber = currentUser.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")

        val document = firestore.collection("users")
            .document(phoneNumber)
            .collection("invoices")
            .document(invoiceNumber)
            .get()
            .await()

        if (document.exists()) {
            val invoice = document.toObject(Invoice::class.java)
                ?: throw Exception("Failed to convert document to Invoice")
            Result.success(invoice)
        } else {
            Result.failure(Exception("Invoice not found"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}