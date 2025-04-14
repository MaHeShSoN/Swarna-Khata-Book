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

    // --- LiveData ---
    private val _notifications = MutableLiveData<List<AppNotification>>()
    val notifications: LiveData<List<AppNotification>> = _notifications

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>() // Use nullable String for errors
    val errorMessage: LiveData<String?> = _errorMessage

    // --- Pagination Logic ---
    companion object {
        private const val PAGE_SIZE = 20 // Define the number of items per page
    }
    private val _isLastPage = MutableLiveData<Boolean>(false)
    val isLastPage: LiveData<Boolean> = _isLastPage

    // Function for the Fragment to know the page size
    fun getPageSize(): Int = PAGE_SIZE

    // --- Coroutine Jobs ---
    private var loadNotificationsJob: Job? = null
    private var loadUnreadCountJob: Job? = null
    private var notificationActionJob: Job? = null

    init {
        loadNotifications() // Load initial data
        refreshUnreadCount()
    }

    // Check if device is online
    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) // Added Ethernet check
    }

    /**
     * Loads the first page of notifications. Resets pagination state.
     */
    fun loadNotifications() {
        // Cancel any existing job before starting a new one
        loadNotificationsJob?.cancel()

        _isLoading.value = true
        _isLastPage.value = false // Reset last page flag on initial load/refresh
        _errorMessage.value = null // Clear previous errors

        loadNotificationsJob = viewModelScope.launch {
            try {
                // Choose source based on connectivity
                val source = if (isOnline()) Source.DEFAULT else Source.CACHE

                repository.getNotifications(false, source, PAGE_SIZE).fold( // Pass PAGE_SIZE to repository
                    onSuccess = { notificationList ->
                        _notifications.value = notificationList
                        // Check if the fetched list size is less than PAGE_SIZE, indicating the last page
                        _isLastPage.value = notificationList.size < PAGE_SIZE
                        _isLoading.value = false
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "Unknown error loading notifications"
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
     * Loads the next page of notifications if not already loading and not on the last page.
     */
    fun loadNextPage() {
        // Prevent loading if already loading or if the last page has been reached
        if (_isLoading.value == true || _isLastPage.value == true) {
            Log.d("NotificationViewModel", "loadNextPage skipped: isLoading=${_isLoading.value}, isLastPage=${_isLastPage.value}")
            return
        }

        // Cancel any existing job before starting a new one
        loadNotificationsJob?.cancel()

        _isLoading.value = true
        _errorMessage.value = null // Clear previous errors

        loadNotificationsJob = viewModelScope.launch {
            try {
                // Choose source based on connectivity
                val source = if (isOnline()) Source.DEFAULT else Source.CACHE

                // Assume repository.getNotifications handles fetching the *next* page based on its internal state
                repository.getNotifications(true, source, PAGE_SIZE).fold( // Pass PAGE_SIZE
                    onSuccess = { newNotifications ->
                        if (newNotifications.isNotEmpty()) {
                            val currentList = _notifications.value ?: listOf()
                            _notifications.value = currentList + newNotifications // Append new items
                            // Check if the fetched list size is less than PAGE_SIZE
                            _isLastPage.value = newNotifications.size < PAGE_SIZE
                        } else {
                            // If no new notifications are returned, we are on the last page
                            _isLastPage.value = true
                        }
                        _isLoading.value = false
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "Unknown error loading next page"
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
     * Refreshes the unread notification count.
     */
    fun refreshUnreadCount() {
        // Cancel any existing job before starting a new one
        loadUnreadCountJob?.cancel()
        _errorMessage.value = null // Clear previous errors

        loadUnreadCountJob = viewModelScope.launch {
            try {
                // Fetch count only if online, maybe not critical otherwise?
                if (!isOnline()) {
                    Log.d("NotificationViewModel", "Skipping unread count refresh: Offline")
                    return@launch
                }

                repository.getUnreadNotificationCount().fold(
                    onSuccess = { count ->
                        _unreadCount.value = count
                    },
                    onFailure = { exception ->
                        // Don't necessarily show this error to the user, just log it
                        Log.e("NotificationViewModel", "Error getting unread count", exception)
                    }
                )
            } catch (e: Exception) {
                Log.e("NotificationViewModel", "Exception getting unread count", e)
            }
        }
    }

    /**
     * Marks a notification as read.
     */
    fun markAsRead(notificationId: String) {
        // Cancel any existing action job before starting a new one
        notificationActionJob?.cancel()
        _errorMessage.value = null // Clear previous errors

        notificationActionJob = viewModelScope.launch {
            try {
                repository.markAsRead(notificationId).fold(
                    onSuccess = {
                        // Update the notification in the local list optimistically
                        val currentList = _notifications.value?.toMutableList() ?: mutableListOf()
                        val index = currentList.indexOfFirst { it.id == notificationId }
                        if (index != -1 && currentList[index].status != NotificationStatus.READ) { // Only update if not already read
                            val updatedNotification = currentList[index].copy(
                                status = NotificationStatus.READ,
                                readAt = java.util.Date() // Set read timestamp
                            )
                            currentList[index] = updatedNotification
                            _notifications.value = currentList // Update LiveData
                            refreshUnreadCount() // Refresh count after successful update
                        }
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "Failed to mark as read"
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
     * Records that action was taken on a notification.
     */
    fun markActionTaken(notificationId: String) {
        // Cancel any existing action job before starting a new one
        notificationActionJob?.cancel()
        _errorMessage.value = null // Clear previous errors

        notificationActionJob = viewModelScope.launch {
            try {
                repository.markActionTaken(notificationId).fold(
                    onSuccess = {
                        // Update the notification in the list optimistically
                        val currentList = _notifications.value?.toMutableList() ?: mutableListOf()
                        val index = currentList.indexOfFirst { it.id == notificationId }
                        if (index != -1 && !currentList[index].actionTaken) { // Only update if action not already marked
                            val updatedNotification = currentList[index].copy(actionTaken = true)
                            currentList[index] = updatedNotification
                            _notifications.value = currentList // Update LiveData
                        }
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "Failed to mark action taken"
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
     * Deletes a notification.
     */
    fun deleteNotification(notificationId: String) {
        // Cancel any existing action job before starting a new one
        notificationActionJob?.cancel()
        _errorMessage.value = null // Clear previous errors

        notificationActionJob = viewModelScope.launch {
            try {
                repository.deleteNotification(notificationId).fold(
                    onSuccess = {
                        // Remove the notification from the list optimistically
                        val currentList = _notifications.value?.toMutableList() ?: mutableListOf()
                        val index = currentList.indexOfFirst { it.id == notificationId }
                        if (index != -1) {
                            currentList.removeAt(index)
                            _notifications.value = currentList // Update LiveData
                            refreshUnreadCount() // Refresh count after successful deletion
                        }
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "Failed to delete notification"
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
     * Create a new notification (Example - might not be needed here directly)
     */
    fun createNotification(notification: AppNotification) {
        // Cancel any existing action job before starting a new one
        notificationActionJob?.cancel()
        _errorMessage.value = null // Clear previous errors

        notificationActionJob = viewModelScope.launch {
            try {
                repository.createNotification(notification).fold(
                    onSuccess = { notificationId ->
                        // If notification was created successfully, refresh the list to show it
                        Log.d("NotificationViewModel", "Notification created successfully: $notificationId")
                        loadNotifications() // Refresh the whole list
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "Failed to create notification"
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
        Log.d("NotificationViewModel", "ViewModel cleared, jobs cancelled.")
    }
}
