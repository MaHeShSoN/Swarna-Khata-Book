package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.jewelrypos.swarnakhatabook.databinding.FragmentGetDetailsBinding
import com.jewelrypos.swarnakhatabook.Utilitys.AnimationUtils
import java.util.concurrent.TimeUnit


class GetDetailsFragment : Fragment() {

    private var _binding: FragmentGetDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private var storedVerificationId: String = ""
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private val TAG = "GetDetailsFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGetDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Apply entrance animation to the main content
        AnimationUtils.fadeIn(binding.contentLayout)

        setupListeners()
    }

    private fun setupListeners() {
        // Add real-time validation to phone number
        binding.phoneNumberText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePhoneNumber(s.toString())
            }
        })

        // Submit button click
        binding.continueButton.setOnClickListener {
            // Apply button animation
            AnimationUtils.pulse(it)
            if (validatePhoneNumber(binding.phoneNumberText.text.toString())) {
                // Disable button to prevent multiple clicks
                binding.continueButton.isEnabled = false

                // Hide keyboard
                hideKeyboard()
                
                // Phone number is valid, proceed with OTP
                sendOTP()
            }
        }
    }

    private fun validatePhoneNumber(number: String): Boolean {
        return when {
            number.isEmpty() -> {
                binding.phoneNumberLayout.error = "Phone number is required"
                false
            }

            !number.matches(Regex("^[0-9]{10}$")) -> {
                binding.phoneNumberLayout.error = "Please enter a valid 10-digit number"
                false
            }

            else -> {
                binding.phoneNumberLayout.error = null
                binding.phoneNumberLayout.isErrorEnabled = false
                true
            }
        }
    }

    // Helper method to hide the keyboard
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.phoneNumberText.windowToken, 0)
    }

    // Send OTP to user's phone number
    private fun sendOTP() {
        // Show loading animation
        binding.continueButton.text = "Sending OTP..."
        binding.continueButton.isEnabled = false

        // Get phone number with country code (assuming India +91)
        val phoneNumber = "+91" + binding.phoneNumberText.text.toString()

        // Setup Firebase Phone Auth callbacks
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // This callback will be invoked in two situations:
                // 1. Instant verification. In some cases, the phone number can be instantly
                //    verified without requiring to enter an OTP.
                // 2. Auto-retrieval. On some devices, Google Play services can automatically
                //    retrieve the verification code.
                Log.d(TAG, "onVerificationCompleted:$credential")

                // Since we want the user to see the OTP screen anyway, we'll just navigate there
                // and let the automatic verification handle itself
                navigateToOtpScreen(phoneNumber, storedVerificationId)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                // This callback is invoked when an invalid request for verification is made,
                // for instance if the phone number format is invalid.
                Log.w(TAG, "onVerificationFailed", e)

                binding.continueButton.isEnabled = true
                binding.continueButton.text = "Continue"

                Toast.makeText(requireContext(),
                    "Verification failed: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                // The SMS verification code has been sent to the provided phone number
                Log.d(TAG, "onCodeSent:$verificationId")

                // Save verification ID and resend token for later use
                storedVerificationId = verificationId
                resendToken = token

                // Navigate to OTP verification screen
                navigateToOtpScreen(phoneNumber, verificationId)
            }
        }

        // Configure and start phone verification
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber) // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout duration
            .setActivity(requireActivity()) // Activity (for callback binding)
            .setCallbacks(callbacks) // OnVerificationStateChangedCallbacks
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun navigateToOtpScreen(phoneNumber: String, verificationId: String) {
        // Navigate to OTP verification fragment with necessary data
        val action = GetDetailsFragmentDirections.actionGetDetailsFragmentToOtpVarificationFragment(
            phoneNumber = phoneNumber,
            verificationId = verificationId,
            name = "",
            shopName = "",
            address = "",
            gstNumber = ""
        )

        findNavController().navigate(action, AnimationUtils.getSlideNavOptions())

        // Reset button state
        binding.continueButton.isEnabled = true
        binding.continueButton.text = "Submit"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}