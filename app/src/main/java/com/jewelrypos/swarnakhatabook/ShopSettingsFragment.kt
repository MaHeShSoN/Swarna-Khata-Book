package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.DataClasses.Shop
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentShopSettingsBinding
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShopSettingsFragment : Fragment() {

    private var _binding: FragmentShopSettingsBinding? = null
    private val binding get() = _binding!!

    private var shop: Shop? = null

    private var logoUri: Uri? = null
    private var signatureUri: Uri? = null

    private val getLogoContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            logoUri = it
            binding.logoImageView.setImageURI(it)
            binding.logoImageView.visibility = View.VISIBLE

            // Save logo URI to shop
            updateShopLogo(it)
        }
    }

    private val getSignatureContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            signatureUri = it
            binding.signatureImageView.setImageURI(it)
            binding.signatureImageView.visibility = View.VISIBLE

            // Save signature URI to shop
            updateShopSignature(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShopSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ShopManager
        ShopManager.initialize(requireContext())

        // Setup toolbar
        setupToolbar()

        // Load shop details
        loadShopDetails()

        // Setup buttons
        setupButtons()
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = binding.topAppBar
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save -> {
                    saveShopDetails()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadShopDetails() {
        binding.progressBar.visibility = View.VISIBLE

        // Get shop details from ShopManager
        ShopManager.getShop(requireContext(), forceRefresh = true) { loadedShop ->
            if (loadedShop != null) {
                shop = loadedShop
                updateUI(loadedShop)
            } else {
                // Create a new shop if none exists
                shop = Shop(
                    name = "",
                    phoneNumber = "",
                    shopName = "",
                    address = "",
                    gstNumber = "",
                    hasGst = false,
                    createdAt = Timestamp.now(),
                    email = ""
                )
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun updateUI(shop: Shop) {
        // Populate fields with shop data
        binding.shopNameEditText.setText(shop.shopName)
        binding.addressEditText.setText(shop.address)
        binding.phoneEditText.setText(shop.phoneNumber)
        binding.emailEditText.setText(shop.email)
        binding.gstNumberEditText.setText(shop.gstNumber)

        // Load logo if available
        shop.logo?.let { logoUriString ->
            try {
                logoUri = Uri.parse(logoUriString)
                binding.logoImageView.setImageURI(logoUri)
                binding.logoImageView.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e("ShopSettings", "Error loading logo: ${e.message}")
            }
        }

        // Load signature if available
        shop.signature?.let { signatureUriString ->
            try {
                signatureUri = Uri.parse(signatureUriString)
                binding.signatureImageView.setImageURI(signatureUri)
                binding.signatureImageView.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e("ShopSettings", "Error loading signature: ${e.message}")
            }
        }
    }

    private fun setupButtons() {
        binding.uploadLogoButton.setOnClickListener {
            getLogoContent.launch("image/*")
        }

        binding.uploadSignatureButton.setOnClickListener {
            getSignatureContent.launch("image/*")
        }
    }

    private fun updateShopLogo(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE

        ShopManager.updateShopLogo(uri, requireContext()) { success, error ->
            binding.progressBar.visibility = View.GONE

            if (success) {
                Toast.makeText(context, "Shop logo updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to update logo: ${error?.message}", Toast.LENGTH_SHORT).show()
                Log.e("ShopSettings", "Error updating logo", error)
            }
        }
    }

    private fun updateShopSignature(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE

        ShopManager.updateShopSignature(uri, requireContext()) { success, error ->
            binding.progressBar.visibility = View.GONE

            if (success) {
                Toast.makeText(context, "Shop signature updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to update signature: ${error?.message}", Toast.LENGTH_SHORT).show()
                Log.e("ShopSettings", "Error updating signature", error)
            }
        }
    }

    private fun saveShopDetails() {
        val shopName = binding.shopNameEditText.text.toString().trim()
        val address = binding.addressEditText.text.toString().trim()
        val phone = binding.phoneEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val gstNumber = binding.gstNumberEditText.text.toString().trim()

        // Validate input
        if (shopName.isEmpty()) {
            binding.shopNameInputLayout.error = "Shop name is required"
            return
        }

        if (address.isEmpty()) {
            binding.addressInputLayout.error = "Address is required"
            return
        }

        if (phone.isEmpty()) {
            binding.phoneInputLayout.error = "Phone number is required"
            return
        }

        // Update shop object
        shop?.let { currentShop ->
            val updatedShop = currentShop.copy(
                shopName = shopName,
                address = address,
                phoneNumber = phone,
                email = email,
                gstNumber = gstNumber,
                hasGst = gstNumber.isNotEmpty(),
                logo = logoUri?.toString() ?: currentShop.logo,
                signature = signatureUri?.toString() ?: currentShop.signature
            )

            binding.progressBar.visibility = View.VISIBLE

            // Save updated shop
            ShopManager.saveShop(updatedShop, requireContext()) { success, error ->
                binding.progressBar.visibility = View.GONE

                if (success) {
                    Toast.makeText(context, "Shop details saved successfully", Toast.LENGTH_SHORT).show()
                    shop = updatedShop
                } else {
                    Toast.makeText(context, "Failed to save shop details: ${error?.message}", Toast.LENGTH_SHORT).show()
                    Log.e("ShopSettings", "Error saving shop details", error)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}