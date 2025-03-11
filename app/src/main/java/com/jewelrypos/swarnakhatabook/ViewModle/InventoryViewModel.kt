package com.jewelrypos.swarnakhatabook.ViewModle

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import kotlinx.coroutines.launch

class InventoryViewModel(
    private val repository: InventoryRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    private val _jewelleryItems = MutableLiveData<List<JewelleryItem>>()
    val jewelleryItems: LiveData<List<JewelleryItem>> = _jewelleryItems
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Local cache of all items loaded so far
    private val itemsList = mutableListOf<JewelleryItem>()

    init {
        loadFirstPage()
    }

    fun refreshData() {
        _jewelleryItems.value = emptyList() // Clear existing data
        loadFirstPage()
    }


    // Check if device is online
    private fun isOnline(): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    private fun loadFirstPage() {
        _isLoading.value = true
        itemsList.clear()
        viewModelScope.launch {
            // Choose source based on connectivity
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            repository.fetchJewelleryItemsPaginated(false, source).fold(
                onSuccess = {
                    itemsList.addAll(it)
                    _jewelleryItems.value = itemsList.toList()
                    _isLoading.value = false
                    Log.d("invFrag", "First page loaded: ${it.size} items")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("invFrag", "Error loading first page: ${it.message}")
                }
            )
        }
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return

        _isLoading.value = true
        viewModelScope.launch {
            // Choose source based on connectivity, same as in loadFirstPage
            val source = if (isOnline()) Source.DEFAULT else Source.CACHE

            repository.fetchJewelleryItemsPaginated(true, source).fold(
                onSuccess = { newItems ->
                    itemsList.addAll(newItems)
                    _jewelleryItems.value = itemsList.toList()
                    _isLoading.value = false
                    Log.d("invFrag", "Next page loaded: ${newItems.size} items")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                    Log.d("invFrag", "Error loading next page: ${it.message}")
                }
            )
        }
    }


    fun addJewelleryItem(jewelleryItem: JewelleryItem) {
        viewModelScope.launch {
            repository.addJewelleryItem(jewelleryItem).fold(
                onSuccess = {
                    loadFirstPage()
                    Log.d("invFrag", "vm1")
                },
                onFailure = {
                    _errorMessage.value = it.message
                    Log.d("invFrag", "vm2")
                }
            )
        }
    }

}