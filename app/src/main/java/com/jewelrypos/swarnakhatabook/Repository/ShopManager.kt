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
import com.jewelrypos.swarnakhatabook.DataClasses.ShopInvoiceDetails
import kotlinx.coroutines.tasks.await
import java.lang.reflect.Type

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

    // Save shop data to both Firebase and SharedPreferences
    fun saveShop(shop: Shop, context: Context, onComplete: (Boolean, Exception?) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: return onComplete(false, Exception("User not logged in"))

        val shopData = hashMapOf(
            "name" to shop.name,
            "phoneNumber" to shop.phoneNumber,
            "shopName" to shop.shopName,
            "address" to shop.address,
            "gstNumber" to shop.gstNumber,
            "hasGST" to shop.hasGst,
            "createdAt" to shop.createdAt,
            "email" to shop.email,
            "logo" to shop.logo,
            "signature" to shop.signature
        )

        // Save to Firebase
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .set(shopData)
            .addOnSuccessListener {
                // Save to SharedPreferences after successful Firebase save
                saveShopLocally(shop, context)
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                onComplete(false, e)
            }
    }

    // Get shop data (first from SharedPreferences, then Firebase if needed)
    fun getShop(
        context: Context,
        forceRefresh: Boolean = false,
        onComplete: (Shop?) -> Unit
    ) {
        if (!forceRefresh) {
            // Try to get from SharedPreferences first
            val localShop = getShopFromLocal(context)
            if (localShop != null) {
                onComplete(localShop)
                return
            }
        }

        // If we need to refresh or don't have local data, get from Firebase
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete(null)
            return
        }

        FirebaseFirestore.getInstance().collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
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

                    // Save fetched data to SharedPreferences
                    saveShopLocally(shop, context)
                    onComplete(shop)
                } else {
                    onComplete(null)
                }
            }
            .addOnFailureListener {
                onComplete(null)
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
}