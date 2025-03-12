package com.jewelrypos.swarnakhatabook.DataClasses

import java.io.Serializable

// Customer data class for Firebase
data class Customer(
    var id: String = "", // Firebase document ID
    val customerType: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    // Address information
    val streetAddress: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "India",
    // Financial information
    val balanceType: String = "Credit", // Credit or Debit
    val openingBalance: Double = 0.0,
    val balanceNotes: String = "",
    val creditLimit: Double = 0.0,
    // Business information (for wholesale customers)
    val businessName: String = "",
    val gstNumber: String = "",
    val taxId: String = "",
    // Relationship information
    val customerSince: String = "",
    val referredBy: String = "",
    val birthday: String = "",
    val anniversary: String = "",
    val notes: String = "",
    // Timestamp fields
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
    //* Last Purchase Date: [Date]
    //* Total Purchase Value: [Currency]
) : Serializable