
package com.jewelrypos.swarnakhatabook.ViewModle

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.ExtraCharge
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.DataClasses.SelectedItemWithPrice
import kotlinx.coroutines.launch

class SalesViewModel : ViewModel() {

    // Selected customer for new order/invoice
    private val _selectedCustomer = MutableLiveData<Customer>()
    val selectedCustomer: LiveData<Customer> = _selectedCustomer

    // Selected items for new order/invoice
    private val _selectedItems = MutableLiveData<List<SelectedItemWithPrice>>()
    val selectedItems: LiveData<List<SelectedItemWithPrice>> = _selectedItems

    // Add a method to add a selected item
    fun addSelectedItem(item: JewelleryItem, price: Double) {
        val currentItems = _selectedItems.value?.toMutableList() ?: mutableListOf()

        // Check if item already exists in the list
        val existingItemIndex = currentItems.indexOfFirst { it.item.id == item.id }

        if (existingItemIndex >= 0) {
            // Update quantity of existing item
            val existingItem = currentItems[existingItemIndex]
            currentItems[existingItemIndex] = SelectedItemWithPrice(
                item = existingItem.item,
                quantity = existingItem.quantity + 1,
                price = price
            )
        } else {
            // Add new item with quantity 1
            currentItems.add(
                SelectedItemWithPrice(
                    item = item,
                    quantity = 1,
                    price = price
                )
            )
        }

        _selectedItems.value = currentItems
    }

    // Remove a selected item
    fun removeSelectedItem(item: SelectedItemWithPrice) {
        val currentItems = _selectedItems.value?.toMutableList() ?: return
        currentItems.remove(item)
        _selectedItems.value = currentItems
    }

    // Update item quantity
    fun updateItemQuantity(itemEdit: SelectedItemWithPrice, quantity: Int) {
        // If quantity is 0 or negative, remove the item
        if (quantity <= 0) {
            removeSelectedItem(itemEdit)
            return
        }

        val currentItems = _selectedItems.value?.toMutableList() ?: return
        val itemIndex = currentItems.indexOfFirst { it.item.id == itemEdit.item.id }

        if (itemIndex >= 0) {
            val item = currentItems[itemIndex]
            currentItems[itemIndex] = SelectedItemWithPrice(
                item = item.item,
                quantity = quantity,
                price = item.price
            )
            _selectedItems.value = currentItems
        }
    }


    // Update selected item
    fun updateSelectedItem(updatedItem: JewelleryItem, newPrice: Double): Boolean {
        val currentItems = _selectedItems.value?.toMutableList() ?: return false
        val itemIndex = currentItems.indexOfFirst { it.item.id == updatedItem.id }

        return if (itemIndex >= 0) {
            // Preserve the quantity from the existing item
            val existingQuantity = currentItems[itemIndex].quantity

            // Replace with updated item but keep same quantity
            currentItems[itemIndex] = SelectedItemWithPrice(
                item = updatedItem,
                quantity = existingQuantity,
                price = newPrice
            )

            // This is a critical line - needs to update the LiveData object
            _selectedItems.value = currentItems

            // Log successful update
            Log.d("SalesViewModel", "Successfully updated item: ${updatedItem.id}")
            true
        } else {
            // Item not found in the list
            Log.w(
                "SalesViewModel",
                "Attempted to update non-existent item with ID: ${updatedItem.id}"
            )
            false
        }
    }


    // Update selected item
//    fun updateSelectedItem(updatedItem: JewelleryItem, newPrice: Double): Boolean {
//        val currentItems = _selectedItems.value?.toMutableList() ?: return false
//        val itemIndex = currentItems.indexOfFirst { it.item.id == updatedItem.id }
//
//        return if (itemIndex >= 0) {
//            // Preserve the quantity from the existing item
//            val existingQuantity = currentItems[itemIndex].quantity
//
//            // Replace with updated item but keep same quantity
//            currentItems[itemIndex] = SelectedItemWithPrice(
//                item = updatedItem,
//                quantity = existingQuantity,
//                price = newPrice
//            )
//
//            _selectedItems.value = currentItems
//            true
//        } else {
//            // Item not found in the list
//            Log.w(
//                "SalesViewModel",
//                "Attempted to update non-existent item with ID: ${updatedItem.id}"
//            )
//            false
//        }
//    }

    // Calculate subtotal (only items price * quantity)
    fun calculateSubtotal(): Double {
        return _selectedItems.value?.sumOf { it.price * it.quantity } ?: 0.0
    }

    // Get all extra charges from all items
    fun getAllExtraCharges(): List<Pair<String, Double>> {
        val charges = mutableListOf<Pair<String, Double>>()

        _selectedItems.value?.forEach { selectedItem ->
            // Multiply each extra charge by the item quantity
            selectedItem.item.listOfExtraCharges.forEach { extraCharge ->
                charges.add(Pair(extraCharge.name, extraCharge.amount * selectedItem.quantity))
            }
        }

        return charges
    }

    // Calculate total extra charges
    fun calculateExtraCharges(): Double {
        return getAllExtraCharges().sumOf { it.second }
    }

    // Calculate tax based on each item's tax rate
    fun calculateTax(): Double {
        var totalTax = 0.0

        _selectedItems.value?.forEach { selectedItem ->
            // Use the totalTax field directly from the item
            totalTax += selectedItem.item.totalTax * selectedItem.quantity
        }

        return totalTax
    }

    // Calculate total (subtotal + extra charges + tax)
    fun calculateTotal(): Double {
        return calculateSubtotal() + calculateExtraCharges() + calculateTax()
    }

    // Set the selected customer
    fun setSelectedCustomer(customer: Customer) {
        _selectedCustomer.value = customer
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