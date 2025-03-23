package com.jewelrypos.swarnakhatabook.Repository


import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.CreditLimitChange
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

    // Add a new customer
    suspend fun addCustomer(customer: Customer): Result<String> = try {
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

        Result.success(docRef.id)
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
     * Updates a customer's credit limit and records the change in history
     * @param customerId The ID of the customer
     * @param currentLimit Current credit limit (to verify no concurrent changes)
     * @param newLimit New credit limit to set
     * @param reason Reason for the change
     * @return Result with the updated customer
     */
    suspend fun updateCustomerCreditLimit(
        customerId: String,
        currentLimit: Double,
        newLimit: Double,
        reason: String
    ): Result<Customer> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        // Get the current customer to verify limit
        val customerDoc = firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document(customerId)
            .get()
            .await()

        val customer = customerDoc.toObject(Customer::class.java)
            ?: throw Exception("Customer not found")

        // Verify current limit matches to prevent concurrent updates
        if (customer.creditLimit != currentLimit) {
            throw ConcurrentModificationException("Credit limit has been modified by another user. Please refresh and try again.")
        }

        // Update the customer's credit limit
        firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document(customerId)
            .update(
                mapOf(
                    "creditLimit" to newLimit,
                    "lastUpdatedAt" to System.currentTimeMillis()
                )
            )
            .await()

        // Record the change in history
        val change = CreditLimitChange(
            id = UUID.randomUUID().toString(),
            customerId = customerId,
            customerName = "${customer.firstName} ${customer.lastName}",
            previousLimit = currentLimit,
            newLimit = newLimit,
            reason = reason,
            changedBy = auth.currentUser?.phoneNumber ?: "Unknown"
        )

        firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document(customerId)
            .collection("creditLimitHistory")
            .document(change.id)
            .set(change)
            .await()

        // Return the updated customer
        Result.success(customer.copy(creditLimit = newLimit))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Gets the credit limit history for a customer
     * @param customerId The ID of the customer
     * @return Result with list of credit limit changes
     */
    suspend fun getCreditLimitHistory(customerId: String): Result<List<CreditLimitChange>> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        val snapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("customers")
            .document(customerId)
            .collection("creditLimitHistory")
            .orderBy("changeDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        val history = snapshot.toObjects(CreditLimitChange::class.java)
        Result.success(history)
    } catch (e: Exception) {
        Result.failure(e)
    }


}