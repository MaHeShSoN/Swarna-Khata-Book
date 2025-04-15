package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
// Import ViewBinding class
import com.jewelrypos.swarnakhatabook.databinding.BottomSheetPinChangeBinding

/**
 * BottomSheet for changing PIN (Refactored)
 * It has several stages: verify current PIN, enter new PIN, confirm new PIN
 */
class PinChangeBottomSheet : BottomSheetDialogFragment() {

    // Use ViewBinding
    private var _binding: BottomSheetPinChangeBinding? = null
    private val binding get() = _binding!! // Valid between onCreateView and onDestroyView

    // PIN Change Flow State
    private enum class PinChangeState {
        VERIFY_CURRENT_PIN,
        ENTER_NEW_PIN,
        CONFIRM_NEW_PIN
    }

    private var currentState = PinChangeState.VERIFY_CURRENT_PIN
    private var currentPin: String = ""
    private var newPin: String = ""

    // Preferences for PIN storage
    private lateinit var prefs: SharedPreferences

    // Callback for completion
    private var onPinChangeCompleted: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenBottomSheetDialog) // Use the full-screen style

        // Get secure preferences
        prefs = SecurePreferences.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPinChangeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Make non-draggable
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false

        // Set up button click listeners
        setupButtonListeners()
        setupPinInputListener() // Add TextWatcher listener

        // Set initial state UI
        updateUIForCurrentState()
    }

    // Ensure the dialog expands to full height and is not draggable
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                setupFullHeight(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
            }
        }

        return dialog
    }

    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
    }

    private fun setupButtonListeners() {
        // Number buttons using ViewBinding
        val numberButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )

        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                if (binding.pinEditText.text?.length ?: 0 < 4) {
                    binding.pinEditText.append(index.toString())
                    // TextWatcher will update dots and check length
                }
            }
        }

        // Delete button using ViewBinding
        binding.btnDelete.setOnClickListener {
            val text = binding.pinEditText.text.toString()
            if (text.isNotEmpty()) {
                binding.pinEditText.setText(text.substring(0, text.length - 1))
                binding.pinEditText.setSelection(binding.pinEditText.text?.length ?: 0)
                // TextWatcher will update dots
            }
        }

        // Clear button using ViewBinding
        binding.btnClear.setOnClickListener {
            binding.pinEditText.setText("")
            // TextWatcher will update dots
        }

        // Cancel button (removed from layout, but keep logic if needed elsewhere or for back press)
        // binding.btnCancel.setOnClickListener {
        //     dismiss()
        //     onPinChangeCompleted?.invoke(false)
        // }
    }

    // Add TextWatcher for auto-check
    private fun setupPinInputListener() {
        binding.pinEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val pin = s.toString()
                updatePinDots() // Update visual dots

                // Automatically trigger action when 4 digits are entered
                if (pin.length == 4) {
                    handlePinEntry(pin)
                }
            }
        })
    }

    // Handle PIN entry automatically
    private fun handlePinEntry(enteredPin: String) {
        when (currentState) {
            PinChangeState.VERIFY_CURRENT_PIN -> verifyCurrentPin(enteredPin)
            PinChangeState.ENTER_NEW_PIN -> {
                newPin = enteredPin
                switchToConfirmNewPin()
            }

            PinChangeState.CONFIRM_NEW_PIN -> confirmNewPin(enteredPin)
        }
    }

    private fun updatePinDots() {
        val pinLength = binding.pinEditText.text?.length ?: 0
        val pinDotsRefs = listOf(binding.pinDot1, binding.pinDot2, binding.pinDot3, binding.pinDot4)

        // Update all dots based on current PIN length
        for (i in pinDotsRefs.indices) {
            pinDotsRefs[i].background = ContextCompat.getDrawable(
                requireContext(),
                if (i < pinLength) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }

        // Clear any error when user starts typing again
        if (binding.pinErrorText.visibility == View.VISIBLE) {
            binding.pinErrorText.visibility = View.GONE
        }
    }

    private fun updateUIForCurrentState() {
        // Clear input and dots
        binding.pinEditText.setText("")
        updatePinDots()
        binding.pinErrorText.visibility = View.GONE

        when (currentState) {
            PinChangeState.VERIFY_CURRENT_PIN -> {
                binding.pinEntryTitle.text = "Current PIN"
                binding.pinFallbackReason.text = "Please enter your current PIN"
                binding.pinFallbackReason.visibility = View.VISIBLE
                binding.pinInfoText.text =
                    "Remember: Entering your PIN in reverse will trigger the emergency data wipe."
                binding.pinInfoText.visibility = View.VISIBLE
            }

            PinChangeState.ENTER_NEW_PIN -> {
                binding.pinEntryTitle.text = "New PIN"
                binding.pinFallbackReason.text = "Enter a new 4-digit PIN"
                binding.pinFallbackReason.visibility = View.VISIBLE
                binding.pinInfoText.text =
                    "Choose a PIN that's easy for you to remember but hard for others to guess."
                binding.pinInfoText.visibility = View.VISIBLE
            }

            PinChangeState.CONFIRM_NEW_PIN -> {
                binding.pinEntryTitle.text = "Confirm PIN"
                binding.pinFallbackReason.text = "Re-enter your new PIN to confirm"
                binding.pinFallbackReason.visibility = View.VISIBLE
                binding.pinInfoText.text =
                    "In an emergency, entering your PIN in reverse will wipe all data."
                binding.pinInfoText.visibility = View.VISIBLE
            }
        }
    }

    private fun verifyCurrentPin(enteredPin: String) {
        // Check if current PIN is valid
        if (PinHashUtil.verifyPin(enteredPin, prefs)) {
            currentPin = enteredPin
            currentState = PinChangeState.ENTER_NEW_PIN
            updateUIForCurrentState()
        } else {
            setError("Incorrect PIN", true)
            binding.pinEditText.setText("") // Clear input after error
            updatePinDots()
        }
    }

    private fun switchToConfirmNewPin() {
        currentState = PinChangeState.CONFIRM_NEW_PIN
        updateUIForCurrentState()
    }

    private fun confirmNewPin(enteredPin: String) {
        if (enteredPin == newPin) {
            PinHashUtil.storePin(newPin, prefs)
            dismiss()
            onPinChangeCompleted?.invoke(true)
        } else {
            setError("PINs don't match", true)
            // Go back to entering the new PIN
            currentState = PinChangeState.ENTER_NEW_PIN
            updateUIForCurrentState() // This also clears the input
        }
    }

    /**
     * Set the error text
     */
    fun setError(error: String?, isVisible: Boolean = true) {
        if (_binding != null) { // Check if binding is available
            if (error != null) {
                binding.pinErrorText.text = error
                binding.pinErrorText.visibility = if (isVisible) View.VISIBLE else View.GONE
            } else {
                binding.pinErrorText.visibility = View.GONE
            }
        }
    }

    /**
     * Set the callback for PIN change completion
     */
    fun setOnPinChangeCompletedListener(listener: (Boolean) -> Unit): PinChangeBottomSheet {
        onPinChangeCompleted = listener
        return this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Release the binding
    }

    companion object {
        /**
         * Create a new instance of the bottom sheet
         */
        fun newInstance(): PinChangeBottomSheet {
            return PinChangeBottomSheet()
        }

        /**
         * Show PIN change bottom sheet
         */
        fun showPinChange(
            context: Context,
            fragmentManager: androidx.fragment.app.FragmentManager,
            onCompleted: (Boolean) -> Unit
        ) {
            val bottomSheet = newInstance()
            bottomSheet.setOnPinChangeCompletedListener(onCompleted)
            bottomSheet.show(fragmentManager, "PinChangeBottomSheet")
        }
    }
}