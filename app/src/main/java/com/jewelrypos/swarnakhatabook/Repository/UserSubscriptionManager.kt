package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.SubscriptionFeatures
import com.jewelrypos.swarnakhatabook.Enums.SubscriptionPlan
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.google.firebase.firestore.Source // Import Source

/**
 * Manages user subscription status and trial period
 */
class UserSubscriptionManager(private val context: Context) {

    private val TAG = "UserSubscriptionManager"
    private val PREFS_NAME = "subscription_prefs"
    private val KEY_FIRST_USE_DATE = "first_use_date"
    private val KEY_TRIAL_DAYS = "trial_days"
    private val KEY_IS_TRIAL_ACTIVE = "is_trial_active"
    private val KEY_SUBSCRIPTION_PLAN = "subscription_plan" // Key for caching the plan name
    private val KEY_MONTHLY_INVOICE_COUNT = "monthly_invoice_count"
    private val KEY_INVOICE_COUNT_RESET_DATE = "invoice_count_reset_date"
    private val KEY_LAST_FETCH_TIME = "last_fetch_time" // Key to store last fetch timestamp
    private val CACHE_DURATION_MILLIS =
        TimeUnit.MINUTES.toMillis(5) // Cache duration (e.g., 5 minutes)


    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records the first use date if not already recorded and starts the trial
     */
    fun recordFirstUseIfNeeded() {
        if (!sharedPreferences.contains(KEY_FIRST_USE_DATE)) {
            val currentTime = System.currentTimeMillis()
            val editor = sharedPreferences.edit()
            editor.putLong(KEY_FIRST_USE_DATE, currentTime)
            editor.putBoolean(KEY_IS_TRIAL_ACTIVE, true)
            editor.putInt(KEY_TRIAL_DAYS, 15) // Default to 15 days for trial
            editor.apply()

            Log.d(TAG, "First use date recorded: ${formatDate(currentTime)}")
            Log.d(TAG, "Trial started with 15 days duration")
        }
    }

    /**
     * Gets the first use date or null if not recorded
     */
    fun getFirstUseDate(): Long? {
        val firstUseDate = sharedPreferences.getLong(KEY_FIRST_USE_DATE, -1)
        return if (firstUseDate != -1L) firstUseDate else null
    }

    /**
     * Sets the trial period length in days (for testing purposes)
     */
    fun setTrialPeriod(days: Int) {
        sharedPreferences.edit().putInt(KEY_TRIAL_DAYS, days).apply()
    }

    /**
     * Gets the configured trial period in days (default 15)
     */
    fun getTrialPeriod(): Int {
        return sharedPreferences.getInt(KEY_TRIAL_DAYS, 15) // Default 15 days
    }

    /**
     * Checks if the trial is currently active
     */
    fun isTrialActive(): Boolean {
        // Check if trial flag is set and not expired
        return sharedPreferences.getBoolean(KEY_IS_TRIAL_ACTIVE, false) && !hasTrialExpired()
    }

