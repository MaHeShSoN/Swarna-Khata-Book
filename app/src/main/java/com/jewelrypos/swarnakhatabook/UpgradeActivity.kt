package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.BuildConfig
import com.jewelrypos.swarnakhatabook.Repository.BillingManager
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.databinding.ActivityUpgradeBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.jewelrypos.swarnakhatabook.Repository.BillingEvent

class UpgradeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpgradeBinding
    private lateinit var billingManager: BillingManager
    private lateinit var subscriptionManager: UserSubscriptionManager
    private var isMonthlySelected = true
    private var selectedPlanType = "STANDARD" // Default selection

    private val SELECTED_CARD_TRANSLATION_Y = -8f // A smaller upward movement to prevent overlap
    private val UNSELECTED_CARD_TRANSLATION_Y =
        0f // Keep cards at their natural position when unselected


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpgradeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        billingManager = SwarnaKhataBook.getBillingManager()
        subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

        // Get current plan from intent if available
        val currentPlanFromIntent = intent.getStringExtra("CURRENT_PLAN")
        if (currentPlanFromIntent != null && currentPlanFromIntent != "NONE") {
            selectedPlanType = currentPlanFromIntent
        }

        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }


        // Setup period toggles
        setupBillingPeriodToggles()

        // Setup subscription buttons
        setupSubscriptionButtons()

        // Set initial plan details
        updateSelectedPlanDetails("STANDARD")

        // Check current subscription status
        checkSubscriptionStatus()


        // Observe billing events
        setupBillingEventObserver()
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
                binding.monthlyToggle.setBackgroundColor(
                    ContextCompat.getColor(
                        this, R.color.my_light_primary
                    )
                )
                binding.monthlyToggle.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.yearlyToggle.setBackgroundColor(
                    ContextCompat.getColor(
                        this, android.R.color.transparent
                    )
                )
                binding.yearlyToggle.setTextColor(
                    ContextCompat.getColor(
                        this, R.color.my_light_secondary
                    )
                )

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
                binding.yearlyToggle.setBackgroundColor(
                    ContextCompat.getColor(
                        this, R.color.my_light_primary
                    )
                )
                binding.yearlyToggle.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.monthlyToggle.setBackgroundColor(
                    ContextCompat.getColor(
                        this, android.R.color.transparent
                    )
                )
                binding.monthlyToggle.setTextColor(
                    ContextCompat.getColor(
                        this, R.color.my_light_secondary
                    )
                )

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

        // Get formatted price from BillingManager
        val price = billingManager.getFormattedPrice(sku) ?: when {
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

                // Update standard plan features

            }

            "PREMIUM" -> {
                binding.selectedPlanTitle.text = "Premium Plan"
                binding.basicPlanFeatures.visibility = View.GONE
                binding.standardPlanFeatures.visibility = View.GONE
                binding.premiumPlanFeatures.visibility = View.VISIBLE

                // Update premium plan features
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
            // Check if user has an active subscription first
            val currentPlan = subscriptionManager.getCurrentSubscriptionPlan()
            
            // Only check trial status if there's no active subscription
            val isTrialActive = if (currentPlan.name == "NONE") {
                subscriptionManager.isTrialActive()
            } else {
                false // If there's an active subscription, trial should be considered inactive
            }

            // Update UI based on current status
            runOnUiThread {
                // Show trial banner only if trial is active AND there's no subscription
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
//        card.elevation = resources.getDimension(R.dimen.card_selected_elevation)

        // Animate the card moving up
//        card.animate()
//            .translationY(SELECTED_CARD_TRANSLATION_Y)
//            .setDuration(200)
//            .start()

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
        card.animate().translationY(UNSELECTED_CARD_TRANSLATION_Y).setDuration(200).start()

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
            updateCardPrices()
        }

        binding.basicYearlyCard.setOnClickListener {
            updateSelectedPlanDetails("BASIC")
            highlightCurrentPlan("BASIC")
            updateCardPrices()
        }

        // Standard plan clicks - only select, don't purchase
        binding.standardMonthlyCard.setOnClickListener {
            updateSelectedPlanDetails("STANDARD")
            highlightCurrentPlan("STANDARD")
            updateCardPrices()
        }

        binding.standardYearlyCard.setOnClickListener {
            updateSelectedPlanDetails("STANDARD")
            highlightCurrentPlan("STANDARD")
            updateCardPrices()
        }

        // Premium plan clicks - only select, don't purchase
        binding.premiumMonthlyCard.setOnClickListener {
            updateSelectedPlanDetails("PREMIUM")
            highlightCurrentPlan("PREMIUM")
            updateCardPrices()
        }

        binding.premiumYearlyCard.setOnClickListener {
            updateSelectedPlanDetails("PREMIUM")
            highlightCurrentPlan("PREMIUM")
            updateCardPrices()
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

        // Show loading indicator
        binding.progressBar.visibility = View.VISIBLE
        binding.purchaseButton.isEnabled = false

        // Launch the Google Play billing flow
        try {
            billingManager.purchaseSubscription(this, sku)

            // Set a timeout to hide the loading indicator if no event is received
            Handler(Looper.getMainLooper()).postDelayed({
                if (binding.progressBar.visibility == View.VISIBLE) {
                    hideLoadingIndicator()
                }
            }, 30000) // 30 seconds timeout
        } catch (e: Exception) {
            // Handle unexpected errors
            hideLoadingIndicator()
            showMessage("Error starting purchase: ${e.message ?: "Unknown error"}")
            Log.e("UpgradeActivity", "Error in purchase flow", e)
        }
    }

    /**
     * Populate subscription card prices with dynamic prices from Google Play
     */
    private fun updateCardPrices() {
        // Set hardcoded prices for all cards
        // Monthly prices
        binding.basicMonthlyPrice.text = "₹99"
        binding.standardMonthlyPrice.text = "₹199"
        binding.premiumMonthlyPrice.text = "₹299"

        // Yearly prices
        binding.basicYearlyPrice.text = "₹999"
        binding.standardYearlyPrice.text = "₹1,999"
        binding.premiumYearlyPrice.text = "₹2,999"

        // Update purchase button text
        updatePurchaseButtonText()
    }


    /**
     * Setup observer for billing events
     */
    private fun setupBillingEventObserver() {
        lifecycleScope.launch {
            billingManager.billingEvents.collect { event ->
                when (event) {
                    is BillingEvent.ConnectionEstablished -> {
                        Log.d("UpgradeActivity", "Billing connection established")
                        // Refresh product details and prices when connection is established
                        updateCardPrices()
                    }

                    is BillingEvent.ConnectionFailed -> {
                        Log.e("UpgradeActivity", "Billing connection failed")
                        showMessage("Could not connect to Google Play billing service")
                    }

                    is BillingEvent.ProductDetailsLoaded -> {
                        Log.d("UpgradeActivity", "Product details loaded")
                        // Update UI with loaded product details
                        updateCardPrices()
                    }

                    is BillingEvent.PurchaseSuccess -> {
                        Log.d("UpgradeActivity", "Purchase successful")
                        hideLoadingIndicator()
                        showMessage("Subscription updated successfully!")

                        // Refresh subscription status and explicitly update trial status
                        lifecycleScope.launch {
                            try {
                                subscriptionManager.refreshSubscriptionStatus()
                                
                                // Force trial banner to hide after successful purchase
                                binding.trialBanner.visibility = View.GONE
                                
                                // Recheck subscription status to update UI
                                checkSubscriptionStatus()
                            } catch (e: Exception) {
                                Log.e("UpgradeActivity", "Error refreshing subscription status", e)
                            }
                        }
                    }

                    is BillingEvent.PurchaseFailed -> {
                        Log.e(
                            "UpgradeActivity", "Purchase failed: ${event.message} (${event.code})"
                        )
                        hideLoadingIndicator()
                        showMessage("Purchase failed: ${event.message}")
                    }

                    is BillingEvent.PurchaseCanceled -> {
                        Log.d("UpgradeActivity", "Purchase canceled")
                        hideLoadingIndicator()
                        showMessage("Purchase canceled")
                    }

                    is BillingEvent.Error -> {
                        Log.e("UpgradeActivity", "Billing error: ${event.message}")
                        hideLoadingIndicator()
                        showMessage(event.message)
                    }
                }
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun hideLoadingIndicator() {
        binding.progressBar.visibility = View.GONE
        binding.purchaseButton.isEnabled = true
    }

    override fun onResume() {
        super.onResume()

        // Reconnect to billing service if needed
        if (!billingManager.connectToPlayBilling()) {
            showMessage("Could not connect to Google Play billing service. Please try again later.")
        } else {
            // Update card prices when activity resumes
            lifecycleScope.launch {
                delay(1000) // Give time for billing client to connect
                runOnUiThread {
                    updateCardPrices()
                }
            }
        }

        // Reset UI elements
        hideLoadingIndicator()
    }

    // Add these values to your dimens.xml file
    // <dimen name="card_selected_stroke_width">2dp</dimen>
    // <dimen name="card_default_stroke_width">1dp</dimen>
    // <dimen name="card_selected_elevation">6dp</dimen>
    // <dimen name="card_default_elevation">1dp</dimen>
}