package com.jewelrypos.swarnakhatabook.ViewModle

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    private val _notifications = MutableLiveData<List<AppNotification>>()
    val notifications: LiveData<List<AppNotification>> = _notifications

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    // Job for managing coroutines
    private var loadNotificationsJob: Job? = null
    private var loadUnreadCountJob: Job? = null
    private var notificationActionJob: Job? = null

    init {
        loadNotifications()
        refreshUnreadCount()
    }

    // Check if device is online
    private fun isOnline(): Boolean {
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    /**
     * Loads the first page of notifications
     */
    fun loadNotifications() {
        // Cancel any existing job before starting a new one
        loadNotificationsJob?.cancel()

        _isLoading.value = true
        loadNotificationsJob = viewModelScope.launch {
            try {
                // Choose source based on connectivity
                val source = if (isOnline()) Source.DEFAULT else Source.CACHE

                repository.getNotifications(false, source).fold(
                    onSuccess = { notificationList ->
                        _notifications.value = notificationList
                        _isLoading.value = false
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                        _isLoading.value = false
                        Log.e("NotificationViewModel", "Error loading notifications", exception)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load notifications: ${e.message}"
                _isLoading.value = false
                Log.e("NotificationViewModel", "Exception loading notifications", e)
            }
        }
    }

    /**
     * Loads the next page of notifications
     */
    fun loadNextPage() {
        if (_isLoading.value == true) return

        // Cancel any existing job before starting a new one
        loadNotificationsJob?.cancel()

        _isLoading.value = true
        loadNotificationsJob = viewModelScope.launch {
            try {
                // Choose source based on connectivity
                val source = if (isOnline()) Source.DEFAULT else Source.CACHE

                repository.getNotifications(true, source).fold(
                    onSuccess = { newNotifications ->
                        val currentList = _notifications.value ?: listOf()
                        _notifications.value = currentList + newNotifications
                        _isLoading.value = false
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                        _isLoading.value = false
                        Log.e("NotificationViewModel", "Error loading more notifications", exception)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load more notifications: ${e.message}"
                _isLoading.value = false
                Log.e("NotificationViewModel", "Exception loading more notifications", e)
            }
        }
    }

    /**
     * Refreshes the unread notification count
     */
    fun refreshUnreadCount() {
        // Cancel any existing job before starting a new one
        loadUnreadCountJob?.cancel()

        loadUnreadCountJob = viewModelScope.launch {
            try {
                repository.getUnreadNotificationCount().fold(
                    onSuccess = { count ->
                        _unreadCount.value = count
                    },
                    onFailure = { exception ->
                        Log.e("NotificationViewModel", "Error getting unread count", exception)
                    }
                )
            } catch (e: Exception) {
                Log.e("NotificationViewModel", "Exception getting unread count", e)
            }
        }
    }

    /**
     * Marks a notification as read
     */
    fun markAsRead(notificationId: String) {
        // Cancel any existing action job before starting a new one
        notificationActionJob?.cancel()

        notificationActionJob = viewModelScope.launch {
            try {
                repository.markAsRead(notificationId).fold(
                    onSuccess = {
                        // Update the notification in the list
                        val currentList = _notifications.value?.toMutableList() ?: mutableListOf()
                        val index = currentList.indexOfFirst { it.id == notificationId }
                        if (index != -1) {
                            val updatedNotification = currentList[index].copy(
                                status = NotificationStatus.READ,
                                readAt = java.util.Date()
                            )
                            currentList[index] = updatedNotification
                            _notifications.value = currentList
                        }
                        refreshUnreadCount()
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                        Log.e("NotificationViewModel", "Error marking notification as read", exception)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to mark notification as read: ${e.message}"
                Log.e("NotificationViewModel", "Exception marking notification as read", e)
            }
        }
    }

    /**
     * Records that action was taken on a notification
     */
    fun markActionTaken(notificationId: String) {
        // Cancel any existing action job before starting a new one
        notificationActionJob?.cancel()

        notificationActionJob = viewModelScope.launch {
            try {
                repository.markActionTaken(notificationId).fold(
                    onSuccess = {
                        // Update the notification in the list
                        val currentList = _notifications.value?.toMutableList() ?: mutableListOf()
                        val index = currentList.indexOfFirst { it.id == notificationId }
                        if (index != -1) {
                            val updatedNotification = currentList[index].copy(actionTaken = true)
                            currentList[index] = updatedNotification
                            _notifications.value = currentList
                        }
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                        Log.e("NotificationViewModel", "Error marking action taken", exception)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to mark action taken: ${e.message}"
                Log.e("NotificationViewModel", "Exception marking action taken", e)
            }
        }
    }

    /**
     * Deletes a notification
     */
    fun deleteNotification(notificationId: String) {
        // Cancel any existing action job before starting a new one
        notificationActionJob?.cancel()

        notificationActionJob = viewModelScope.launch {
            try {
                repository.deleteNotification(notificationId).fold(
                    onSuccess = {
                        // Remove the notification from the list
                        val currentList = _notifications.value?.toMutableList() ?: mutableListOf()
                        val index = currentList.indexOfFirst { it.id == notificationId }
                        if (index != -1) {
                            currentList.removeAt(index)
                            _notifications.value = currentList
                        }
                        refreshUnreadCount()
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                        Log.e("NotificationViewModel", "Error deleting notification", exception)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete notification: ${e.message}"
                Log.e("NotificationViewModel", "Exception deleting notification", e)
            }
        }
    }

    /**
     * Create a new notification
     */
    fun createNotification(notification: AppNotification) {
        // Cancel any existing action job before starting a new one
        notificationActionJob?.cancel()

        notificationActionJob = viewModelScope.launch {
            try {
                repository.createNotification(notification).fold(
                    onSuccess = { notificationId ->
                        // If notification was created successfully, refresh the list
                        loadNotifications()
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message
                        Log.e("NotificationViewModel", "Error creating notification", exception)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to create notification: ${e.message}"
                Log.e("NotificationViewModel", "Exception creating notification", e)
            }
        }
    }

    // Clean up Jobs when ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        loadNotificationsJob?.cancel()
        loadUnreadCountJob?.cancel()
        notificationActionJob?.cancel()
    }
}