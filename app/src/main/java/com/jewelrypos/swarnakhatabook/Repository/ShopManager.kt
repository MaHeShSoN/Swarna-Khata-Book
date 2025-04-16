package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.jewelrypos.swarnakhatabook.DataClasses.Shop
import com.jewelrypos.swarnakhatabook.DataClasses.ShopDetails
import com.jewelrypos.swarnakhatabook.DataClasses.ShopInvoiceDetails
import com.jewelrypos.swarnakhatabook.DataClasses.UserProfile
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.tasks.await
import java.lang.reflect.Type
import java.util.UUID

object ShopManager {
    private const val TAG = "ShopManager"
    private const val PREF_NAME = "shop_preferences"
    private const val KEY_SHOP_DATA = "shop_data"
    private const val KEY_SHOP_BRANDING = "shop_branding"
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson

    // Initialize in your Application class or a splash screen
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Setup Gson with custom type adapters for Timestamp
        gson = GsonBuilder()
            .registerTypeAdapter(Timestamp::class.java, TimestampSerializer())
            .registerTypeAdapter(Timestamp::class.java, TimestampDeserializer())
            .create()
    }

    // ======== NEW MULTI-SHOP METHODS ========

    // Get user profile by userId
    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        return try {
            val document = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val userProfile = UserProfile(
                    userId = userId,
                    name = document.getString("name") ?: "",
                    phoneNumber = document.getString("phoneNumber") ?: "",
                    managedShops = document.get("managedShops") as? Map<String, Boolean> ?: emptyMap(),
                    createdAt = document.getTimestamp("createdAt") ?: Timestamp.now()
                )
                Result.success(userProfile)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            Result.failure(e)
        }
    }

    // Save user profile
    suspend fun saveUserProfile(userProfile: UserProfile): Result<Unit> {
        return try {
            val userProfileMap = hashMapOf(
                "userId" to userProfile.userId,
                "name" to userProfile.name,
                "phoneNumber" to userProfile.phoneNumber,
                "managedShops" to userProfile.managedShops,
                "createdAt" to userProfile.createdAt
            )

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userProfile.userId)
                .set(userProfileMap)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
            Result.failure(e)
        }
    }

    // Create a new shop and add it to the user's managed shops
    suspend fun createShop(userId: String, shopDetails: ShopDetails): Result<String> {
        return try {
            // Generate a new shop ID
            val shopId = UUID.randomUUID().toString()

            // Set the shop details with the new ID and owner user ID
            val finalShopDetails = shopDetails.copy(
                shopId = shopId,
                ownerUserId = userId
            )

            // Save the shop details to the shops collection
            FirebaseFirestore.getInstance()
                .collection("shops")
                .document(shopId)
                .set(finalShopDetails)
                .await()

            // Update the user's managed shops
            val userResult = getUserProfile(userId)
            val userProfile = if (userResult.isSuccess && userResult.getOrNull() != null) {
                userResult.getOrNull()!!
            } else {
                // If user profile doesn't exist, create a new one
                UserProfile(
                    userId = userId,
                    managedShops = emptyMap()
                )
            }

            // Add the new shop to the user's managedShops
            val updatedManagedShops = userProfile.managedShops.toMutableMap()
            updatedManagedShops[shopId] = true
            
            // Save the updated user profile
            val updatedUserProfile = userProfile.copy(managedShops = updatedManagedShops)
            saveUserProfile(updatedUserProfile)

            Result.success(shopId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating shop", e)
            Result.failure(e)
        }
    }

    // Get shop details by shopId
    suspend fun getShopDetails(shopId: String): Result<ShopDetails?> {
        return try {
            val document = FirebaseFirestore.getInstance()
                .collection("shops")
                .document(shopId)
                .get()
                .await()

            if (document.exists()) {
                val shopDetails = ShopDetails(
                    shopId = shopId,
                    ownerUserId = document.getString("ownerUserId") ?: "",
                    shopName = document.getString("shopName") ?: "",
                    address = document.getString("address") ?: "",
                    gstNumber = document.getString("gstNumber"),
                    hasGst = document.getBoolean("hasGst") ?: false,
                    logoUrl = document.getString("logoUrl"),
                    signatureUrl = document.getString("signatureUrl"),
                    createdAt = document.getTimestamp("createdAt") ?: Timestamp.now()
                )
                Result.success(shopDetails)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting shop details", e)
            Result.failure(e)
        }
    }

    // Get all shops managed by a user
    suspend fun getManagedShops(userId: String): Result<Map<String, Boolean>> {
        return try {
            val userProfileResult = getUserProfile(userId)
            
            if (userProfileResult.isSuccess) {
                val userProfile = userProfileResult.getOrNull()
                if (userProfile != null) {
                    Result.success(userProfile.managedShops)
                } else {
                    Result.success(emptyMap())
                }
            } else {
                Result.failure(userProfileResult.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting managed shops", e)
            Result.failure(e)
        }
    }

    // Update shop details
    suspend fun updateShopDetails(shopDetails: ShopDetails): Result<Unit> {
        return try {
            FirebaseFirestore.getInstance()
                .collection("shops")
                .document(shopDetails.shopId)
                .set(shopDetails)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating shop details", e)
            Result.failure(e)
        }
    }

    // Convert a ShopDetails to the legacy Shop format for backward compatibility
    fun convertToLegacyShop(shopDetails: ShopDetails): Shop {
        return Shop(
            shopName = shopDetails.shopName,
            // phoneNumber will be filled from user profile if needed
            phoneNumber = "",
            name = "",
            hasGst = shopDetails.hasGst,
            gstNumber = shopDetails.gstNumber ?: "",
            address = shopDetails.address,
            createdAt = shopDetails.createdAt,
            email = "",
            logo = shopDetails.logoUrl,
            signature = shopDetails.signatureUrl
        )
    }

    // ======== ORIGINAL METHODS (MAINTAINED FOR BACKWARD COMPATIBILITY) ========

    // Save shop data to Firestore and SharedPreferences
    fun saveShop(
        shop: Shop,
        context: Context,
        onComplete: (Boolean, Exception?) -> Unit
    ) {
        // Update the local cache first
        saveShopLocally(shop, context)

        // Get the current user ID and active shop ID
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val shopId = SessionManager.getActiveShopId(context)
        
        if (userId == null || shopId == null) {
            onComplete(false, Exception("User not authenticated or no active shop"))
            return
        }

        // Create ShopDetails object from legacy Shop
        val shopDetails = ShopDetails(
            shopId = shopId,
            ownerUserId = userId,
            shopName = shop.shopName,
            address = shop.address,
            gstNumber = if (shop.hasGst) shop.gstNumber else null,
            hasGst = shop.hasGst,
            logoUrl = shop.logo,
            signatureUrl = shop.signature,
            createdAt = shop.createdAt
        )

        // Save shop details to the shops collection
        FirebaseFirestore.getInstance().collection("shops")
            .document(shopId)
            .set(shopDetails)
            .addOnSuccessListener {
                // Now update user information (phone, name, email) in the user document
                val userUpdates = hashMapOf<String, Any>()
                
                // Only update non-empty fields
                if (shop.phoneNumber.isNotEmpty()) {
                    userUpdates["phoneNumber"] = shop.phoneNumber
                }
                if (shop.name.isNotEmpty()) {
                    userUpdates["name"] = shop.name
                }
                if (shop.email.isNotEmpty()) {
                    userUpdates["email"] = shop.email
                }
                
                // If we have user fields to update
                if (userUpdates.isNotEmpty()) {
                    FirebaseFirestore.getInstance().collection("users")
                        .document(userId)
                        .update(userUpdates)
                        .addOnSuccessListener {
                            Log.d(TAG, "User profile updated successfully")
                            onComplete(true, null)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error updating user profile", e)
                            // Still consider the operation successful if only the user update fails
                            onComplete(true, null)
                        }
                } else {
                    Log.d(TAG, "Shop details updated successfully")
                    onComplete(true, null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating shop details", e)
                onComplete(false, e)
            }
    }

    // Get shop data (first from SharedPreferences, then Firebase if needed)
    fun getShop(context: Context, forceRefresh: Boolean = false, callback: (Shop?) -> Unit) {
        if (!forceRefresh) {
            // Try to get from local cache first
            val localShop = getShopFromLocal(context)
            if (localShop != null) {
                callback(localShop)
                return
            }
        }

        // If not in cache or force refresh, fetch from Firestore
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            callback(null)
            return
        }

        // Get the active shop ID and user ID
        val shopId = SessionManager.getActiveShopId(context)
        val userId = currentUser.uid
        
        if (shopId == null) {
            callback(null)
            return
        }
        
        // Fetch from /shops/{shopId} instead of the user document
        FirebaseFirestore.getInstance().collection("shops")
            .document(shopId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    try {
                        // Convert ShopDetails to legacy Shop format
                        val shopDetails = ShopDetails(
                            shopId = shopId,
                            ownerUserId = document.getString("ownerUserId") ?: "",
                            shopName = document.getString("shopName") ?: "",
                            address = document.getString("address") ?: "",
                            gstNumber = document.getString("gstNumber"),
                            hasGst = document.getBoolean("hasGst") ?: false,
                            logoUrl = document.getString("logoUrl"),
                            signatureUrl = document.getString("signatureUrl"),
                            createdAt = document.getTimestamp("createdAt") ?: Timestamp.now()
                        )
                        
                        // Now get the user profile to get the phone number
                        FirebaseFirestore.getInstance().collection("users")
                            .document(userId)
                            .get()
                            .addOnSuccessListener { userDoc ->
                                val phoneNumber = userDoc.getString("phoneNumber") ?: ""
                                val email = userDoc.getString("email") ?: ""
                                val name = userDoc.getString("name") ?: ""
                                
                                // Create the Shop object with user data
                                val shop = Shop(
                                    shopName = shopDetails.shopName,
                                    phoneNumber = phoneNumber,
                                    name = name,
                                    hasGst = shopDetails.hasGst,
                                    gstNumber = shopDetails.gstNumber ?: "",
                                    address = shopDetails.address,
                                    createdAt = shopDetails.createdAt,
                                    email = email,
                                    logo = shopDetails.logoUrl,
                                    signature = shopDetails.signatureUrl
                                )
                                
                                // Save to local cache
                                saveShopLocally(shop, context)
                                
                                callback(shop)
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Error getting user profile", e)
                                
                                // Fall back to just the shop details without phone number
                                val shop = convertToLegacyShop(shopDetails)
                                saveShopLocally(shop, context)
                                callback(shop)
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing shop data", e)
                        callback(null)
                    }
                } else {
                    Log.d(TAG, "Shop document does not exist for ID: $shopId")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error getting shop from Firestore", e)
                callback(null)
            }
    }

    // Coroutine version
    suspend fun getShopCoroutine(context: Context, forceRefresh: Boolean = false): Shop? {
        if (!forceRefresh) {
            // Try to get from SharedPreferences first
            val localShop = getShopFromLocal(context)
            if (localShop != null) {
                return localShop
            }
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null

        return try {
            val document = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val shop = Shop(
                    name = document.getString("name") ?: "",
                    phoneNumber = document.getString("phoneNumber") ?: "",
                    shopName = document.getString("shopName") ?: "",
                    address = document.getString("address") ?: "",
                    gstNumber = document.getString("gstNumber") ?: "",
                    hasGst = document.getBoolean("hasGST") ?: false,
                    createdAt = document.getTimestamp("createdAt") ?: Timestamp.now(),
                    email = document.getString("email") ?: "",
                    logo = document.getString("logo"),
                    signature = document.getString("signature")
                )

                // Save to SharedPreferences
                saveShopLocally(shop, context)
                shop
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting shop from Firestore", e)
            null
        }
    }

    // Save shop data to SharedPreferences
    private fun saveShopLocally(shop: Shop, context: Context) {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }

        val shopJson = gson.toJson(shop)
        sharedPreferences.edit().putString(KEY_SHOP_DATA, shopJson).apply()
    }

    // Retrieve shop data from SharedPreferences
    private fun getShopFromLocal(context: Context): Shop? {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }

        val shopJson = sharedPreferences.getString(KEY_SHOP_DATA, null) ?: return null
        return try {
            gson.fromJson(shopJson, Shop::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing shop data from SharedPreferences", e)
            null
        }
    }

    // Clear shop data from SharedPreferences (e.g., on logout)
    fun clearLocalShop(context: Context) {
        if (!::sharedPreferences.isInitialized) {
            initialize(context)
        }

        sharedPreferences.edit().remove(KEY_SHOP_DATA).apply()
    }

    // Function to update shop logo
    fun updateShopLogo(logoUri: Uri, context: Context, onComplete: (Boolean, Exception?) -> Unit) {
        val shop =
            getShopFromLocal(context) ?: return onComplete(false, Exception("Shop data not found"))

        // Update the shop object with the new logo URI
        val updatedShop = shop.copy(logo = logoUri.toString())

        // Save the updated shop
        saveShop(updatedShop, context, onComplete)
    }

    // Function to update shop signature
    fun updateShopSignature(
        signatureUri: Uri,
        context: Context,
        onComplete: (Boolean, Exception?) -> Unit
    ) {
        val shop =
            getShopFromLocal(context) ?: return onComplete(false, Exception("Shop data not found"))

        // Update the shop object with the new signature URI
        val updatedShop = shop.copy(signature = signatureUri.toString())

        // Save the updated shop
        saveShop(updatedShop, context, onComplete)
    }

    // Get shop details in the format needed for invoice generation
    fun getShopDetails(context: Context): ShopInvoiceDetails {
        // Get the shop from local storage
        val shop = getShopFromLocal(context)

        // Convert to the invoice-compatible Shop object
        return ShopInvoiceDetails(
            id = FirebaseAuth.getInstance().currentUser?.uid ?: "default",
            shopName = shop?.shopName ?: "Your Jewelry Shop",
            address = shop?.address ?: "123 Jewelry Lane, Bangalore, 560001",
            phoneNumber = shop?.phoneNumber ?: "9876543210",
            email = shop?.email ?: "contact@yourjewelryshop.com",
            gstNumber = shop?.gstNumber ?: "29ABCDE1234F1Z5",
            logo = shop?.logo,
            signature = shop?.signature
        )
    }

    // Custom serializer/deserializer for Firebase Timestamp
    private class TimestampSerializer : JsonSerializer<Timestamp> {
        override fun serialize(
            src: Timestamp?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement {
            return JsonPrimitive(src!!.seconds * 1000 + src.nanoseconds / 1000000)
        }
    }

    private class TimestampDeserializer : JsonDeserializer<Timestamp> {
        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Timestamp {
            val millis = json!!.asLong
            return Timestamp(java.util.Date(millis))
        }
    }

    // Add a new method to get the active shop's details
    suspend fun getActiveShopDetails(context: Context): Result<ShopDetails?> {
        return try {
            val shopId = SessionManager.getActiveShopId(context)
                ?: return Result.failure(Exception("No active shop selected"))
            
            getShopDetails(shopId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active shop details", e)
            Result.failure(e)
        }
    }
}