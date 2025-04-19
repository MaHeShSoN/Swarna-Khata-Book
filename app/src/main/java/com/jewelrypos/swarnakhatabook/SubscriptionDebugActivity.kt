package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.Enums.SubscriptionPlan
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
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
    private lateinit var subscriptionManager: UserSubscriptionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get subscription manager once
        subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

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
            // Get current status
            val currentPlan = subscriptionManager.getCurrentSubscriptionPlan()
            val isTrialActive = subscriptionManager.isTrialActive()
            val trialExpired = subscriptionManager.hasTrialExpired()
            val daysRemaining = subscriptionManager.getDaysRemaining()
            val trialPeriod = subscriptionManager.getTrialPeriod()
            val monthlyInvoiceCount = subscriptionManager.getMonthlyInvoiceCount()

            // Format first use date
            val firstUseDate = subscriptionManager.getFirstUseDate()
            val formattedDate = if (firstUseDate != null) {
                SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(firstUseDate))
            } else {
                "Not set"
            }

            // Update UI
            runOnUiThread {
                binding.statusCurrentPlan.text = "Current Plan: ${currentPlan.name}"
                binding.statusTrialActive.text = "Trial Active: $isTrialActive"
                binding.statusTrialExpired.text = "Trial Expired: $trialExpired" 
                binding.statusDaysRemaining.text = "Days Remaining: $daysRemaining / $trialPeriod"
                binding.statusFirstUseDate.text = "First Use Date: $formattedDate"
                binding.statusMonthlyInvoices.text = "Monthly Invoices: $monthlyInvoiceCount"
            }
        }
    }

    private fun setupButtons() {
        // Trial management buttons
        binding.btnResetTrial.setOnClickListener {
            lifecycleScope.launch {
                subscriptionManager.resetTrial()
                refreshStatus()
                Toast.makeText(this@SubscriptionDebugActivity, "Trial reset", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEndTrial.setOnClickListener {
            lifecycleScope.launch {
                subscriptionManager.endTrial()
                refreshStatus()
                Toast.makeText(this@SubscriptionDebugActivity, "Trial ended", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetTrialDays.setOnClickListener {
            val days = binding.editTrialDays.text.toString().toIntOrNull() ?: 15
            subscriptionManager.setTrialPeriod(days)
            refreshStatus()
            Toast.makeText(this@SubscriptionDebugActivity, "Trial period set to $days days", Toast.LENGTH_SHORT).show()
        }

        // Plan management buttons
        binding.btnSetBasic.setOnClickListener {
            lifecycleScope.launch {
                subscriptionManager.updateSubscriptionPlan(SubscriptionPlan.BASIC)
                refreshStatus()
                Toast.makeText(this@SubscriptionDebugActivity, "Set to BASIC plan", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetStandard.setOnClickListener {
            lifecycleScope.launch {
                subscriptionManager.updateSubscriptionPlan(SubscriptionPlan.STANDARD)
                refreshStatus()
                Toast.makeText(this@SubscriptionDebugActivity, "Set to STANDARD plan", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetPremium.setOnClickListener {
            lifecycleScope.launch {
                subscriptionManager.updateSubscriptionPlan(SubscriptionPlan.PREMIUM)
                refreshStatus()
                Toast.makeText(this@SubscriptionDebugActivity, "Set to PREMIUM plan", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetNone.setOnClickListener {
            lifecycleScope.launch {
                subscriptionManager.updateSubscriptionPlan(SubscriptionPlan.NONE)
                refreshStatus()
                Toast.makeText(this@SubscriptionDebugActivity, "Set to NO plan", Toast.LENGTH_SHORT).show()
            }
        }

        // Monthly invoice counter
        binding.btnIncrementInvoices.setOnClickListener {
            subscriptionManager.incrementMonthlyInvoiceCount()
            refreshStatus()
            Toast.makeText(this@SubscriptionDebugActivity, "Incremented invoice count", Toast.LENGTH_SHORT).show()
        }
    }
}