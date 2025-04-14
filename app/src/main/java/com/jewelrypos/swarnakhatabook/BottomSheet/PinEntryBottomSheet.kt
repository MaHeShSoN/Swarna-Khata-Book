package com.jewelrypos.swarnakhatabook.BottomSheet

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Utilitys.PinCheckResult
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus

/**
 * A full-screen bottom sheet for PIN entry
 */
class PinEntryBottomSheet : BottomSheetDialogFragment() {

    private lateinit var pinEditText: TextInputEditText
    private lateinit var errorText: TextView
    private lateinit var titleText: TextView
    private lateinit var reasonText: TextView

    private val pinDots = arrayOfNulls<View>(4)
    private val numberButtons = arrayOfNulls<MaterialButton>(10)
    private lateinit var deleteButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var confirmButton: MaterialButton

    // Callbacks
    private var onPinConfirmedListener: ((String) -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenBottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_pin_entry, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find views
        pinEditText = view.findViewById(R.id.pinEditText)
        errorText = view.findViewById(R.id.pin_error_text)
        titleText = view.findViewById(R.id.pin_entry_title)
        reasonText = view.findViewById(R.id.pin_fallback_reason)
        cancelButton = view.findViewById(R.id.btn_cancel)
        confirmButton = view.findViewById(R.id.btn_confirm)

        // Initialize PIN dots
        pinDots[0] = view.findViewById(R.id.pin_dot_1)
        pinDots[1] = view.findViewById(R.id.pin_dot_2)
        pinDots[2] = view.findViewById(R.id.pin_dot_3)
        pinDots[3] = view.findViewById(R.id.pin_dot_4)

        // Initialize number buttons
        numberButtons[0] = view.findViewById(R.id.btn_0)
        numberButtons[1] = view.findViewById(R.id.btn_1)
        numberButtons[2] = view.findViewById(R.id.btn_2)
        numberButtons[3] = view.findViewById(R.id.btn_3)
        numberButtons[4] = view.findViewById(R.id.btn_4)
        numberButtons[5] = view.findViewById(R.id.btn_5)
        numberButtons[6] = view.findViewById(R.id.btn_6)
        numberButtons[7] = view.findViewById(R.id.btn_7)
        numberButtons[8] = view.findViewById(R.id.btn_8)
        numberButtons[9] = view.findViewById(R.id.btn_9)

        deleteButton = view.findViewById(R.id.btn_delete)
        clearButton = view.findViewById(R.id.btn_clear)

        // Set up button click listeners
        setupButtonListeners()

        // Set up cancel button
        cancelButton.setOnClickListener {
            onCancelListener?.invoke()
            dismiss()
        }

        // Set up confirm button
        confirmButton.setOnClickListener {
            val enteredPin = pinEditText.text.toString()
            if (enteredPin.length == 4) {
                onPinConfirmedListener?.invoke(enteredPin)
            } else {
                setError("Please enter a 4-digit PIN", true)
            }
        }
    }

