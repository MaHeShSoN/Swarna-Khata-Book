package com.jewelrypos.swarnakhatabook.Repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import kotlinx.coroutines.tasks.await

// Define a default page size
internal const val DEFAULT_PAGE_SIZE = 20

class CustomerPagingSource(
    private val firestore: FirebaseFirestore,
    private val shopId: String,
    private val searchQuery: String,
    private val customerType: String?,
    private val initialSortField: String = "createdAt", // Default sort: newest first
    private val initialSortDirection: Query.Direction = Query.Direction.DESCENDING
) : PagingSource<QuerySnapshot, Customer>() {

    override fun getRefreshKey(state: PagingState<QuerySnapshot, Customer>): QuerySnapshot? {
        return null
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, Customer> {
        return try {
            var query: Query = firestore.collection("shopData")
                .document(shopId)
                .collection("customers")

            val isSearchActive = searchQuery.isNotBlank()
            val isTypeFilterActive = !customerType.isNullOrEmpty()

            // Apply customerType filter first
            if (isTypeFilterActive) {
                query = query.whereEqualTo("customerType", customerType)
            }

            // Determine the primary field for ordering based on filters/search
            var primarySortField: String? = null
            var primarySortDirection: Query.Direction? = null
            var secondarySorts = mutableListOf<Pair<String, Query.Direction>>()

            if (isSearchActive) {
                val searchLower = searchQuery.lowercase() // Assuming searchable field is lowercase
                if (searchLower.all { it.isDigit() } && searchLower.length >= 7) {
                    // If it looks like a phone number, search phone number
                    query = query.whereEqualTo("phoneNumber", searchQuery) // Phone number probably not lowercase
                    primarySortField = "phoneNumber"
                    primarySortDirection = Query.Direction.ASCENDING
                    secondarySorts.add(Pair("createdAt", Query.Direction.DESCENDING))
                    secondarySorts.add(Pair("fullNameSearchable", Query.Direction.ASCENDING)) // Use combined field for consistent secondary sort
                } else {
                    // Otherwise, search combined name field using prefix search
                    query = query.whereGreaterThanOrEqualTo("fullNameSearchable", searchLower)
                        .whereLessThanOrEqualTo("fullNameSearchable", searchLower + "\uf8ff")
                    primarySortField = "fullNameSearchable"
                    primarySortDirection = Query.Direction.ASCENDING
                    secondarySorts.add(Pair("createdAt", Query.Direction.DESCENDING)) // Secondary sort
                }
            } else {
                // No search, use default sort or filter-based sort
                primarySortField = initialSortField // e.g., "createdAt"
                primarySortDirection = initialSortDirection // e.g., DESCENDING
                secondarySorts.add(Pair("fullNameSearchable", Query.Direction.ASCENDING)) // Secondary sort by name
            }

            // Add primary sort
            if (primarySortField != null) {
                query = query.orderBy(primarySortField, primarySortDirection ?: Query.Direction.ASCENDING)
            }

            // Add mandatory sort for equality filter if applicable
            if (isTypeFilterActive && primarySortField != "customerType") {
                query = query.orderBy("customerType", Query.Direction.ASCENDING)
            }

            // Add secondary sorts, ensuring Firestore order requirements are met
            for ((field, direction) in secondarySorts) {
                // Avoid adding the primary sort field again as a secondary sort
                if (field != primarySortField && !(isTypeFilterActive && field == "customerType")) {
                    query = query.orderBy(field, direction)
                }
            }

            // Get the current page
            val currentPage = params.key ?: query.limit(params.loadSize.toLong()).get().await()
            val customers = currentPage.toObjects(Customer::class.java)

            // Calculate the next page query
            val nextPageQuery = if (customers.size < params.loadSize || currentPage.documents.isEmpty()) {
                null // No more data or empty page
            } else {
                // Create a new query for the next page
                query.startAfter(currentPage.documents.last()).limit(params.loadSize.toLong())
            }

            LoadResult.Page(
                data = customers,
                prevKey = null, // Only paging forward
                nextKey = nextPageQuery?.get()?.await() // Get the QuerySnapshot for the next page
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}