package com.jewelrypos.swarnakhatabook.Utilitys

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jewelrypos.swarnakhatabook.R

/**
 * Handler class for the professional PIN entry dialog
 */
class PinEntryDialogHandler(private val context: Context) {

    private lateinit var dialog: AlertDialog
    private lateinit var pinEditText: TextInputEditText
    private lateinit var pinLayout: TextInputLayout
    private lateinit var builder: AlertDialog.Builder
    private lateinit var dialogView: View
    private lateinit var errorText: TextView
    private lateinit var titleText: TextView
    private lateinit var reasonText: TextView

    private val pinDots = arrayOfNulls<View>(4)
    private val numberButtons = arrayOfNulls<MaterialButton>(10)
    private lateinit var deleteButton: MaterialButton
    private lateinit var clearButton: MaterialButton

    private var onPositiveClickListener: ((String) -> Unit)? = null
    private var onNegativeClickListener: (() -> Unit)? = null

    init {
        initializeDialog()
    }

    private fun initializeDialog() {
        builder = AlertDialog.Builder(context)
        dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_pin_input, null)

        // Find all views
        pinEditText = dialogView.findViewById(R.id.pinEditText)
        pinLayout = dialogView.findViewById(R.id.pin)
        errorText = dialogView.findViewById(R.id.pin_error_text)
        titleText = dialogView.findViewById(R.id.pin_entry_title)
        reasonText = dialogView.findViewById(R.id.pin_fallback_reason)

        // Initialize PIN dots
        pinDots[0] = dialogView.findViewById(R.id.pin_dot_1)
        pinDots[1] = dialogView.findViewById(R.id.pin_dot_2)
        pinDots[2] = dialogView.findViewById(R.id.pin_dot_3)
        pinDots[3] = dialogView.findViewById(R.id.pin_dot_4)

        // Initialize number buttons
        numberButtons[0] = dialogView.findViewById(R.id.btn_0)
        numberButtons[1] = dialogView.findViewById(R.id.btn_1)
        numberButtons[2] = dialogView.findViewById(R.id.btn_2)
        numberButtons[3] = dialogView.findViewById(R.id.btn_3)
        numberButtons[4] = dialogView.findViewById(R.id.btn_4)
        numberButtons[5] = dialogView.findViewById(R.id.btn_5)
        numberButtons[6] = dialogView.findViewById(R.id.btn_6)
        numberButtons[7] = dialogView.findViewById(R.id.btn_7)
        numberButtons[8] = dialogView.findViewById(R.id.btn_8)
        numberButtons[9] = dialogView.findViewById(R.id.btn_9)

        deleteButton = dialogView.findViewById(R.id.btn_delete)
        clearButton = dialogView.findViewById(R.id.btn_clear)

        // Set up button click listeners
        setupButtonListeners()

