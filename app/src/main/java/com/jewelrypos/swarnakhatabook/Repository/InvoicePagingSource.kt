package com.jewelrypos.swarnakhatabook.Repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import kotlinx.coroutines.tasks.await

class InvoicePagingSource(
    private val query: Query,
    private val firestore: FirebaseFirestore
) : PagingSource<QuerySnapshot, Invoice>() {

    override fun getRefreshKey(state: PagingState<QuerySnapshot, Invoice>): QuerySnapshot? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey
        }
    }

    override suspend fun load(params: LoadParams<QuerySnapshot>): LoadResult<QuerySnapshot, Invoice> {
        return try {
            val currentPage = params.key ?: query.limit(params.loadSize.toLong()).get().await()
            
            val lastDocumentSnapshot = currentPage.documents.lastOrNull()
            val nextPage = if (lastDocumentSnapshot == null) {
                null
            } else {
                query.startAfter(lastDocumentSnapshot)
                    .limit(params.loadSize.toLong())
                    .get()
                    .await()
            }

            val invoices = currentPage.documents.mapNotNull { document ->
                try {
                    document.toObject(Invoice::class.java)?.apply {
                        id = document.id
                    }
                } catch (e: Exception) {
                    null
                }
            }

            LoadResult.Page(
                data = invoices,
                prevKey = null, // Only supporting forward paging
                nextKey = nextPage
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
} 