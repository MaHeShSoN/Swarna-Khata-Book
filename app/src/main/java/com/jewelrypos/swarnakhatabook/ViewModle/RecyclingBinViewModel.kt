package com.jewelrypos.swarnakhatabook.ViewModle

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.RecycledItem
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.RecycledItemsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecyclingBinViewModel(
    private val recycledItemsRepository: RecycledItemsRepository,
    private val invoiceRepository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    private val _recycledItems = MutableLiveData<List<RecycledItem>>()
    val recycledItems: LiveData<List<RecycledItem>> = _recycledItems

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _restoreSuccess = MutableLiveData<Pair<Boolean, String>>()
    val restoreSuccess: LiveData<Pair<Boolean, String>> = _restoreSuccess

    init {
        loadRecycledItems()
    }

    fun loadRecycledItems(itemType: String? = null) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = if (itemType != null) {
                    recycledItemsRepository.getRecycledItemsByType(itemType)
                } else {
                    recycledItemsRepository.getRecycledItems()
                }

                result.fold(
                    onSuccess = { items ->
                        _recycledItems.value = items
                        if (items.isEmpty()) {
                            _errorMessage.value = "No items in recycling bin"
                        }
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Error loading recycled items: ${error.message}"
                        _recycledItems.value = emptyList()
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "Unexpected error: ${e.message}"
                _recycledItems.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadRecycledInvoices() {
        loadRecycledItems("INVOICE")
    }

    fun restoreCustomer(recycledItemId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                recycledItemsRepository.restoreCustomer(recycledItemId).fold(
                    onSuccess = { customer ->
                        _restoreSuccess.value = Pair(true, "Customer ${customer.firstName} ${customer.lastName} restored successfully")
                        loadRecycledItems() // Refresh the list (or filter by type if needed)
                        EventBus.postCustomerUpdated() // Notify other parts of the app
                    },
                    onFailure = { error ->
                        Log.e("RecyclingBinVM", "Error restoring customer: ${error.message}", error) // Add logging
                        _restoreSuccess.value = Pair(false, "Failed to restore customer: ${error.message}")
                        _errorMessage.value = "Failed to restore customer: ${error.message}" // Also set error message
                    }
                )
            } catch (e: Exception) {
                Log.e("RecyclingBinVM", "Exception during customer restore: ${e.message}", e) // Add logging
                _restoreSuccess.value = Pair(false, "Unexpected error restoring customer: ${e.message}")
                _errorMessage.value = "Unexpected error: ${e.message}" // Also set error message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun restoreJewelleryItem(recycledItemId: String) {
        _isLoading.value = true // Indicate loading state
        viewModelScope.launch { // Launch coroutine in ViewModel scope
            try {
                // Call the repository function to restore the item
                recycledItemsRepository.restoreJewelleryItem(recycledItemId).fold(
                    onSuccess = { jewelleryItem ->
                        // On success, update LiveData to notify UI
                        _restoreSuccess.value = Pair(true, "Item '${jewelleryItem.displayName}' restored successfully")
                        loadRecycledItems() // Refresh the list of recycled items
                        EventBus.postInventoryUpdated() // Notify other parts of the app about inventory change
                    },
                    onFailure = { error ->
                        // On failure, log the error and update LiveData
                        Log.e("RecyclingBinVM", "Error restoring jewellery item: ${error.message}", error)
                        _restoreSuccess.value = Pair(false, "Failed to restore item: ${error.message}")
                        _errorMessage.value = "Failed to restore item: ${error.message}" // Set error message for UI
                    }
                )
            } catch (e: Exception) {
                // Catch any unexpected exceptions during the process
                Log.e("RecyclingBinVM", "Exception during jewellery item restore: ${e.message}", e)
                _restoreSuccess.value = Pair(false, "Unexpected error restoring item: ${e.message}")
                _errorMessage.value = "Unexpected error: ${e.message}" // Set error message for UI
            } finally {
                _isLoading.value = false // End loading state regardless of outcome
            }
        }
    }

    fun restoreInvoice(recycledItemId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                recycledItemsRepository.restoreInvoice(recycledItemId).fold(
                    onSuccess = { invoice ->
                        _restoreSuccess.value = Pair(true, "Invoice ${invoice.invoiceNumber} restored successfully")
                        loadRecycledInvoices() // Refresh the list
                        EventBus.postInvoiceUpdated() // Notify the app that invoices have changed
                    },
                    onFailure = { error ->
                        _restoreSuccess.value = Pair(false, "Failed to restore invoice: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _restoreSuccess.value = Pair(false, "Unexpected error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Modify permanentlyDeleteItem if it needs type-specific logic (unlikely)
    fun permanentlyDeleteItem(recycledItemId: String) {
        // ... (existing implementation is likely okay) ...
        _isLoading.value = true
        viewModelScope.launch {
            try {
                recycledItemsRepository.permanentlyDeleteItem(recycledItemId).fold(
                    onSuccess = {
                        _restoreSuccess.value = Pair(true, "Item permanently deleted")
                        loadRecycledItems() // Refresh the list after permanent deletion
                    },
                    onFailure = { error ->
                        _restoreSuccess.value = Pair(false, "Failed to delete item permanently: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                _restoreSuccess.value = Pair(false, "Unexpected error deleting item permanently: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun formatDeletedDate(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return "Unknown"

        val date = timestamp.toDate()
        val now = Date()
        val diffInMillis = now.time - date.time
        val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

        return when {
            diffInDays < 1 -> {
                // Format as time if less than a day
                SimpleDateFormat("'Today at' h:mm a", Locale.getDefault()).format(date)
            }
            diffInDays < 2 -> {
                "Yesterday"
            }
            diffInDays < 7 -> {
                SimpleDateFormat("EEEE", Locale.getDefault()).format(date) // Day name
            }
            else -> {
                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
            }
        }
    }

    fun calculateExpiryTimeRemaining(expiresAt: Long): String {
        val now = System.currentTimeMillis()
        val diffInMillis = expiresAt - now

        if (diffInMillis <= 0) {
            return "Expired"
        }

        val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

        return when {
            diffInDays < 1 -> "Today"
            diffInDays < 2 -> "Tomorrow"
            else -> "In $diffInDays days"
        }
    }

    private fun isOnline(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )
    }

    companion object {
        private const val TAG = "RecyclingBinViewModel"
    }
}