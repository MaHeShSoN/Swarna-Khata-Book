package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages PIN security, including failed attempts tracking and lockouts
 */
object PinSecurityManager {
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000 // 5 minutes
    const val KEY_PIN_LOCK_ENABLED = "pin_lock_enabled"
    private const val KEY_ATTEMPTS = "pin_attempts"
    private const val KEY_LOCKOUT_TIME = "pin_lockout_time"


    fun hasPinBeenSetUp(context: Context): Boolean {
        val prefs = SecurePreferences.getInstance(context)
        // Check if both hash and salt keys exist
        return prefs.contains(PinHashUtil.KEY_PIN_HASH) && prefs.contains(PinHashUtil.KEY_PIN_SALT)
    }
    fun isPinLockEnabled(context: Context): Boolean {
        // Check the enabled flag, default to false if not set but a PIN exists
        val prefs = SecurePreferences.getInstance(context)
        // Default to true if a PIN has been set but the flag doesn't exist yet (for migration)
        val defaultValue = hasPinBeenSetUp(context)
        return prefs.getBoolean(KEY_PIN_LOCK_ENABLED, defaultValue)
    }
    fun setPinLockEnabled(context: Context, enabled: Boolean) {
        val prefs = SecurePreferences.getInstance(context)
        prefs.edit().putBoolean(KEY_PIN_LOCK_ENABLED, enabled).apply()
    }
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

    /**
     * Checks if a PIN is set
     */
    fun isPinSet(context: Context): Boolean {
        val prefs = SecurePreferences.getInstance(context)
        return prefs.contains("pin_hash") && prefs.contains("pin_salt")
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