package com.jewelrypos.swarnakhatabook.ViewModle

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.Order
import com.jewelrypos.swarnakhatabook.DataClasses.Payment
import com.jewelrypos.swarnakhatabook.DataClasses.SelectedItemWithPrice
import kotlinx.coroutines.launch

class SalesViewModel : ViewModel() {

    // Selected customer for new order/invoice
    private val _selectedCustomer = MutableLiveData<Customer>()
    val selectedCustomer: LiveData<Customer> = _selectedCustomer

    // Selected items for new order/invoice
    private val _selectedItems = MutableLiveData<List<SelectedItemWithPrice>>()
    val selectedItems: LiveData<List<SelectedItemWithPrice>> = _selectedItems

    // Temporary storage for callback
    private var itemSelectionCallback: ((List<SelectedItemWithPrice>) -> Unit)? = null


    // Add these properties to SalesViewModel
    private val _pendingOrders = MutableLiveData<List<Order>>()
    val pendingOrders: LiveData<List<Order>> = _pendingOrders

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Add this property and method to SalesViewModel
    private val _currentPayment = MutableLiveData<Payment?>()
    val currentPayment: LiveData<Payment?> = _currentPayment


    fun setCurrentPayment(payment: Payment) {
        _currentPayment.value = payment
    }


    // Add these methods to SalesViewModel
    fun loadPendingOrders() {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // This would normally come from a repository
                // For now, simulate loading pending orders
                val orders = getOrdersFromRepository().filter { it.status == "Pending" }
                _pendingOrders.value = orders
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchOrders(query: String, onlyPending: Boolean = false) {
        viewModelScope.launch {
            try {
                // This would normally come from a repository
                // For now, simulate searching orders
                val allOrders = getOrdersFromRepository()
                val filteredOrders = allOrders.filter { order ->
                    // Filter by status if needed
                    (!onlyPending || order.status == "Pending") &&
                            // Filter by search terms
                            (order.orderNumber.contains(query, ignoreCase = true) ||
                                    order.customerName.contains(query, ignoreCase = true))
                }

                if (onlyPending) {
                    _pendingOrders.value = filteredOrders
                } else {
                    // You might need another LiveData for all orders
                    //_allOrders.value = filteredOrders
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    // Placeholder method to simulate getting orders from a repository
    private fun getOrdersFromRepository(): List<Order> {
        // In a real app, this would come from a repository
        return emptyList()
    }


    // Set the selected customer
    fun setSelectedCustomer(customer: Customer) {
        _selectedCustomer.value = customer
    }

    // Set the selected items
    fun setSelectedItems(items: List<SelectedItemWithPrice>) {
        _selectedItems.value = items
    }

    // Store callback for item selection
    fun setItemSelectionCallback(callback: (List<SelectedItemWithPrice>) -> Unit) {
        itemSelectionCallback = callback
    }

    // Get the item selection callback
    fun getItemSelectionCallback(): ((List<SelectedItemWithPrice>) -> Unit)? {
        return itemSelectionCallback
    }

    // Save a new order
    fun saveOrder(order: Order, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Implementation would connect to repository
                // For now, just simulate success
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    // Save a new invoice
    fun saveInvoice(invoice: Invoice, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Implementation would connect to repository
                // For now, just simulate success
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }
}