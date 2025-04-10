package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.databinding.ActivityUpgradeBinding
import kotlinx.coroutines.launch

class UpgradeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpgradeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpgradeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }

        // Setup subscription buttons
        setupSubscriptionButtons()
    }

    private fun setupSubscriptionButtons() {
        // Basic monthly plan
        binding.basicMonthlyCard.setOnClickListener {
            processPurchase("basic_monthly")
        }

        // Standard monthly plan
        binding.standardMonthlyCard.setOnClickListener {
            processPurchase("standard_monthly")
        }

        // Premium monthly plan
        binding.premiumMonthlyCard.setOnClickListener {
            processPurchase("premium_monthly")
        }

        // Basic yearly plan
        binding.basicYearlyCard.setOnClickListener {
            processPurchase("basic_yearly")
        }

        // Standard yearly plan
        binding.standardYearlyCard.setOnClickListener {
            processPurchase("standard_yearly")
        }

        // Premium yearly plan
        binding.premiumYearlyCard.setOnClickListener {
            processPurchase("premium_yearly")
        }
    }

    private fun processPurchase(productId: String) {
        // Show progress
        binding.progressBar.show()

        // In a real app, this would initiate the payment flow with Google Play Billing or another payment provider
        // For this demo, we'll just simulate a successful purchase after a short delay

        lifecycleScope.launch {
            try {
                // Simulate network delay
                kotlinx.coroutines.delay(1500)

                // Update premium status
                val subscriptionManager = SwarnaKhataBook.userSubscriptionManager
                val success = subscriptionManager.updatePremiumStatus(true)

                if (success) {
                    // Purchase succeeded
                    Toast.makeText(this@UpgradeActivity,
                        "Successfully upgraded to Premium!",
                        Toast.LENGTH_SHORT).show()

                    // Navigate back to main activity
                    finish()
                } else {
                    // Purchase failed
                    Toast.makeText(this@UpgradeActivity,
                        "Failed to process payment. Please try again.",
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UpgradeActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            } finally {
                // Hide progress
                binding.progressBar.hide()
            }
        }
    }
}