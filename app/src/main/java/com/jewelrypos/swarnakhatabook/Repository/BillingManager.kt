package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchasesUpdatedListener

class BillingManager(private val context: Context) {

    private lateinit var billingClient: BillingClient
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // Process purchases
            for (purchase in purchases) {
//                handlePurchase(purchase)
            }
        }
    }

    init {
        setupBillingClient()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready
//                    querySkuDetails()
//                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection
            }
        })
    }

    // Rest of the implementation...
}