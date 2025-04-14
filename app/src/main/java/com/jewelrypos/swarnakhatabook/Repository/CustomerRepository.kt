package com.jewelrypos.swarnakhatabook.Repository


import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment.Companion.TAG
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CustomerRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    // Pagination configuration
    private val pageSize = 10
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false

    // Get current user's phone number for document path
    private fun getCurrentUserPhoneNumber(): String {
        val currentUser = auth.currentUser ?: throw UserNotAuthenticatedException("User not authenticated.")
        return currentUser.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")
    }

    // Add to CustomerRepository.kt
    suspend fun getCustomerInvoiceCount(customerId: String): Result<Int> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        // Get the query snapshot first
        val snapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("invoices")
            .whereEqualTo("customerId", customerId)
            .get()
            .await()

        // Then get the size of the result set
        val count = snapshot.size()

        Result.success(count)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        // First, update any invoices to mark them as having a deleted customer
        val invoices = firestore.collection("users")
            .document(phoneNumber)
            .collection("invoices")
            .whereEqualTo("customerId", customerId)
            .get()
            .await()

        // For each invoice, we'll keep the customer name but mark the customerId as deleted
        invoices.documents.forEach { doc ->
            try {
                // Get the current notes value
                val currentNotes = doc.getString("notes") ?: ""

                // Create the new notes by appending the deletion information
                val updatedNotes = if (currentNotes.isNotEmpty()) {
                    "$currentNotes\nCustomer was deleted on ${java.util.Date()}"
                } else {
                    "Customer was deleted on ${java.util.Date()}"
                }

                // Update the document with the correct string format
                firestore.collection("users")
                    .document(phoneNumber)
                    .collection("invoices")
                    .document(doc.id)
                    .update(
                        mapOf(
                            "customerId" to "DELETED", // Mark as deleted but preserve the data
                            "notes" to updatedNotes // Use string instead of arrayUnion
                        )
                    )
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating invoice notes on customer deletion: ${e.message}")
            }
        }

        // Now delete the customer
        firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document(customerId)
            .delete()
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Add a new customer
    suspend fun addCustomer(customer: Customer): Result<Customer> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        // Create document with auto-generated ID
        val docRef = firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document()

        // Set ID in customer object
        val customerWithId = customer.copy(id = docRef.id)

        // Save to Firestore
        docRef.set(customerWithId).await()

        Result.success(customerWithId)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Update existing customer
    suspend fun updateCustomer(customer: Customer): Result<Unit> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        // Update existing document
        firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document(customer.id)
            .set(customer)
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Fetch paginated customers
    suspend fun fetchCustomersPaginated(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<Customer>> {
        return try {
            val phoneNumber = getCurrentUserPhoneNumber()

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
            var query = firestore.collection("users")
                .document(phoneNumber)
                .collection("customers")
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

            val customers = snapshot.toObjects(Customer::class.java)
            Result.success(customers)

        } catch (e: Exception) {
            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                return fetchCustomersPaginated(loadNextPage, Source.SERVER)
            }
            Result.failure(e)
        }
    }

    // Get a single customer by ID
    suspend fun getCustomerById(customerId: String): Result<Customer> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        val doc = firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document(customerId)
            .get()
            .await()

        if (doc.exists()) {
            val customer = doc.toObject(Customer::class.java)
            if (customer != null) {
                Result.success(customer)
            } else {
                Result.failure(Exception("Failed to parse customer data"))
            }
        } else {
            Result.failure(Exception("Customer not found"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Moves a customer to the recycling bin instead of permanently deleting
     */
    suspend fun moveCustomerToRecycleBin(customerId: String): Result<Unit> {
        return try {
            val phoneNumber = getCurrentUserPhoneNumber()

            // First, get the customer to be recycled
            val customerDoc = firestore.collection("users")
                .document(phoneNumber)
                .collection("customers")
                .document(customerId)
                .get()
                .await()

            if (!customerDoc.exists()) {
                return Result.failure(Exception("Customer not found"))
            }

            val customer = customerDoc.toObject(Customer::class.java)
                ?: return Result.failure(Exception("Failed to convert document to Customer"))

            // Update any invoices to mark them as having a deleted customer (same as before)
            val invoices = firestore.collection("users")
                .document(phoneNumber)
                .collection("invoices")
                .whereEqualTo("customerId", customerId)
                .get()
                .await()

            // For each invoice, we'll keep the customer name but mark the customerId as deleted
            invoices.documents.forEach { doc ->
                try {
                    // Get the current notes value
                    val currentNotes = doc.getString("notes") ?: ""

                    // Create the new notes by appending the deletion information
                    val updatedNotes = if (currentNotes.isNotEmpty()) {
                        "$currentNotes\nCustomer was deleted on ${java.util.Date()}"
                    } else {
                        "Customer was deleted on ${java.util.Date()}"
                    }

                    // Update the document with the correct string format
                    firestore.collection("users")
                        .document(phoneNumber)
                        .collection("invoices")
                        .document(doc.id)
                        .update(
                            mapOf(
                                "customerId" to "DELETED",
                                "notes" to updatedNotes
                            )
                        )
                        .await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating invoice notes on customer deletion: ${e.message}")
                }
            }

            // Create a RecycledItemsRepository instance
            val recycledItemsRepository = RecycledItemsRepository(firestore, auth)

            // Move the customer to recycling bin
            val result = recycledItemsRepository.moveCustomerToRecycleBin(customer)

            if (result.isSuccess) {
                // Delete the customer from the active collection
                firestore.collection("users")
                    .document(phoneNumber)
                    .collection("customers")
                    .document(customerId)
                    .delete()
                    .await()

                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to move customer to recycling bin"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes a customer without using the recycling bin
     * This should only be used in special cases
     */
    suspend fun permanentlyDeleteCustomer(customerId: String): Result<Unit> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        // Original delete logic here
        // (Keeping the invoice update code)

        firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document(customerId)
            .delete()
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

}