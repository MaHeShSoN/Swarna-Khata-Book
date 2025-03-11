package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.jewelrypos.swarnakhatabook.databinding.FragmentGetDetailsBinding
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

        setupListeners()
    }

    private fun setupListeners() {
        // Show/hide GST field based on checkbox
        binding.hasGstCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.gstNumber.isVisible = isChecked

            // Clear GST field when hiding it
            if (!isChecked) {
                binding.gstNumberText.text?.clear()
            }
        }

        // Add real-time validation to owner number
        binding.numberOfOwnerText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateOwnerNumber(s.toString())
            }
        })

        // Add real-time validation to GST number
        binding.gstNumberText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (binding.hasGstCheckBox.isChecked) {
                    validateGSTNumber(s.toString())
                }
            }
        })

        // Submit button click
        binding.submitButton.setOnClickListener {
            if (validateAllFields()) {
                // All fields valid, proceed with form submission
                sendOTP()
            }
        }
    }

    private fun validateOwnerNumber(number: String): Boolean {
        return when {
            number.isEmpty() -> {
                binding.numberOfOwner.error = "Owner number is required"
                false
            }

            !number.matches(Regex("^[0-9]{10}$")) -> {
                binding.numberOfOwner.error = "Please enter a valid 10-digit number"
                false
            }

            else -> {
                binding.numberOfOwner.error = null
                true
            }
        }
    }

    private fun validateName(name: String): Boolean {
        return when {
            name.isEmpty() -> {
                binding.nameOfOwner.error = "Name is required"
                false
            }

            name.length < 2 -> {
                binding.nameOfOwner.error = "Name must be at least 2 characters"
                false
            }

            else -> {
                binding.nameOfOwner.error = null
                true
            }
        }
    }

    private fun validateAddress(address: String): Boolean {
        return when {
            address.isEmpty() -> {
                binding.shopAddress.error = "Address is required"
                false
            }

            address.length < 5 -> {
                binding.shopAddress.error = "Please enter a complete address"
                false
            }

            else -> {
                binding.shopAddress.error = null
                true
            }
        }
    }

    private fun validateGSTNumber(gstNumber: String): Boolean {
        // If GST checkbox is not checked, no validation needed
        if (!binding.hasGstCheckBox.isChecked) {
            return true
        }

        // If GST checkbox is checked, field is required
        if (gstNumber.isEmpty()) {
            binding.gstNumber.error = "GST number is required"
            return false
        }

        // GST format validation (Indian GST)
        // Format: 2 digits (state code) + 10 chars (PAN) + 1 char (entity) + 1 char (Z by default) + 1 char (checksum)
        val gstPattern = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")

        if (!gstNumber.matches(gstPattern)) {
            binding.gstNumber.error = "Invalid GST format"
            return false
        }

        binding.gstNumber.error = null

        return true
    }

    private fun validateAllFields(): Boolean {
        val nameValid = validateName(binding.nameOfOwnerText.text.toString())
        val numberValid = validateOwnerNumber(binding.numberOfOwnerText.text.toString())
        val addressValid = validateAddress(binding.shopAddressText.text.toString())
        val gstValid = validateGSTNumber(binding.gstNumberText.text.toString())

        return nameValid && numberValid && addressValid && gstValid
    }

    // Send OTP to user's phone number
    private fun sendOTP() {
        // Show loading indicator
        binding.submitButton.isEnabled = false
        binding.submitButton.text = "Sending OTP..."

        // Get phone number with country code (assuming India +91)
        val phoneNumber = "+91" + binding.numberOfOwnerText.text.toString()

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

                binding.submitButton.isEnabled = true
                binding.submitButton.text = "Submit"

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
        // Prepare data for OTP screen
        val ownerData = getOwnerData()

        // Navigate to OTP verification fragment with necessary data
        val action = GetDetailsFragmentDirections.actionGetDetailsFragmentToOtpVarificationFragment(
            phoneNumber = phoneNumber,
            verificationId = verificationId,
            name = ownerData.name,
            shopName = binding.shopName.editText?.text.toString(),
            address = ownerData.address,
            gstNumber = ownerData.gstNumber ?: ""
        )

        findNavController().navigate(action)

        // Reset button state
        binding.submitButton.isEnabled = true
        binding.submitButton.text = "Submit"
    }

    // Get user data from form
    private fun getOwnerData(): OwnerData {
        return OwnerData(
            ownerNumber = binding.numberOfOwnerText.text.toString(),
            name = binding.nameOfOwnerText.text.toString(),
            address = binding.shopAddressText.text.toString(),
            hasGST = binding.hasGstCheckBox.isChecked,
            gstNumber = if (binding.hasGstCheckBox.isChecked) binding.gstNumberText.text.toString() else null
        )
    }

    // Data class to hold form information
    data class OwnerData(
        val ownerNumber: String,
        val name: String,
        val address: String,
        val hasGST: Boolean,
        val gstNumber: String?
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}