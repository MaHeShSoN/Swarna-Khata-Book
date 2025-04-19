package com.jewelrypos.swarnakhatabook.Repository

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Enums.SubscriptionPlan
import com.jewelrypos.swarnakhatabook.SwarnaKhataBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Manages subscription purchases and verification using Google Play Billing Library
 */
class BillingManager(private val context: Context) {

    private val TAG = "BillingManager"
    
    // Define subscription product IDs - these must match exactly with Play Console
    companion object {
        // Monthly subscription products
        const val SKU_BASIC_MONTHLY = "jewelry_pos_basic_monthly"
        const val SKU_STANDARD_MONTHLY = "jewelry_pos_standard_monthly"
        const val SKU_PREMIUM_MONTHLY = "jewelry_pos_premium_monthly"
        
        // Yearly subscription products
        const val SKU_BASIC_YEARLY = "jewelry_pos_basic_yearly"
        const val SKU_STANDARD_YEARLY = "jewelry_pos_standard_yearly"
        const val SKU_PREMIUM_YEARLY = "jewelry_pos_premium_yearly"
        
        // Base plan IDs - must match those in Play Console
        const val BASE_PLAN_BASIC_MONTHLY = "basic-monthly-plan"
        const val BASE_PLAN_STANDARD_MONTHLY = "standard-monthly-plan"
        const val BASE_PLAN_PREMIUM_MONTHLY = "premium-monthly-plan"
        const val BASE_PLAN_BASIC_YEARLY = "basic-yearly-plan" 
        const val BASE_PLAN_STANDARD_YEARLY = "standard-yearly-plan"
        const val BASE_PLAN_PREMIUM_YEARLY = "premium-yearly-plan"
    }
    
    // Map subscription product IDs to subscription plans
    private val productToSubscriptionPlanMap = mapOf(
        SKU_BASIC_MONTHLY to SubscriptionPlan.BASIC,
        SKU_STANDARD_MONTHLY to SubscriptionPlan.STANDARD,
        SKU_PREMIUM_MONTHLY to SubscriptionPlan.PREMIUM,
        SKU_BASIC_YEARLY to SubscriptionPlan.BASIC,
        SKU_STANDARD_YEARLY to SubscriptionPlan.STANDARD,
        SKU_PREMIUM_YEARLY to SubscriptionPlan.PREMIUM
    )

    // Maintain a background scope for operations
    private val billingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val firestore = FirebaseFirestore.getInstance()
    
    // Billing client and connection state
    private lateinit var billingClient: BillingClient
    private var isClientConnected = false
    
