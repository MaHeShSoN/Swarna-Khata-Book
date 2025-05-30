package com.jewelrypos.swarnakhatabook.Repository

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Enums.InventoryType
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository.Companion.PAGE_SIZE
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository.Companion.TAG
import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import kotlinx.coroutines.tasks.await
import kotlin.text.contains

// Define the PagingSource class
class InventoryPagingSource(
    private val firestore: FirebaseFirestore,
    private val shopId: String,
    private val searchQuery: String,
    private val activeFilters: Set<String>, // Received as uppercase strings
    private val source: Source // Data source for fetching
) : PagingSource<QuerySnapshot, JewelleryItem>() {

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, JewelleryItem> {
        return try {
            // Build the base query
            var query: Query = firestore.collection("shopData")
                .document(shopId)
                .collection("inventory")
                .orderBy("displayName", Query.Direction.ASCENDING)

            // Start after the key (last document snapshot from the previous page)
            val currentPageQuery = params.key?.let {
                query.startAfter(it.documents.lastOrNull())
            } ?: query

            // Limit the number of documents per page
            val snapshot = currentPageQuery.limit(PAGE_SIZE.toLong()).get(source).await()

            val items = snapshot.toObjects(JewelleryItem::class.java)

            // --- Apply filters and search in memory (less efficient for large datasets in Firestore) ---
            // For a robust solution, consider how filters and search can be part of the Firestore query itself.
            val filteredAndSearchedItems = items.filter { item ->
                // Metal type filter logic
                val itemTypeUpper = item.itemType.uppercase()
                val metalTypeFilters = activeFilters.intersect(setOf("GOLD", "SILVER", "OTHER"))
                val matchesMetalType = if (metalTypeFilters.isNotEmpty()) {
                    metalTypeFilters.contains(itemTypeUpper)
                } else {
                    true // No metal type filter selected, so it matches this criteria
                }

                // Low Stock filter logic (independent)
                val lowStockFilterActive = activeFilters.contains("LOW_STOCK")
                val isLowStock = when (item.inventoryType) {
                    InventoryType.BULK_STOCK -> item.totalWeightGrams <= InventoryViewModel.LOW_STOCK_WEIGHT_THRESHOLD // Use ViewModel constant
                    else -> item.stock <= InventoryViewModel.LOW_STOCK_THRESHOLD // Use ViewModel constant
                }
                val matchesLowStock = if (lowStockFilterActive) {
                    isLowStock // If low stock filter is active, item must be low stock
                } else {
                    true // Low stock filter is not active, so it matches this criteria
                }


                // Apply Search Query
                val matchesSearch = if (searchQuery.isNotEmpty()) {
                    item.displayName.contains(searchQuery, ignoreCase = true) ||
                            item.category.contains(searchQuery, ignoreCase = true) ||
                            item.itemType.contains(searchQuery, ignoreCase = true) ||
                            item.location.contains(searchQuery, ignoreCase = true) ||
                            item.purity.contains(searchQuery, ignoreCase = true)
                } else {
                    true // No search query means it matches search
                }

                // An item must match the metal type criteria AND the low stock criteria AND the search query
                matchesMetalType && matchesLowStock && matchesSearch
            }
            // --- End of in-memory filtering/searching ---
            // Important: In-memory filtering means you might fetch more items than needed per page.
            // A better approach for large datasets is to incorporate filtering/sorting into the Firestore query.


            val nextKey = if (snapshot.documents.isNotEmpty()) snapshot else null // Pass the whole snapshot as the key for the next page
            val prevKey = null // We don't support loading backwards

            LoadResult.Page(
                data = filteredAndSearchedItems,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                // This requires re-creating the PagingSource with Source.SERVER
                // which Paging 3 doesn't directly support within the load method's error handling.
                // A common pattern is to handle cache errors at the ViewModel level
                // or rely on the Paging 3 retry mechanism with a network check.
                // For this example, we'll just log and return the error.
                Log.e(TAG, "Cache load failed, potentially no network, returning error", e)
            } else {
                Log.e(TAG, "Error loading paginated inventory items", e)
            }
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<QuerySnapshot, JewelleryItem>): QuerySnapshot? {
        // The refresh key is the snapshot of the first item on the currently
        // displayed page. This allows Paging 3 to start loading from there when
        // refreshing, preserving the user's scroll position.
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            // Return the snapshot of the first item on the anchor page
            anchorPage?.data?.firstOrNull()?.let { firstItem ->
                // This requires finding the original snapshot for the first item.
                // Firestore PagingSource typically uses the last DocumentSnapshot
                // as the key for the *next* page. Getting the key for refresh (previous snapshot)
                // is more complex. A simpler approach for refresh is often to just
                // reload from the beginning (return null). However, to preserve position,
                // one might store the ID of the first item in view and find its snapshot.
                // For simplicity here, we'll return null for a simple refresh-from-top behavior.
                // For a more advanced refresh, you'd need to fetch the document snapshot
                // corresponding to the item at `anchorPosition`.
                null // Refresh from the start
            }
        }
    }
}
