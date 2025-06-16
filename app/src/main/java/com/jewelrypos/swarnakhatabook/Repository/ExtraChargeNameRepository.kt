package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.ExtraChargeName
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ExtraChargeNameRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context
) {
    // Get current active shop ID from SessionManager
    private fun getCurrentShopId(): String {
        return SessionManager.getActiveShopId(context)
            ?: throw ShopNotSelectedException("No active shop selected.")
    }

    private fun getCollection() = firestore
        .collection("shopData")
        .document(getCurrentShopId())
        .collection("extraChargeNames")

    suspend fun saveChargeName(name: String): Result<ExtraChargeName> {
        return try {
            val collection = getCollection()
            
            // Check if name already exists
            val existingQuery = collection
                .whereEqualTo("name", name)
                .get()
                .await()

            if (!existingQuery.isEmpty) {
                return Result.failure(Exception("Charge name already exists"))
            }

            // Create new charge name
            val chargeName = ExtraChargeName(
                id = UUID.randomUUID().toString(),
                name = name
            )

            // Save to Firestore
            collection.document(chargeName.id)
                .set(chargeName)
                .await()

            Result.success(chargeName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllChargeNames(): Result<List<ExtraChargeName>> {
        return try {
            val collection = getCollection()
            
            val snapshot = collection
                .orderBy("name")
                .get()
                .await()

            val chargeNames = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ExtraChargeName::class.java)
            }

            Result.success(chargeNames)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Custom exceptions
    class ShopNotSelectedException(message: String) : Exception(message)
} 