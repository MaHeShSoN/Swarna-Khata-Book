package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.util.Date

object PinSecurityManager {
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000 // 5 minutes

    private const val KEY_ATTEMPTS = "pin_attempts"
    private const val KEY_LOCKOUT_TIME = "pin_lockout_time"

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

    fun resetAttempts(context: Context) {
        val prefs = SecurePreferences.getInstance(context)
        prefs.edit().remove(KEY_ATTEMPTS).remove(KEY_LOCKOUT_TIME).apply()
    }

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

    private fun getRemainingLockoutTime(context: Context): Long {
        val prefs = SecurePreferences.getInstance(context)
        val lockoutTime = prefs.getLong(KEY_LOCKOUT_TIME, 0)
        return Math.max(0, lockoutTime - System.currentTimeMillis())
    }
}

sealed class PinSecurityStatus {
    object Normal : PinSecurityStatus()
    data class Limited(val remainingAttempts: Int) : PinSecurityStatus()
    data class Locked(val remainingLockoutTimeMs: Long) : PinSecurityStatus()
}