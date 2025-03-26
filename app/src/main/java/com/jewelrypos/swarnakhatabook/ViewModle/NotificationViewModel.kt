package com.jewelrypos.swarnakhatabook.ViewModle

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    private val _notifications = MutableLiveData<List<PaymentNotification>>()
    val notifications: LiveData<List<PaymentNotification>> = _notifications

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

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
        _isLoading.value = true
        viewModelScope.launch {
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
        }
    }

    /**
     * Loads the next page of notifications
     */
    fun loadNextPage() {
        if (_isLoading.value == true) return

        _isLoading.value = true
        viewModelScope.launch {
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
        }
    }

    /**
     * Refreshes the unread notification count
     */
    fun refreshUnreadCount() {
        viewModelScope.launch {
            repository.getUnreadNotificationCount().fold(
                onSuccess = { count ->
                    _unreadCount.value = count
                },
                onFailure = { exception ->
                    Log.e("NotificationViewModel", "Error getting unread count", exception)
                }
            )
        }
    }

    /**
     * Marks a notification as read
     */
    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
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
        }
    }

    /**
     * Records that action was taken on a notification
     */
    fun markActionTaken(notificationId: String) {
        viewModelScope.launch {
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
        }
    }

    /**
     * Deletes a notification
     */
    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
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
        }
    }
}