package com.jewelrypos.swarnakhatabook.Events

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object EventBus {
    // Existing invoice events
    private val _invoiceAddedEvent = MutableLiveData<Boolean>()
    val invoiceAddedEvent: LiveData<Boolean> = _invoiceAddedEvent

    private val _invoiceDeletedEvent = MutableLiveData<Boolean>()
    val invoiceDeletedEvent: LiveData<Boolean> = _invoiceDeletedEvent

    private val _invoiceUpdatedEvent = MutableLiveData<Boolean>()
    val invoiceUpdatedEvent: LiveData<Boolean> = _invoiceUpdatedEvent

    // New inventory events
    private val _inventoryUpdatedEvent = MutableLiveData<Boolean>()
    val inventoryUpdatedEvent: LiveData<Boolean> = _inventoryUpdatedEvent

    private val _inventoryDeletedEvent = MutableLiveData<Boolean>()
    val inventoryDeletedEvent: LiveData<Boolean> = _inventoryDeletedEvent

    // Existing invoice methods
    fun postInvoiceAdded() {
        _invoiceAddedEvent.postValue(true)
    }

    fun postInvoiceDeleted() {
        _invoiceDeletedEvent.postValue(true)
    }

    fun postInvoiceUpdated() {
        _invoiceUpdatedEvent.value = true
    }

    // New inventory methods
    fun postInventoryUpdated() {
        _inventoryUpdatedEvent.value = true
    }

    fun postInventoryDeleted() {
        _inventoryDeletedEvent.value = true
    }

    // Existing reset methods
    fun resetInvoiceAddedEvent() {
        _invoiceAddedEvent.value = false
    }

    fun resetInvoiceDeletedEvent() {
        _invoiceDeletedEvent.value = false
    }

    fun resetInvoiceUpdatedEvent() {
        _invoiceUpdatedEvent.value = false
    }

    // New reset methods
    fun resetInventoryUpdatedEvent() {
        _inventoryUpdatedEvent.value = false
    }

    fun resetInventoryDeletedEvent() {
        _inventoryDeletedEvent.value = false
    }
}