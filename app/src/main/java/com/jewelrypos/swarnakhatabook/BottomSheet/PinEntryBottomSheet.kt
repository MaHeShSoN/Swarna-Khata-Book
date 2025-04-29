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
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Utilitys.PinCheckResult
import com.jewelrypos.swarnakhatabook.Utilitys.PinHashUtil
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityManager
import com.jewelrypos.swarnakhatabook.Utilitys.PinSecurityStatus
import com.jewelrypos.swarnakhatabook.databinding.BottomSheetPinEntryBinding // Import ViewBinding

/**
 * A full-screen bottom sheet for PIN entry (Refactored with ViewBinding and requested changes)
 */
class PinEntryBottomSheet : BottomSheetDialogFragment() {

    // Use ViewBinding
    private var _binding: BottomSheetPinEntryBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView.

    private val pinDots = arrayOfNulls<View>(4)

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
    ): View {
        _binding = BottomSheetPinEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize PIN dots using binding
        pinDots[0] = binding.pinDot1
        pinDots[1] = binding.pinDot2
        pinDots[2] = binding.pinDot3
        pinDots[3] = binding.pinDot4

        // Set up button click listeners using binding
        setupButtonListeners()
        setupPinInputListener() // Setup TextWatcher
        
        // Add entrance animations
        animateEntrance()

        // Apply stored arguments and initial state
        applyArguments()
    }

    // Refactored applyArguments using ViewBinding
    private fun applyArguments() {
        arguments?.let { args ->
            if (args.containsKey(ARG_TITLE)) {
                binding.pinEntryTitle.text = args.getString(ARG_TITLE)
            }

            val reason = args.getString(ARG_REASON)
            binding.pinFallbackReason.text = reason
            binding.pinFallbackReason.visibility = if (reason != null && args.getBoolean(ARG_REASON_VISIBLE, true))
                View.VISIBLE else View.GONE

            val error = args.getString(ARG_ERROR)
            binding.pinErrorText.text = error
            binding.pinErrorText.visibility = if (error != null && args.getBoolean(ARG_ERROR_VISIBLE, true))
                View.VISIBLE else View.GONE
        }
    }

    // Refactored setupButtonListeners using ViewBinding with animation feedback
    private fun setupButtonListeners() {
        val numberButtons = listOf(
            binding.btn0, binding.btn1, binding.btn2, binding.btn3, binding.btn4,
            binding.btn5, binding.btn6, binding.btn7, binding.btn8, binding.btn9
        )

        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                // Add animation feedback
                com.jewelrypos.swarnakhatabook.Animations.EnhancedAnimations.animateButtonClick(button)
                
                if (binding.pinEditText.text?.length ?: 0 < 4) {
                    binding.pinEditText.append(index.toString())
                    // TextWatcher will handle the rest
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            // Add animation feedback
            com.jewelrypos.swarnakhatabook.Animations.EnhancedAnimations.animateButtonClick(binding.btnDelete)
            
            val text = binding.pinEditText.text.toString()
            if (text.isNotEmpty()) {
                binding.pinEditText.setText(text.substring(0, text.length - 1))
                binding.pinEditText.setSelection(binding.pinEditText.text?.length ?: 0)
                // TextWatcher will update dots
            }
        }

        binding.btnClear.setOnClickListener {
            // Add animation feedback
            com.jewelrypos.swarnakhatabook.Animations.EnhancedAnimations.animateButtonClick(binding.btnClear)
            
            binding.pinEditText.setText("")
            // TextWatcher will update dots
        }
    }

    // Setup TextWatcher for auto-verification
    private fun setupPinInputListener() {
        binding.pinEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val pin = s.toString()
                updatePinDots() // Update visual dots

                // Automatically trigger verification when 4 digits are entered
                if (pin.length == 4) {
                    onPinConfirmedListener?.invoke(pin)
                    // Listener should handle dismiss/clear based on verification result
                }
            }
        })
    }

    // Enhanced updatePinDots with animation
    private fun updatePinDots() {
        val pinLength = binding.pinEditText.text?.length ?: 0

        for (i in pinDots.indices) {
            val shouldBeFilled = i < pinLength
            val currentDrawable = pinDots[i]?.background
            val newDrawableResId = if (shouldBeFilled) R.drawable.pin_dot_filled else R.drawable.pin_dot_empty
            
            // Only animate if the state is changing
            if ((shouldBeFilled && currentDrawable != ContextCompat.getDrawable(requireContext(), R.drawable.pin_dot_filled)) || 
                (!shouldBeFilled && currentDrawable != ContextCompat.getDrawable(requireContext(), R.drawable.pin_dot_empty))) {
                
                // Set the new background immediately
                pinDots[i]?.background = ContextCompat.getDrawable(requireContext(), newDrawableResId)
                
                // Add a small scale animation for feedback
                pinDots[i]?.animate()
                    ?.scaleX(0.85f)
                    ?.scaleY(0.85f)
                    ?.setDuration(100)
                    ?.withEndAction {
                        pinDots[i]?.animate()
                            ?.scaleX(1f)
                            ?.scaleY(1f)
                            ?.setDuration(100)
                            ?.start()
                    }
                    ?.start()
            }
        }

        // Clear any error when user starts typing again
        if (binding.pinErrorText.visibility == View.VISIBLE) {
            binding.pinErrorText.visibility = View.GONE
        }
    }

    // Refactored setTitle using ViewBinding
    fun setTitle(title: String): PinEntryBottomSheet {
        if (_binding != null) { // Check if binding is available
            binding.pinEntryTitle.text = title
        } else {
            arguments = (arguments ?: Bundle()).apply { putString(ARG_TITLE, title) }
        }
        return this
    }

    // Refactored setReason using ViewBinding
    fun setReason(reason: String?, isVisible: Boolean = true): PinEntryBottomSheet {
        if (_binding != null) {
            binding.pinFallbackReason.text = reason
            binding.pinFallbackReason.visibility = if (reason != null && isVisible) View.VISIBLE else View.GONE
        } else if (reason != null) {
            arguments = (arguments ?: Bundle()).apply {
                putString(ARG_REASON, reason)
                putBoolean(ARG_REASON_VISIBLE, isVisible)
            }
        }
        return this
    }

    // Refactored setError with enhanced animation and ViewBinding
    fun setError(error: String?, isVisible: Boolean = true): PinEntryBottomSheet {
        if (_binding != null) {
            if (error != null && isVisible) {
                binding.pinErrorText.text = error
                binding.pinErrorText.alpha = 0f
                binding.pinErrorText.visibility = View.VISIBLE
                binding.pinErrorText.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start()
                
                // Apply enhanced shake animation
                try {
                    // First shake the container
                    val shakeAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.shake_animation)
                    binding.pinDotsContainer.startAnimation(shakeAnimation)
                    
                    // Then highlight the error with a red flash
                    for (dot in pinDots) {
                        dot?.background = ContextCompat.getDrawable(requireContext(), R.drawable.pin_dot_error)
                        
                        // Reset to appropriate state after error indication
                        dot?.postDelayed({
                            updatePinDots() // Reset dots to current state
                        }, 500)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PinEntryBottomSheet", "Error loading or applying animation", e)
                }
            } else {
                binding.pinErrorText.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        binding.pinErrorText.visibility = View.GONE
                    }
                    .start()
            }
        } else if (error != null && isVisible) {
            arguments = (arguments ?: Bundle()).apply {
                putString(ARG_ERROR, error)
                putBoolean(ARG_ERROR_VISIBLE, isVisible)
            }
        }
        return this
    }

    // --- Make Non-Draggable ---
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.isDraggable = false // Disable dragging
                setupFullHeight(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
    }

    // Add a smooth entrance animation
    private fun animateEntrance() {
        // Animate title
        binding.pinEntryTitle.alpha = 0f
        binding.pinEntryTitle.translationY = -50f
        binding.pinEntryTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(100)
            .start()
            
        // Animate dots container
        binding.pinDotsContainer.alpha = 0f
        binding.pinDotsContainer.translationY = 50f
        binding.pinDotsContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(200)
            .start()
            
        // Animate keypad
        binding.keypadContainer.alpha = 0f
        binding.keypadContainer.translationY = 100f
        binding.keypadContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(300)
            .start()
    }

    fun setOnPinConfirmedListener(listener: (String) -> Unit): PinEntryBottomSheet {
        onPinConfirmedListener = listener
        return this
    }


    fun setOnCancelListener(listener: () -> Unit): PinEntryBottomSheet {
        onCancelListener = listener
        return this
    }

    // Refactored clearPin using ViewBinding
    fun clearPin() {
        if (_binding != null) {
            binding.pinEditText.setText("")
            // TextWatcher handles updating dots
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancelListener?.invoke() // Still trigger cancel if user dismisses manually
    }

    // Clean up ViewBinding
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_REASON = "reason"
        private const val ARG_REASON_VISIBLE = "reason_visible"
        private const val ARG_ERROR = "error"
        private const val ARG_ERROR_VISIBLE = "error_visible"

        fun newInstance(title: String): PinEntryBottomSheet {
            return PinEntryBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                }
            }
        }

        // Updated showPinVerification to use the modified setError
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
            val securityStatus = PinSecurityManager.checkStatus(context)

            if (securityStatus is PinSecurityStatus.Locked) {
                val minutes = (securityStatus.remainingLockoutTimeMs / 60000).toInt() + 1
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Too Many Failed Attempts")
                    .setMessage("PIN entry has been disabled for $minutes minutes due to multiple failed attempts.")
                    .setPositiveButton("OK") { _, _ -> onCancelled() }
                    .setCancelable(false)
                    .show()
                return
            }

            val bottomSheet = newInstance(title)
            bottomSheet.setReason(reason)
                .setOnPinConfirmedListener { enteredPin ->
                    when (PinHashUtil.checkPin(enteredPin, prefs)) {
                        PinCheckResult.NORMAL_MATCH -> {
                            bottomSheet.dismiss()
                            PinSecurityManager.resetAttempts(context)
                            onPinCorrect()
                        }
                        PinCheckResult.REVERSE_MATCH -> {
                            bottomSheet.dismiss()
                            onReversePinEntered()
                        }
                        PinCheckResult.NO_MATCH -> {
                            val updatedStatus = PinSecurityManager.recordFailedAttempt(context)
                            if (updatedStatus is PinSecurityStatus.Locked) {
                                bottomSheet.dismiss() // Dismiss before showing lockout dialog
                                onPinIncorrect(updatedStatus)
                            } else {
                                // Use setError which now includes animation
                                val attemptsLeft = (updatedStatus as? PinSecurityStatus.Limited)?.remainingAttempts
                                val errorMsg = if (attemptsLeft != null) {
                                    "Incorrect PIN ($attemptsLeft attempts remaining)"
                                } else {
                                    "Incorrect PIN"
                                }
                                bottomSheet.setError(errorMsg, true)
                                bottomSheet.clearPin() // Clear PIN after showing error
                                onPinIncorrect(updatedStatus)
                            }
                        }
                    }
                }
                .setOnCancelListener {
                    onCancelled()
                }

            bottomSheet.show(fragmentManager, "PinEntryBottomSheet")
        }
    }
}