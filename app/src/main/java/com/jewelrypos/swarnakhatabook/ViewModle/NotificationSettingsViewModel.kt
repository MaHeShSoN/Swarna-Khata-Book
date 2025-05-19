// app/src/main/java/com/jewelrypos/swarnakhatabook/ViewModle/NotificationSettingsViewModel.kt
package com.jewelrypos.swarnakhatabook.ViewModle

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jewelrypos.swarnakhatabook.DataClasses.NotificationPreferences
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import kotlinx.coroutines.launch

class NotificationSettingsViewModel(
    private val repository: NotificationRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    private val _preferences = MutableLiveData<NotificationPreferences>()
    val preferences: LiveData<NotificationPreferences> = _preferences

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    init {
        loadPreferences()
    }

    // Check if device is online
    private fun isOnline(): Boolean {
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun loadPreferences() {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getNotificationPreferences().fold(
                onSuccess = { prefs ->
                    _preferences.value = prefs
                    _isLoading.value = false
                },
                onFailure = { error ->
                    Log.e("NotificationSettingsVM", "Error loading preferences", error)
                    _errorMessage.value = "Could not load notification preferences. ${error.message}"
                    _preferences.value = NotificationPreferences() // Default values
                    _isLoading.value = false
                }
            )
        }
    }

    fun updatePreference(type: NotificationType, enabled: Boolean) {
        Log.d("NotificationSettingsVM", "Updating preference: $type to $enabled")
        
        val currentPrefs = _preferences.value ?: NotificationPreferences()
        Log.d("NotificationSettingsVM", "Current preferences: $currentPrefs")

        val updatedPrefs = when (type) {
            NotificationType.APP_UPDATE -> currentPrefs.copy(appUpdates = enabled)
            NotificationType.PAYMENT_DUE -> currentPrefs.copy(paymentDue = enabled)
            NotificationType.PAYMENT_OVERDUE -> currentPrefs.copy(paymentOverdue = enabled)
            NotificationType.CREDIT_LIMIT -> currentPrefs.copy(creditLimit = enabled)
            NotificationType.GENERAL -> currentPrefs.copy(businessInsights = enabled)
            NotificationType.BIRTHDAY -> currentPrefs.copy(customerBirthday = enabled)
            NotificationType.ANNIVERSARY -> currentPrefs.copy(customerAnniversary = enabled)
        }

        Log.d("NotificationSettingsVM", "Updated preferences: $updatedPrefs")
        _preferences.value = updatedPrefs
        savePreferences(updatedPrefs)
    }

    fun updateLowStockAlerts(enabled: Boolean) {
        val currentPrefs = _preferences.value ?: NotificationPreferences()
        val updatedPrefs = currentPrefs.copy(lowStock = enabled)
        _preferences.value = updatedPrefs
        savePreferences(updatedPrefs)
    }

    fun updatePaymentDueReminderDays(days: Int) {
        val currentPrefs = _preferences.value ?: NotificationPreferences()
        val updatedPrefs = currentPrefs.copy(paymentDueReminderDays = days)
        _preferences.value = updatedPrefs
        savePreferences(updatedPrefs)
    }

    fun updatePaymentOverdueAlertDays(days: Int) {
        val currentPrefs = _preferences.value ?: NotificationPreferences()
        val updatedPrefs = currentPrefs.copy(paymentOverdueAlertDays = days)
        _preferences.value = updatedPrefs
        savePreferences(updatedPrefs)
    }

    private fun savePreferences(prefs: NotificationPreferences) {
        if (!isOnline()) {
            Log.e("NotificationSettingsVM", "Cannot save changes while offline")
            _errorMessage.value = "Cannot save changes while offline."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            Log.d("NotificationSettingsVM", "Attempting to save preferences: $prefs")
            
            repository.updateNotificationPreferences(prefs).fold(
                onSuccess = {
                    Log.d("NotificationSettingsVM", "Successfully saved preferences")
                    _saveSuccess.value = true
                    _isLoading.value = false
                },
                onFailure = { error ->
                    Log.e("NotificationSettingsVM", "Error saving preferences", error)
                    val errorMessage = when {
                        error.message?.contains("No active shop ID") == true -> 
                            "Please select a shop before changing notification settings"
                        else -> "Could not save preferences. ${error.message}"
                    }
                    _errorMessage.value = errorMessage
                    _isLoading.value = false
                }
            )
        }
    }
}