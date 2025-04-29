package com.jewelrypos.swarnakhatabook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.jewelrypos.swarnakhatabook.DataClasses.UserProfile
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.AnimationUtils
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentOtpVarificationBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class OtpVarificationFragment : Fragment() {

    private var _binding: FragmentOtpVarificationBinding? = null
    private val binding get() = _binding!!

    private val args: OtpVarificationFragmentArgs by navArgs()
    private lateinit var auth: FirebaseAuth
    private val TAG = "OtpVerificationFragment"

    // List to store all OTP digit inputs for easier access
    private lateinit var otpDigits: List<EditText>

    // Flag to prevent multiple verification attempts
    private var isVerifying = false

    // SMS retriever
    private val smsVerificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                val extras = intent.extras
                val status = extras?.get(SmsRetriever.EXTRA_STATUS) as Status

                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        // Get SMS message content
                        val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: ""
                        // Extract the OTP from message
                        extractOtpFromMessage(message)
                    }
                    CommonStatusCodes.TIMEOUT -> {
                        // Waiting for SMS timed out
                        Log.d(TAG, "SMS retriever timed out")
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOtpVarificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply entrance animations
        AnimationUtils.fadeIn(binding.layoutOtpInputs)
        AnimationUtils.fadeIn(binding.tvPhoneNumber, 400)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize ShopManager if not already initialized
        ShopManager.initialize(requireContext())

        // Initialize SessionManager
        SessionManager.initialize(requireContext())

        // Display the phone number
        binding.tvPhoneNumber.text = args.phoneNumber

        // Setup OTP digit inputs
        setupOtpInputs()

        // Setup Verify button
        binding.btnVerify.setOnClickListener {
            // Apply button animation
            AnimationUtils.pulse(it)
            if (!isVerifying) {
                verifyOtp()
            }
        }

        // Setup Resend button
        binding.tvResend.setOnClickListener {
            // Apply button animation
            AnimationUtils.pulse(it)
            resendOtp()
        }

        // Start SMS retriever
        startSmsRetriever()
    }

    // In onStart() method
    override fun onStart() {
        super.onStart()
        // Register the SMS receiver
        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(
                smsVerificationReceiver,
                intentFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireContext().registerReceiver(smsVerificationReceiver, intentFilter)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unregister the receiver to avoid memory leaks
        try {
            requireContext().unregisterReceiver(smsVerificationReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Receiver not registered: ${e.message}")
        }
    }

    private fun setupOtpInputs() {
        // Get all the EditText fields in a list for easier access
        otpDigits = listOf(
            binding.etDigit,
            binding.etDigit2,
            binding.etDigit3,
            binding.etDigit4,
            binding.etDigit5,
            binding.etDigit6
        )

        // Add automatic focus change when a digit is entered
        for (i in otpDigits.indices) {
            otpDigits[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Move focus to next EditText when a digit is entered
                    if (s?.length == 1 && i < otpDigits.size - 1) {
                        otpDigits[i + 1].requestFocus()
                    }

                    // Auto-verify when all digits are filled
                    if (isOtpComplete() && !isVerifying) {
                        verifyOtp()
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            // Add backspace handling to move to previous digit
            otpDigits[i].setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                    otpDigits[i].text.isEmpty() &&
                    i > 0) {
                    // Move to previous EditText on backspace when current is empty
                    otpDigits[i - 1].requestFocus()
                    otpDigits[i - 1].text.clear()
                    return@setOnKeyListener true
                }
                false
            }
        }
    }

    private fun startSmsRetriever() {
        val client = SmsRetriever.getClient(requireContext())
        val task = client.startSmsRetriever()

        task.addOnSuccessListener {
            // SMS retriever started successfully
            Log.d(TAG, "SMS retriever started")
        }

        task.addOnFailureListener {
            // Failed to start SMS retriever
            Log.e(TAG, "Failed to start SMS retriever: ${it.message}")
        }
    }

    private fun extractOtpFromMessage(message: String) {
        // Use a regex pattern to find a 6-digit number in the message
        val pattern = Pattern.compile("(\\d{6})")
        val matcher = pattern.matcher(message)

        if (matcher.find()) {
            val otp = matcher.group(0)
            Log.d(TAG, "OTP extracted: $otp")

            // Fill the OTP fields
            fillOtpFields(otp)

            // Verify OTP automatically if not already verifying
            if (!isVerifying) {
                verifyOtp()
            }
        }
    }

    private fun fillOtpFields(otp: String) {
        if (otp.length == 6) {
            for (i in otp.indices) {
                otpDigits[i].setText(otp[i].toString())
            }
        }
    }

    private fun isOtpComplete(): Boolean {
        return otpDigits.all { it.text.isNotEmpty() }
    }

    private fun getEnteredOtp(): String {
        val otpBuilder = StringBuilder()
        for (digit in otpDigits) {
            otpBuilder.append(digit.text)
        }
        return otpBuilder.toString()
    }

    private fun verifyOtp() {
        // Get the OTP from input fields
        val otp = getEnteredOtp()
        
        if (otp.length != 6) {
            Toast.makeText(requireContext(), "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Prevent multiple verification attempts
        isVerifying = true
        
        // Show loading state
        setLoading(true)
        
        try {
            // Create credential with verification ID from args and entered OTP
            val credential = PhoneAuthProvider.getCredential(args.verificationId, otp)
            
            // Sign in with credential
            signInWithPhoneAuthCredential(credential)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying OTP", e)
            setLoading(false)
            isVerifying = false
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resendOtp() {
        Toast.makeText(requireContext(), "Resending OTP...", Toast.LENGTH_SHORT).show()
        binding.tvResend.isEnabled = false
        
        // Get phone number from args
        val phoneNumber = args.phoneNumber
        
        // Setup callbacks
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted:$credential")
                if (!isVerifying) {
                    isVerifying = true
                    signInWithPhoneAuthCredential(credential)
                }
            }
            
            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "onVerificationFailed", e)
                Toast.makeText(requireContext(), "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.tvResend.isEnabled = true
            }
            
            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "onCodeSent:$verificationId")
                Toast.makeText(requireContext(), "OTP resent successfully", Toast.LENGTH_SHORT).show()
                binding.tvResend.isEnabled = true
                
                // Start SMS retriever to auto-fill the new OTP
                startSmsRetriever()
            }
        }
        
        // Configure and start phone verification
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(requireActivity())
            .setCallbacks(callbacks)
            .build()
            
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success
                    Log.d(TAG, "signInWithCredential:success")

                    // Get the current user
                    val user = auth.currentUser

                    if (user != null) {
                        // Create a user profile in Firestore if needed
                        saveUserProfile(user.uid)
                    } else {
                        setLoading(false)
                        isVerifying = false
                        Toast.makeText(requireContext(), "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Sign in failed
                    setLoading(false)
                    isVerifying = false
                    Log.w(TAG, "signInWithCredential:failure", task.exception)

                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        // The verification code was invalid
                        Toast.makeText(requireContext(), "Invalid verification code", Toast.LENGTH_SHORT).show()
                        // Clear OTP fields and apply shake animation
                        clearOtpFields()
                        AnimationUtils.shake(binding.layoutOtpInputs)
                    } else {
                        Toast.makeText(requireContext(), "Authentication failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun clearOtpFields() {
        for (digit in otpDigits) {
            digit.text.clear()
        }
        // Set focus to first digit
        otpDigits[0].requestFocus()
    }

    private fun saveUserProfile(userId: String) {
        lifecycleScope.launch {
            try {
                // Try to get existing user profile first
                val userProfileResult = ShopManager.getUserProfile(userId)
                val existingProfile = userProfileResult.getOrNull()
                
                // Create or update UserProfile
                val userProfile = if (existingProfile != null) {
                    // Update existing profile
                    existingProfile.copy(
                        name = args.name.takeIf { it.isNotEmpty() } ?: existingProfile.name,
                        phoneNumber = args.phoneNumber
                    )
                } else {
                    // Create new profile
                    UserProfile(
                        userId = userId,
                        name = args.name.takeIf { it.isNotEmpty() } ?: "User",
                        phoneNumber = args.phoneNumber,
                        createdAt = Timestamp.now()
                    )
                }

                // Save UserProfile to Firestore
                val result = ShopManager.saveUserProfile(userProfile)
                
                if (result.isSuccess) {
                    // Create login session with phone number
                    SessionManager.createLoginSession(requireContext(), args.phoneNumber)
                    
                    // Set current user ID in SessionManager (for future use)
                    SessionManager.getCurrentUserId()
                    
                    // Navigate to shop selection
                    navigateToShopSelection()
                } else {
                    setLoading(false)
                    isVerifying = false
                    throw result.exceptionOrNull() ?: Exception("Failed to save user profile")
                }
            } catch (e: Exception) {
                setLoading(false)
                isVerifying = false
                Log.e(TAG, "Error saving user profile", e)
                Toast.makeText(
                    requireContext(),
                    "Failed to save user profile: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun navigateToShopSelection() {
        // Navigate to shop selection with animation
        findNavController().navigate(
            R.id.action_otpVarificationFragment_to_shopSelectionFragment,
            null,
            AnimationUtils.getSlideNavOptions()
        )
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnVerify.isEnabled = !isLoading
        binding.tvResend.isEnabled = !isLoading
        binding.btnVerify.text = if (isLoading) "Verifying..." else "Verify"
        
        // Disable OTP input fields during loading
        otpDigits.forEach { it.isEnabled = !isLoading }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}