package com.jewelrypos.swarnakhatabook.Repository


import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import kotlinx.coroutines.tasks.await

class InventoryRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // Add these properties to InventoryRepository class
    private val pageSize = 10
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false



    suspend fun addJewelleryItem(jewelleryItem: JewelleryItem): Result<Unit> = try {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            throw UserNotAuthenticatedException("User not authenticated.")
        }
        val phoneNumber = currentUser.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")

        firestore.collection("users")
            .document(phoneNumber)
            .collection("inventory")
            .add(jewelleryItem)
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Add this method to fetch paginated data
    suspend fun fetchJewelleryItemsPaginated(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT // Source.DEFAULT, Source.CACHE, or Source.SERVER
    ): Result<List<JewelleryItem>> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                throw UserNotAuthenticatedException("User not authenticated.")
            }
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

            // Build query with pagination
            var query = firestore.collection("users")
                .document(phoneNumber)
                .collection("inventory")
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

            val items = snapshot.toObjects(JewelleryItem::class.java)
            Result.success(items)
        } catch (e: Exception) {
            // If using cache and got an error, try from server
            if (source == Source.CACHE) {
                return fetchJewelleryItemsPaginated(loadNextPage, Source.SERVER)
            }
            Result.failure(e)
        }
    }
}

class UserNotAuthenticatedException(message: String) : Exception(message)
class PhoneNumberInvalidException(message: String) : Exception(message)