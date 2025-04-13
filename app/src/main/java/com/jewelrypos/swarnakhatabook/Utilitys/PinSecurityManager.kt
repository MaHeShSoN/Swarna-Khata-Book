package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.SharedPreferences
import java.util.Date

/**
 * Manages PIN security, including failed attempts tracking and lockouts
 */
object PinSecurityManager {
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000 // 5 minutes

    private const val KEY_ATTEMPTS = "pin_attempts"
    private const val KEY_LOCKOUT_TIME = "pin_lockout_time"

    /**
     * Records a failed PIN attempt and returns the updated security status
     */
    fun recordFailedAttempt(context: Context): PinSecurityStatus {
        val prefs = SecurePreferences.getInstance(context)
        val attempts = prefs.getInt(KEY_ATTEMPTS, 0) + 1

        // Save the updated attempt count
        prefs.edit().putInt(KEY_ATTEMPTS, attempts).apply()

        // If we've reached max attempts, set a lockout time
        if (attempts >= MAX_ATTEMPTS) {
            val lockoutTime = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            prefs.edit().putLong(KEY_LOCKOUT_TIME, lockoutTime).apply()
            return PinSecurityStatus.Locked(getRemainingLockoutTime(context))
        }

        return PinSecurityStatus.Limited(MAX_ATTEMPTS - attempts)
    }

    /**
     * Resets the failed attempts counter and lockout timer
     */
    fun resetAttempts(context: Context) {
        val prefs = SecurePreferences.getInstance(context)
        prefs.edit().remove(KEY_ATTEMPTS).remove(KEY_LOCKOUT_TIME).apply()
    }

    /**
     * Checks the current PIN security status
     */
    fun checkStatus(context: Context): PinSecurityStatus {
        val prefs = SecurePreferences.getInstance(context)
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIME, 0)

        // Check if we're in a lockout period
        if (lockoutTime > 0) {
            if (System.currentTimeMillis() < lockoutTime) {
                return PinSecurityStatus.Locked(getRemainingLockoutTime(context))
            } else {
                // Lockout period is over, reset attempts
                resetAttempts(context)
            }
        }

        val attempts = prefs.getInt(KEY_ATTEMPTS, 0)
        return if (attempts > 0) {
            PinSecurityStatus.Limited(MAX_ATTEMPTS - attempts)
        } else {
            PinSecurityStatus.Normal
        }
    }

    /**
     * Gets the remaining lockout time in milliseconds
     */
    private fun getRemainingLockoutTime(context: Context): Long {
        val prefs = SecurePreferences.getInstance(context)
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIME, 0)
        return Math.max(0, lockoutTime - System.currentTimeMillis())
    }
}

/**
 * Represents the current PIN security status
 */
sealed class PinSecurityStatus {
    object Normal : PinSecurityStatus()
    data class Limited(val remainingAttempts: Int) : PinSecurityStatus()
    data class Locked(val remainingLockoutTimeMs: Long) : PinSecurityStatus()
}