package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.SharedPreferences

class SharedPrefsManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "JewelryPrefs"
        private const val KEY_LAST_GOLD_RATE = "last_gold_rate"
        private const val KEY_LAST_MAKING_CHARGES = "last_making_charges"
        private const val KEY_LAST_GOLD_RATE_ON = "last_gold_rate_on"
        private const val KEY_LAST_MAKING_CHARGES_TYPE = "last_making_charges_type"

        // Default values
        private const val DEFAULT_GOLD_RATE = 9200.0
        private const val DEFAULT_MAKING_CHARGES = 500.0
        private const val DEFAULT_GOLD_RATE_ON = "Net Weight"
        private const val DEFAULT_MAKING_CHARGES_TYPE = "PER GRAM"
    }

    fun saveLastGoldRate(rate: Double) {
        sharedPreferences.edit().putFloat(KEY_LAST_GOLD_RATE, rate.toFloat()).apply()
    }

    fun getLastGoldRate(): Double {
        val savedRate = sharedPreferences.getFloat(KEY_LAST_GOLD_RATE, -1f)
        return if (savedRate == -1f) {
            // If no value is saved, save and return default value
            saveLastGoldRate(DEFAULT_GOLD_RATE)
            DEFAULT_GOLD_RATE
        } else {
            savedRate.toDouble()
        }
    }

    fun saveLastMakingCharges(charges: Double) {
        sharedPreferences.edit().putFloat(KEY_LAST_MAKING_CHARGES, charges.toFloat()).apply()
    }

    fun getLastMakingCharges(): Double {
        val savedCharges = sharedPreferences.getFloat(KEY_LAST_MAKING_CHARGES, -1f)
        return if (savedCharges == -1f) {
            // If no value is saved, save and return default value
            saveLastMakingCharges(DEFAULT_MAKING_CHARGES)
            DEFAULT_MAKING_CHARGES
        } else {
            savedCharges.toDouble()
        }
    }

    fun saveLastGoldRateOn(rateOn: String) {
        sharedPreferences.edit().putString(KEY_LAST_GOLD_RATE_ON, rateOn).apply()
    }

    fun getLastGoldRateOn(): String {
        return sharedPreferences.getString(KEY_LAST_GOLD_RATE_ON, DEFAULT_GOLD_RATE_ON) ?: DEFAULT_GOLD_RATE_ON
    }

    fun saveLastMakingChargesType(type: String) {
        sharedPreferences.edit().putString(KEY_LAST_MAKING_CHARGES_TYPE, type).apply()
    }

    fun getLastMakingChargesType(): String {
        return sharedPreferences.getString(KEY_LAST_MAKING_CHARGES_TYPE, DEFAULT_MAKING_CHARGES_TYPE) ?: DEFAULT_MAKING_CHARGES_TYPE
    }

    // Method to reset all values to defaults
    fun resetToDefaults() {
        saveLastGoldRate(DEFAULT_GOLD_RATE)
        saveLastMakingCharges(DEFAULT_MAKING_CHARGES)
        saveLastGoldRateOn(DEFAULT_GOLD_RATE_ON)
        saveLastMakingChargesType(DEFAULT_MAKING_CHARGES_TYPE)
    }
} 