    /**
     * Retrieve the current subscription plan, prioritizing cache and falling back to Firestore
     */
    suspend fun getCurrentSubscriptionPlan(): SubscriptionPlan {
        // First check if we have a cached plan and if it's still valid
        val cachedPlanName = sharedPreferences.getString(KEY_SUBSCRIPTION_PLAN, null)
        val lastFetchTime = sharedPreferences.getLong(KEY_LAST_FETCH_TIME, 0)
        val currentTime = System.currentTimeMillis()

        if (cachedPlanName != null && (currentTime - lastFetchTime) < CACHE_DURATION_MILLIS) {
            // Cache is valid, return cached plan
            try {
                val cachedPlan = SubscriptionPlan.valueOf(cachedPlanName)
                Log.d(TAG, "Returning cached subscription plan: $cachedPlan")
                return cachedPlan
            } catch (e: IllegalArgumentException) {
                // Handle case where cached plan name is invalid (shouldn't happen with proper saving)
                Log.e(TAG, "Invalid cached subscription plan name: $cachedPlanName")
            }
        }

        // If cache is invalid or not present, fetch from Firestore
        val user = auth.currentUser ?: return SubscriptionPlan.NONE
        val userId = user.phoneNumber?.replace("+", "") ?: return SubscriptionPlan.NONE

        return try {
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                val planName = document.getString("subscriptionPlan") ?: ""
                val plan = try {
                    if (planName.isNotEmpty()) {
                        SubscriptionPlan.valueOf(planName)
                    } else {
                        SubscriptionPlan.NONE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing subscription plan: ${e.message}")
                    SubscriptionPlan.NONE
                }

                // Cache the fetched plan and fetch time
                sharedPreferences.edit()
                    .putString(KEY_SUBSCRIPTION_PLAN, plan.name)
                    .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                    .apply()

                Log.d(TAG, "Fetched and cached subscription plan: $plan")
                plan
            } else {
                // No document means no subscription
                sharedPreferences.edit()
                    .putString(KEY_SUBSCRIPTION_PLAN, SubscriptionPlan.NONE.name)
                    .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                    .apply()
                SubscriptionPlan.NONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking subscription plan from Firestore: ${e.message}")
            SubscriptionPlan.NONE
        }
    }

    /**
     * Updates the subscription plan in Firestore and clears cache
     */
    suspend fun updateSubscriptionPlan(plan: SubscriptionPlan): Boolean {
        val user = auth.currentUser ?: return false
        val userId = user.phoneNumber?.replace("+", "") ?: return false

        return try {
            firestore.collection("users")
                .document(userId)
                .set(
                    mapOf(
                        "subscriptionPlan" to plan.name,
                        "subscriptionUpdatedAt" to com.google.firebase.Timestamp.now()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()

            // If updating to any plan other than NONE, also deactivate the trial
            if (plan != SubscriptionPlan.NONE) {
                sharedPreferences.edit().putBoolean(KEY_IS_TRIAL_ACTIVE, false).apply()
            }

            // Clear cached subscription plan so next check fetches fresh data
            sharedPreferences.edit()
                .remove(KEY_SUBSCRIPTION_PLAN)
                .remove(KEY_LAST_FETCH_TIME)
                .apply()


            Log.d(TAG, "Subscription plan updated to: $plan")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscription plan: ${e.message}")
            false
        }
    }

    /**
     * Backward compatibility: Checks if the user is a premium subscriber
     * This will return true for any paid plan (BASIC, STANDARD, or PREMIUM)
     */
    suspend fun isPremiumUser(): Boolean {
        val plan = getCurrentSubscriptionPlan()
        return plan != SubscriptionPlan.NONE
    }

    suspend fun hasMinimumPlan(minimumPlan: SubscriptionPlan): Boolean {
        val currentPlan = getCurrentSubscriptionPlan()
        return currentPlan.ordinal >= minimumPlan.ordinal
    }

    /**
     * Updates the premium status in Firestore (legacy method)
     * This now maps to the PREMIUM subscription plan
     */
    suspend fun updatePremiumStatus(isPremium: Boolean): Boolean {
        return if (isPremium) {
            updateSubscriptionPlan(SubscriptionPlan.PREMIUM)
        } else {
            updateSubscriptionPlan(SubscriptionPlan.NONE)
        }
    }

    /**
     * Checks if trial period has expired
     */
    fun hasTrialExpired(): Boolean {
        val firstUseDate = getFirstUseDate() ?: return false
        val currentTime = System.currentTimeMillis()
        val trialPeriodDays = getTrialPeriod()

        val diffMillis = currentTime - firstUseDate
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

        Log.d(TAG, "Trial check: ${diffDays}/${trialPeriodDays} days used")
        return diffDays >= trialPeriodDays
    }

    /**
     * Get the current authenticated user\'s ID
     * Returns user\'s phone number (without + prefix) or empty string if not authenticated
     */
    fun getCurrentUserId(): String {
        val user = auth.currentUser ?: return ""
        return user.phoneNumber?.replace("+", "") ?: ""
    }

    /**
     * Calculates and returns days remaining in trial
     */
    fun getDaysRemaining(): Int {
        val firstUseDate = getFirstUseDate() ?: return getTrialPeriod()
        val currentTime = System.currentTimeMillis()
        val trialPeriodDays = getTrialPeriod()

        val diffMillis = currentTime - firstUseDate
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return (trialPeriodDays - diffDays).coerceAtLeast(0).toInt()
    }

    /**
     * Ends the trial period (used when purchasing a subscription)
     */
    fun endTrial() {
        sharedPreferences.edit().putBoolean(KEY_IS_TRIAL_ACTIVE, false).apply()
        Log.d(TAG, "Trial period ended")
    }

    /**
     * Resets the trial period (for testing only)\n     */
    fun resetTrial() {
        val editor = sharedPreferences.edit()
        editor.remove(KEY_FIRST_USE_DATE)
        editor.putBoolean(KEY_IS_TRIAL_ACTIVE, true)
        editor.apply()
        Log.d(TAG, "Trial period reset")
    }

    /**
     * For debugging - format a date in readable format
     */
    private fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    /**
     * Get the active subscription features based on the current plan and trial status
     */
    suspend fun getActiveSubscriptionFeatures(): SubscriptionFeatures {
        // If trial is active, return STANDARD plan features
        if (isTrialActive()) {
            return SubscriptionFeatures.forPlan(SubscriptionPlan.STANDARD)
        }

        // Otherwise, return features based on the current plan
        val currentPlan = getCurrentSubscriptionPlan()
        return SubscriptionFeatures.forPlan(currentPlan)
    }

    /**
     * Check if the specified feature is available for the current subscription
     */
    suspend fun isFeatureAvailable(featureCheck: (SubscriptionFeatures) -> Boolean): Boolean {
        val features = getActiveSubscriptionFeatures()
        return featureCheck(features)
    }

    /**
     * Track the number of invoices created in the current month (for Basic plan limits)
     */
    fun incrementMonthlyInvoiceCount() {
        // Check if we need to reset the counter for a new month
        val lastResetDate = sharedPreferences.getLong(KEY_INVOICE_COUNT_RESET_DATE, 0)
        val currentTime = System.currentTimeMillis()

        // If the last reset was in a different month, reset the counter
        if (!isSameMonth(lastResetDate, currentTime)) {
            sharedPreferences.edit()
                .putInt(KEY_MONTHLY_INVOICE_COUNT, 1)
                .putLong(KEY_INVOICE_COUNT_RESET_DATE, currentTime)
                .apply()
            return
        }

        // Otherwise, increment the counter
        val currentCount = sharedPreferences.getInt(KEY_MONTHLY_INVOICE_COUNT, 0)
        sharedPreferences.edit().putInt(KEY_MONTHLY_INVOICE_COUNT, currentCount + 1).apply()
    }

    /**
     * Get the current month\'s invoice count
     */
    fun getMonthlyInvoiceCount(): Int {
        // Check if we need to reset for a new month
        val lastResetDate = sharedPreferences.getLong(KEY_INVOICE_COUNT_RESET_DATE, 0)
        val currentTime = System.currentTimeMillis()

        // If different month, reset counter and return 0
        if (!isSameMonth(lastResetDate, currentTime)) {
            sharedPreferences.edit()
                .putInt(KEY_MONTHLY_INVOICE_COUNT, 0)
                .putLong(KEY_INVOICE_COUNT_RESET_DATE, currentTime)
                .apply()
            return 0
        }

        return sharedPreferences.getInt(KEY_MONTHLY_INVOICE_COUNT, 0)
    }

    /**
     * Check if two timestamps are in the same month
     */
    private fun isSameMonth(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.timeInMillis = time1
        cal2.timeInMillis = time2

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    /**
     * Get the end date of the current trial as a timestamp
     */
    fun getTrialEndDate(): Long? {
        val firstUseDate = getFirstUseDate() ?: return null
        val trialPeriodDays = getTrialPeriod()

        // Calculate end date by adding trial days to first use date
        return firstUseDate + TimeUnit.DAYS.toMillis(trialPeriodDays.toLong())
    }

    /**
     * Refreshes subscription status from Firestore - this should be called
     * after a purchase to ensure the UI updates immediately
     */
    suspend fun refreshSubscriptionStatus(): SubscriptionPlan {
        // Force a refresh from Firestore
        try {
            // Clear any local cached subscription info
            sharedPreferences.edit()
                .remove(KEY_SUBSCRIPTION_PLAN)
                .apply()

            // Get updated plan from Firestore
            val user = auth.currentUser ?: return SubscriptionPlan.NONE
            val userId = user.phoneNumber?.replace("+", "") ?: return SubscriptionPlan.NONE

            val document = firestore.collection("users")
                .document(userId)
                .get(com.google.firebase.firestore.Source.SERVER) // Force server fetch
                .await()

            if (document.exists()) {
                val planName = document.getString("subscriptionPlan") ?: ""
                val plan = try {
                    if (planName.isNotEmpty()) {
                        SubscriptionPlan.valueOf(planName)
                    } else {
                        SubscriptionPlan.NONE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing subscription plan: ${e.message}")
                    SubscriptionPlan.NONE
                }

                // Cache the updated plan locally
                sharedPreferences.edit()
                    .putString(KEY_SUBSCRIPTION_PLAN, plan.name)
                    .putLong(
                        KEY_LAST_FETCH_TIME,
                        System.currentTimeMillis()
                    ) // Update last fetch time
                    .apply()

                Log.d(TAG, "Subscription refreshed: $plan")
                return plan
            }
            return SubscriptionPlan.NONE
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing subscription: ${e.message}")
            return SubscriptionPlan.NONE
        }
    }
}
