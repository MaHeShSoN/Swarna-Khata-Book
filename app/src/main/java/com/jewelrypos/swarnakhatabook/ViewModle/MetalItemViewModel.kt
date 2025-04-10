package com.jewelrypos.swarnakhatabook.ViewModle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jewelrypos.swarnakhatabook.DataClasses.MetalItem
import com.jewelrypos.swarnakhatabook.Repository.MetalItemRepository
import kotlinx.coroutines.launch

class MetalItemViewModel(
    application: Application,
    private val repository: MetalItemRepository
) : AndroidViewModel(application) {

    private val _items = MutableLiveData<List<MetalItem>>()
    val items: LiveData<List<MetalItem>> = _items

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    init {
        loadItems()
    }

    fun addItem(item: MetalItem) {
        _isLoading.value = true
        viewModelScope.launch {
            repository.addMetalItem(item).fold(
                onSuccess = {
                    loadItems()
                    _isLoading.value = false
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _isLoading.value = false
                }
            )
        }
    }

    private fun loadItems() {
        _isLoading.value = true
        viewModelScope.launch {
            repository.getMetalItems().fold(
                onSuccess = {
                    _items.value = it
                    _isLoading.value = false
                },
                onFailure = {
                    _errorMessage.value = it.message
                    _items.value = emptyList()
                    _isLoading.value = false
                }
            )
        }
    }
}