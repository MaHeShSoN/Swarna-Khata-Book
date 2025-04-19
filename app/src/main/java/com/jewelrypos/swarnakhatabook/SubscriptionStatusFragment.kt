package com.jewelrypos.swarnakhatabook

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.ktx.BuildConfig
import com.jewelrypos.swarnakhatabook.Enums.SubscriptionPlan
import com.jewelrypos.swarnakhatabook.Repository.UserSubscriptionManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentSubscriptionStatusBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fragment to display subscription status and manage subscriptions
 */
class SubscriptionStatusFragment : Fragment() {

    private var _binding: FragmentSubscriptionStatusBinding? = null
    private val binding get() = _binding!!
    private lateinit var subscriptionManager: UserSubscriptionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get subscription manager
        subscriptionManager = SwarnaKhataBook.getUserSubscriptionManager()

        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        // Setup UI
        loadSubscriptionStatus()
        setupButtons()
    }

    private fun loadSubscriptionStatus() {
        lifecycleScope.launch {
            // Get subscription status
            val currentPlan = subscriptionManager.getCurrentSubscriptionPlan()
            val isTrialActive = subscriptionManager.isTrialActive()
            val daysRemaining = subscriptionManager.getDaysRemaining()

            // Update UI based on subscription status
            if (currentPlan != SubscriptionPlan.NONE) {
                // Paid user UI
                val planName = when (currentPlan) {
                    SubscriptionPlan.BASIC -> "Basic"
                    SubscriptionPlan.STANDARD -> "Standard"
                    SubscriptionPlan.PREMIUM -> "Premium"
                    else -> "Unknown"
                }
                
                binding.subscriptionTitle.text = "$planName Subscription"
                binding.subscriptionStatusIcon.setImageResource(R.drawable.fluent__premium_24_regular)
                binding.daysRemainingText.visibility = View.GONE
                binding.trialProgressContainer.visibility = View.GONE
                
                binding.subscriptionDescription.text = when (currentPlan) {
                    SubscriptionPlan.BASIC -> "You have access to basic features. Upgrade to Standard or Premium for more features."
                    SubscriptionPlan.STANDARD -> "You have access to all standard features. Upgrade to Premium for additional features."
                    SubscriptionPlan.PREMIUM -> "You have access to all premium features and benefits."
                    else -> "You have an active subscription."
                }
                
                binding.upgradeButton.text = if (currentPlan == SubscriptionPlan.PREMIUM) "Manage Subscription" else "Upgrade Plan"
                
                // Update benefits list based on the plan
                binding.benefitsList.text = getBenefitsText(currentPlan)
            } else if (isTrialActive) {
                // Free trial UI
                binding.subscriptionTitle.text = "Free Trial"
                binding.subscriptionStatusIcon.setImageResource(R.drawable.material_symbols__timer_outline)
                binding.daysRemainingText.visibility = View.VISIBLE
                binding.daysRemainingText.text = "$daysRemaining days remaining"

                // Setup progress bar
                binding.trialProgressContainer.visibility = View.VISIBLE
                val totalDays = subscriptionManager.getTrialPeriod()
                val usedDays = totalDays - daysRemaining
                val progressPercent = (usedDays.toFloat() / totalDays.toFloat() * 100).toInt()
                binding.trialProgressBar.progress = progressPercent

                // First use date
                val firstUseDate = subscriptionManager.getFirstUseDate()
                firstUseDate?.let {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    val startDate = dateFormat.format(Date(it))

                    // Calculate end date
                    val endCalendar = Calendar.getInstance()
                    endCalendar.timeInMillis = it
                    endCalendar.add(Calendar.DAY_OF_YEAR, totalDays)
                    val endDate = dateFormat.format(endCalendar.time)

                    binding.trialPeriodDates.text = "Started: $startDate · Ends: $endDate"
                }

                binding.subscriptionDescription.text =
                    "Enjoy all features during your trial period. Upgrade before your trial ends to continue using premium features."
                binding.upgradeButton.text = "Upgrade Now"

                // Different text for almost expired trial
                if (daysRemaining <= 3) {
                    binding.subscriptionDescription.text = "Your trial is ending soon! Upgrade now to keep access to all features."
                    binding.daysRemainingText.setTextColor(resources.getColor(R.color.my_dark_error, null))
                }

                binding.benefitsList.text = """
                    With premium you get:
                    • Unlimited customers and invoices
                    • All premium templates
                    • Advanced reporting
                    • Email and export features
                    • Priority support
                    • Custom branding
                """.trimIndent()
            } else {
                // Expired trial / No subscription UI
                binding.subscriptionTitle.text = "Trial Expired"
                binding.subscriptionStatusIcon.setImageResource(R.drawable.fluent__error_circle_12_regular)
                binding.daysRemainingText.visibility = View.GONE
                binding.trialProgressContainer.visibility = View.GONE
                
                binding.subscriptionDescription.text = 
                    "Your free trial has ended. Choose a subscription plan to continue using premium features."
                binding.upgradeButton.text = "Choose a Plan"
                
                binding.benefitsList.text = """
                    Choose a plan to unlock:
                    • Manage multiple shop profiles
                    • Unlimited customers and items
                    • Unlimited invoices
                    • Advanced reporting
                    • Customization options
                    • And many more features!
                """.trimIndent()
            }
        }
    }
    
    private fun getBenefitsText(plan: SubscriptionPlan): String {
        return when (plan) {
            SubscriptionPlan.BASIC -> """
                • Manage 1 shop profile
                • 100 customer entries
                • 100 inventory items
                • 100 invoices per month
                • Basic sales reports
                • Standard invoice template
                • Community support
            """.trimIndent()
            
            SubscriptionPlan.STANDARD -> """
                • Manage up to 2 shop profiles
                • Unlimited customers and items
                • Unlimited invoices
                • All standard reports
                • Multiple invoice templates
                • Basic customization options
                • PIN security
                • Low stock notifications
                • Data export (CSV, PDF)
                • Recycling bin feature
                • Standard email/chat support
            """.trimIndent()
            
            SubscriptionPlan.PREMIUM -> """
                • Manage up to 3 shop profiles
                • All Standard plan features
                • Multi-user access
                • Full invoice customization
                • Advanced notifications
                • Business insight reports
                • Data backup & restore
                • Priority support
            """.trimIndent()
            
            else -> ""
        }
    }

    private fun setupButtons() {
        // Upgrade button setup
        binding.upgradeButton.setOnClickListener {
            startActivity(Intent(requireContext(), UpgradeActivity::class.java))
        }

        // Debug button (only in debug builds)


        if (BuildConfig.DEBUG) {
            binding.resetTrialButton?.visibility = View.VISIBLE
            binding.resetTrialButton?.setOnClickListener {
                lifecycleScope.launch {
                    subscriptionManager.resetTrial()
                    subscriptionManager.updateSubscriptionPlan(SubscriptionPlan.NONE)
                    loadSubscriptionStatus()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh subscription status when returning to the fragment
        loadSubscriptionStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}