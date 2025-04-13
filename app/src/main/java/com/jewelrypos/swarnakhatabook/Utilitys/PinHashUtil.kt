package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Utility for securely handling PIN hashing and verification
 */
object PinHashUtil {
    private const val HASH_ALGORITHM = "SHA-256"
    private const val SALT_LENGTH = 16 // bytes

    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"

    /**
     * Generates a cryptographically secure random salt
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        val random = SecureRandom()
        random.nextBytes(salt)
        return salt
    }

    /**
     * Hashes a PIN with the provided salt using SHA-256
     */
    fun hashPin(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance(HASH_ALGORITHM)
        md.update(salt)
        val hashedBytes = md.digest(pin.toByteArray())
        return Base64.encodeToString(hashedBytes, Base64.NO_WRAP)
    }

    /**
     * Stores a PIN securely (hashed with salt) in SharedPreferences
     */
    fun storePin(pin: String, prefs: SharedPreferences) {
        val salt = generateSalt()
        val hashedPin = hashPin(pin, salt)
        val saltString = Base64.encodeToString(salt, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_PIN_HASH, hashedPin)
            .putString(KEY_PIN_SALT, saltString)
            .apply()
    }

    /**
     * Verifies if the entered PIN matches the stored hashed PIN
     */
    fun verifyPin(enteredPin: String, prefs: SharedPreferences): Boolean {
        // For extra security, add a minimum delay to prevent timing attacks
        // This makes brute force attacks more time-consuming
        Thread.sleep(300)

        // Basic validation
        if (enteredPin.isBlank()) return false

        // Check for new secure format
        val storedHash = prefs.getString(KEY_PIN_HASH, "")
        val saltString = prefs.getString(KEY_PIN_SALT, "")

        if (!storedHash.isNullOrEmpty() && !saltString.isNullOrEmpty()) {
            val salt = try {
                Base64.decode(saltString, Base64.NO_WRAP)
            } catch (e: Exception) {
                Log.e("PinHashUtil", "Error decoding salt: ${e.message}")
                return false
            }

            val computedHash = hashPin(enteredPin, salt)
            return constantTimeEquals(computedHash, storedHash)
        }

        // Fall back to legacy format for backward compatibility during migration
        val legacyPin = prefs.getString("app_lock_pin", null)
        return legacyPin != null && legacyPin == enteredPin
    }
    /**
     * Constant-time comparison to prevent timing attacks
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}