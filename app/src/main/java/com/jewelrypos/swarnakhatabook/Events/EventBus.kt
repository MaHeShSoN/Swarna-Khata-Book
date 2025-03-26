package com.jewelrypos.swarnakhatabook.Events

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.protobuf.Internal.BooleanList

object EventBus {
    private val _invoiceAddedEvent = MutableLiveData<Boolean>()
    val invoiceAddedEvent: LiveData<Boolean> = _invoiceAddedEvent

    private val _invoiceDeletedEvent = MutableLiveData<Boolean>()
    val invoiceDeletedEvent: LiveData<Boolean> = _invoiceDeletedEvent

    private val _invoiceUpdatedEvent = MutableLiveData<Boolean>()
    val invoiceUpdatedEvent: LiveData<Boolean> = _invoiceUpdatedEvent

    fun postInvoiceAdded() {
        _invoiceAddedEvent.postValue(true)
    }

    fun postInvoiceDeleted() {
        _invoiceDeletedEvent.postValue(true)
    }

    fun postInvoiceUpdated() {
        _invoiceUpdatedEvent.value = true
    }

    // Reset methods
    fun resetInvoiceAddedEvent() {
        _invoiceAddedEvent.value = false
    }

    fun resetInvoiceDeletedEvent() {
        _invoiceDeletedEvent.value = false
    }

    fun resetInvoiceUpdatedEvent() {
        _invoiceUpdatedEvent.value = false
    }
}