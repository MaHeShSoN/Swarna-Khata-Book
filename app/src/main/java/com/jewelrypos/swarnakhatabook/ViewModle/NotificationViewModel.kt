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
import com.jewelrypos.swarnakhatabook.DataClasses.NavigationAction
import com.jewelrypos.swarnakhatabook.Enums.NotificationStatus
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.Utilitys.Event
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

class NotificationViewModel(
    private val repository: NotificationRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    // Reference to Firestore for debugging
    private val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

    // --- LiveData ---

    private val _notifications = MutableLiveData<List<AppNotification>>()
    val notifications: LiveData<List<AppNotification>> = _notifications

    private val _unreadCount = MutableLiveData<Int>()
    val unreadCount: LiveData<Int> = _unreadCount

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>() // Use nullable String for errors
    val errorMessage: LiveData<String?> = _errorMessage

    private val _navigationEvent = MutableLiveData<Event<NavigationAction>>()
    val navigationEvent: LiveData<Event<NavigationAction>> = _navigationEvent

    // Current active shop ID
    private val _currentShopId = MutableLiveData<String?>(null)
    val currentShopId: LiveData<String?> = _currentShopId

    // --- Pagination Logic ---

    companion object {
        private const val PAGE_SIZE = 20 // Define the number of items per page
        private const val TAG = "NotificationViewModel"
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
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
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

                // If a shop ID is set, load shop-specific notifications
                val shopId = _currentShopId.value
                val result = if (shopId != null) {
                    Log.d(TAG, "Loading notifications for shop: $shopId")
                    repository.getNotificationsForShop(shopId, false, source, PAGE_SIZE)
                } else {
                    Log.d(TAG, "Loading all notifications (no shop filter)")
                    repository.getNotifications(false, source, PAGE_SIZE)
                }

                result.fold(
                    onSuccess = { notificationList ->
                        _notifications.value = notificationList
                        // Check if the fetched list size is less than PAGE_SIZE, indicating the last page
                        _isLastPage.value = notificationList.size < PAGE_SIZE
                        _isLoading.value = false
                    },
                    onFailure = { exception ->
                        _errorMessage.value =
                            exception.message ?: "Unknown error loading notifications"
                        _isLoading.value = false
                        Log.e(TAG, "Error loading notifications", exception)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load notifications: ${e.message}"
                _isLoading.value = false
                Log.e(TAG, "Exception loading notifications", e)
            }
        }
    }

    /**
     * Loads the next page of notifications if not already loading and not on the last page.
     */
    fun loadNextPage() {
        // Prevent loading if already loading or if the last page has been reached
        if (_isLoading.value == true || _isLastPage.value == true) {
            Log.d(
                "NotificationViewModel",
                "loadNextPage skipped: isLoading=${_isLoading.value}, isLastPage=${_isLastPage.value}"
            )
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

                // If a shop ID is set, load shop-specific notifications
                val shopId = _currentShopId.value
                val result = if (shopId != null) {
                    Log.d(TAG, "Loading next page of notifications for shop: $shopId")
                    repository.getNotificationsForShop(shopId, true, source, PAGE_SIZE)
                } else {
                    Log.d(TAG, "Loading next page of all notifications (no shop filter)")
                    repository.getNotifications(true, source, PAGE_SIZE)
                }

                result.fold(
                    onSuccess = { newNotifications ->
                        if (newNotifications.isNotEmpty()) {
                            val currentList = _notifications.value ?: listOf()
                            _notifications.value =
                                currentList + newNotifications // Append new items
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
                        Log.e(
                            "NotificationViewModel", "Error loading more notifications", exception
                        )
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
        loadUnreadCountJob?.cancel()

        loadUnreadCountJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Refreshing unread notification count for shop: ${_currentShopId.value}")
                
                // If a shop ID is set, get unread count for that shop
                val shopId = _currentShopId.value
                val result = if (shopId != null) {
                    repository.getUnreadNotificationCountForShop(shopId)
                } else {
                    repository.getUnreadNotificationCount()
                }

                result.fold(
                    onSuccess = { count ->
                        Log.d(TAG, "New unread count: $count for shop: ${_currentShopId.value}")
                        _unreadCount.value = count
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Error getting unread count: ${exception.message}", exception)
                        
                        // Try a fallback method if available
                        try {
                            val fallbackCount = repository.getUnreadNotificationCountSafe()
                            if (fallbackCount >= 0) { // -1 indicates error in the safe method
                                Log.d(TAG, "Using fallback unread count: $fallbackCount")
                                _unreadCount.value = fallbackCount
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Fallback count also failed: ${e.message}", e)
                        }
                    }
                )
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Exception getting unread count: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Sets the current shop ID for filtering notifications
     * Pass null to show notifications from all shops
     */
    fun setCurrentShop(shopId: String?) {
        if (_currentShopId.value != shopId) {
            Log.d(TAG, "Setting current shop ID: $shopId")
            _currentShopId.value = shopId
            // Reload notifications with the new shop filter
            loadNotifications()
            refreshUnreadCount()
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
                // Get notification status before marking as read to avoid unnecessary count refresh
                val currentList = _notifications.value ?: emptyList()
                val notification = currentList.find { it.id == notificationId }
                val wasUnread = notification?.status == NotificationStatus.UNREAD

                repository.markAsRead(notificationId).fold(
                    onSuccess = {
                        // Update the notification in the local list optimistically
                        val updatedList = currentList.toMutableList()
                        val index = updatedList.indexOfFirst { it.id == notificationId }

                        if (index != -1 && updatedList[index].status != NotificationStatus.READ) { // Only update if not already read
                            val updatedNotification = updatedList[index].copy(
                                status = NotificationStatus.READ,
                                readAt = java.util.Date() // Set read timestamp
                            )

                            updatedList[index] = updatedNotification
                            _notifications.value = updatedList // Update LiveData

                            // Only refresh count if notification was actually unread before
                            if (wasUnread) {
                                // Update locally first for immediate UI response
                                val currentCount = _unreadCount.value ?: 0
                                if (currentCount > 0) {
                                    _unreadCount.value = currentCount - 1
                                }
                                
                                // Then refresh from server to ensure accuracy
                                refreshUnreadCount()
                            }
                        }
                    },
                    onFailure = { exception ->
                        _errorMessage.value = exception.message ?: "Failed to mark as read"
                        Log.e(TAG, "Error marking notification as read", exception)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Failed to mark notification as read: ${e.message}"
                Log.e(TAG, "Exception marking notification as read", e)
            }
        }
    }

    /**
     * Handle notification dismiss/delete with improved error handling
     */
    fun handleNotificationDismiss(notificationId: String) {
        notificationActionJob?.cancel()

        // Get notification details first for tracking
        val notification = _notifications.value?.find { it.id == notificationId }
        if (notification == null) {
            _errorMessage.value = "Cannot find notification to delete."
            return
        }

        // Get current shop ID directly from the ViewModel
        val shopId = _currentShopId.value
        if (shopId.isNullOrEmpty()) {
            _errorMessage.value = "No active shop selected. Please refresh and try again."
            return
        }

        // Log what we're trying to delete
        Log.d(TAG, "Starting deletion of notification: $notificationId, type: ${notification.type}, shop: $shopId")

        // Optimistically remove from UI first (better UX)
        val originalList = _notifications.value?.toMutableList() ?: mutableListOf()
        val updatedList = originalList.filter { it.id != notificationId }
        _notifications.value = updatedList
        
        // Track if we need to refresh unread count
        val wasUnread = notification.status == NotificationStatus.UNREAD
        
        // Update local unread count immediately if needed
        if (wasUnread) {
            val currentCount = _unreadCount.value ?: 0
            if (currentCount > 0) {
                _unreadCount.value = currentCount - 1
            }
        }

        notificationActionJob = viewModelScope.launch {
            try {
                // Try to delete on the server, pass shop ID explicitly
                val result = repository.deleteNotification(notificationId, shopId)
                
                if (result.isFailure) {
                    // Get details from the error
                    val exception = result.exceptionOrNull()
                    Log.e(TAG, "Failed to delete notification: ${exception?.message}")
                    
                    // Create an appropriate error message
                    val errorMessage = when {
                        exception?.message?.contains("permission") == true || 
                        exception?.message?.contains("PERMISSION_DENIED") == true ->
                            "You don't have permission to delete this notification."
                            
                        exception?.message?.contains("network") == true || 
                        exception?.message?.contains("UNAVAILABLE") == true ->
                            "Network error. Please check your connection and try again."
                            
                        exception?.message?.contains("NOT_FOUND") == true ->
                            "Notification has already been deleted."
                            
                        exception?.message?.contains("UNAUTHENTICATED") == true ||
                        exception?.message?.contains("authentication") == true ->
                            "Authentication error. Please log in again."
                            
                        else -> "Failed to delete notification. Please try again later."
                    }
                    
                    // Restore the item in the list
                    _notifications.value = originalList
                    
                    // Restore unread count if needed
                    if (wasUnread) {
                        refreshUnreadCount()
                    }
                    
                    _errorMessage.value = errorMessage
                } else {
                    // Delete was successful, refresh unread count to be sure
                    if (wasUnread) {
                        refreshUnreadCount()
                    }
                    
                    Log.d(TAG, "Successfully deleted notification: $notificationId")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Exception deleting notification: ${e.javaClass.name}", e)
                    
                    // Restore the item in the list
                    _notifications.value = originalList
                    
                    // Restore unread count if needed
                    if (wasUnread) {
                        refreshUnreadCount()
                    }
                    
                    // Set a user-friendly error message
                    _errorMessage.value = "Could not delete notification due to a system error. Please try again later."
                }
            }
        }
    }

    fun handleNotificationClick(notification: AppNotification, isActionButton: Boolean) {
        viewModelScope.launch {
            try {
                // Important operations first - mark as read
                val readResult = repository.markAsRead(notification.id)

                // Update local cache regardless of server success
                updateNotificationStatusLocally(notification.id, true, isActionButton)

                // Process action taken if needed
                if (isActionButton) {
                    try {
                        repository.markActionTaken(notification.id)
                    } catch (e: Exception) {
                        // Log but continue
                        Log.e("NotificationViewModel", "Error marking action taken", e)
                    }
                }

                // Trigger navigation - do this last
                _navigationEvent.postValue(Event(NavigationAction(notification, isActionButton)))

            } catch (e: Exception) {
                // Still navigate even if operations failed
                _navigationEvent.postValue(Event(NavigationAction(notification, isActionButton)))
                _errorMessage.value = "Error processing notification: ${e.message}"
            }
        }
    }

    private fun updateNotificationStatusLocally(notificationId: String, markRead: Boolean, markAction: Boolean) {
        val currentList = _notifications.value?.toMutableList() ?: mutableListOf()
        val index = currentList.indexOfFirst { it.id == notificationId }

        if (index != -1) {
            val oldNotification = currentList[index]
            val wasUnread = oldNotification.status != NotificationStatus.READ

            val updatedNotification = oldNotification.copy(
                status = if (markRead) NotificationStatus.READ else oldNotification.status,
                actionTaken = if (markAction) true else oldNotification.actionTaken,
                readAt = if (markRead) Date() else oldNotification.readAt
            )

            currentList[index] = updatedNotification
            _notifications.value = currentList

            // Update unread count locally if status changed
            if (wasUnread && markRead) {
                val currentCount = _unreadCount.value ?: 0
                if (currentCount > 0) {
                    _unreadCount.value = currentCount - 1
                }
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
                        Log.d(
                            "NotificationViewModel",
                            "Notification created successfully: $notificationId"
                        )
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

    /**
     * Helper method to check Firestore security rules and permissions
     * This is for development/debugging only
     */
    fun checkFirestoreRules(shopId: String?) {
        if (shopId.isNullOrEmpty()) {
            _errorMessage.value = "No shop ID available for rule checking"
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // 1. Try to read a notification
                val readResult = try {
                    val query = firestore.collection("shopData")
                        .document(shopId)
                        .collection("notifications")
                        .limit(1)
                        
                    val snapshot = query.get().await()
                    if (snapshot.isEmpty) "Read success (no notifications found)" else "Read success (found ${snapshot.size()} notifications)"
                } catch (e: Exception) {
                    "Read failed: ${e.message}"
                }
                
                // 2. Try to create a test notification
                val testId = "test_${System.currentTimeMillis()}"
                val writeResult = try {
                    val testDoc = firestore.collection("shopData")
                        .document(shopId)
                        .collection("notifications")
                        .document(testId)
                        
                    val testData = hashMapOf(
                        "title" to "Test Notification",
                        "message" to "This is a test notification for debugging",
                        "type" to "GENERAL",
                        "status" to "UNREAD",
                        "shopId" to shopId,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    
                    testDoc.set(testData).await()
                    "Write success"
                } catch (e: Exception) {
                    "Write failed: ${e.message}"
                }
                
                // 3. Try to delete a notification (either the test one or any other)
                val deleteResult = try {
                    // First try to delete the test notification if we created it
                    if (writeResult == "Write success") {
                        firestore.collection("shopData")
                            .document(shopId)
                            .collection("notifications")
                            .document(testId)
                            .delete()
                            .await()
                        "Delete success (test notification)"
                    } else {
                        // Otherwise try to find any notification to delete
                        val query = firestore.collection("shopData")
                            .document(shopId)
                            .collection("notifications")
                            .limit(1)
                            
                        val snapshot = query.get().await()
                        if (!snapshot.isEmpty) {
                            val doc = snapshot.documents.first()
                            doc.reference.delete().await()
                            "Delete success (found notification)"
                        } else {
                            "No notifications to delete"
                        }
                    }
                } catch (e: Exception) {
                    "Delete failed: ${e.message}"
                }
                
                // Compile results
                val resultMessage = """
                    Firestore Rules Check Results:
                    - Read: $readResult
                    - Write: $writeResult
                    - Delete: $deleteResult
                    
                    If 'Delete failed', your Firestore rules likely need updating.
                """.trimIndent()
                
                Log.d(TAG, resultMessage)
                _errorMessage.value = resultMessage
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Firestore rules", e)
                _errorMessage.value = "Error during rule check: ${e.message}"
            } finally {
                _isLoading.value = false
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