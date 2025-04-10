package com.jewelrypos.swarnakhatabook.Repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.MetalItem
import kotlinx.coroutines.tasks.await

class MetalItemRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun getCurrentUserPhoneNumber(): String {
        return auth.currentUser?.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")
    }

    suspend fun addMetalItem(metalItem: MetalItem): Result<Unit> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        firestore.collection("users")
            .document(phoneNumber)
            .collection("metal_items")
            .document(metalItem.fieldName)
            .set(metalItem)
            .await()

        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getMetalItems(): Result<List<MetalItem>> = try {
        val phoneNumber = getCurrentUserPhoneNumber()

        val snapshot = firestore.collection("users")
            .document(phoneNumber)
            .collection("metal_items")
            .get()
            .await()

        val items = snapshot.toObjects(MetalItem::class.java)
        Result.success(items)
    } catch (e: Exception) {
        Result.failure(e)
    }
}