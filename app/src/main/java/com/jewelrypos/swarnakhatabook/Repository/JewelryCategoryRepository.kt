package com.jewelrypos.swarnakhatabook.Repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.JewelryCategory
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class JewelryCategoryRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("jewelry_categories")

    suspend fun addCategory(category: JewelryCategory): Result<String> =
        suspendCoroutine { continuation ->
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
                val categoryWithUser = category.copy(createdBy = userId)

                collection.add(categoryWithUser)
                    .addOnSuccessListener { documentReference ->
                        continuation.resume(Result.success(documentReference.id))
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

    suspend fun getCategoriesByMetalType(metalType: String): Result<List<JewelryCategory>> =
        suspendCoroutine { continuation ->
            try {
                collection.whereEqualTo("metalType", metalType)
                    .get()
                    .addOnSuccessListener { documents ->
                        val categories =
                            documents.mapNotNull { it.toObject(JewelryCategory::class.java) }
                        continuation.resume(Result.success(categories))
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

    suspend fun addDefaultCategories(): Result<Unit> = suspendCoroutine { continuation ->
        try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")

            // Check if categories already exist
            collection.get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        // Add default categories
                        val defaultCategories = listOf(
                            JewelryCategory(
                                name = "Aad",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Aavla",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Bajubandh",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Baju",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Bangadi",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Bajer Kanthi",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Bangles",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Borla",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),JewelryCategory(
                                name = "Choker",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Gajara",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Haath Phool",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Jadau Jewellery",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Jodha Haar",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Jhumkas",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Kamarbandh",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Kardhani",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),JewelryCategory(
                                name = "Kandora",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),JewelryCategory(
                                name = "Kanoti",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Tagdi",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Kanthi",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Kundan Jewellery",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Kundan Butti",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Maang Tikka",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Maharani Haar",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Matha Patti",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Sheeshphool",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),JewelryCategory(
                                name = "Seven Piece Set",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Meenakari Jewellery",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Motimala",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),JewelryCategory(
                                name = "Moti Haar",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Nath",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Nathni",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Nose Pin",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Pacheli",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Puncha",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Pata Jhala",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Polki Jewellery",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Rakhdi",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Rakhdi Set",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Rani Haar",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),  JewelryCategory(
                                name = "Ring",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Set",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ), JewelryCategory(
                                name = "Sohan Kanthi",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Thewa Jewellery",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Tussi",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Tokariya",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Tikka",
                                metalType = "GOLD",
                                createdBy = userId,
                                isDefault = true
                            ),

// Silver Jewelry Categories
                            JewelryCategory(
                                name = "Anklets",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Payal",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Pajeb",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Bajubandh",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Bangadi",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Bishnoi Silver Jewellery",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Choora",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Gokhru",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Gokharu",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Hasli",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Hollow Neck Ring",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Mandliya",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Matha Patti",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Sheeshphool",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Pajeb",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Pacheli",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Payal",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Sambharani",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Silver Earrings",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Jhaale",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Surliya",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Silver Nose Ring",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Nathni",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Silver Rings",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Tokariya",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Toe Rings",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Bichiya",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Tribal Silver Jewellery",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Upper Armlet",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Waist Chain",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            ),
                            JewelryCategory(
                                name = "Meenakari Jewellery",
                                metalType = "SILVER",
                                createdBy = userId,
                                isDefault = true
                            )
                        )

                        // Add all categories in a batch
                        val batch = firestore.batch()
                        defaultCategories.forEach { category ->
                            val docRef = collection.document()
                            batch.set(docRef, category)
                        }

                        batch.commit()
                            .addOnSuccessListener {
                                continuation.resume(Result.success(Unit))
                            }
                            .addOnFailureListener { e ->
                                continuation.resume(Result.failure(e))
                            }
                    } else {
                        // Categories already exist
                        continuation.resume(Result.success(Unit))
                    }
                }
                .addOnFailureListener { e ->
                    continuation.resume(Result.failure(e))
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
} 