package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.jewelrypos.swarnakhatabook.DataClasses.PdfSettings


class PdfSettingsManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    /**
     * Save PDF settings to SharedPreferences
     */
    fun saveSettings(settings: PdfSettings) {
        try {
            val json = gson.toJson(settings)
            sharedPreferences.edit().putString(SETTINGS_KEY, json).apply()
            Log.d(TAG, "PDF settings saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PDF settings: ${e.message}")
            throw e
        }
    }

    /**
     * Load PDF settings from SharedPreferences
     * Returns default settings if none exist
     */
    fun loadSettings(): PdfSettings {
        return try {
            val json = sharedPreferences.getString(SETTINGS_KEY, null)
            if (json != null) {
                gson.fromJson(json, PdfSettings::class.java)
            } else {
                PdfSettings() // Return default settings
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading PDF settings: ${e.message}")
            PdfSettings() // Return default settings on error
        }
    }

    companion object {
        private const val TAG = "PdfSettingsManager"
        private const val PREFS_NAME = "jewelry_pos_pdf_settings"
        private const val SETTINGS_KEY = "pdf_settings"
    }
}