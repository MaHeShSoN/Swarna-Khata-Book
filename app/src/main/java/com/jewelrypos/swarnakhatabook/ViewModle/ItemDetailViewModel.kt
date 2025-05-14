package com.jewelrypos.swarnakhatabook.ViewModle

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.jewelrypos.swarnakhatabook.DataClasses.ItemUsageStats
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Events.EventBus
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ItemDetailViewModel(
    private val repository: InventoryRepository,
    private val connectivityManager: ConnectivityManager
) : ViewModel() {

    private val _jewelryItem = MutableLiveData<JewelleryItem>()
    val jewelryItem: LiveData<JewelleryItem> = _jewelryItem


    private val _itemUsageStats = MutableLiveData<ItemUsageStats>()
    val itemUsageStats: LiveData<ItemUsageStats> = _itemUsageStats

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private var itemId: String = ""

    private val firestore = FirebaseFirestore.getInstance()

    // Load item details by ID
    fun loadItem(id: String) {
        this.itemId = id
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Choose source based on connectivity
                val source = if (isOnline()) Source.DEFAULT else Source.CACHE

                repository.getJewelleryItemById(id, source).fold(
                    onSuccess = { item ->
                        _jewelryItem.value = item
                        loadItemUsageStats(id)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to load item: ${error.message}"
                        _isLoading.value = false
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // Load item usage statistics from invoices
    private fun loadItemUsageStats(itemId: String) {
        viewModelScope.launch {
            try {
                val userId = repository.getCurrentUserId()
                val invoiceCollection = firestore.collection("users")
                    .document(userId)
                    .collection("invoices")

                // Get all invoices containing this item
                val invoicesQuery = invoiceCollection
                    .get()
                    .await()

                val invoicesWithItem = invoicesQuery.documents.filter { invoiceDoc ->
                    val items = invoiceDoc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    items.any { it["itemId"] == itemId }
                }

                var totalQuantity = 0
                var totalRevenue = 0.0
                var lastSoldDate = 0L
                val customerSales = mutableMapOf<String, Int>() // Customer ID to quantity sold

                // Process each invoice to gather statistics
                invoicesWithItem.forEach { invoiceDoc ->
                    val invoiceData = invoiceDoc.data ?: return@forEach
                    val items = invoiceData["items"] as? List<Map<String, Any>> ?: emptyList()
                    val customerId = invoiceData["customerId"] as? String ?: ""
                    val customerName = invoiceData["customerName"] as? String ?: ""
                    val invoiceDate = (invoiceData["invoiceDate"] as? Long) ?: 0L

                    items.forEach { itemMap ->
                        if (itemMap["itemId"] == itemId) {
                            val quantity = (itemMap["quantity"] as? Long)?.toInt() ?: 0
                            val price = (itemMap["price"] as? Double) ?: 0.0

                            totalQuantity += quantity
                            totalRevenue += price * quantity

                            // Track customer purchase quantities
                            if (customerId.isNotEmpty() && customerName.isNotEmpty()) {
                                customerSales[customerName] = (customerSales[customerName] ?: 0) + quantity
                            }

                            // Track latest sale date
                            if (invoiceDate > lastSoldDate) {
                                lastSoldDate = invoiceDate
                            }
                        }
                    }
                }

                // Find top customer
                val topCustomerEntry = customerSales.entries.maxByOrNull { it.value }
                val topCustomerName = topCustomerEntry?.key ?: ""
                val topCustomerQuantity = topCustomerEntry?.value ?: 0

                // Update stats LiveData
                _itemUsageStats.value = ItemUsageStats(
                    totalInvoicesUsed = invoicesWithItem.size,
                    totalQuantitySold = totalQuantity,
                    totalRevenue = totalRevenue,
                    lastSoldDate = lastSoldDate,
                    topCustomerName = topCustomerName,
                    topCustomerQuantity = topCustomerQuantity
                )

                _isLoading.value = false

            } catch (e: Exception) {
                Log.e("ItemDetailViewModel", "Error loading usage stats", e)
                _errorMessage.value = "Failed to load usage statistics"
                _isLoading.value = false
            }
        }
    }

    fun moveItemToRecycleBin(callback: (Boolean) -> Unit) {
        val currentItem = _jewelryItem.value ?: return // Ensure we have the item data
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Call the repository function to move the item to the recycle bin
                repository.moveItemToRecycleBin(currentItem).fold( // Assuming you create this function in InventoryRepository
                    onSuccess = {
                        _isLoading.value = false
                        EventBus.postInventoryDeleted() // Post event
                        callback(true) // Indicate success
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to move item to recycle bin: ${error.message}"
                        _isLoading.value = false
                        callback(false) // Indicate failure
                        Log.e("ItemDetailViewModel", "Error moving item to recycle bin", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
                _isLoading.value = false
                callback(false)
                Log.e("ItemDetailViewModel", "Exception moving item to recycle bin", e)
            }
        }
    }

    // Update item stock based on inventory type
    fun updateStock(adjustment: Int, callback: (Boolean) -> Unit) {
        val currentItem = _jewelryItem.value ?: return
        
        // Handle differently based on inventory type
        when (currentItem.inventoryType) {
            com.jewelrypos.swarnakhatabook.Enums.InventoryType.IDENTICAL_BATCH -> {
                // For batch items, ensure whole numbers
                updateItemStock(currentItem, adjustment.toDouble(), callback)
            }
            com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> {
                // For bulk items, adjust the total weight
                updateBulkWeight(adjustment.toDouble(), callback)
            }
        }
    }
    
    // Update stock for batch items
    private fun updateItemStock(currentItem: JewelleryItem, adjustment: Double, callback: (Boolean) -> Unit) {
        val newStock = currentItem.stock + adjustment

        if (newStock < 0) {
            _errorMessage.value = "Stock cannot be negative"
            callback(false)
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = repository.updateItemStock(currentItem.id, newStock)
                result.fold(
                    onSuccess = {
                        // Update the local item data
                        _jewelryItem.value = currentItem.copy(stock = newStock)
                        _isLoading.value = false
                        callback(true)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to update stock: ${error.message}"
                        _isLoading.value = false
                        callback(false)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
                _isLoading.value = false
                callback(false)
            }
        }
    }
    
    // Update total weight for bulk items
    fun updateBulkWeight(adjustment: Double, callback: (Boolean) -> Unit) {
        val currentItem = _jewelryItem.value ?: return
        
        // Only allow this for BULK_STOCK items
        if (currentItem.inventoryType != com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK) {
            _errorMessage.value = "Weight adjustment is only applicable for bulk stock items"
            callback(false)
            return
        }
        
        val newWeight = currentItem.totalWeightGrams + adjustment
        
        if (newWeight < 0) {
            _errorMessage.value = "Total weight cannot be negative"
            callback(false)
            return
        }
        
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                val result = repository.updateItemBulkWeight(currentItem.id, newWeight)
                result.fold(
                    onSuccess = {
                        // Update the local item data
                        _jewelryItem.value = currentItem.copy(totalWeightGrams = newWeight)
                        _isLoading.value = false
                        callback(true)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to update weight: ${error.message}"
                        _isLoading.value = false
                        callback(false)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
                _isLoading.value = false
                callback(false)
            }
        }
    }

    // Update the entire item
    fun updateItem(updatedItem: JewelleryItem, callback: (Boolean) -> Unit) {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                repository.updateJewelleryItem(updatedItem).fold(
                    onSuccess = {
                        // Update local data
                        _jewelryItem.value = updatedItem
                        _isLoading.value = false
                        callback(true)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to update item: ${error.message}"
                        _isLoading.value = false
                        callback(false)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
                _isLoading.value = false
                callback(false)
            }
        }
    }

    // Check if item is used in any invoices
    fun checkItemUsage(callback: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val userId = repository.getCurrentUserId()
                val invoiceCollection = firestore.collection("users")
                    .document(userId)
                    .collection("invoices")

                // Count invoices with this item
                val invoicesQuery = invoiceCollection
                    .get()
                    .await()

                val count = invoicesQuery.documents.count { invoiceDoc ->
                    val items = invoiceDoc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    items.any { it["itemId"] == itemId }
                }

                callback(count)
            } catch (e: Exception) {
                Log.e("ItemDetailViewModel", "Error checking item usage", e)
                callback(0) // Default to 0 on error
            }
        }
    }

    // Delete the item
    fun deleteItem(callback: (Boolean) -> Unit) {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                repository.deleteJewelleryItem(itemId).fold(
                    onSuccess = {
                        _isLoading.value = false
                        callback(true)
                    },
                    onFailure = { error ->
                        _errorMessage.value = "Failed to delete item: ${error.message}"
                        _isLoading.value = false
                        callback(false)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "An error occurred: ${e.message}"
                _isLoading.value = false
                callback(false)
            }
        }
    }

    // Helper to check network connectivity
    private fun isOnline(): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }
    
    // Upload item image to Firebase Storage
    fun uploadItemImage(imageFile: java.io.File, callback: (Boolean, String) -> Unit) {
        if (!isOnline()) {
            _errorMessage.value = "Cannot upload images while offline"
            callback(false, "")
            return
        }
        
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val userId = repository.getCurrentUserId()
                val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                val imagesRef = storageRef.child("users/$userId/inventory/${itemId}_${System.currentTimeMillis()}.jpg")
                
                // Compress the image before uploading
                val compressedImageFile = compressImage(imageFile)
                
                val uploadTask = imagesRef.putFile(android.net.Uri.fromFile(compressedImageFile))
                uploadTask.continueWithTask { task ->
                    if (!task.isSuccessful) {
                        throw task.exception ?: Exception("Unknown error during upload")
                    }
                    imagesRef.downloadUrl
                }.addOnCompleteListener { task ->
                    _isLoading.value = false
                    if (task.isSuccessful) {
                        val downloadUrl = task.result.toString()
                        callback(true, downloadUrl)
                    } else {
                        Log.e("ItemDetailViewModel", "Error uploading image", task.exception)
                        _errorMessage.value = "Failed to upload image: ${task.exception?.message}"
                        callback(false, "")
                    }
                }
            } catch (e: Exception) {
                Log.e("ItemDetailViewModel", "Exception during image upload", e)
                _errorMessage.value = "Error uploading image: ${e.message}"
                _isLoading.value = false
                callback(false, "")
            }
        }
    }
    
    private suspend fun compressImage(imageFile: java.io.File): java.io.File {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Load the bitmap from file
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
                
                // Calculate sample size for resizing
                var inSampleSize = 1
                val reqWidth = 800 // Target width
                val reqHeight = 800 // Target height
                
                val height = options.outHeight
                val width = options.outWidth
                
                if (height > reqHeight || width > reqWidth) {
                    val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
                    val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())
                    inSampleSize = Math.max(heightRatio, widthRatio)
                }
                
                // Decode with calculated sample size
                options.apply {
                    inJustDecodeBounds = false
                    inSampleSize = inSampleSize
                }
                
                val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
                
                // Create a temporary file for the compressed image
                val compressedFile = java.io.File.createTempFile(
                    "compressed_${System.currentTimeMillis()}", 
                    ".jpg",
                    imageFile.parentFile
                )
                
                // Compress and save
                val out = java.io.FileOutputStream(compressedFile)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                out.flush()
                out.close()
                
                compressedFile
            } catch (e: Exception) {
                Log.e("ItemDetailViewModel", "Error compressing image", e)
                // If compression fails, return the original file
                imageFile
            }
        }
    }
}