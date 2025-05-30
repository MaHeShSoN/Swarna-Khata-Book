package com.jewelrypos.swarnakhatabook.Repository


import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.BottomSheet.CustomerBottomSheetFragment.Companion.TAG
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData

class CustomerRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context
) {
    // Pagination configuration
    private val pageSize = 10
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

    // Add to CustomerRepository.kt
    suspend fun getCustomerInvoiceCount(customerId: String): Result<Int> = try {
        val shopId = getCurrentShopId()

        // Get the query snapshot first
        val snapshot = firestore.collection("shopData").document(shopId).collection("invoices")
            .whereEqualTo("customerId", customerId).get().await()

        // Then get the size of the result set
        val count = snapshot.size()

        Result.success(count)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> = try {
        val shopId = getCurrentShopId()

        // First, update any invoices to mark them as having a deleted customer
        val invoices = firestore.collection("shopData").document(shopId).collection("invoices")
            .whereEqualTo("customerId", customerId).get().await()

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
                firestore.collection("shopData").document(shopId).collection("invoices")
                    .document(doc.id).update(
                        mapOf(
                            "customerId" to "DELETED", // Mark as deleted but preserve the data
                            "notes" to updatedNotes // Use string instead of arrayUnion
                        )
                    ).await()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating invoice notes on customer deletion: ${e.message}")
            }
        }

        // Now delete the customer
        firestore.collection("shopData").document(shopId).collection("customers")
            .document(customerId).delete().await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Add a new customer
    suspend fun addCustomer(customer: Customer): Result<Customer> = try {
        Log.d("CustomerRepository", "addCustomer: Starting customer addition")
        val shopId = getCurrentShopId()
        Log.d("CustomerRepository", "addCustomer: Shop ID: $shopId")

        // Create document with auto-generated ID
        val docRef = firestore.collection("shopData").document(shopId).collection("customers").document()
        Log.d("CustomerRepository", "addCustomer: Created document reference with ID: ${docRef.id}")

        // Set ID in customer object and calculate fullNameSearchable
        val customerWithId = customer.copy(
            id = docRef.id,
            fullNameSearchable = "${customer.firstName} ${customer.lastName}".lowercase()
        )
        Log.d("CustomerRepository", "addCustomer: Customer object prepared with ID: ${customerWithId.id}")

        // Save to Firestore
        docRef.set(customerWithId).await()
        Log.d("CustomerRepository", "addCustomer: Customer saved to Firestore successfully")

        Result.success(customerWithId)
    } catch (e: Exception) {
        Log.e("CustomerRepository", "addCustomer: Error adding customer", e)
        Result.failure(e)
    }

    // Update existing customer
    suspend fun updateCustomer(customer: Customer): Result<Unit> = try {
        val shopId = getCurrentShopId()

        // Calculate fullNameSearchable and update customer
        val updatedCustomer = customer.copy(
            fullNameSearchable = "${customer.firstName} ${customer.lastName}".lowercase()
        )

        // Update existing document
        firestore.collection("shopData").document(shopId).collection("customers")
            .document(customer.id).set(updatedCustomer).await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Fetch paginated customers
    suspend fun fetchCustomersPaginated(
        loadNextPage: Boolean = false, source: Source = Source.DEFAULT
    ): Result<List<Customer>> {
        return try {
            val shopId = getCurrentShopId()

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
            var query = firestore.collection("shopData").document(shopId).collection("customers")
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
        val shopId = getCurrentShopId()

        val doc = firestore.collection("shopData").document(shopId).collection("customers")
            .document(customerId).get().await()

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
            val shopId = getCurrentShopId()

            // First, get the customer to be recycled
            val customerDoc =
                firestore.collection("shopData").document(shopId).collection("customers")
                    .document(customerId).get().await()

            if (!customerDoc.exists()) {
                return Result.failure(Exception("Customer not found"))
            }

            val customer = customerDoc.toObject(Customer::class.java) ?: return Result.failure(
                Exception("Failed to convert document to Customer")
            )

            // Update any invoices to mark them as having a deleted customer (same as before)
            val invoices = firestore.collection("shopData").document(shopId).collection("invoices")
                .whereEqualTo("customerId", customerId).get().await()

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
                    firestore.collection("shopData").document(shopId).collection("invoices")
                        .document(doc.id).update(
                            mapOf(
                                "customerId" to "DELETED", "notes" to updatedNotes
                            )
                        ).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating invoice notes on customer deletion: ${e.message}")
                }
            }

            // Create a RecycledItemsRepository instance
            val recycledItemsRepository = RecycledItemsRepository(firestore, auth, context)

            // Move the customer to recycling bin
            val result = recycledItemsRepository.moveCustomerToRecycleBin(customer)

            if (result.isSuccess) {
                // Delete the customer from the active collection
                firestore.collection("shopData").document(shopId).collection("customers")
                    .document(customerId).delete().await()

                Result.success(Unit)
            } else {
                Result.failure(
                    result.exceptionOrNull()
                        ?: Exception("Failed to move customer to recycling bin")
                )
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
        val shopId = getCurrentShopId()

        // Original delete logic here
        // (Keeping the invoice update code)

        firestore.collection("shopData").document(shopId).collection("customers")
            .document(customerId).delete().await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Gets the total count of customers for the current shop
     * Used to enforce subscription limits
     */
    suspend fun getCustomerCount(): Result<Int> = try {
        val shopId = getCurrentShopId()
        
        // Use a count query to minimize data transfer
        val snapshot = firestore.collection("shopData")
            .document(shopId)
            .collection("customers")
            .get()
            .await()
        
        // Return the size of the result (number of documents)
        Result.success(snapshot.size())
    } catch (e: Exception) {
        Log.e(TAG, "Error getting customer count: ${e.message}")
        Result.failure(e)
    }

    fun getAllCustomersForShop(): Flow<PagingData<Customer>> {
        val shopId = SessionManager.getActiveShopId(context)
        val userId = auth.currentUser?.uid

        if (shopId == null || userId == null) {
            return flowOf(PagingData.empty())
        }

        val query = firestore.collection("shopData")
            .document(shopId)
            .collection("customers")
            .orderBy("name", com.google.firebase.firestore.Query.Direction.ASCENDING)

        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                CustomerPagingSource(
                    firestore = firestore,
                    shopId = shopId,
                    searchQuery = query.toString(),
                    customerType = "Consumer"  //
                )
            }
        ).flow
    }

    fun getPaginatedCustomers(
        shopId: String,
        searchQuery: String,
        customerType: String?,
        // Add sort parameters if you want them to be configurable from ViewModel
        // sortField: String = "createdAt",
        // sortDirection: Query.Direction = Query.Direction.DESCENDING
    ): Flow<PagingData<Customer>> {
        // val userId = auth.currentUser?.uid // You might need userId for rules, but PagingSource directly uses shopId for collection path

        return Pager(
            config = PagingConfig(
                pageSize = pageSize, // Use the same constant
                enablePlaceholders = false // Typically false for network sources
                // prefetchDistance: Can be configured
            ),
            pagingSourceFactory = {
                CustomerPagingSource(
                    firestore = firestore,
                    shopId = shopId,
                    searchQuery = searchQuery,
                    customerType = customerType
                    // Pass sort parameters if needed
                )
            }
        ).flow
    }

    // For dashboard calculations where we need the full list
    suspend fun getAllCustomersListForShop(): List<Customer> {
        Log.d("CustomerRepository", "getAllCustomersListForShop: Starting to fetch customers")
        val shopId = SessionManager.getActiveShopId(context)
        val userId = auth.currentUser?.uid

        if (shopId == null || userId == null) {
            Log.e("CustomerRepository", "getAllCustomersListForShop: No shop ID or user ID found")
            return emptyList()
        }

        return try {
            Log.d("CustomerRepository", "getAllCustomersListForShop: Fetching customers from Firestore")

            val snapshot = firestore.collection("shopData")
                .document(shopId)
                .collection("customers")
                .get()
                .await()

            Log.d("CustomerRepository", "getAllCustomersListForShop: Received ${snapshot.documents.size} customers")
            val customers = snapshot.documents.mapNotNull { document ->
                try {
                    document.toObject(Customer::class.java)?.apply {
                        id = document.id
                    }
                } catch (e: Exception) {
                    Log.e("CustomerRepository", "getAllCustomersListForShop: Error parsing customer document ${document.id}", e)
                    null
                }
            }
            Log.d("CustomerRepository", "getAllCustomersListForShop: Successfully parsed ${customers.size} customers")
            customers
        } catch (e: Exception) {
            Log.e("CustomerRepository", "getAllCustomersListForShop: Error fetching customers", e)
            emptyList()
        }
    }

    // Custom exceptions for shop-related issues
    class ShopNotSelectedException(message: String) : Exception(message)
    class UserNotAuthenticatedException(message: String) : Exception(message)
    class PhoneNumberInvalidException(message: String) : Exception(message)
}