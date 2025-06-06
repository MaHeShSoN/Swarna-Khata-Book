package com.jewelrypos.swarnakhatabook.Repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.toObject
import com.jewelrypos.swarnakhatabook.DataClasses.Customer // Assuming needed
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem // Assuming needed
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import kotlinx.coroutines.Dispatchers // Import Dispatchers
import kotlinx.coroutines.withContext // Import withContext
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import kotlin.math.abs
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

// Define custom exceptions if not already defined elsewhere
class UserNotAuthenticatedException(message: String) : Exception(message)
class PhoneNumberInvalidException(message: String) : Exception(message)
class ShopNotSelectedException(message: String) : Exception(message)


class InvoiceRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val context: Context
) {
    // Companion object for configuration
    companion object {
        private const val PAGE_SIZE = 10
        private const val TAG = "InvoiceRepository"
    }

    // Pagination state
    private var lastDocumentSnapshot: DocumentSnapshot? = null
    private var isLastPage = false

    // New pagination state for customer specific invoices
    private var lastCustomerInvoiceDocumentSnapshot: DocumentSnapshot? = null
    private var isLastCustomerInvoicePage = false

    // Get current active shop ID from SessionManager
    internal fun getCurrentShopId(): String {
        return SessionManager.getActiveShopId(context)
            ?: throw ShopNotSelectedException("No active shop selected.")
    }

    // Get current user ID for validation
    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid
            ?: throw UserNotAuthenticatedException("User not authenticated.")
    }

    // Centralized method for getting Firestore collection references
    // This doesn't need Dispatchers.IO as it just builds a reference
    private fun getShopCollection(collectionName: String) = firestore.collection("shopData")
        .document(getCurrentShopId())
        .collection(collectionName)


    /**
     * Generates searchable keywords from an invoice's data.
     * These keywords are used for efficient searching in Firestore.
     */
    private fun generateKeywordsForInvoice(invoice: Invoice): List<String> {
        Log.d(TAG, "Generating keywords for invoice: ${invoice.invoiceNumber}")
        val keywords = mutableSetOf<String>()

        // Helper function to generate prefixes for a given word
        fun addPrefixes(word: String) {
            val lowerWord = word.lowercase(Locale.ROOT)
            if (lowerWord.length > 1) {
                for (i in 1..lowerWord.length) {
                    keywords.add(lowerWord.substring(0, i))
                }
            } else if (lowerWord.isNotEmpty()) {
                keywords.add(lowerWord)
            }
        }

        // 1. Invoice Number
        invoice.invoiceNumber.trim().takeIf { it.isNotEmpty() }?.let { invNum ->
            Log.d(TAG, "Adding keywords for invoice number: $invNum")
            addPrefixes(invNum)
        }

        // 2. Customer Name
        invoice.customerName.trim().takeIf { it.isNotEmpty() }?.let { name ->
            Log.d(TAG, "Adding keywords for customer name: $name")
            keywords.add(name.lowercase(Locale.ROOT))
            name.split(" ").forEach { part ->
                if (part.trim().isNotEmpty()) {
                    addPrefixes(part.trim())
                }
            }
        }

        // 3. Customer Phone
        invoice.customerPhone.trim().takeIf { it.isNotEmpty() }?.let { phone ->
            Log.d(TAG, "Adding keywords for customer phone: $phone")
            val numbersOnly = phone.replace(Regex("\\s+|-"), "")
            addPrefixes(phone)
            if (phone != numbersOnly) {
                addPrefixes(numbersOnly)
            }
        }

        // 4. Customer Address
        invoice.customerAddress.trim().takeIf { it.isNotEmpty() }?.let { address ->
            Log.d(TAG, "Adding keywords for customer address: $address")
            keywords.add(address.lowercase(Locale.ROOT))
            address.split(" ", ",").forEach { part ->
                val trimmedPart = part.trim()
                if (trimmedPart.length > 2) {
                    addPrefixes(trimmedPart)
                } else if (trimmedPart.isNotEmpty()) {
                    keywords.add(trimmedPart.lowercase(Locale.ROOT))
                }
            }
        }

        // 5. Notes
        invoice.notes.trim().takeIf { it.isNotEmpty() }?.let { notes ->
            Log.d(TAG, "Adding keywords for notes: $notes")
            keywords.add(notes.lowercase(Locale.ROOT))
            notes.split(" ").forEach { word ->
                val trimmedWord = word.trim()
                if (trimmedWord.length > 2) {
                    addPrefixes(trimmedWord)
                } else if (trimmedWord.isNotEmpty()) {
                    keywords.add(trimmedWord.lowercase(Locale.ROOT))
                }
            }
        }

        // 6. Item names and details
        invoice.items.forEach { item ->
            item.itemDetails.displayName.trim().takeIf { it.isNotEmpty() }?.let { itemName ->
                Log.d(TAG, "Adding keywords for item: $itemName")
                keywords.add(itemName.lowercase(Locale.ROOT))
                itemName.split(" ").forEach { part ->
                    if (part.trim().isNotEmpty()) {
                        addPrefixes(part.trim())
                    }
                }
            }
        }

        Log.d(TAG, "Generated ${keywords.size} keywords for invoice ${invoice.invoiceNumber}")
        return keywords.toList()
    }
    /**
     * Saves or updates an invoice, ensuring Firestore operations run on the IO dispatcher.
     * Also updates customer balance and inventory stock accordingly.
     */
    suspend fun saveInvoice(invoice: Invoice): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting to save invoice: ${invoice.invoiceNumber}")
        try {
            // Calculate payment status based on amounts
            val paymentStatus = when {
                invoice.paidAmount >= invoice.totalAmount -> "PAID"
                invoice.paidAmount > 0 -> "PARTIAL"
                else -> "UNPAID"
            }
            Log.d(TAG, "Calculated payment status: $paymentStatus (Paid: ${invoice.paidAmount}, Total: ${invoice.totalAmount})")

            // Determine if any item in the invoice is taxable
            val hasTaxableItems = invoice.items.any { it.itemDetails.taxRate > 0.0 }
            Log.d(TAG, "Invoice has taxable items: $hasTaxableItems")

            // Generate keywords for search
            val keywords = generateKeywordsForInvoice(invoice)

            // Prepare invoice with ID, payment status, keywords, and hasTaxableItems
            val invoiceWithId = if (invoice.id.isEmpty()) {
                invoice.copy(
                    id = invoice.invoiceNumber,
                    paymentStatus = paymentStatus,
                    keywords = keywords,
                    hasTaxableItems = hasTaxableItems
                )
            } else {
                invoice.copy(
                    paymentStatus = paymentStatus,
                    keywords = keywords,
                    hasTaxableItems = hasTaxableItems
                )
            }
            Log.d(TAG, "Prepared invoice with ID: ${invoiceWithId.id}")

            // Get existing invoice (if any) for comparison
            val existingInvoice = if (invoiceWithId.id.isNotEmpty()) {
                try {
                    Log.d(TAG, "Checking for existing invoice: ${invoiceWithId.id}")
                    getShopCollection("invoices")
                        .document(invoiceWithId.id)
                        .get()
                        .await()
                        .toObject(Invoice::class.java)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not retrieve existing invoice for update check: ${e.message}")
                    null
                }
            } else {
                null
            }

            // Calculate balance change based on difference in unpaid amounts
            val unpaidAmountBefore = existingInvoice?.let { it.totalAmount - it.paidAmount } ?: 0.0
            val unpaidAmountAfter = invoiceWithId.totalAmount - invoiceWithId.paidAmount
            val balanceChange = unpaidAmountAfter - unpaidAmountBefore
            Log.d(TAG, "Balance change calculation: Before=$unpaidAmountBefore, After=$unpaidAmountAfter, Change=$balanceChange")

            // Save/Update the invoice document
            Log.d(TAG, "Saving invoice document to Firestore")
            getShopCollection("invoices")
                .document(invoiceWithId.id)
                .set(invoiceWithId)
                .await()
            Log.d(TAG, "Invoice document saved successfully")

            // Update customer balance if needed
            if (balanceChange != 0.0 && invoiceWithId.customerId.isNotEmpty()) {
                Log.d(TAG, "Updating customer balance for customer: ${invoiceWithId.customerId}")
                updateCustomerBalanceForSave(invoiceWithId.customerId, balanceChange)
            }

            // Update inventory stock
            if (existingInvoice == null) {
                Log.d(TAG, "Processing inventory for new invoice")
                updateInventoryStockForNewInvoice(invoiceWithId)
            } else {
                Log.d(TAG, "Processing inventory changes for updated invoice")
                handleInventoryStockChanges(existingInvoice, invoiceWithId)
            }

            Log.d(TAG, "Invoice save completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving invoice ${invoice.invoiceNumber}", e)
            Result.failure(e)
        }
    }

    /**
     * Helper to update customer balance during saveInvoice.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */

    private suspend fun updateCustomerBalanceForSave(customerId: String, balanceChange: Double) {
        try {
            if (balanceChange == 0.0) {
                Log.d(TAG, "No balance change needed for customer $customerId.")
                return // Skip if no change
            }

            val customerDocRef = getShopCollection("customers").document(customerId)
            val customerDoc = customerDocRef.get().await()

            if (!customerDoc.exists()) {
                Log.w(TAG, "Customer $customerId not found for balance update.")
                return
            }

            val currentBalanceValue = customerDoc.getDouble("currentBalance") ?: 0.0
            // Default to "Baki" if balanceType is missing or null, which is consistent with new customer creation.
            val currentBalanceType = customerDoc.getString("balanceType") ?: "Baki"

            var finalBalanceValue: Double
            var finalBalanceType: String

            Log.d(TAG, "Updating balance for customer $customerId: currentBalance=$currentBalanceValue, currentType=$currentBalanceType, balanceChange=$balanceChange")

            if (currentBalanceType == "Baki") {
                // Customer currently owes the shop (Baki)
                // balanceChange > 0 means they owe more
                // balanceChange < 0 means they owe less (payment/credit)
                val newPotentialBaki = currentBalanceValue + balanceChange
                if (newPotentialBaki >= 0) {
                    // Still Baki or settled
                    finalBalanceValue = newPotentialBaki
                    finalBalanceType = "Baki"
                } else {
                    // Flipped to Jama (shop owes customer)
                    finalBalanceValue = abs(newPotentialBaki)
                    finalBalanceType = "Jama"
                }
            } else { // currentBalanceType == "Jama"
                // Shop currently owes customer (Jama) / Customer has advance
                // balanceChange > 0 means shop owes less (customer used advance for a purchase)
                // balanceChange < 0 means shop owes more (customer added more advance/refund)
                val newPotentialJama = currentBalanceValue - balanceChange
                if (newPotentialJama >= 0) {
                    // Still Jama or settled
                    finalBalanceValue = newPotentialJama
                    finalBalanceType = "Jama"
                } else {
                    // Flipped to Baki (customer owes shop)
                    finalBalanceValue = abs(newPotentialJama)
                    finalBalanceType = "Baki"
                }
            }

            // Normalize if very close to zero, treat as settled (0.0)
            if (abs(finalBalanceValue) < 0.001) {
                finalBalanceValue = 0.0
                // When settled, we can default to Baki (or Jama consistently, Baki is fine)
                // If it was Jama and becomes 0, it could stay Jama 0.0, or become Baki 0.0.
                // For simplicity, if it's 0, let's ensure a consistent type.
                // If the calculation naturally resulted in 0.0 Baki from Jama, or 0.0 Jama from Baki, that's okay.
                // This primarily handles the type if it becomes exactly 0.
                if (finalBalanceValue == 0.0 && finalBalanceType == "Jama" && balanceChange > 0 && currentBalanceType == "Jama"){
                    // If it was Jama, and a purchase made it exactly 0, it's still technically Jama (fully utilized advance)
                    finalBalanceType = "Jama" // or "Baki" if you prefer Baki 0.0 as the universal "settled" state
                } else if (finalBalanceValue == 0.0 && finalBalanceType == "Baki" && balanceChange < 0 && currentBalanceType == "Baki") {
                    // If it was Baki, and a payment made it exactly 0
                    finalBalanceType = "Baki"
                }
                // If it flipped type and landed on 0, the determined finalBalanceType is already correct.
            }


            Log.d(TAG, "Customer $customerId: finalBalanceValue=$finalBalanceValue, finalBalanceType=$finalBalanceType")

            customerDocRef.update(
                mapOf(
                    "currentBalance" to finalBalanceValue,
                    "balanceType" to finalBalanceType,
                    "lastUpdatedAt" to System.currentTimeMillis() // Also update lastUpdatedAt
                )
            ).await()
            Log.d(TAG, "Updated customer $customerId balance: $currentBalanceValue ($currentBalanceType) -> $finalBalanceValue ($finalBalanceType)")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating customer balance for $customerId during save/update", e)
            // Decide if rethrowing is necessary or if logging is sufficient
        }
    }


    /**
     * Handles calculating and applying stock changes when an existing invoice is updated.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun handleInventoryStockChanges(oldInvoice: Invoice, newInvoice: Invoice) {
        try {
            // Determine if the customer is a wholesaler (affects stock direction)
            val customerDoc =
                getShopCollection("customers").document(newInvoice.customerId).get().await()
            val customer = customerDoc.toObject<Customer>()
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

            Log.d(TAG, "Handling inventory changes between invoices (Wholesaler: $isWholesaler)")

            // Create inventory repository
            val inventoryRepository = InventoryRepository(firestore, auth, context)

            // Process each item from both old and new invoices
            val allItemIds =
                (oldInvoice.items.map { it.itemId } + newInvoice.items.map { it.itemId }).distinct()

            for (itemId in allItemIds) {
                try {
                    // Get item details to determine inventory type
                    val itemDoc = getShopCollection("inventory").document(itemId).get().await()
                    if (!itemDoc.exists()) {
                        Log.e(TAG, "Item $itemId not found in inventory during invoice update")
                        continue
                    }

                    val item = itemDoc.toObject<JewelleryItem>()
                    if (item == null) {
                        Log.e(TAG, "Failed to parse inventory item $itemId")
                        continue
                    }

                    Log.d(
                        TAG,
                        "Processing inventory change for item $itemId (${item.displayName}), type=${item.inventoryType}"
                    )

                    // Find the items in old and new invoices
                    val oldInvoiceItem = oldInvoice.items.find { it.itemId == itemId }
                    val newInvoiceItem = newInvoice.items.find { it.itemId == itemId }

                    when (item.inventoryType) {
                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> {
                            // For weight-based items, compare the usedWeight values
                            val oldUsedWeight = oldInvoiceItem?.usedWeight ?: 0.0
                            val newUsedWeight = newInvoiceItem?.usedWeight ?: 0.0

                            // If usedWeight is not set, fall back to calculating from quantity and gross weight
                            val effectiveOldWeight = if (oldUsedWeight > 0.0) {
                                oldUsedWeight
                            } else {
                                val oldQuantity = oldInvoiceItem?.quantity ?: 0
                                val oldGrossWeight = oldInvoiceItem?.itemDetails?.grossWeight ?: 0.0
                                oldQuantity * oldGrossWeight
                            }

                            val effectiveNewWeight = if (newUsedWeight > 0.0) {
                                newUsedWeight
                            } else {
                                val newQuantity = newInvoiceItem?.quantity ?: 0
                                val newGrossWeight = newInvoiceItem?.itemDetails?.grossWeight ?: 0.0
                                newQuantity * newGrossWeight
                            }

                            val weightDifference = effectiveNewWeight - effectiveOldWeight

                            if (weightDifference != 0.0) {
                                // For weight-based items:
                                // If wholesaler: positive difference means ADD to stock (buying more)
                                // If consumer: positive difference means REMOVE from stock (selling more)
                                val effectiveWeightChange =
                                    if (isWholesaler) weightDifference else -weightDifference

                                Log.d(
                                    TAG,
                                    "Weight-based item change: oldWeight=${effectiveOldWeight}g, newWeight=${effectiveNewWeight}g, " +
                                            "diff=${weightDifference}g, effectiveChange=${effectiveWeightChange}g " +
                                            "(${if (effectiveWeightChange > 0) "increasing" else "decreasing"} stock)"
                                )

                                // Update the inventory
                                val result = inventoryRepository.updateInventoryStock(
                                    itemId,
                                    effectiveWeightChange
                                )
                                result.fold(
                                    onSuccess = {
                                        Log.d(
                                            TAG,
                                            "Successfully updated weight-based inventory for item $itemId"
                                        )
                                    },
                                    onFailure = { error ->
                                        Log.e(
                                            TAG,
                                            "Failed to update weight-based inventory for item $itemId: ${error.message}"
                                        )
                                    }
                                )
                            } else {
                                Log.d(
                                    TAG,
                                    "No weight change for item $itemId: old=${effectiveOldWeight}g, new=${effectiveNewWeight}g"
                                )
                            }
                        }

                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.IDENTICAL_BATCH -> {
                            // For quantity-based items, compare the quantities
                            val oldQuantity = oldInvoiceItem?.quantity ?: 0
                            val newQuantity = newInvoiceItem?.quantity ?: 0
                            val quantityDifference = newQuantity - oldQuantity

                            if (quantityDifference != 0) {
                                // For quantity-based items:
                                // If wholesaler: positive difference means ADD to stock (buying more)
                                // If consumer: positive difference means REMOVE from stock (selling more)
                                val effectiveQuantityChange =
                                    if (isWholesaler) quantityDifference.toDouble() else -quantityDifference.toDouble()

                                Log.d(
                                    TAG,
                                    "Quantity-based item change: oldQty=$oldQuantity, newQty=$newQuantity, " +
                                            "diff=$quantityDifference, effectiveChange=$effectiveQuantityChange " +
                                            "(${if (effectiveQuantityChange > 0) "increasing" else "decreasing"} stock)"
                                )

                                // Update the inventory
                                val result = inventoryRepository.updateInventoryStock(
                                    itemId,
                                    effectiveQuantityChange
                                )
                                result.fold(
                                    onSuccess = {
                                        Log.d(
                                            TAG,
                                            "Successfully updated quantity-based inventory for item $itemId"
                                        )
                                    },
                                    onFailure = { error ->
                                        Log.e(
                                            TAG,
                                            "Failed to update quantity-based inventory for item $itemId: ${error.message}"
                                        )
                                    }
                                )
                            } else {
                                Log.d(
                                    TAG,
                                    "No quantity change for item $itemId: old=$oldQuantity, new=$newQuantity"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Error processing inventory change for item $itemId: ${e.message}",
                        e
                    )
                    // Continue with other items
                }
            }

            Log.d(
                TAG,
                "Finished handling inventory stock changes for updated invoice ${newInvoice.id}"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error handling inventory stock changes for invoice ${newInvoice.id}: ${e.message}",
                e
            )
            // Decide on error handling: log, throw, etc.
        }
    }

    /**
     * Updates inventory stock when a completely new invoice is created.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun updateInventoryStockForNewInvoice(invoice: Invoice) {
        try {
            // Determine if the customer is a wholesaler
            val customerDoc =
                getShopCollection("customers").document(invoice.customerId).get().await()
            val customer = customerDoc.toObject<Customer>()
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)

            Log.d(
                TAG,
                "Updating inventory for NEW invoice ${invoice.id} (Wholesaler: $isWholesaler)"
            )

            // Create inventory repository
            val inventoryRepository = InventoryRepository(firestore, auth, context)

            // Iterate through items and update stock individually
            invoice.items.forEach { invoiceItem ->
                try {
                    // Get item details to determine inventory type
                    val itemDoc =
                        getShopCollection("inventory").document(invoiceItem.itemId).get().await()
                    if (!itemDoc.exists()) {
                        Log.e(
                            TAG,
                            "Item ${invoiceItem.itemId} not found in inventory during new invoice processing"
                        )
                        return@forEach
                    }

                    val item = itemDoc.toObject<JewelleryItem>()
                    if (item == null) {
                        Log.e(TAG, "Failed to parse inventory item ${invoiceItem.itemId}")
                        return@forEach
                    }

                    Log.d(
                        TAG,
                        "Processing new invoice item: id=${invoiceItem.itemId}, name=${item.displayName}, " +
                                "type=${item.inventoryType}, quantity=${invoiceItem.quantity}, usedWeight=${invoiceItem.usedWeight}"
                    )

                    when (item.inventoryType) {
                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> {
                            // For weight-based items, use the actual weight from the invoice
                            val usedWeight = invoiceItem.usedWeight

                            // If usedWeight is not explicitly set, calculate from quantity and gross weight
                            val effectiveWeight = if (usedWeight > 0.0) {
                                usedWeight
                            } else {
                                val grossWeight = invoiceItem.itemDetails.grossWeight
                                if (grossWeight <= 0.0) {
                                    Log.e(
                                        TAG,
                                        "Invalid gross weight (${grossWeight}g) for weight-based item ${invoiceItem.itemId}"
                                    )
                                    return@forEach
                                }
                                grossWeight * invoiceItem.quantity
                            }

                            // For weight-based items:
                            // If wholesaler: positive effectiveWeight means ADD to stock (buying from them)
                            // If consumer: positive effectiveWeight means REMOVE from stock (selling to them)
                            val effectiveWeightChange =
                                if (isWholesaler) effectiveWeight else -effectiveWeight

                            Log.d(
                                TAG,
                                "Weight-based item: usedWeight=${usedWeight}g, effectiveWeight=${effectiveWeight}g, " +
                                        "change=${effectiveWeightChange}g (${if (effectiveWeightChange > 0) "increasing" else "decreasing"} stock)"
                            )

                            // Update the inventory
                            val result = inventoryRepository.updateInventoryStock(
                                invoiceItem.itemId,
                                effectiveWeightChange
                            )
                            result.fold(
                                onSuccess = {
                                    Log.d(
                                        TAG,
                                        "Successfully updated weight-based inventory for new invoice item ${invoiceItem.itemId}"
                                    )
                                },
                                onFailure = { error ->
                                    Log.e(
                                        TAG,
                                        "Failed to update weight-based inventory for new invoice item ${invoiceItem.itemId}: ${error.message}"
                                    )
                                }
                            )
                        }

                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.IDENTICAL_BATCH -> {
                            // For quantity-based items:
                            // If wholesaler: positive quantity means ADD to stock (buying from them)
                            // If consumer: positive quantity means REMOVE from stock (selling to them)
                            val effectiveQuantityChange =
                                if (isWholesaler) invoiceItem.quantity.toDouble() else -invoiceItem.quantity.toDouble()

                            Log.d(
                                TAG, "Quantity-based item: quantity=${invoiceItem.quantity}, " +
                                        "change=${effectiveQuantityChange} (${if (effectiveQuantityChange > 0) "increasing" else "decreasing"} stock)"
                            )

                            // Update the inventory
                            val result = inventoryRepository.updateInventoryStock(
                                invoiceItem.itemId,
                                effectiveQuantityChange
                            )
                            result.fold(
                                onSuccess = {
                                    Log.d(
                                        TAG,
                                        "Successfully updated quantity-based inventory for new invoice item ${invoiceItem.itemId}"
                                    )
                                },
                                onFailure = { error ->
                                    Log.e(
                                        TAG,
                                        "Failed to update quantity-based inventory for new invoice item ${invoiceItem.itemId}: ${error.message}"
                                    )
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Error processing inventory for new invoice item ${invoiceItem.itemId}: ${e.message}",
                        e
                    )
                    // Continue with other items
                }
            }

            Log.d(TAG, "Finished updating inventory for NEW invoice ${invoice.id}")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Error determining customer type or updating stock for new invoice ${invoice.id}: ${e.message}",
                e
            )
            // Decide on error handling
        }
    }


    /**
     * Deletes an invoice, ensuring Firestore operations run on the IO dispatcher.
     * Also reverts customer balance and inventory stock changes.
     */
    suspend fun deleteInvoice(invoiceNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting to delete invoice: $invoiceNumber")
        try {
            // 1. Get the invoice to be deleted
            val invoiceRef = getShopCollection("invoices").document(invoiceNumber)
            val invoiceDoc = invoiceRef.get().await()
            val invoice = invoiceDoc.toObject<Invoice>()
                ?: return@withContext Result.failure(Exception("Invoice $invoiceNumber not found"))

            Log.d(TAG, "Found invoice to delete: ${invoice.invoiceNumber}")

            // 2. Get customer details for type checking
            val customerDoc = getShopCollection("customers").document(invoice.customerId).get().await()
            val customer = customerDoc.toObject<Customer>()
            val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)
            Log.d(TAG, "Customer type for deletion: ${customer?.customerType ?: "Unknown"} (Wholesaler: $isWholesaler)")

            // 3. Revert inventory changes
            Log.d(TAG, "Reverting inventory changes")
            updateInventoryOnDeletion(invoice, isWholesaler)

            // 4. Revert customer balance change
            if (invoice.customerId.isNotEmpty() && customer != null) {
                Log.d(TAG, "Reverting customer balance changes")
                updateCustomerBalanceOnDeletion(invoice, customer, isWholesaler)
            } else {
                Log.w(TAG, "Customer not found or ID missing for balance reversion: ${invoice.customerId}")
            }

            // 5. Delete the actual invoice document
            Log.d(TAG, "Deleting invoice document from Firestore")
            invoiceRef.delete().await()

            Log.d(TAG, "Invoice deletion completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting invoice $invoiceNumber", e)
            Result.failure(e)
        }
    }

    /**
     * Reverts inventory stock changes when an invoice is deleted.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun updateInventoryOnDeletion(invoice: Invoice, isWholesaler: Boolean) {
        Log.d(
            TAG,
            "Reverting inventory for deleted invoice ${invoice.id}, items=${invoice.items.size} (Wholesaler: $isWholesaler)"
        )

        // Create InventoryRepository instance
        val inventoryRepository = InventoryRepository(firestore, auth, context)

        // For each item in the invoice
        invoice.items.forEach { invoiceItem ->
            try {
                Log.d(
                    TAG,
                    "Processing deletion reversion for invoice item: itemId=${invoiceItem.itemId}, " +
                            "quantity=${invoiceItem.quantity}, usedWeight=${invoiceItem.usedWeight}"
                )

                // Get the current item details to determine its type
                val inventoryRef = getShopCollection("inventory").document(invoiceItem.itemId)
                val inventoryItemDoc = inventoryRef.get().await()

                if (!inventoryItemDoc.exists()) {
                    Log.e(
                        TAG,
                        "Item ${invoiceItem.itemId} not found in inventory during invoice deletion reversion"
                    )
                    return@forEach
                }

                val currentItem = inventoryItemDoc.toObject<JewelleryItem>()

                currentItem?.let {
                    Log.d(
                        TAG,
                        "Found item for reversion: ${it.displayName}, type=${it.inventoryType}, " +
                                "stock=${it.stock}${it.stockUnit}, grossWeight=${it.grossWeight}g, totalWeightGrams=${it.totalWeightGrams}g"
                    )

                    // Reverting means applying the *opposite* quantity/weight change
                    when (it.inventoryType) {
                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.BULK_STOCK -> {
                            // For weight-based items, use the actual weight from the invoice
                            val usedWeight = invoiceItem.usedWeight

                            // If usedWeight is not explicitly set, calculate from quantity and gross weight
                            val effectiveWeight = if (usedWeight > 0.0) {
                                usedWeight
                            } else {
                                val grossWeight = invoiceItem.itemDetails.grossWeight
                                if (grossWeight <= 0.0) {
                                    Log.e(
                                        TAG,
                                        "Invalid gross weight (${grossWeight}g) for weight-based item ${invoiceItem.itemId}"
                                    )
                                    return@let
                                }
                                grossWeight * invoiceItem.quantity
                            }

                            // When reverting, we do the OPPOSITE of what we would do for a new invoice
                            // If consumer: we ADD the weight back (items return to inventory)
                            // If wholesaler: we SUBTRACT the weight (items no longer purchased)
                            val effectiveWeightChange =
                                if (isWholesaler) -effectiveWeight else effectiveWeight

                            Log.d(
                                TAG,
                                "Reverting weight: usedWeight=${usedWeight}g, effectiveWeight=${effectiveWeight}g, " +
                                        "reversion=${effectiveWeightChange}g (${if (effectiveWeightChange > 0) "increasing" else "decreasing"} stock)"
                            )

                            val result = inventoryRepository.updateInventoryStock(
                                invoiceItem.itemId,
                                effectiveWeightChange
                            )
                            result.fold(
                                onSuccess = {
                                    Log.d(
                                        TAG,
                                        "Successfully reverted weight-based stock for deleted invoice item ${invoiceItem.itemId}"
                                    )
                                },
                                onFailure = { error ->
                                    Log.e(
                                        TAG,
                                        "Failed to revert weight-based stock for deleted invoice item ${invoiceItem.itemId}: ${error.message}"
                                    )
                                }
                            )
                        }

                        com.jewelrypos.swarnakhatabook.Enums.InventoryType.IDENTICAL_BATCH -> {
                            // For quantity-based items, we need to revert the quantity change
                            // When reverting, we do the OPPOSITE of what we would do for a new invoice
                            // If consumer: we ADD the quantity back (items return to inventory)
                            // If wholesaler: we SUBTRACT the quantity (items no longer purchased)
                            val effectiveQuantityChange =
                                if (isWholesaler) -invoiceItem.quantity.toDouble() else invoiceItem.quantity.toDouble()

                            Log.d(
                                TAG, "Reverting quantity: quantity=${invoiceItem.quantity}, " +
                                        "reversion=${effectiveQuantityChange} (${if (effectiveQuantityChange > 0) "increasing" else "decreasing"} stock)"
                            )

                            val result = inventoryRepository.updateInventoryStock(
                                invoiceItem.itemId,
                                effectiveQuantityChange
                            )
                            result.fold(
                                onSuccess = {
                                    Log.d(
                                        TAG,
                                        "Successfully reverted quantity-based stock for deleted invoice item ${invoiceItem.itemId}"
                                    )
                                },
                                onFailure = { error ->
                                    Log.e(
                                        TAG,
                                        "Failed to revert quantity-based stock for deleted invoice item ${invoiceItem.itemId}: ${error.message}"
                                    )
                                }
                            )
                        }
                    }
                } ?: Log.e(
                    TAG,
                    "Failed to parse inventory item ${invoiceItem.itemId} during invoice deletion reversion"
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error reverting inventory for deleted invoice item ${invoiceItem.itemId}: ${e.message}",
                    e
                )
                // Continue with other items even if one fails
            }
        }
    }

    /**
     * Reverts customer balance changes when an invoice is deleted.
     * Assumes it's called within a withContext(Dispatchers.IO) block.
     */
    private suspend fun updateCustomerBalanceOnDeletion(
        invoice: Invoice,
        customer: Customer, // Pass the fetched Customer object with its current balance state
        isWholesaler: Boolean // isWholesaler might not be directly needed here if logic is self-contained
    ) {
        try {
            // The amount that was unpaid for this specific invoice.
            // This is the value that contributed to the balanceChange when the invoice was saved.
            val invoiceUnpaidAmount = invoice.totalAmount - invoice.paidAmount

            // To revert, we apply the negative of this invoice's impact.
            // This 'balanceChangeForReversion' will be fed into the same core logic
            // as updateCustomerBalanceForSave.
            val balanceChangeForReversion = -invoiceUnpaidAmount

            Log.d(TAG, "Reverting balance for customer ${invoice.customerId} due to invoice ${invoice.id} deletion.")
            Log.d(TAG, "Invoice unpaid amount was: $invoiceUnpaidAmount, so balanceChangeForReversion is: $balanceChangeForReversion")

            // Fetch the most up-to-date customer document to avoid stale data issues,
            // though 'customer' param should ideally be fresh. Re-fetching adds robustness.
            val customerDocRef = getShopCollection("customers").document(invoice.customerId)
            val customerDocSnapshot = customerDocRef.get().await()
            if (!customerDocSnapshot.exists()) {
                Log.w(TAG, "Customer ${invoice.customerId} not found for balance reversion during deletion.")
                return
            }

            val currentBalanceValue = customerDocSnapshot.getDouble("currentBalance") ?: 0.0
            val currentBalanceType = customerDocSnapshot.getString("balanceType") ?: "Baki"

            var finalRevertedBalanceValue: Double
            var finalRevertedBalanceType: String

            Log.d(TAG, "Customer ${invoice.customerId} before reversion: currentBalance=$currentBalanceValue, currentType=$currentBalanceType")

            // Apply the balanceChangeForReversion using the standard logic
            if (currentBalanceType == "Baki") {
                val newPotentialBaki = currentBalanceValue + balanceChangeForReversion
                if (newPotentialBaki >= 0) {
                    finalRevertedBalanceValue = newPotentialBaki
                    finalRevertedBalanceType = "Baki"
                } else {
                    finalRevertedBalanceValue = abs(newPotentialBaki)
                    finalRevertedBalanceType = "Jama"
                }
            } else { // currentBalanceType == "Jama"
                val newPotentialJama = currentBalanceValue - balanceChangeForReversion
                if (newPotentialJama >= 0) {
                    finalRevertedBalanceValue = newPotentialJama
                    finalRevertedBalanceType = "Jama"
                } else {
                    finalRevertedBalanceValue = abs(newPotentialJama)
                    finalRevertedBalanceType = "Baki"
                }
            }

            if (abs(finalRevertedBalanceValue) < 0.001) {
                finalRevertedBalanceValue = 0.0
                // Consistent settled type handling as in save
                if (finalRevertedBalanceValue == 0.0 && finalRevertedBalanceType == "Jama" && balanceChangeForReversion > 0 && currentBalanceType == "Jama"){
                    finalRevertedBalanceType = "Jama"
                } else if (finalRevertedBalanceValue == 0.0 && finalRevertedBalanceType == "Baki" && balanceChangeForReversion < 0 && currentBalanceType == "Baki") {
                    finalRevertedBalanceType = "Baki"
                }
            }

            Log.d(TAG, "Customer ${invoice.customerId} after reversion: finalBalanceValue=$finalRevertedBalanceValue, finalBalanceType=$finalRevertedBalanceType")

            customerDocRef.update(
                mapOf(
                    "currentBalance" to finalRevertedBalanceValue,
                    "balanceType" to finalRevertedBalanceType,
                    "lastUpdatedAt" to System.currentTimeMillis()
                )
            ).await()
            Log.d(TAG, "Reverted customer balance for ${invoice.customerId} due to invoice deletion: newBalance=$finalRevertedBalanceValue ($finalRevertedBalanceType)")

        } catch (e: Exception) {
            Log.e(TAG, "Error reverting customer balance for ${invoice.customerId} during invoice deletion", e)
        }
    }

    /**
     * Moves an invoice to the recycling bin instead of permanently deleting it.
     * This replaces the original deleteInvoice method.
     */
    suspend fun moveInvoiceToRecycleBin(invoiceNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting to move invoice to recycling bin: $invoiceNumber")
        try {
            // First, get the invoice to be "deleted"
            val invoiceRef = getShopCollection("invoices").document(invoiceNumber)
            val invoiceDoc = invoiceRef.get().await()

            if (!invoiceDoc.exists()) {
                Log.e(TAG, "Invoice $invoiceNumber not found for recycling")
                return@withContext Result.failure(Exception("Invoice $invoiceNumber not found"))
            }

            val invoice = invoiceDoc.toObject<Invoice>()
                ?: return@withContext Result.failure(Exception("Failed to convert document to Invoice"))

            Log.d(TAG, "Found invoice to recycle: ${invoice.invoiceNumber}")

            // Create a RecycledItemsRepository to handle the recycling bin operation
            val recycledItemsRepository = RecycledItemsRepository(firestore, auth, context)

            // Move to recycling bin
            Log.d(TAG, "Moving invoice to recycling bin")
            val recycleResult = recycledItemsRepository.moveInvoiceToRecycleBin(invoice)

            // If successful, proceed with all the usual reversion of inventory and customer balance changes
            if (recycleResult.isSuccess) {
                // Get customer details for type checking
                val customerDoc = getShopCollection("customers").document(invoice.customerId).get().await()
                val customer = customerDoc.toObject<Customer>()
                val isWholesaler = customer?.customerType.equals("Wholesaler", ignoreCase = true)
                Log.d(TAG, "Customer type for recycling: ${customer?.customerType ?: "Unknown"} (Wholesaler: $isWholesaler)")

                // Revert inventory changes
                Log.d(TAG, "Reverting inventory changes")
                updateInventoryOnDeletion(invoice, isWholesaler)

                // Revert customer balance change
                if (invoice.customerId.isNotEmpty() && customer != null) {
                    Log.d(TAG, "Reverting customer balance changes")
                    updateCustomerBalanceOnDeletion(invoice, customer, isWholesaler)
                }

                // Delete the invoice from active invoices
                Log.d(TAG, "Deleting invoice from active invoices")
                invoiceRef.delete().await()

                Log.d(TAG, "Invoice recycling completed successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to recycle invoice: ${recycleResult.exceptionOrNull()?.message}")
                Result.failure(recycleResult.exceptionOrNull() ?: Exception("Unknown error recycling invoice"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error moving invoice to recycling bin: ${e.message}", e)
            Result.failure(e)
        }
    }


    /**
     * Fetches invoices with pagination, ensuring Firestore operations run on the IO dispatcher.
     */
    suspend fun fetchInvoicesPaginated(
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<Invoice>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching invoices paginated (loadNextPage: $loadNextPage, source: $source)")
        
        // Reset pagination state if needed
        if (!loadNextPage) {
            lastDocumentSnapshot = null
            isLastPage = false
            Log.d(TAG, "Pagination reset for fetching first page")
        }
        if (isLastPage) {
            Log.d(TAG, "Already on the last page, returning empty list")
            return@withContext Result.success(emptyList())
        }

        try {
            // Build query with pagination
            var query = getShopCollection("invoices")
                .orderBy("invoiceDate", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())

            // Add startAfter for pagination if needed
            if (loadNextPage && lastDocumentSnapshot != null) {
                Log.d(TAG, "Starting after document: ${lastDocumentSnapshot?.id}")
                query = query.startAfter(lastDocumentSnapshot)
            }

            // Get data with specified source
            Log.d(TAG, "Executing Firestore query")
            val queryStartTime = System.currentTimeMillis()
            val snapshot = query.get(source).await()
            val queryEndTime = System.currentTimeMillis()
            Log.d(TAG, "Firestore query executed in ${queryEndTime - queryStartTime}ms. Documents fetched: ${snapshot.documents.size}")

            // Update pagination state
            if (snapshot.documents.size < PAGE_SIZE) {
                isLastPage = true
                Log.d(TAG, "Reached last page with ${snapshot.documents.size} documents")
            }

            // Save last document for next page query
            if (snapshot.documents.isNotEmpty()) {
                lastDocumentSnapshot = snapshot.documents.last()
                Log.d(TAG, "Last document ID for pagination: ${lastDocumentSnapshot?.id}")
            }

            // Convert to invoice objects
            val conversionStartTime = System.currentTimeMillis()
            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Invoice::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document to Invoice: ${e.message}")
                    null
                }
            }
            val conversionEndTime = System.currentTimeMillis()
            Log.d(TAG, "Document conversion completed in ${conversionEndTime - conversionStartTime}ms")

            Log.d(TAG, "Successfully fetched ${invoices.size} invoices")
            Result.success(invoices)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching invoices: ${e.message}")

            // Try from server if cache source fails
            if (source == Source.CACHE) {
                Log.d(TAG, "Retrying fetch from server")
                return@withContext fetchInvoicesPaginated(loadNextPage, Source.SERVER)
            }

            Result.failure(e)
        }
    }

    /**
     * Fetches invoices for a specific customer with pagination, ensuring Firestore operations run on the IO dispatcher.
     */
    suspend fun fetchInvoicesForCustomerPaginated(
        customerId: String,
        loadNextPage: Boolean = false,
        source: Source = Source.DEFAULT
    ): Result<List<Invoice>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching invoices for customer $customerId paginated (loadNextPage: $loadNextPage, source: $source)")

        if (customerId.isBlank()) {
            Log.e(TAG, "Customer ID cannot be blank for fetching customer invoices.")
            return@withContext Result.failure(IllegalArgumentException("Customer ID cannot be blank"))
        }
        
        // Reset pagination state if needed for this specific customer query
        if (!loadNextPage) {
            lastCustomerInvoiceDocumentSnapshot = null
            isLastCustomerInvoicePage = false
            Log.d(TAG, "Pagination reset for fetching first page for customer $customerId")
        }
        if (isLastCustomerInvoicePage) {
            Log.d(TAG, "Already on the last page for customer $customerId, returning empty list")
            return@withContext Result.success(emptyList())
        }

        try {
            // Build query with customer ID and pagination
            var query = getShopCollection("invoices")
                .whereEqualTo("customerId", customerId) // Filter by customerId
                .orderBy("invoiceDate", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())

            // Add startAfter for pagination if needed
            if (loadNextPage && lastCustomerInvoiceDocumentSnapshot != null) {
                Log.d(TAG, "Starting after document: ${lastCustomerInvoiceDocumentSnapshot?.id} for customer $customerId")
                query = query.startAfter(lastCustomerInvoiceDocumentSnapshot)
            }

            // Get data with specified source
            Log.d(TAG, "Executing Firestore query for customer $customerId")
            val queryStartTime = System.currentTimeMillis()
            val snapshot = query.get(source).await()
            val queryEndTime = System.currentTimeMillis()
            Log.d(TAG, "Firestore query for customer $customerId executed in ${queryEndTime - queryStartTime}ms. Documents fetched: ${snapshot.documents.size}")

            // Update pagination state for customer invoices
            if (snapshot.documents.size < PAGE_SIZE) {
                isLastCustomerInvoicePage = true
                Log.d(TAG, "Reached last page for customer $customerId with ${snapshot.documents.size} documents")
            }

            // Save last document for next page query
            if (snapshot.documents.isNotEmpty()) {
                lastCustomerInvoiceDocumentSnapshot = snapshot.documents.last()
                Log.d(TAG, "Last document ID for pagination for customer $customerId: ${lastCustomerInvoiceDocumentSnapshot?.id}")
            }

            // Convert to invoice objects
            val conversionStartTime = System.currentTimeMillis()
            val invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Invoice::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document to Invoice for customer $customerId: ${e.message}")
                    null
                }
            }
            val conversionEndTime = System.currentTimeMillis()
            Log.d(TAG, "Document conversion for customer $customerId completed in ${conversionEndTime - conversionStartTime}ms")

            Log.d(TAG, "Successfully fetched ${invoices.size} invoices for customer $customerId")
            Result.success(invoices)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching invoices for customer $customerId: ${e.message}")

            // Try from server if cache source fails
            if (source == Source.CACHE) {
                Log.d(TAG, "Retrying fetch from server for customer $customerId")
                return@withContext fetchInvoicesForCustomerPaginated(customerId, loadNextPage, Source.SERVER)
            }

            Result.failure(e)
        }
    }

    /**
     * Gets a single invoice by its number (ID), running on the IO dispatcher.
     */
    suspend fun getInvoiceByNumber(invoiceNumber: String): Result<Invoice> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting invoice by number: $invoiceNumber")
        if (invoiceNumber.isBlank()) {
            Log.e(TAG, "Invoice number cannot be blank")
            return@withContext Result.failure(IllegalArgumentException("Invoice number cannot be blank"))
        }
        try {
            Log.d(TAG, "Fetching invoice document from Firestore")
            val document = getShopCollection("invoices")
                .document(invoiceNumber)
                .get()
                .await()

            if (document.exists()) {
                Log.d(TAG, "Invoice found, converting to object")
                document.toObject<Invoice>()
                    ?.let { 
                        Log.d(TAG, "Successfully retrieved invoice: $invoiceNumber")
                        Result.success(it) 
                    }
                    ?: Result.failure(Exception("Failed to convert document to Invoice object for $invoiceNumber"))
            } else {
                Log.e(TAG, "Invoice not found: $invoiceNumber")
                Result.failure(Exception("Invoice $invoiceNumber not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting invoice by number $invoiceNumber", e)
            Result.failure(e)
        }
    }

    suspend fun getInvoicesBetweenDates(
        startDate: Date,
        endDate: Date,
        customerId: String? = null
    ): List<Invoice> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting invoices between dates: $startDate to $endDate" +
                (if (customerId != null) " for customer ID: $customerId" else ""))
        try {
            // Get the time in milliseconds for comparison
            val startTime = startDate.time
            // Adjust end time to include the whole day
            val calendar = Calendar.getInstance()
            calendar.time = endDate
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endTime = calendar.timeInMillis

            Log.d(TAG, "Adjusted time range: ${Date(startTime)} to ${Date(endTime)}")

            // Updated to use shop collection
            Log.d(TAG, "Executing Firestore query for date range")
            var query = getShopCollection("invoices")
                .whereGreaterThanOrEqualTo("invoiceDate", startTime)
                .whereLessThanOrEqualTo("invoiceDate", endTime)

            if (customerId != null) {
                Log.d(TAG, "Adding customer ID filter to query: $customerId")
                query = query.whereEqualTo("customerId", customerId)
            }

            val snapshot = query.get()
                .await()

            val invoices = snapshot.toObjects(Invoice::class.java)
            Log.d(TAG, "Found ${invoices.size} invoices in date range" +
                    (if (customerId != null) " for customer ID: $customerId" else ""))
            return@withContext invoices
        } catch (e: Exception) {
            Log.e(TAG, "Error getting invoices between dates", e)
            return@withContext emptyList<Invoice>()
        }
    }



    /**
     * Fetches invoices with pagination, ensuring Firestore operations run on the IO dispatcher.
     */

    suspend fun getInvoices(
        page: Int,
        pageSize: Int,
        dateFilter: com.jewelrypos.swarnakhatabook.Enums.DateFilterType,
        statusFilter: com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter,
        searchQuery: String
    ): List<Invoice> {
        try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            val shopId = getCurrentShopId()

            Log.d(TAG, "Fetching invoices - Page: $page, Size: $pageSize, ShopId: $shopId, Query: '$searchQuery'")

            // Start with base query
            var queryBuilder = getShopCollection("invoices")
                .orderBy("invoiceDate", Query.Direction.DESCENDING)

            // Apply date filter
            queryBuilder = applyDateFilter(queryBuilder, dateFilter)
            Log.d(TAG, "Applied date filter: $dateFilter")

            // Apply status filter
            queryBuilder = applyStatusFilter(queryBuilder, statusFilter)
            Log.d(TAG, "Applied status filter: $statusFilter")

            val normalizedSearchQuery = searchQuery.trim().lowercase(Locale.ROOT)

            // Apply search query if present
            if (normalizedSearchQuery.isNotEmpty()) {
                // Use the first word for Firestore query
                val firstWord = normalizedSearchQuery.split(" ").first()
                queryBuilder = queryBuilder.whereArrayContains("keywords", firstWord)
                Log.d(TAG, "Applied Firestore search using first word: $firstWord")
            }

            // Apply pagination
            queryBuilder = queryBuilder.limit(pageSize.toLong())

            // If not first page, get the last document from previous page
            if (page > 0) {
                // Get the last document from the previous page
                val lastDocSnapshot = queryBuilder.get().await().documents.lastOrNull()
                if (lastDocSnapshot != null) {
                    queryBuilder = queryBuilder.startAfter(lastDocSnapshot)
                    Log.d(TAG, "Starting after document: ${lastDocSnapshot.id}")
                }
            }

            // Execute query
            val snapshot = queryBuilder.get().await()
            var invoices = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(Invoice::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document ${doc.id} to Invoice", e)
                    null
                }
            }

            Log.d(TAG, "Fetched ${invoices.size} invoices from Firestore before client-side filtering")

            // Apply additional client-side filtering for multi-word searches
            if (normalizedSearchQuery.isNotEmpty() && invoices.isNotEmpty()) {
                val searchWords = normalizedSearchQuery.split(" ").filter { it.isNotEmpty() }
                if (searchWords.size > 1) {
                    invoices = invoices.filter { invoice ->
                        // Check if all search words are present in any of the invoice's keywords
                        searchWords.all { word ->
                            invoice.keywords.any { keyword ->
                                keyword.contains(word)
                            }
                        }
                    }
                    Log.d(TAG, "Applied client-side filtering for multi-word search. Result count: ${invoices.size}")
                }
            }

            Log.d(TAG, "Returning ${invoices.size} invoices for page $page after all filtering")
            return invoices

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching invoices for page $page: ${e.message}", e)
            throw Exception("Failed to fetch invoices: ${e.message}")
        }
    }
    private fun applyDateFilter(
        query: Query,
        dateFilter: com.jewelrypos.swarnakhatabook.Enums.DateFilterType
    ): Query {
        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        return when (dateFilter) {
            com.jewelrypos.swarnakhatabook.Enums.DateFilterType.TODAY -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                query.whereGreaterThanOrEqualTo("invoiceDate", startOfDay)
            }

            com.jewelrypos.swarnakhatabook.Enums.DateFilterType.YESTERDAY -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfYesterday = calendar.timeInMillis
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfYesterday = calendar.timeInMillis
                query.whereGreaterThanOrEqualTo("invoiceDate", startOfYesterday)
                    .whereLessThanOrEqualTo("invoiceDate", endOfYesterday)
            }

            com.jewelrypos.swarnakhatabook.Enums.DateFilterType.THIS_WEEK -> {
                calendar.timeInMillis = now
                // Set to start of current week (Sunday)
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfWeek = calendar.timeInMillis

                // Set to end of current week (Saturday)
                calendar.add(Calendar.DAY_OF_WEEK, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfWeek = calendar.timeInMillis

                Log.d(
                    TAG,
                    "THIS_WEEK filter - Start: ${Date(startOfWeek)}, End: ${Date(endOfWeek)}"
                )

                query.whereGreaterThanOrEqualTo("invoiceDate", startOfWeek)
                    .whereLessThanOrEqualTo("invoiceDate", endOfWeek)
            }

            com.jewelrypos.swarnakhatabook.Enums.DateFilterType.THIS_MONTH -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis
                query.whereGreaterThanOrEqualTo("invoiceDate", startOfMonth)
            }

            com.jewelrypos.swarnakhatabook.Enums.DateFilterType.LAST_MONTH -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val endOfLastMonth = calendar.timeInMillis - 1
                calendar.add(Calendar.MONTH, -1)
                val startOfLastMonth = calendar.timeInMillis
                query.whereGreaterThanOrEqualTo("invoiceDate", startOfLastMonth)
                    .whereLessThanOrEqualTo("invoiceDate", endOfLastMonth)
            }

            com.jewelrypos.swarnakhatabook.Enums.DateFilterType.THIS_QUARTER -> {
                calendar.timeInMillis = now
                val currentMonth = calendar.get(Calendar.MONTH)
                val firstMonthOfQuarter = (currentMonth / 3) * 3
                calendar.set(Calendar.MONTH, firstMonthOfQuarter)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfQuarter = calendar.timeInMillis
                query.whereGreaterThanOrEqualTo("invoiceDate", startOfQuarter)
            }

            com.jewelrypos.swarnakhatabook.Enums.DateFilterType.THIS_YEAR -> {
                calendar.timeInMillis = now
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfYear = calendar.timeInMillis
                query.whereGreaterThanOrEqualTo("invoiceDate", startOfYear)
            }

            com.jewelrypos.swarnakhatabook.Enums.DateFilterType.ALL_TIME -> query
        }
    }

    private fun applyStatusFilter(
        query: Query,
        statusFilter: com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter
    ): Query {
        return when (statusFilter) {
            com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter.PAID -> {
                // For paid invoices, paidAmount equals totalAmount
                query.whereEqualTo("paymentStatus", "PAID")
            }

            com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter.PARTIAL -> {
                // For partial payments, paidAmount is greater than 0 but less than totalAmount
                query.whereEqualTo("paymentStatus", "PARTIAL")
            }

            com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter.UNPAID -> {
                // For unpaid invoices, paidAmount is 0
                query.whereEqualTo("paymentStatus", "UNPAID")
            }

            com.jewelrypos.swarnakhatabook.Enums.PaymentStatusFilter.ALL -> query
        }
    }

    fun getAllInvoicesForShop(): Flow<PagingData<Invoice>> {
        val shopId = SessionManager.getActiveShopId(context)

        if (shopId == null) {
            return flowOf(PagingData.empty())
        }

        val query = firestore.collection("shopData")
            .document(shopId)
            .collection("invoices")
            .orderBy("invoiceDate", com.google.firebase.firestore.Query.Direction.DESCENDING)

        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                InvoicePagingSource(query, firestore)
            }
        ).flow
    }

    // For dashboard calculations where we need the full list
    suspend fun getAllInvoicesListForShop(): List<Invoice> {
        val shopId = SessionManager.getActiveShopId(context)
        Log.d("DashBoardFragment", "Fetching all invoices for shop: $shopId")
        if (shopId == null) {
            return emptyList()
        }

        return try {
            val snapshot = firestore.collection("shopData")
                .document(shopId)
                .collection("invoices")
                .orderBy("invoiceDate", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { document ->
                try {
                    document.toObject(Invoice::class.java)?.apply {
                        id = document.id
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document to Invoice: ${e.message}")
                    null
                }
            }.also { invoices ->
                Log.d(TAG, "Successfully fetched ${invoices.size} invoices for sales insights")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching invoices for sales insights: ${e.message}")
            emptyList()
        }
    }

    suspend fun getTaxableInvoicesBetweenDates(
        startDate: Date,
        endDate: Date
    ): Result<List<Invoice>> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching taxable invoices between dates: $startDate to $endDate")
        try {
            val shopId = getCurrentShopId() // Ensure shopId is valid

            // Adjust end date to include the whole day (as in getInvoicesBetweenDates)
            val calendar = Calendar.getInstance()
            calendar.time = endDate
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val adjustedEndTime = calendar.timeInMillis

            val startTimestamp = startDate.time
            Log.d(TAG, "Adjusted time range for query: ${Date(startTimestamp)} to ${Date(adjustedEndTime)}")

            // --- Build the Firestore Query ---
            // This query will filter by date range AND the new hasTaxableItems field.
            var query = getShopCollection("invoices")
                .whereGreaterThanOrEqualTo("invoiceDate", startTimestamp)
                .whereLessThanOrEqualTo("invoiceDate", adjustedEndTime)
                .whereEqualTo("hasTaxableItems", true) // Crucial filter for GST report
                .orderBy("invoiceDate", Query.Direction.ASCENDING) // Order is important for consistent results and potential future pagination

            val allTaxableInvoices = mutableListOf<Invoice>()
            var lastDocSnapshot: DocumentSnapshot? = null
            var hasMore = true

            // Implement internal pagination to fetch all results if the query returns many documents
            while (hasMore) {
                var currentQuery = query
                if (lastDocSnapshot != null) {
                    currentQuery = currentQuery.startAfter(lastDocSnapshot)
                    Log.d(TAG, "Continuing query after document: ${lastDocSnapshot.id}")
                }
                
                val snapshot = currentQuery.limit(PAGE_SIZE.toLong()).get().await()
                
                if (snapshot.documents.isEmpty()) {
                    hasMore = false
                    Log.d(TAG, "No more documents found for the query.")
                } else {
                    val pageInvoices = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(Invoice::class.java)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document to Invoice: ${e.message}")
                            null
                        }
                    }
                    allTaxableInvoices.addAll(pageInvoices)
                    lastDocSnapshot = snapshot.documents.last()
                    Log.d(TAG, "Fetched ${pageInvoices.size} invoices. Total so far: ${allTaxableInvoices.size}")

                    if (snapshot.documents.size < PAGE_SIZE) {
                        hasMore = false
                        Log.d(TAG, "Fetched last page for this query.")
                    }
                }
            }

            Log.d(TAG, "Successfully fetched total of ${allTaxableInvoices.size} taxable invoices for GST report.")
            Result.success(allTaxableInvoices)

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching taxable invoices between dates: ${e.message}", e)
            Result.failure(e)
        }
    }
}