    private fun setupButtonListeners() {
        // Number buttons
        for (i in 0..9) {
            numberButtons[i]?.setOnClickListener {
                if (pinEditText.text?.length ?: 0 < 4) {
                    pinEditText.append(i.toString())
                    updatePinDots()

                    // Enable confirm button when PIN is complete
                    if (pinEditText.text?.length == 4) {
                        confirmButton.isEnabled = true
                    }
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

                // Disable confirm button if PIN is incomplete
                if (pinEditText.text?.length ?: 0 < 4) {
                    confirmButton.isEnabled = false
                }
            }
        }

        // Clear button
        clearButton.setOnClickListener {
            pinEditText.setText("")
            updatePinDots()
            confirmButton.isEnabled = false
        }
    }

    private fun updatePinDots() {
        val pinLength = pinEditText.text?.length ?: 0

        // Update all dots based on current PIN length
        for (i in pinDots.indices) {
            pinDots[i]?.background = if (i < pinLength) {
                ContextCompat.getDrawable(requireContext(), R.drawable.pin_dot_filled)
            } else {
                ContextCompat.getDrawable(requireContext(), R.drawable.pin_dot_empty)
            }
        }

        // Clear any error when user starts typing again
        if (errorText.visibility == View.VISIBLE) {
            errorText.visibility = View.GONE
        }
    }

    /**
     * Set the title text
     */
    fun setTitle(title: String): PinEntryBottomSheet {
        if (::titleText.isInitialized) {
            titleText.text = title
        } else {
            arguments = (arguments ?: Bundle()).apply {
                putString(ARG_TITLE, title)
            }
        }
        return this
    }

    /**
     * Set the reason text
     */
    fun setReason(reason: String?, isVisible: Boolean = true): PinEntryBottomSheet {
        if (::reasonText.isInitialized) {
            if (reason != null) {
                reasonText.text = reason
                reasonText.visibility = if (isVisible) View.VISIBLE else View.GONE
            } else {
                reasonText.visibility = View.GONE
            }
        } else if (reason != null) {
            arguments = (arguments ?: Bundle()).apply {
                putString(ARG_REASON, reason)
                putBoolean(ARG_REASON_VISIBLE, isVisible)
            }
        }
        return this
    }

    /**
     * Set the error text
     */
    fun setError(error: String?, isVisible: Boolean = true): PinEntryBottomSheet {
        if (::errorText.isInitialized) {
            if (error != null) {
                errorText.text = error
                errorText.visibility = if (isVisible) View.VISIBLE else View.GONE
            } else {
                errorText.visibility = View.GONE
            }
        } else if (error != null) {
            arguments = (arguments ?: Bundle()).apply {
                putString(ARG_ERROR, error)
                putBoolean(ARG_ERROR_VISIBLE, isVisible)
            }
        }
        return this
    }

    /**
     * Set the PIN confirmation listener
     */
    fun setOnPinConfirmedListener(listener: (String) -> Unit): PinEntryBottomSheet {
        onPinConfirmedListener = listener
        return this
    }

    /**
     * Set the cancel listener
     */
    fun setOnCancelListener(listener: () -> Unit): PinEntryBottomSheet {
        onCancelListener = listener
        return this
    }

    /**
     * Clear the PIN input
     */
    fun clearPin() {
        if (::pinEditText.isInitialized) {
            pinEditText.setText("")
            updatePinDots()
            confirmButton.isEnabled = false
        }
    }

    override fun onStart() {
        super.onStart()

        // Apply stored arguments if available
        arguments?.let { args ->
            if (args.containsKey(ARG_TITLE)) {
                titleText.text = args.getString(ARG_TITLE)
            }

            if (args.containsKey(ARG_REASON)) {
                reasonText.text = args.getString(ARG_REASON)
                reasonText.visibility = if (args.getBoolean(ARG_REASON_VISIBLE, true))
                    View.VISIBLE else View.GONE
            }

            if (args.containsKey(ARG_ERROR)) {
                errorText.text = args.getString(ARG_ERROR)
                errorText.visibility = if (args.getBoolean(ARG_ERROR_VISIBLE, true))
                    View.VISIBLE else View.GONE
            }
        }

        // Initial state for confirm button
        confirmButton.isEnabled = false
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_REASON = "reason"
        private const val ARG_REASON_VISIBLE = "reason_visible"
        private const val ARG_ERROR = "error"
        private const val ARG_ERROR_VISIBLE = "error_visible"

        /**
         * Create a new instance of the bottom sheet
         */
        fun newInstance(title: String): PinEntryBottomSheet {
            return PinEntryBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                }
            }
        }

        /**
         * Show PIN verification using bottom sheet
         */
        /**
         * Show PIN verification using bottom sheet
         */
        fun showPinVerification(
            context: Context,
            fragmentManager: androidx.fragment.app.FragmentManager,
            prefs: SharedPreferences,
            title: String,
            reason: String? = null,
            onPinCorrect: () -> Unit,
            onPinIncorrect: (PinSecurityStatus) -> Unit,
            onReversePinEntered: () -> Unit,
            onCancelled: () -> Unit
        ) {
            // Check security status first
            val securityStatus = PinSecurityManager.checkStatus(context)

            if (securityStatus is PinSecurityStatus.Locked) {
                // Don't show PIN entry if locked out
                val minutes = (securityStatus.remainingLockoutTimeMs / 60000).toInt() + 1
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Too Many Failed Attempts")
                    .setMessage("PIN entry has been disabled for $minutes minutes due to multiple failed attempts.")
                    .setPositiveButton("OK") { _, _ -> onCancelled() }
                    .setCancelable(false)
                    .show()
                return
            }

            // Create the bottom sheet instance and store it in a variable
            val bottomSheet = newInstance(title)

            // Configure the bottom sheet
            bottomSheet.setReason(reason)
                .setOnPinConfirmedListener { enteredPin ->
                    // Verify PIN
                    when (PinHashUtil.checkPin(enteredPin, prefs)) {
                        PinCheckResult.NORMAL_MATCH -> {
                            // PIN is correct
                            bottomSheet.dismiss()
                            PinSecurityManager.resetAttempts(context)
                            onPinCorrect()
                        }
                        PinCheckResult.REVERSE_MATCH -> {
                            // Reverse PIN entered
                            bottomSheet.dismiss()
                            onReversePinEntered()
                        }
                        PinCheckResult.NO_MATCH -> {
                            // PIN is incorrect
                            val updatedStatus = PinSecurityManager.recordFailedAttempt(context)

                            when (updatedStatus) {
                                is PinSecurityStatus.Locked -> {
                                    bottomSheet.dismiss()
                                    onPinIncorrect(updatedStatus)
                                }
                                is PinSecurityStatus.Limited -> {
                                    bottomSheet.setError(
                                        "Incorrect PIN (${updatedStatus.remainingAttempts} attempts remaining)",
                                        true
                                    )
                                    bottomSheet.clearPin()
                                    onPinIncorrect(updatedStatus)
                                }
                                else -> {
                                    bottomSheet.setError("Incorrect PIN", true)
                                    bottomSheet.clearPin()
                                    onPinIncorrect(updatedStatus)
                                }
                            }
                        }
                    }
                }
                .setOnCancelListener {
                    onCancelled()
                }

            // Show the bottom sheet
            bottomSheet.show(fragmentManager, "PinEntryBottomSheet")
        }
    }
}