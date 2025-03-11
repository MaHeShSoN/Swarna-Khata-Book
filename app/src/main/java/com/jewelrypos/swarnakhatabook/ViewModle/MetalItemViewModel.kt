package com.jewelrypos.swarnakhatabook.ViewModle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.room.Room
import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.jewelrypos.swarnakhatabook.DataClasses.MetalItem
import com.jewelrypos.swarnakhatabook.Entity.MetalItemEntity
import com.jewelrypos.swarnakhatabook.RoomDatabase.MetalItemAppDatabase
import kotlinx.coroutines.withContext

class MetalItemViewModel(application: Application) : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val itemCollection = firestore.collection("items")

    private val db = Room.databaseBuilder(
        application,
        MetalItemAppDatabase::class.java, "item_database"
    ).build()

    private val itemDao = db.itemDao()

    // LiveData to observe items
    private val _items = MutableLiveData<List<MetalItem>>()
    val items: LiveData<List<MetalItem>> get() = _items

    init {
        loadItems() // Load items when ViewModel is created
    }

    // Function to add a single MetalItem
    fun addItem(item: MetalItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val itemEntity = MetalItemEntity(item.fieldName, item.type)
                itemDao.insertItem(itemEntity) // Insert single item into Room
                itemCollection.document(item.fieldName).set(item).await() // Add to Firestore

                // Reload items after adding a new one
                loadItems()
            } catch (e: Exception) {
                // Handle add item errors
                e.printStackTrace()
            }
        }
    }

    // Private function to load items and update LiveData
    private fun loadItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val roomItems = itemDao.getAllItems().map { MetalItem(it.fieldName, it.type) }
                val items = if (roomItems.isNotEmpty()) {
                    roomItems
                } else {
                    val snapshot = itemCollection.get().await()
                    val firestoreItems = snapshot.documents.mapNotNull { it.toObject(MetalItem::class.java) }
                    if (firestoreItems.isNotEmpty()) {
                        itemDao.insertItems(firestoreItems.map { MetalItemEntity(it.fieldName, it.type) })
                        firestoreItems
                    } else {
                        emptyList()
                    }
                }

                // Update LiveData on Main thread
                withContext(Dispatchers.Main) {
                    _items.value = items
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _items.value = emptyList()
                }
            }
        }
    }

    // Optional: Keep this for backward compatibility
    fun retrieveItems(onItemsRetrieved: (List<MetalItem>) -> Unit) {
        viewModelScope.launch {
            onItemsRetrieved(items.value ?: emptyList())
        }
    }
}