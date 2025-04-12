package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.databinding.ActivitySubscriptionDebugBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-only activity for testing subscription features
 * This should be removed or disabled in production builds
 */
class SubscriptionDebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionDebugBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }

        // Load current status
        refreshStatus()

        // Setup buttons
        setupButtons()
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

            // Get current status
            val isPremium = subscriptionManager.isPremiumUser()
            val firstUseDate = subscriptionManager.getFirstUseDate()
            val daysRemaining = subscriptionManager.getDaysRemaining()
            val trialPeriod = subscriptionManager.getTrialPeriod()

            // Update UI
            binding.statusPremium.text = "Premium: $isPremium"
            binding.statusDaysRemaining.text = "Days Remaining: $daysRemaining / $trialPeriod"

            if (firstUseDate != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                binding.statusFirstUse.text = "First Use: ${dateFormat.format(Date(firstUseDate))}"
            } else {
                binding.statusFirstUse.text = "First Use: Not set"
            }

            // Update trial length field
            binding.trialLengthInput.setText(trialPeriod.toString())
        }
    }

    private fun setupButtons() {
        // Set Premium Status
        binding.btnSetPremium.setOnClickListener {
            lifecycleScope.launch {
                val success = SwarnaKhataBook.getUserSubscriptionManager()
                    .updatePremiumStatus(true)

                if (success) {
                    Toast.makeText(this@SubscriptionDebugActivity,
                        "Premium status set to TRUE", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SubscriptionDebugActivity,
                        "Failed to update premium status", Toast.LENGTH_SHORT).show()
                }

                refreshStatus()
            }
        }

        // Set Free Status
        binding.btnSetFree.setOnClickListener {
            lifecycleScope.launch {
                val success = SwarnaKhataBook.getUserSubscriptionManager()
                    .updatePremiumStatus(false)

                if (success) {
                    Toast.makeText(this@SubscriptionDebugActivity,
                        "Premium status set to FALSE", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SubscriptionDebugActivity,
                        "Failed to update premium status", Toast.LENGTH_SHORT).show()
                }

                refreshStatus()
            }
        }

        // Reset Trial
        binding.btnResetTrial.setOnClickListener {
            SwarnaKhataBook.getUserSubscriptionManager().resetTrial()
            Toast.makeText(this@SubscriptionDebugActivity,
                "Trial period reset", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        // Set Trial to 1 Day
        binding.btnExpireSoon.setOnClickListener {
            SwarnaKhataBook.getUserSubscriptionManager().setTrialPeriod(1)
            Toast.makeText(this@SubscriptionDebugActivity,
                "Trial period set to 1 day", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }

        // Update Trial Length
        binding.btnUpdateTrialLength.setOnClickListener {
            val trialLength = binding.trialLengthInput.text.toString().toIntOrNull()
            if (trialLength != null && trialLength > 0) {
                SwarnaKhataBook.getUserSubscriptionManager().setTrialPeriod(trialLength)
                Toast.makeText(this@SubscriptionDebugActivity,
                    "Trial period set to $trialLength days", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SubscriptionDebugActivity,
                    "Please enter a valid number of days", Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }
    }
}