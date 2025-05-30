package com.jewelrypos.swarnakhatabook.DataClasses

import com.google.firebase.firestore.PropertyName
import java.io.Serializable

// Customer data class for Firebase
data class Customer(
    @PropertyName("id")
    var id: String = "",
    @PropertyName("customerType")
    val customerType: String = "",
    @PropertyName("firstName")
    val firstName: String = "",
    @PropertyName("lastName")
    val lastName: String = "",
    @PropertyName("fullNameSearchable")
    val fullNameSearchable: String = "",
    @PropertyName("phoneNumber")
    val phoneNumber: String = "",
    @PropertyName("email")
    val email: String = "",
    // Address information
    @PropertyName("streetAddress")
    val streetAddress: String = "",
    @PropertyName("city")
    val city: String = "",
    @PropertyName("state")
    val state: String = "",
    @PropertyName("postalCode")
    val postalCode: String = "",
    @PropertyName("country")
    val country: String = "India",
    @PropertyName("previousAddresses")
    val previousAddresses: List<String> = emptyList(), // New field to store previous addresses
    // Financial information
    @PropertyName("balanceType")
    val balanceType: String = "Jama", // JAMA or BAKI
    @PropertyName("openingBalance")
    val openingBalance: Double = 0.0,
    @PropertyName("currentBalance")
    val currentBalance: Double = 0.0, // New field to track ongoing balance
    @PropertyName("balanceNotes")
    val balanceNotes: String = "",
    // Business information (for wholesale customers)
    @PropertyName("businessName")
    val businessName: String = "",
    @PropertyName("gstNumber")
    val gstNumber: String = "",
    @PropertyName("taxId")
    val taxId: String = "",
    // Relationship information
    @PropertyName("customerSince")
    val customerSince: String = "",
    @PropertyName("referredBy")
    val referredBy: String = "",
    @PropertyName("birthday")
    val birthday: String = "",
    @PropertyName("anniversary")
    val anniversary: String = "",
    @PropertyName("notes")
    val notes: String = "",
    // Timestamp fields
    @PropertyName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @PropertyName("lastUpdatedAt")
    val lastUpdatedAt: Long = System.currentTimeMillis()
    //* Last Purchase Date: [Date]
    //* Total Purchase Value: [Currency]

) : Serializable