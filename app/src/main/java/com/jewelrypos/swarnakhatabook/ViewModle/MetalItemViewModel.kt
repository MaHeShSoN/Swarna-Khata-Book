package com.jewelrypos.swarnakhatabook.ViewModle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.room.Room
import android.app.Application
import com.jewelrypos.swarnakhatabook.DataClasses.MetalItem
import com.jewelrypos.swarnakhatabook.Entity.MetalItemEntity
import com.jewelrypos.swarnakhatabook.RoomDatabase.MetalItemAppDatabase
class MetalItemViewModel(application: Application) : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val itemCollection = firestore.collection("items")

    private val db = Room.databaseBuilder(
        application,
        MetalItemAppDatabase::class.java, "item_database"
    ).build()

    private val itemDao = db.itemDao()

    // Function to add a single MetalItem
    fun addItem(item: MetalItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val itemEntity = MetalItemEntity(item.fieldName, item.type)
                itemDao.insertItem(itemEntity) // Insert single item into Room
                itemCollection.document(item.fieldName).set(item).await() // Add to Firestore
            } catch (e: Exception) {
                // Handle add item errors
                e.printStackTrace()
            }
        }
    }

    // Function to retrieve all MetalItems as a list
    fun retrieveItems(onItemsRetrieved: (List<MetalItem>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val roomItems = itemDao.getAllItems().map { MetalItem(it.fieldName, it.type) }
                if (roomItems.isNotEmpty()) {
                    onItemsRetrieved(roomItems)
                } else {
                    val snapshot = itemCollection.get().await()
                    val firestoreItems = snapshot.documents.mapNotNull { it.toObject(MetalItem::class.java) }
                    if (firestoreItems.isNotEmpty()) {
                        itemDao.insertItems(firestoreItems.map { MetalItemEntity(it.fieldName, it.type) })
                        onItemsRetrieved(firestoreItems)
                    } else {
                        onItemsRetrieved(emptyList())
                    }
                }
            } catch (e: Exception) {
                // Handle retrieval errors
                e.printStackTrace()
                onItemsRetrieved(emptyList())
            }
        }
    }
}