        // Build the dialog
        builder.setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("OK", null) // Will be set later
            .setNegativeButton("Cancel") { _, _ ->
                onNegativeClickListener?.invoke()
            }
    }

    private fun setupButtonListeners() {
        // Number buttons
        for (i in 0..9) {
            numberButtons[i]?.setOnClickListener {
                if (pinEditText.text?.length ?: 0 < 4) {
                    pinEditText.append(i.toString())
                    updatePinDots()
                }
            }
        }

        // Delete button
        deleteButton.setOnClickListener {
            val text = pinEditText.text.toString()
            if (text.isNotEmpty()) {
                pinEditText.setText(text.substring(0, text.length - 1))
                pinEditText.setSelection(pinEditText.text?.length ?: 0)
                updatePinDots()
            }
        }

        // Clear button
        clearButton.setOnClickListener {
            pinEditText.setText("")
            updatePinDots()
        }
    }

    private fun updatePinDots() {
        val pinLength = pinEditText.text?.length ?: 0

        // Update all dots based on current PIN length
        for (i in pinDots.indices) {
            pinDots[i]?.background = if (i < pinLength) {
                ContextCompat.getDrawable(context, R.drawable.pin_dot_filled)
            } else {
                ContextCompat.getDrawable(context, R.drawable.pin_dot_empty)
            }
        }
    }

    /**
     * Set the dialog title text
     */
    fun setTitle(title: String): PinEntryDialogHandler {
        titleText.text = title
        return this
    }

    /**
     * Set the reason text (displayed below the title)
     */
    fun setReason(reason: String, isVisible: Boolean = true): PinEntryDialogHandler {
        reasonText.text = reason
        reasonText.visibility = if (isVisible) View.VISIBLE else View.GONE
        return this
    }

    /**
     * Set the error text (displayed at the bottom)
     */
    fun setError(error: String, isVisible: Boolean = true): PinEntryDialogHandler {
        errorText.text = error
        errorText.visibility = if (isVisible) View.VISIBLE else View.GONE
        return this
    }

    /**
     * Set the button callbacks
     */
    fun setCallbacks(
        onPositive: (String) -> Unit,
        onNegative: () -> Unit
    ): PinEntryDialogHandler {
        this.onPositiveClickListener = onPositive
        this.onNegativeClickListener = onNegative
        return this
    }

    /**
     * Show the dialog
     */
    fun show() {
        // Create and show the dialog
        dialog = builder.create()
        dialog.show()

        // Set the positive button click listener
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            onPositiveClickListener?.invoke(enteredPin)
        }
    }

    /**
     * Dismiss the dialog
     */
    fun dismiss() {
        if (::dialog.isInitialized && dialog.isShowing) {
            dialog.dismiss()
        }
    }

    /**
     * Update the helper text
     */
    fun setHelperText(helperText: String) {
        pinLayout.helperText = helperText
    }

    /**
     * Clear the PIN input
     */
    fun clearPin() {
        pinEditText.setText("")
        updatePinDots()
    }

    companion object {
        /**
         * Creates a PIN entry dialog and handles verification against stored PIN
         */
        fun showPinVerificationDialog(
            context: Context,
            prefs: SharedPreferences,
            title: String,
            reason: String? = null,
            onPinCorrect: () -> Unit,
            onPinIncorrect: (PinSecurityStatus) -> Unit,
            onReversePinEntered: () -> Unit,
            onCancelled: () -> Unit
        ) {
            val handler = PinEntryDialogHandler(context)
                .setTitle(title)

            // Set reason if provided
            if (!reason.isNullOrEmpty()) {
                handler.setReason(reason, true)
            }

            handler.setCallbacks(
                onPositive = { enteredPin ->
                    // Verify PIN
                    when (PinHashUtil.checkPin(enteredPin, prefs)) {
                        PinCheckResult.NORMAL_MATCH -> {
                            // PIN is correct
                            handler.dismiss()
                            PinSecurityManager.resetAttempts(context)
                            onPinCorrect()
                        }
                        PinCheckResult.REVERSE_MATCH -> {
                            // Reverse PIN entered
                            handler.dismiss()
                            onReversePinEntered()
                        }
                        PinCheckResult.NO_MATCH -> {
                            // PIN is incorrect
                            val securityStatus = PinSecurityManager.recordFailedAttempt(context)

                            when (securityStatus) {
                                is PinSecurityStatus.Locked -> {
                                    handler.dismiss()
                                    onPinIncorrect(securityStatus)
                                }
                                is PinSecurityStatus.Limited -> {
                                    handler.setError(
                                        "Incorrect PIN (${securityStatus.remainingAttempts} attempts remaining)",
                                        true
                                    )
                                    handler.clearPin()
                                    onPinIncorrect(securityStatus)
                                }
                                else -> {
                                    handler.setError("Incorrect PIN", true)
                                    handler.clearPin()
                                    onPinIncorrect(securityStatus)
                                }
                            }
                        }
                    }
                },
                onNegative = {
                    handler.dismiss()
                    onCancelled()
                }
            )

            handler.show()
        }
    }
}