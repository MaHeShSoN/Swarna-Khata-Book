package com.jewelrypos.swarnakhatabook.Events

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.protobuf.Internal.BooleanList

object EventBus {
    private val _invoiceAddedEvent = MutableLiveData<Boolean>()
    private val _invoiceDeletedEvent = MutableLiveData<Boolean>()
    val invoiceAddedEvent: LiveData<Boolean> = _invoiceAddedEvent
    val invoiceDeletedEvent: LiveData<Boolean> = _invoiceDeletedEvent

    fun postInvoiceAdded() {
        _invoiceAddedEvent.postValue(true)
    }

    fun postInvoiceDeleted() {
        _invoiceDeletedEvent.postValue(true)
    }

}