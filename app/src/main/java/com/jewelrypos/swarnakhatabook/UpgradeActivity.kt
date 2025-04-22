package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
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
    private var isMonthlySelected = true
    private var selectedPlanType = "STANDARD" // Default selection

    private val SELECTED_CARD_TRANSLATION_Y = -8f // A smaller upward movement to prevent overlap
    private val UNSELECTED_CARD_TRANSLATION_Y = 0f // Keep cards at their natural position when unselected


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

        // Setup period toggles
        setupBillingPeriodToggles()

        // Setup subscription buttons
        setupSubscriptionButtons()

        // Set initial plan details
        updateSelectedPlanDetails("STANDARD")

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
     * Setup the monthly/yearly toggle buttons
     */
    private fun setupBillingPeriodToggles() {
        // Monthly toggle click listener
        binding.monthlyToggle.setOnClickListener {
            if (!isMonthlySelected) {
                isMonthlySelected = true

                // Update toggle visuals
                binding.monthlyToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.my_light_primary))
                binding.monthlyToggle.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.yearlyToggle.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                binding.yearlyToggle.setTextColor(ContextCompat.getColor(this, R.color.my_light_secondary))

                // Show monthly cards, hide yearly cards
                binding.basicMonthlyCard.visibility = View.VISIBLE
                binding.standardMonthlyCard.visibility = View.VISIBLE
                binding.premiumMonthlyCard.visibility = View.VISIBLE
                binding.basicYearlyCard.visibility = View.GONE
                binding.standardYearlyCard.visibility = View.GONE
                binding.premiumYearlyCard.visibility = View.GONE

                // Update highlighted plan
                highlightCurrentPlan(selectedPlanType)

                // Update purchase button based on current selection
                updatePurchaseButtonText()
            }
        }

        // Yearly toggle click listener
        binding.yearlyToggle.setOnClickListener {
            if (isMonthlySelected) {
                isMonthlySelected = false

                // Update toggle visuals
                binding.yearlyToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.my_light_primary))
                binding.yearlyToggle.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.monthlyToggle.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                binding.monthlyToggle.setTextColor(ContextCompat.getColor(this, R.color.my_light_secondary))

                // Show yearly cards, hide monthly cards
                binding.basicMonthlyCard.visibility = View.GONE
                binding.standardMonthlyCard.visibility = View.GONE
                binding.premiumMonthlyCard.visibility = View.GONE
                binding.basicYearlyCard.visibility = View.VISIBLE
                binding.standardYearlyCard.visibility = View.VISIBLE
                binding.premiumYearlyCard.visibility = View.VISIBLE

                // Update highlighted plan
                highlightCurrentPlan(selectedPlanType)

                // Update purchase button based on current selection
                updatePurchaseButtonText()
            }
        }

        // Start with monthly selected
        binding.monthlyToggle.performClick()
    }

    /**
     * Update the purchase button text based on selected plan and period
     */
    private fun updatePurchaseButtonText() {
        val price = when {
            isMonthlySelected && selectedPlanType == "BASIC" -> "₹99"
            isMonthlySelected && selectedPlanType == "STANDARD" -> "₹199"
            isMonthlySelected && selectedPlanType == "PREMIUM" -> "₹299"
            !isMonthlySelected && selectedPlanType == "BASIC" -> "₹999"
            !isMonthlySelected && selectedPlanType == "STANDARD" -> "₹1,999"
            !isMonthlySelected && selectedPlanType == "PREMIUM" -> "₹2,999"
            else -> "₹199" // Default to standard monthly
        }

        val period = if (isMonthlySelected) "/month" else "/year"
        binding.purchaseButton.text = "Subscribe for $price$period"
    }

    /**
     * Update the plan details section based on the selected plan
     */
    private fun updateSelectedPlanDetails(planType: String) {
        selectedPlanType = planType

        // Update title and features visibility based on selected plan
        when (planType) {
            "BASIC" -> {
                binding.selectedPlanTitle.text = "Basic Plan"
                binding.basicPlanFeatures.visibility = View.VISIBLE
                binding.standardPlanFeatures.visibility = View.GONE
                binding.premiumPlanFeatures.visibility = View.GONE
            }
            "STANDARD" -> {
                binding.selectedPlanTitle.text = "Standard Plan"
                binding.basicPlanFeatures.visibility = View.GONE
                binding.standardPlanFeatures.visibility = View.VISIBLE
                binding.premiumPlanFeatures.visibility = View.GONE
            }
            "PREMIUM" -> {
                binding.selectedPlanTitle.text = "Premium Plan"
                binding.basicPlanFeatures.visibility = View.GONE
                binding.standardPlanFeatures.visibility = View.GONE
                binding.premiumPlanFeatures.visibility = View.VISIBLE
            }
        }

        // Update purchase button text
        updatePurchaseButtonText()
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
                    binding.trialDaysRemaining.text = "$daysRemaining days remaining"
                }

                // Highlight current plan if any
                highlightCurrentPlan(currentPlan.name)

                // Also update selected plan type to match current plan
                if (currentPlan.name != "NONE") {
                    updateSelectedPlanDetails(currentPlan.name)
                }
            }
        }
    }

    /**
     * Highlight the card of the currently active plan
     */
    private fun highlightCurrentPlan(planName: String) {
        // Reset all cards to default state
        resetCardHighlighting(binding.basicMonthlyCard)
        resetCardHighlighting(binding.standardMonthlyCard)
        resetCardHighlighting(binding.premiumMonthlyCard)
        resetCardHighlighting(binding.basicYearlyCard)
        resetCardHighlighting(binding.standardYearlyCard)
        resetCardHighlighting(binding.premiumYearlyCard)

        // Keep the "CurrentPlan" views for compatibility, but they're not visible
        binding.basicMonthlyCurrentPlan.visibility = View.GONE
        binding.standardMonthlyCurrentPlan.visibility = View.GONE
        binding.premiumMonthlyCurrentPlan.visibility = View.GONE
        binding.basicYearlyCurrentPlan.visibility = View.GONE
        binding.standardYearlyCurrentPlan.visibility = View.GONE
        binding.premiumYearlyCurrentPlan.visibility = View.GONE

        // Update the "CurrentPlan" visibility for compatibility with old code
        when (planName) {
            "BASIC" -> {
                binding.basicMonthlyCurrentPlan.visibility = View.VISIBLE
                binding.basicYearlyCurrentPlan.visibility = View.VISIBLE

                // Visually highlight the appropriate card based on monthly/yearly toggle
                if (isMonthlySelected) {
                    highlightCard(binding.basicMonthlyCard)
                } else {
                    highlightCard(binding.basicYearlyCard)
                }
            }
            "STANDARD" -> {
                binding.standardMonthlyCurrentPlan.visibility = View.VISIBLE
                binding.standardYearlyCurrentPlan.visibility = View.VISIBLE

                // Visually highlight the appropriate card based on monthly/yearly toggle
                if (isMonthlySelected) {
                    highlightCard(binding.standardMonthlyCard)
                } else {
                    highlightCard(binding.standardYearlyCard)
                }
            }
            "PREMIUM" -> {
                binding.premiumMonthlyCurrentPlan.visibility = View.VISIBLE
                binding.premiumYearlyCurrentPlan.visibility = View.VISIBLE

                // Visually highlight the appropriate card based on monthly/yearly toggle
                if (isMonthlySelected) {
                    highlightCard(binding.premiumMonthlyCard)
                } else {
                    highlightCard(binding.premiumYearlyCard)
                }
            }
        }
    }

    /**
     * Highlight a card to indicate selection
     */
    private fun highlightCard(card: MaterialCardView) {
        // Reset all cards first
        resetAllCards()

        // Now highlight the selected card
        card.strokeColor = ContextCompat.getColor(this, R.color.my_light_primary)
        card.strokeWidth = resources.getDimensionPixelSize(R.dimen.card_selected_stroke_width)
        card.elevation = resources.getDimension(R.dimen.card_selected_elevation)

        // Animate the card moving up
        card.animate()
            .translationY(SELECTED_CARD_TRANSLATION_Y)
            .setDuration(200)
            .start()

        card.tag = "selected"
    }
    /**
     * Reset card highlighting to default state
     */
    private fun resetCardHighlighting(card: MaterialCardView) {
        // Reset stroke color and width to default
        card.strokeColor = ContextCompat.getColor(this, R.color.my_light_outline)
        card.strokeWidth = resources.getDimensionPixelSize(R.dimen.card_default_stroke_width)
        card.elevation = resources.getDimension(R.dimen.card_default_elevation)

        // Animate the card moving down
        card.animate()
            .translationY(UNSELECTED_CARD_TRANSLATION_Y)
            .setDuration(200)
            .start()

        // Clear the "selected" tag
        card.tag = null
    }
    private fun resetAllCards() {
        val allCards = listOf(
            binding.basicMonthlyCard,
            binding.standardMonthlyCard,
            binding.premiumMonthlyCard,
            binding.basicYearlyCard,
            binding.standardYearlyCard,
            binding.premiumYearlyCard
        )

        for (card in allCards) {
            resetCardHighlighting(card)
        }
    }

    private fun setupSubscriptionButtons() {
        // Basic plan clicks - only select, don't purchase
        binding.basicMonthlyCard.setOnClickListener {
            updateSelectedPlanDetails("BASIC")
            highlightCurrentPlan("BASIC")
        }

        binding.basicYearlyCard.setOnClickListener {
            updateSelectedPlanDetails("BASIC")
            highlightCurrentPlan("BASIC")
        }

        // Standard plan clicks - only select, don't purchase
        binding.standardMonthlyCard.setOnClickListener {
            updateSelectedPlanDetails("STANDARD")
            highlightCurrentPlan("STANDARD")
        }

        binding.standardYearlyCard.setOnClickListener {
            updateSelectedPlanDetails("STANDARD")
            highlightCurrentPlan("STANDARD")
        }

        // Premium plan clicks - only select, don't purchase
        binding.premiumMonthlyCard.setOnClickListener {
            updateSelectedPlanDetails("PREMIUM")
            highlightCurrentPlan("PREMIUM")
        }

        binding.premiumYearlyCard.setOnClickListener {
            updateSelectedPlanDetails("PREMIUM")
            highlightCurrentPlan("PREMIUM")
        }

        // Purchase button - only this triggers the purchase
        binding.purchaseButton.setOnClickListener {
            val sku = if (isMonthlySelected) {
                when (selectedPlanType) {
                    "BASIC" -> BillingManager.SKU_BASIC_MONTHLY
                    "STANDARD" -> BillingManager.SKU_STANDARD_MONTHLY
                    "PREMIUM" -> BillingManager.SKU_PREMIUM_MONTHLY
                    else -> BillingManager.SKU_STANDARD_MONTHLY
                }
            } else {
                when (selectedPlanType) {
                    "BASIC" -> BillingManager.SKU_BASIC_YEARLY
                    "STANDARD" -> BillingManager.SKU_STANDARD_YEARLY
                    "PREMIUM" -> BillingManager.SKU_PREMIUM_YEARLY
                    else -> BillingManager.SKU_STANDARD_YEARLY
                }
            }

            processPurchase(sku)
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

    // Add these values to your dimens.xml file
    // <dimen name="card_selected_stroke_width">2dp</dimen>
    // <dimen name="card_default_stroke_width">1dp</dimen>
    // <dimen name="card_selected_elevation">6dp</dimen>
    // <dimen name="card_default_elevation">1dp</dimen>
}