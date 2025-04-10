package com.jewelrypos.swarnakhatabook

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.BuildConfig
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
            val subscriptionManager = SwarnaKhataBook.userSubscriptionManager

            // Get subscription status
            val isPremium = subscriptionManager.isPremiumUser()
            val firstUseDate = subscriptionManager.getFirstUseDate()
            val daysRemaining = subscriptionManager.getDaysRemaining()

            // Update UI based on subscription status
            if (isPremium) {
                // Premium user UI
                binding.subscriptionTitle.text = "Premium Subscription"
                binding.subscriptionStatusIcon.setImageResource(R.drawable.fluent__premium_24_regular)
                binding.daysRemainingText.visibility = View.GONE
                binding.trialProgressContainer.visibility = View.GONE
                binding.subscriptionDescription.text =
                    "You have access to all premium features and benefits."
                binding.upgradeButton.text = "Manage Subscription"
                binding.benefitsList.text = """
                    • Unlimited customers and invoices
                    • All premium templates
                    • Advanced reporting
                    • Email and export features
                    • Priority support
                    • Custom branding
                """.trimIndent()
            } else {
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
                    binding.daysRemainingText.setTextColor(requireContext().getColor(R.color.status_unpaid))
                    binding.subscriptionDescription.text =
                        "Your trial is ending soon! Upgrade now to continue using all features without interruption."
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
            }
        }
    }

    private fun setupButtons() {
        lifecycleScope.launch {
            val isPremium = SwarnaKhataBook.userSubscriptionManager.isPremiumUser()

            // Move this outside the coroutine because it doesn't use suspend functions
            requireActivity().runOnUiThread {
                binding.upgradeButton.setOnClickListener {
                    startActivity(Intent(requireContext(), UpgradeActivity::class.java))
                }

                // For testing purposes only - add a reset button when in development
                if (BuildConfig.DEBUG) {
                    binding.resetTrialButton.visibility = View.VISIBLE
                    binding.resetTrialButton.setOnClickListener {
                        // Launch a new coroutine for the suspend functions
                        lifecycleScope.launch {
                            SwarnaKhataBook.userSubscriptionManager.resetTrial()
                            SwarnaKhataBook.userSubscriptionManager.updatePremiumStatus(false)
                            loadSubscriptionStatus()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}