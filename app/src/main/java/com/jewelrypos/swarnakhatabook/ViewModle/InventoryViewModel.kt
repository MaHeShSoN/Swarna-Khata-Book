package com.jewelrypos.swarnakhatabook.ViewModle


// JewelleryViewModel.kt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem

class InventoryViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val _jewelleryItems = MutableLiveData<List<JewelleryItem>>()
    val jewelleryItems: LiveData<List<JewelleryItem>> = _jewelleryItems

    init {
        fetchJewelleryItems()
    }

    fun addJewelleryItem(jewelleryItem: JewelleryItem, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("jewellery")
            .add(jewelleryItem)
            .addOnSuccessListener {
                fetchJewelleryItems() // Refresh the list after adding
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    private fun fetchJewelleryItems() {
        db.collection("jewellery")
            .get()
            .addOnSuccessListener { result ->
                val items = result.toObjects(JewelleryItem::class.java)
                _jewelleryItems.value = items
            }
            .addOnFailureListener { e ->
                // Handle error
            }
    }
}