package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.Timestamp
import com.jewelrypos.swarnakhatabook.DataClasses.ShopDetails
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentCreateShopBinding
import kotlinx.coroutines.launch

class CreateShopFragment : Fragment() {

    private var _binding: FragmentCreateShopBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateShopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize ShopManager and SessionManager if not already initialized
        context?.let {
            ShopManager.initialize(it)
            SessionManager.initialize(it)
        }
        
        setupListeners()
    }
    
    private fun setupListeners() {
        // Show/hide GST field based on checkbox
        binding.hasGstCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.gstNumber.visibility = if (isChecked) View.VISIBLE else View.GONE
            
            // Clear GST field when hiding it
            if (!isChecked) {
                binding.editTextGstNumber.text?.clear()
            }
        }
        
        binding.buttonCreate.setOnClickListener {
            if (validateInputs()) {
                createShop()
            }
        }
        
        binding.buttonCancel.setOnClickListener {
            findNavController().navigateUp()
        }
    }
    
    private fun validateInputs(): Boolean {
        var isValid = true
        
        // Validate shop name
        val shopName = binding.editTextShopName.text.toString().trim()
        if (shopName.isEmpty()) {
            binding.shopName.error = "Shop name is required"
            isValid = false
        } else {
            binding.shopName.error = null
        }
        
        // Validate address
        val address = binding.editTextAddress.text.toString().trim()
        if (address.isEmpty()) {
            binding.shopAddress.error = "Address is required"
            isValid = false
        } else {
            binding.shopAddress.error = null
        }
        
        // Validate GST number if GST is enabled
        if (binding.hasGstCheckBox.isChecked) {
            val gstNumber = binding.editTextGstNumber.text.toString().trim()
            
            if (gstNumber.isEmpty()) {
                binding.gstNumber.error = "GST number is required"
                isValid = false
            } else if (!gstNumber.matches(Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$"))) {
                binding.gstNumber.error = "Invalid GST format"
                isValid = false
            } else {
                binding.gstNumber.error = null
            }
        }
        
        return isValid
    }
    
    private fun createShop() {
        val userId = SessionManager.getCurrentUserId()
        if (userId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get phone number from SessionManager
        val phoneNumber = context?.let { SessionManager.getPhoneNumber(it) }
        if (phoneNumber == null) {
            Toast.makeText(requireContext(), "Phone number not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        android.util.Log.d("CreateShopFragment", "Creating shop for user ID: $userId, phone: $phoneNumber")
        
        // Show loading
        setLoading(true)
        
        // Create shop details object
        val shopDetails = ShopDetails(
            shopName = binding.editTextShopName.text.toString().trim(),
            address = binding.editTextAddress.text.toString().trim(),
            hasGst = binding.hasGstCheckBox.isChecked,
            gstNumber = if (binding.hasGstCheckBox.isChecked) binding.editTextGstNumber.text.toString().trim() else null,
            createdAt = Timestamp.now()
        )
        
        // Create shop in Firestore
        lifecycleScope.launch {
            try {
                // Pass both userId and phoneNumber to ensure proper association
                val result = ShopManager.createShop(userId, phoneNumber, shopDetails)
                
                if (result.isSuccess) {
                    val shopId = result.getOrNull()
                    if (shopId != null) {
                        // Set active shop
                        context?.let {
                            SessionManager.setActiveShopId(it, shopId)
                        }
                        
                        android.util.Log.d("CreateShopFragment", "Shop created successfully with ID: $shopId")
                        Toast.makeText(requireContext(), "Shop created successfully", Toast.LENGTH_SHORT).show()
                        
                        // Navigate to main screen with popUpTo to clear the back stack
                        findNavController().navigate(
                            R.id.action_createShopFragment_to_mainScreenFragment,
                            null,
                            null
                        )
                    } else {
                        throw Exception("Failed to get shop ID")
                    }
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            } catch (e: Exception) {
                android.util.Log.e("CreateShopFragment", "Failed to create shop: ${e.message}", e)
                Toast.makeText(requireContext(), "Failed to create shop: ${e.message}", Toast.LENGTH_SHORT).show()
                setLoading(false)
            }
        }
    }
    
    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonCreate.isEnabled = !isLoading
        binding.buttonCancel.isEnabled = !isLoading
        binding.editTextShopName.isEnabled = !isLoading
        binding.editTextAddress.isEnabled = !isLoading
        binding.editTextGstNumber.isEnabled = !isLoading
        binding.hasGstCheckBox.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}