    // Cache product details for quick access during purchase flow
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    
    // Purchase listener for handling purchase updates
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    Log.d(TAG, "Purchase successful, processing ${purchases.size} purchases")
                    billingScope.launch {
                        for (purchase in purchases) {
                            handlePurchase(purchase)
                        }
                    }
                } else {
                    Log.d(TAG, "Purchase successful but no purchases returned")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "User canceled the purchase")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.i(TAG, "Item already owned, refreshing purchases")
                billingScope.launch {
                    queryPurchases()
                }
            }
            else -> {
                Log.e(TAG, "Purchase failed with code: ${billingResult.responseCode}, message: ${billingResult.debugMessage}")
            }
        }
    }

    init {
        setupBillingClient()
    }

    /**
     * Set up and connect the billing client
     */
    private fun setupBillingClient() {
        Log.d(TAG, "Setting up BillingClient")
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        // Connect to Play Billing service
        connectToPlayBilling()
    }

    /**
     * Connect to Google Play Billing service for subscription management
     * Returns true if already connected or connection was initiated
     */
    fun connectToPlayBilling(): Boolean {
        Log.d(TAG, "Connecting to Play Billing service")
        
        if (isClientConnected) {
            Log.d(TAG, "Already connected to Play Billing service")
            return true
        }
        
        if (!billingClient.isReady) {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        isClientConnected = true
                        Log.d(TAG, "Play Billing service connection established")
                        
                        // Query product details on successful connection
                        queryProductDetails()
                        
                        // Check existing purchases
                        billingScope.launch {
                            queryPurchases()
                        }
                    } else {
                        isClientConnected = false
                        Log.e(TAG, "Failed to connect to Play Billing service: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    isClientConnected = false
                    Log.e(TAG, "Play Billing service disconnected")
                    // Try to reconnect with delay (simple approach)
                    Handler(Looper.getMainLooper()).postDelayed({
                        connectToPlayBilling()
                    }, 3000) // 3 second delay before reconnect attempt
                }
            })
            return true
        }
        
        return isClientConnected
    }
    
    /**
     * Query subscription product details from Google Play
     */
    private fun queryProductDetails() {
        Log.d(TAG, "Querying subscription product details")
        
        if (!isClientConnected) {
            Log.e(TAG, "Billing client not connected")
            return
        }
        
        // Create list of products to query
        val productList = listOf(
            SKU_BASIC_MONTHLY, SKU_STANDARD_MONTHLY, SKU_PREMIUM_MONTHLY,
            SKU_BASIC_YEARLY, SKU_STANDARD_YEARLY, SKU_PREMIUM_YEARLY
        ).map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
            
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Product details query successful, found ${productDetailsList.size} products")
                
                // Clear existing cache and store new product details
                productDetailsMap.clear()
                productDetailsList.forEach { productDetails ->
                    productDetailsMap[productDetails.productId] = productDetails
                    
                    // Log available subscription offers for debugging
                    val offerCount = productDetails.subscriptionOfferDetails?.size ?: 0
                    Log.d(TAG, "Product ${productDetails.productId} has $offerCount subscription offers")
                    
                    productDetails.subscriptionOfferDetails?.forEach { offer ->
                        Log.d(TAG, "  - Offer: ${offer.basePlanId}, token: ${offer.offerToken.take(10)}...")
                    }
                }
            } else {
                Log.e(TAG, "Failed to query product details: ${billingResult.responseCode}, ${billingResult.debugMessage}")
            }
        }
    }
    
    /**
     * Check for existing subscription purchases
     */
    private suspend fun queryPurchases() {
        if (!isClientConnected) {
            Log.e(TAG, "Billing client not connected")
            return
        }
        
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
            
        // Using suspendCancellableCoroutine to handle the async callback
        val purchasesResult = suspendCancellableCoroutine<List<Purchase>> { continuation ->
            billingClient.queryPurchasesAsync(
                params,
                PurchasesResponseListener { billingResult, purchases ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "Found ${purchases.size} existing subscription purchases")
                        continuation.resume(purchases)
                    } else {
                        Log.e(TAG, "Failed to query purchases: ${billingResult.responseCode}, ${billingResult.debugMessage}")
                        continuation.resume(emptyList())
                    }
                }
            )
        }
            
        // Process all active purchases
        for (purchase in purchasesResult) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                handlePurchase(purchase)
            }
        }
    }
    
    /**
     * Handle a purchase and update the user's subscription plan
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        // Check if purchase is acknowledged
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            // Acknowledge the purchase
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
                
            // Using suspendCancellableCoroutine to handle the async callback
            val billingResult = suspendCancellableCoroutine<BillingResult> { continuation ->
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
                    continuation.resume(result)
                }
            }
            
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                return
            }
        }
        
        // Update subscription in Firestore
        try {
            // Get all products in this purchase
            val products = purchase.products
            if (products.isEmpty()) {
                Log.e(TAG, "Purchase has no products")
                return
            }
            
            Log.d(TAG, "Purchase contains products: ${products.joinToString()}")
            
            for (productId in products) {
                val subscriptionPlan = productToSubscriptionPlanMap[productId]
                if (subscriptionPlan != null) {
                    val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()
                    val success = subscriptionManager.updateSubscriptionPlan(subscriptionPlan)
                    
                    if (success) {
                        // End trial if active
                        subscriptionManager.endTrial()
                        
                        // Store purchase details in Firestore
                        storeSubscriptionDetails(purchase, subscriptionPlan)
                        
                        Log.d(TAG, "Subscription updated to ${subscriptionPlan.name} for product $productId")
                    } else {
                        Log.e(TAG, "Failed to update subscription plan for product $productId")
                    }
                } else {
                    Log.e(TAG, "Unknown product ID: $productId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscription status", e)
        }
    }
    
    /**
     * Store subscription purchase details in Firestore for tracking
     */
    private suspend fun storeSubscriptionDetails(purchase: Purchase, plan: SubscriptionPlan) {
        withContext(Dispatchers.IO) {
            try {
                val userId = SwarnaKhataBook.getUserSubscriptionManager().getCurrentUserId()
                if (userId.isEmpty()) {
                    Log.e(TAG, "No user ID available")
                    return@withContext
                }
                
                val purchaseData = hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "orderId" to (purchase.orderId ?: ""),
                    "purchaseTime" to purchase.purchaseTime,
                    "products" to purchase.products,
                    "subscriptionPlan" to plan.name,
                    "updatedAt" to Timestamp.now()
                )
                
                firestore.collection("users")
                    .document(userId)
                    .collection("subscriptions")
                    .document(purchase.orderId ?: purchase.purchaseToken)
                    .set(purchaseData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Subscription details stored in Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error storing subscription details", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error storing subscription details", e)
            }
        }
    }
    
    /**
     * Launch the purchase flow for a subscription
     */
    fun purchaseSubscription(activity: Activity, productId: String) {
        Log.d(TAG, "Initiating purchase flow for $productId")
        
        if (!isClientConnected) {
            Log.e(TAG, "Billing client not connected, attempting to connect")
            if (connectToPlayBilling()) {
                // If connection was initiated, retry purchase after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isClientConnected) {
                        purchaseSubscription(activity, productId)
                    } else {
                        Log.e(TAG, "Failed to connect to billing service, cannot initiate purchase")
                    }
                }, 2000) // 2 second delay to allow connection to complete
            }
            return
        }
        
        val productDetails = productDetailsMap[productId]
        if (productDetails == null) {
            Log.e(TAG, "Product details not found for $productId, refreshing product details")
            queryProductDetails()
            return
        }
        
        // Get subscription offer details
        val offerDetails = productDetails.subscriptionOfferDetails
        if (offerDetails.isNullOrEmpty()) {
            Log.e(TAG, "No subscription offers found for $productId")
            return
        }
        
        // Find the correct base plan to use
        val basePlanId = when (productId) {
            SKU_BASIC_MONTHLY -> BASE_PLAN_BASIC_MONTHLY
            SKU_STANDARD_MONTHLY -> BASE_PLAN_STANDARD_MONTHLY
            SKU_PREMIUM_MONTHLY -> BASE_PLAN_PREMIUM_MONTHLY
            SKU_BASIC_YEARLY -> BASE_PLAN_BASIC_YEARLY
            SKU_STANDARD_YEARLY -> BASE_PLAN_STANDARD_YEARLY
            SKU_PREMIUM_YEARLY -> BASE_PLAN_PREMIUM_YEARLY
            else -> null
        }
        
        if (basePlanId == null) {
            Log.e(TAG, "Unknown product ID: $productId")
            return
        }
        
        // Find the offer with matching base plan ID
        val selectedOffer = offerDetails.find { it.basePlanId == basePlanId }
        if (selectedOffer == null) {
            Log.e(TAG, "Could not find offer with base plan ID: $basePlanId")
            
            // Log available offers for debugging
            offerDetails.forEach { offer ->
                Log.d(TAG, "Available offer: ${offer.basePlanId}")
            }
            
            // Fall back to first offer if specific one not found
            Log.d(TAG, "Falling back to first available offer")
            val fallbackOffer = offerDetails.firstOrNull()
            if (fallbackOffer == null) {
                Log.e(TAG, "No offers available at all, cannot proceed with purchase")
                return
            }
            
            launchBillingFlow(activity, productDetails, fallbackOffer.offerToken)
            return
        }
        
        // Launch with the selected offer
        launchBillingFlow(activity, productDetails, selectedOffer.offerToken)
    }
    
    /**
     * Launch the billing flow with the given product details and offer token
     */
    private fun launchBillingFlow(activity: Activity, productDetails: ProductDetails, offerToken: String) {
        // Create the billing flow params
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        // Launch the billing flow
        Log.d(TAG, "Launching billing flow for ${productDetails.productId} with offer token: ${offerToken.take(10)}...")
        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Failed to launch billing flow: ${billingResult.responseCode}, ${billingResult.debugMessage}")
        }
    }
    
    /**
     * Get the active subscription plan
     */
    suspend fun getActiveSubscriptionPlan(): SubscriptionPlan {
        try {
            // Refresh purchases to ensure we have the latest data
            queryPurchases()
            
            // Get plan from subscription manager
            return SwarnaKhataBook.getUserSubscriptionManager().getCurrentSubscriptionPlan()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active subscription plan", e)
            return SubscriptionPlan.NONE
        }
    }
    
    /**
     * Check if there is an active subscription
     */
    suspend fun hasActiveSubscription(): Boolean {
        return try {
            getActiveSubscriptionPlan() != SubscriptionPlan.NONE
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active subscription", e)
            false
        }
    }
    
    /**
     * For debugging: get all available product details
     */
    fun getAvailableProducts(): List<String> {
        return productDetailsMap.keys.toList()
    }
    
    /**
     * For debugging: get subscription offers for a product
     */
    fun getSubscriptionOffers(productId: String): List<String> {
        val productDetails = productDetailsMap[productId] ?: return emptyList()
        return productDetails.subscriptionOfferDetails?.map { 
            "${it.basePlanId} (${it.pricingPhases.pricingPhaseList.firstOrNull()?.formattedPrice ?: "unknown price"})"
        } ?: emptyList()
    }
}