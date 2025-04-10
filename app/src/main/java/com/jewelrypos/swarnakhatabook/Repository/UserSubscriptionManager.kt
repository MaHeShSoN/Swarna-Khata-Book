package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages user subscription status and trial period
 */
class UserSubscriptionManager(private val context: Context) {

    private val TAG = "UserSubscriptionManager"
    private val PREFS_NAME = "subscription_prefs"
    private val KEY_FIRST_USE_DATE = "first_use_date"
    private val KEY_TRIAL_DAYS = "trial_days"

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Records the first use date if not already recorded
     */
    fun recordFirstUseIfNeeded() {
        if (!sharedPreferences.contains(KEY_FIRST_USE_DATE)) {
            val currentTime = System.currentTimeMillis()
            sharedPreferences.edit().putLong(KEY_FIRST_USE_DATE, currentTime).apply()
            Log.d(TAG, "First use date recorded: ${formatDate(currentTime)}")
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
     * Gets the configured trial period in days (default 10)
     */
    fun getTrialPeriod(): Int {
        return sharedPreferences.getInt(KEY_TRIAL_DAYS, 10) // Default 10 days
    }

    /**
     * Checks if the user is a premium subscriber
     */
    suspend fun isPremiumUser(): Boolean {
        val user = auth.currentUser ?: return false
        val userId = user.phoneNumber?.replace("+", "") ?: return false

        try {
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()

            if (document.exists()) {
                return document.getBoolean("isPremium") ?: false
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking premium status: ${e.message}")
            return false
        }
    }

    /**
     * Updates the premium status in Firestore
     */
    suspend fun updatePremiumStatus(isPremium: Boolean): Boolean {
        val user = auth.currentUser ?: return false
        val userId = user.phoneNumber?.replace("+", "") ?: return false

        return try {
            firestore.collection("users")
                .document(userId)
                .update("isPremium", isPremium)
                .await()

            Log.d(TAG, "Premium status updated to: $isPremium")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating premium status: ${e.message}")
            false
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
     * Resets the trial period (for testing only)
     */
    fun resetTrial() {
        sharedPreferences.edit().remove(KEY_FIRST_USE_DATE).apply()
        Log.d(TAG, "Trial period reset")
    }

    /**
     * For debugging - format a date in readable format
     */
    private fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}