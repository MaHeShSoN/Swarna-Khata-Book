package com.jewelrypos.swarnakhatabook.BottomSheet

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout // Import FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.SecurePreferences
import com.jewelrypos.swarnakhatabook.databinding.BottomSheetPinChangeBinding // Assuming it uses the same layout

/**
 * BottomSheet for setting up a new PIN (Refactored)
 * It has two stages: enter new PIN and confirm new PIN
 */
class PinSetupBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPinChangeBinding? = null
    private val binding get() = _binding!!

    private enum class PinSetupState { ENTER_NEW_PIN, CONFIRM_NEW_PIN }
    private var currentState = PinSetupState.ENTER_NEW_PIN
    private var newPin: String = ""

    private lateinit var prefs: SharedPreferences
    private var onPinSetupCompleted: ((Boolean) -> Unit)? = null
    private var wasPinSuccessfullySet = false // Flag to track successful completion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use the custom theme for full screen
        setStyle(STYLE_NORMAL, R.style.FullScreenBottomSheetDialog)
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
                behavior.isDraggable = false

            }
        }

        return dialog
    }
    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wasPinSuccessfullySet = false // Reset flag on view creation
        setupButtonListeners()
        setupPinInputListener()
        updateUIForCurrentState()
    }

    // *** FIX: Add onDismiss listener to handle cancellation ***
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        // If the dialog is dismissed and the PIN was *not* successfully set,
        // trigger the callback with 'false'
        if (!wasPinSuccessfullySet) {
            onPinSetupCompleted?.invoke(false)
        }
        // Clean up the listener to avoid leaks if needed, though it's usually handled
        onPinSetupCompleted = null
    }
    // ********************************************************


    private fun setupButtonListeners() {
        val numberButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )
        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                if ((binding.pinEditText.text?.length ?: 0) < 4) {
                    binding.pinEditText.append(index.toString())
                }
            }
        }
        binding.btnDelete.setOnClickListener {
            val text = binding.pinEditText.text.toString()
            if (text.isNotEmpty()) {
                binding.pinEditText.setText(text.substring(0, text.length - 1))
                binding.pinEditText.setSelection(binding.pinEditText.text?.length ?: 0)
            }
        }
        binding.btnClear.setOnClickListener {
            binding.pinEditText.setText("")
        }
    }

    private fun setupPinInputListener() {
        binding.pinEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pin = s.toString()
                updatePinDots()
                if (pin.length == 4) {
                    handlePinEntry(pin)
                }
            }
        })
    }

    private fun handlePinEntry(enteredPin: String) {
        when (currentState) {
            PinSetupState.ENTER_NEW_PIN -> {
                newPin = enteredPin
                switchToConfirmPin()
            }
            PinSetupState.CONFIRM_NEW_PIN -> {
                confirmNewPin(enteredPin)
            }
        }
    }

    private fun updatePinDots() {
        val pinLength = binding.pinEditText.text?.length ?: 0
        val pinDotsRefs = listOf(binding.pinDot1, binding.pinDot2, binding.pinDot3, binding.pinDot4)
        pinDotsRefs.forEachIndexed { index, dot ->
            dot.background = ContextCompat.getDrawable(
                requireContext(),
                if (index < pinLength) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            )
        }
        if (binding.pinErrorText.visibility == View.VISIBLE) {
            binding.pinErrorText.visibility = View.GONE
        }
    }

    private fun updateUIForCurrentState() {
        binding.pinEditText.setText("")
        updatePinDots()
        binding.pinErrorText.visibility = View.GONE

        when (currentState) {
            PinSetupState.ENTER_NEW_PIN -> {
                binding.pinEntryTitle.text = getString(R.string.pin_setup_title_enter) // Use string resource
                binding.pinFallbackReason.text = getString(R.string.pin_setup_reason_enter) // Use string resource
                binding.pinFallbackReason.visibility = View.VISIBLE
                binding.pinInfoText.text = getString(R.string.pin_setup_info_enter) // Use string resource
                binding.pinInfoText.visibility = View.VISIBLE
            }
            PinSetupState.CONFIRM_NEW_PIN -> {
                binding.pinEntryTitle.text = getString(R.string.pin_setup_title_confirm) // Use string resource
                binding.pinFallbackReason.text = getString(R.string.pin_setup_reason_confirm) // Use string resource
                binding.pinFallbackReason.visibility = View.VISIBLE
                binding.pinInfoText.text = getString(R.string.pin_setup_info_confirm) // Use string resource
                binding.pinInfoText.visibility = View.VISIBLE
            }
        }
    }

    private fun switchToConfirmPin() {
        currentState = PinSetupState.CONFIRM_NEW_PIN
        updateUIForCurrentState()
    }

    private fun confirmNewPin(enteredPin: String) {
        if (enteredPin == newPin) {
            // PINs match, save the new PIN
            PinHashUtil.storePin(newPin, prefs)
            wasPinSuccessfullySet = true // Mark as successful *before* dismissing
            dismiss() // This will trigger onDismiss
            // onPinSetupCompleted?.invoke(true) // No longer needed here, handled in onDismiss
        } else {
            // PINs don't match
            setError(getString(R.string.pin_error_mismatch), true) // Use string resource
            // Go back to entering the new PIN state
            currentState = PinSetupState.ENTER_NEW_PIN
            // Short delay before clearing UI to allow user to see error
            binding.root.postDelayed({
                if (_binding != null) { // Check binding again in case dismissed during delay
                    updateUIForCurrentState() // This clears the input
                }
            }, 500) // 500ms delay
        }
    }

    private fun setError(error: String?, isVisible: Boolean = true) {
        if (_binding != null) {
            binding.pinErrorText.text = error ?: ""
            binding.pinErrorText.visibility = if (isVisible && error != null) View.VISIBLE else View.GONE
        }
    }

    // Renamed for clarity
    fun setCompletionCallback(listener: (Boolean) -> Unit) {
        onPinSetupCompleted = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Release the binding
    }

    companion object {
        const val TAG = "PinSetupBottomSheet" // Define a tag

        fun newInstance(): PinSetupBottomSheet {
            return PinSetupBottomSheet()
        }

        fun showPinSetup(
            context: Context, // Context not strictly needed here if using FragmentManager
            fragmentManager: androidx.fragment.app.FragmentManager,
            onCompleted: (Boolean) -> Unit
        ) {
            val bottomSheet = newInstance()
            bottomSheet.setCompletionCallback(onCompleted)
            bottomSheet.show(fragmentManager, TAG)
        }
    }
}
