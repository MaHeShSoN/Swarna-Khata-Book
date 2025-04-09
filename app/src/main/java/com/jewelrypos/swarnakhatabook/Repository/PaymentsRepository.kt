package com.jewelrypos.swarnakhatabook.Repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import kotlinx.coroutines.tasks.await

class PaymentsRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private fun getCurrentUserPhoneNumber(): String {
        return auth.currentUser?.phoneNumber?.replace("+", "")
            ?: throw PhoneNumberInvalidException("User phone number not available.")
    }

    suspend fun fetchInvoicesWithPayments(source: Source = Source.DEFAULT): Result<List<Invoice>> {
        return try {
            val phoneNumber = getCurrentUserPhoneNumber()

            val snapshot = firestore.collection("users")
                .document(phoneNumber)
                .collection("invoices")
                .whereGreaterThan("paidAmount", 0.0)  // Only fetch invoices with payments
                .get(source)
                .await()

            val invoices = snapshot.documents.mapNotNull { document ->
                document.toObject(Invoice::class.java)
            }

            Result.success(invoices)
        } catch (e: Exception) {
            Log.e("PaymentsRepository", "Error fetching invoices with payments", e)

            // If initial fetch fails with cache source, try server
            if (source == Source.CACHE) {
                fetchInvoicesWithPayments(Source.SERVER)
            } else {
                Result.failure(e)
            }
        }
    }

    // Optional method to extract all payments from invoices
}