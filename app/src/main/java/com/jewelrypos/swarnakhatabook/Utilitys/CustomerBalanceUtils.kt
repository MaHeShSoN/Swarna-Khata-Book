package com.jewelrypos.swarnakhatabook.Utilitys

import android.util.Log
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.DataClasses.Invoice

/**
 * Utility class for handling customer balance calculations and operations
 * Centralizes all balance logic to ensure consistency across the application
 */
object CustomerBalanceUtils {
    private const val TAG = "CustomerBalanceUtils"

    /**
     * Calculates the invoice impact on customer balance
     *
     * @param invoice The invoice to calculate impact for
     * @return The balance impact amount (positive means increase in balance)
     */
    fun calculateInvoiceBalanceImpact(invoice: Invoice): Double {
        return invoice.totalAmount - invoice.paidAmount
    }

    /**
     * Calculates the balance change needed when an invoice changes
     *
     * @param oldInvoice The invoice before changes
     * @param newInvoice The invoice after changes
     * @return The balance change amount (positive means increase in balance)
     */
    fun calculateBalanceChange(oldInvoice: Invoice?, newInvoice: Invoice): Double {
        val oldImpact = oldInvoice?.let { calculateInvoiceBalanceImpact(it) } ?: 0.0
        val newImpact = calculateInvoiceBalanceImpact(newInvoice)
        return newImpact - oldImpact
    }

    /**
     * Applies a balance change to a customer based on their balance type
     * Handles Credit vs Debit balance types correctly
     *
     * @param customer The customer to update
     * @param changeAmount The raw balance change (positive means increase)
     * @return Updated customer with new balance
     */
    fun applyBalanceChange(customer: Customer, changeAmount: Double): Customer {
        // Adjust change direction based on balance type
        val finalChange = when (customer.balanceType.uppercase()) {
            "DEBIT" -> -changeAmount  // Inverse for debit customers
            else -> changeAmount      // Normal for credit customers
        }

        val newBalance = customer.currentBalance + finalChange

        Log.d(TAG, "Applying balance change: customer=${customer.firstName} ${customer.lastName}, " +
                "type=${customer.balanceType}, old=${customer.currentBalance}, " +
                "change=$finalChange, new=$newBalance")

        return customer.copy(currentBalance = newBalance)
    }

    /**
     * Calculates credit usage percentage
     *
     * @param customer The customer to calculate for
     * @return Percentage of credit used (0-100), or 0 if no credit limit
     */
    fun calculateCreditUsagePercentage(customer: Customer): Int {
        if (customer.balanceType != "Credit" || customer.creditLimit <= 0.0 || customer.currentBalance <= 0.0) {
            return 0
        }

        val percentage = (customer.currentBalance / customer.creditLimit) * 100
        // Cap at 100% for display purposes
        return percentage.coerceAtMost(100.0).toInt()
    }

    /**
     * Checks if a customer is over their credit limit
     *
     * @param customer The customer to check
     * @return True if over credit limit, false otherwise
     */
    fun isOverCreditLimit(customer: Customer): Boolean {
        return customer.balanceType == "Credit" &&
                customer.creditLimit > 0.0 &&
                customer.currentBalance > customer.creditLimit
    }

    /**
     * Checks if an invoice would put a customer over their credit limit
     *
     * @param customer The customer to check
     * @param invoice The invoice being added or modified
     * @param previousInvoice The previous version of the invoice (null if new)
     * @return Triple of (would exceed limit, current balance, new balance)
     */
    fun wouldExceedCreditLimit(
        customer: Customer,
        invoice: Invoice,
        previousInvoice: Invoice? = null
    ): Triple<Boolean, Double, Double> {
        // Only applicable for Credit type customers
        if (customer.balanceType != "Credit" || customer.creditLimit <= 0.0) {
            return Triple(false, customer.currentBalance, customer.currentBalance)
        }

        // Calculate balance impact
        val balanceChange = calculateBalanceChange(previousInvoice, invoice)

        // Apply change but don't adjust for balance type yet (that's done in applyBalanceChange)
        val newBalance = customer.currentBalance + balanceChange

        return Triple(newBalance > customer.creditLimit, customer.currentBalance, newBalance)
    }
}