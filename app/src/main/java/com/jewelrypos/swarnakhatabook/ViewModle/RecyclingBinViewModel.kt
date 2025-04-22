package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.RecycledItem
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.Repository.RecycledItemsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecyclingBinViewModel(
    application: Application,
    private val recycledItemsRepository: RecycledItemsRepository,
    private val invoiceRepository: InvoiceRepository,
    private val connectivityManager: ConnectivityManager
) : AndroidViewModel(application) {

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
                            _errorMessage.value = getApplication<Application>().getString(R.string.no_items_in_recycling_bin)
                        }
                    },
                    onFailure = { error ->
                        _errorMessage.value = getApplication<Application>().getString(R.string.error_loading_recycled_items, error.message)
                        _recycledItems.value = emptyList()
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.unexpected_error, e.message)
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
                        _restoreSuccess.value = Pair(true, getApplication<Application>().getString(R.string.customer_restored_successfully, "${customer.firstName} ${customer.lastName}"))
                        loadRecycledItems() // Refresh the list (or filter by type if needed)
                        EventBus.postCustomerUpdated() // Notify other parts of the app
                    },
                    onFailure = { error ->
                        Log.e("RecyclingBinVM", "Error restoring customer: ${error.message}", error) // Add logging
                        _restoreSuccess.value = Pair(false, getApplication<Application>().getString(R.string.failed_to_restore_customer, error.message))
                        _errorMessage.value = getApplication<Application>().getString(R.string.failed_to_restore_customer, error.message) // Also set error message
                    }
                )
            } catch (e: Exception) {
                Log.e("RecyclingBinVM", "Exception during customer restore: ${e.message}", e) // Add logging
                _restoreSuccess.value = Pair(false, getApplication<Application>().getString(R.string.unexpected_error_restoring_customer, e.message))
                _errorMessage.value = getApplication<Application>().getString(R.string.unexpected_error, e.message) // Also set error message
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
                        _restoreSuccess.value = Pair(true, getApplication<Application>().getString(R.string.item_restored_successfully, jewelleryItem.displayName))
                        loadRecycledItems() // Refresh the list of recycled items
                        EventBus.postInventoryUpdated() // Notify other parts of the app about inventory change
                    },
                    onFailure = { error ->
                        // On failure, log the error and update LiveData
                        Log.e("RecyclingBinVM", "Error restoring jewellery item: ${error.message}", error)
                        _restoreSuccess.value = Pair(false, getApplication<Application>().getString(R.string.failed_to_restore_item, error.message))
                        _errorMessage.value = getApplication<Application>().getString(R.string.failed_to_restore_item, error.message) // Set error message for UI
                    }
                )
            } catch (e: Exception) {
                // Catch any unexpected exceptions during the process
                Log.e("RecyclingBinVM", "Exception during jewellery item restore: ${e.message}", e)
                _restoreSuccess.value = Pair(false, getApplication<Application>().getString(R.string.unexpected_error_restoring_item, e.message))
                _errorMessage.value = getApplication<Application>().getString(R.string.unexpected_error, e.message) // Set error message for UI
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
                        _restoreSuccess.value = Pair(true, getApplication<Application>().getString(R.string.invoice_restored_successfully, invoice.invoiceNumber))
                        loadRecycledInvoices() // Refresh the list
                        EventBus.postInvoiceUpdated() // Notify the app that invoices have changed
                    },
                    onFailure = { error ->
                        _restoreSuccess.value = Pair(false, getApplication<Application>().getString(R.string.failed_to_restore_invoice, error.message))
                    }
                )
            } catch (e: Exception) {
                _restoreSuccess.value = Pair(false, getApplication<Application>().getString(R.string.unexpected_error, e.message))
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
                        _restoreSuccess.value = Pair(true, getApplication<Application>().getString(R.string.item_permanently_deleted))
                        loadRecycledItems() // Refresh the list after permanent deletion
                    },
                    onFailure = { error ->
                        _restoreSuccess.value = Pair(false, getApplication<Application>().getString(R.string.failed_to_delete_item_permanently, error.message))
                    }
                )
            } catch (e: Exception) {
                _restoreSuccess.value = Pair(false, getApplication<Application>().getString(R.string.unexpected_error_deleting_item, e.message))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun formatDeletedDate(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return getApplication<Application>().getString(R.string.unknown)

        val date = timestamp.toDate()
        val now = Date()
        val diffInMillis = now.time - date.time
        val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

        return when {
            diffInDays < 1 -> {
                // Format as time if less than a day
                val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                getApplication<Application>().getString(R.string.today_at_time, formattedTime)
            }
            diffInDays < 2 -> {
                getApplication<Application>().getString(R.string.yesterday)
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
            return getApplication<Application>().getString(R.string.expired)
        }

        val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)

        return when {
            diffInDays < 1 -> getApplication<Application>().getString(R.string.today)
            diffInDays < 2 -> getApplication<Application>().getString(R.string.tomorrow)
            else -> getApplication<Application>().getString(R.string.in_x_days, diffInDays)
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