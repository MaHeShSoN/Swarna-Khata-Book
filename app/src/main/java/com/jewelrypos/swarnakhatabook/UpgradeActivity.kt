package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.BuildConfig
import com.jewelrypos.swarnakhatabook.Repository.BillingManager
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.databinding.ActivityUpgradeBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UpgradeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpgradeBinding
    private lateinit var billingManager: BillingManager
    private lateinit var subscriptionManager: UserSubscriptionManager
    private var debugMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpgradeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        billingManager = SwarnaKhataBook.getBillingManager()
        subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
        
        // Enable debug mode in debug builds
        debugMode = BuildConfig.DEBUG
        if (debugMode) {
            setupDebugMode()
        }

        // Setup subscription buttons
        setupSubscriptionButtons()
        
        // Check current subscription status
        checkSubscriptionStatus()
        
        // Wait a moment and then verify product details are loaded
        if (debugMode) {
            lifecycleScope.launch {
                delay(2000) // Wait for product details to load
                verifyProductDetails()
            }
        }
    }
    
    /**
     * Check the current subscription status and update UI accordingly
     */
    private fun checkSubscriptionStatus() {
        lifecycleScope.launch {
            // Check if trial is active
            val isTrialActive = subscriptionManager.isTrialActive()
            
            // Check current subscription plan
            val currentPlan = subscriptionManager.getCurrentSubscriptionPlan()
            
            // Update UI based on current status
            runOnUiThread {
                // Show trial badge if active
                binding.trialBanner.visibility = if (isTrialActive) View.VISIBLE else View.GONE
                
                // If trial is active, show days remaining
                if (isTrialActive) {
                    val daysRemaining = subscriptionManager.getDaysRemaining()
                    binding.trialDaysRemaining.text = "Days remaining: $daysRemaining"
                }
                
                // Highlight current plan if any
                highlightCurrentPlan(currentPlan.name)
            }
        }
    }
    
    /**
     * Highlight the card of the currently active plan
     */
    private fun highlightCurrentPlan(planName: String) {
        // Reset all cards to default state
        binding.basicMonthlyCard.alpha = 1.0f
        binding.standardMonthlyCard.alpha = 1.0f
        binding.premiumMonthlyCard.alpha = 1.0f
        binding.basicYearlyCard.alpha = 1.0f
        binding.standardYearlyCard.alpha = 1.0f
        binding.premiumYearlyCard.alpha = 1.0f
        
        // Remove any "Current Plan" indicators
        binding.basicMonthlyCurrentPlan.visibility = View.GONE
        binding.standardMonthlyCurrentPlan.visibility = View.GONE
        binding.premiumMonthlyCurrentPlan.visibility = View.GONE
        binding.basicYearlyCurrentPlan.visibility = View.GONE
        binding.standardYearlyCurrentPlan.visibility = View.GONE
        binding.premiumYearlyCurrentPlan.visibility = View.GONE
        
        // Highlight the current plan if any
        when (planName) {
            "BASIC" -> {
                binding.basicMonthlyCurrentPlan.visibility = View.VISIBLE
                binding.basicYearlyCurrentPlan.visibility = View.VISIBLE
            }
            "STANDARD" -> {
                binding.standardMonthlyCurrentPlan.visibility = View.VISIBLE
                binding.standardYearlyCurrentPlan.visibility = View.VISIBLE
            }
            "PREMIUM" -> {
                binding.premiumMonthlyCurrentPlan.visibility = View.VISIBLE
                binding.premiumYearlyCurrentPlan.visibility = View.VISIBLE
            }
        }
    }

    private fun setupSubscriptionButtons() {
        // Basic monthly plan (₹99/month)
        binding.basicMonthlyCard.setOnClickListener {
            processPurchase(BillingManager.SKU_BASIC_MONTHLY)
        }

        // Standard monthly plan (₹199/month)
        binding.standardMonthlyCard.setOnClickListener {
            processPurchase(BillingManager.SKU_STANDARD_MONTHLY)
        }

        // Premium monthly plan (₹299/month)
        binding.premiumMonthlyCard.setOnClickListener {
            processPurchase(BillingManager.SKU_PREMIUM_MONTHLY)
        }

        // Basic yearly plan
        binding.basicYearlyCard.setOnClickListener {
            processPurchase(BillingManager.SKU_BASIC_YEARLY)
        }

        // Standard yearly plan
        binding.standardYearlyCard.setOnClickListener {
            processPurchase(BillingManager.SKU_STANDARD_YEARLY)
        }

        // Premium yearly plan
        binding.premiumYearlyCard.setOnClickListener {
            processPurchase(BillingManager.SKU_PREMIUM_YEARLY)
        }
    }

    private fun processPurchase(sku: String) {
        if (debugMode) {
            // In debug mode, show details about the product
            showProductDetails(sku)
        } else {
            // Launch the Google Play billing flow
            billingManager.purchaseSubscription(this, sku)
        }
    }
    
    /**
     * Setup debug mode UI elements
     */
    private fun setupDebugMode() {
        // Show debug info container
        binding.debugContainer.visibility = View.VISIBLE
        
        // Add verify button
        binding.verifyButton.setOnClickListener {
            verifyProductDetails()
        }
    }
    
    /**
     * Verify that product details are loaded correctly from Google Play
     */
    private fun verifyProductDetails() {
        val availableProducts = billingManager.getAvailableProducts()
        val debugText = StringBuilder()
        
        debugText.append("Available Products: ${availableProducts.size}\n\n")
        
        if (availableProducts.isEmpty()) {
            debugText.append("No products loaded. Check Play Console setup.")
        } else {
            availableProducts.forEach { productId ->
                debugText.append("• $productId\n")
                val offers = billingManager.getSubscriptionOffers(productId)
                if (offers.isEmpty()) {
                    debugText.append("  - No offers available\n")
                } else {
                    offers.forEach { offer ->
                        debugText.append("  - $offer\n")
                    }
                }
                debugText.append("\n")
            }
        }
        
        binding.debugText.text = debugText.toString()
    }
    
    /**
     * Show detailed information about a specific product
     */
    private fun showProductDetails(productId: String) {
        val offers = billingManager.getSubscriptionOffers(productId)
        val debugText = StringBuilder()
        
        debugText.append("Product: $productId\n\n")
        
        if (offers.isEmpty()) {
            debugText.append("No offers available for this product.\n")
            debugText.append("Check that the product is properly configured in Play Console.")
        } else {
            debugText.append("Available Offers:\n")
            offers.forEach { offer ->
                debugText.append("• $offer\n")
            }
            
            // Show which base plan would be used
            val basePlanId = when (productId) {
                BillingManager.SKU_BASIC_MONTHLY -> BillingManager.BASE_PLAN_BASIC_MONTHLY
                BillingManager.SKU_STANDARD_MONTHLY -> BillingManager.BASE_PLAN_STANDARD_MONTHLY
                BillingManager.SKU_PREMIUM_MONTHLY -> BillingManager.BASE_PLAN_PREMIUM_MONTHLY
                BillingManager.SKU_BASIC_YEARLY -> BillingManager.BASE_PLAN_BASIC_YEARLY
                BillingManager.SKU_STANDARD_YEARLY -> BillingManager.BASE_PLAN_STANDARD_YEARLY
                BillingManager.SKU_PREMIUM_YEARLY -> BillingManager.BASE_PLAN_PREMIUM_YEARLY
                else -> null
            }
            
            debugText.append("\nTargeted base plan: $basePlanId\n")
        }
        
        binding.debugText.text = debugText.toString()
        
        // Scroll to make debug text visible
        binding.scrollView.post {
            binding.scrollView.smoothScrollTo(0, binding.debugContainer.top)
        }
        
        Toast.makeText(this, "Debug info shown below", Toast.LENGTH_SHORT).show()
    }
}