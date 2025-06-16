package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import com.jewelrypos.swarnakhatabook.Enums.InventoryType

data class JewelleryItem(
    @PropertyName("id")
    val id: String = "",
    
    @PropertyName("displayName")
    val displayName: String = "",
    
    @PropertyName("itemType")
    val itemType: String = "",
    
    @PropertyName("category")
    val category: String = "",
    
    @PropertyName("grossWeight")
    val grossWeight: Double = 0.0,
    
    @PropertyName("netWeight")
    val netWeight: Double = 0.0,
    
    @PropertyName("stoneWeight")
    val stoneWeight: Double = 0.0,
    
    @PropertyName("wastage")
    val wastage: Double = 0.0,
    
    @PropertyName("wastageType")
    val wastageType: String = "",
    
    @PropertyName("purity")
    val purity: String = "",
    
    @PropertyName("makingCharges")
    val makingCharges: Double = 0.0,
    
    @PropertyName("makingChargesType")
    val makingChargesType: String = "",
    
    @PropertyName("stock")
    val stock: Double = 0.0,
    
    @PropertyName("stockUnit")
    val stockUnit: String = "",
    
    @PropertyName("location")
    val location: String = "",
    
    @PropertyName("diamondPrice")
    val diamondPrice: Double = 0.0,
    
    @PropertyName("metalRate")
    val metalRate: Double = 0.0,
    
    @PropertyName("metalRateOn")
    val metalRateOn: String = "",
    
    @PropertyName("taxRate")
    val taxRate: Double = 0.0,
    
    @PropertyName("totalTax")
    val totalTax: Double = 0.0,
    
    @PropertyName("listOfExtraCharges")
    val listOfExtraCharges: List<ExtraCharge> = emptyList(),
    
    @PropertyName("inventoryType")
    val inventoryType: InventoryType = InventoryType.IDENTICAL_BATCH,
    
    @PropertyName("totalWeightGrams")
    val totalWeightGrams: Double = 0.0,
    
    @PropertyName("imageUrl")
    val imageUrl: String = ""
)