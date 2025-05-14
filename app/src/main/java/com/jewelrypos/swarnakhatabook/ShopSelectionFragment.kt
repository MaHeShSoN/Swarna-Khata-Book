package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.ShopSelectionAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.ShopDetails
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.AnimationUtils
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentShopSelectionBinding
import kotlinx.coroutines.launch
import java.lang.Exception
import com.google.firebase.auth.FirebaseAuth

class ShopSelectionFragment : Fragment() {

    private var _binding: FragmentShopSelectionBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ShopSelectionAdapter
    private var currentActiveShopId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShopSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize ShopManager and SessionManager if not already initialized
        context?.let {
            ShopManager.initialize(it)
            SessionManager.initialize(it)
        }
        
        // Get the current active shop ID
        currentActiveShopId = context?.let { SessionManager.getActiveShopId(it) }
        
        setupUI()
        
        // Apply entrance animations after UI setup
        binding.recyclerViewShops.visibility = View.INVISIBLE
        binding.textViewEmptyState.visibility = View.INVISIBLE
        
        // Apply entrance animations with a slight delay to ensure views are properly laid out
        view.post {
            AnimationUtils.fadeIn(binding.buttonCreateNewShop, 200)
        }
        
        loadShops()
    }
    
    private fun setupUI() {
        setupRecyclerView()
        setupClickListeners()
    }
    
    private fun setupRecyclerView() {
        adapter = ShopSelectionAdapter { shopId ->
            selectShop(shopId)
        }
        
        // Set the active shop ID in the adapter
        adapter.setActiveShopId(currentActiveShopId)
        
        binding.recyclerViewShops.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ShopSelectionFragment.adapter
        }
        
        // Apply animations to RecyclerView items
        AnimationUtils.animateRecyclerView(binding.recyclerViewShops)
    }
    
    private fun setupClickListeners() {
        binding.buttonCreateNewShop.setOnClickListener {
            // Apply button animation
            AnimationUtils.pulse(it)
            // Check the shop limit before allowing creation
            checkShopLimitAndNavigate()
        }
    }
    
    private fun checkShopLimitAndNavigate() {
        binding.progressBar.visibility = View.VISIBLE
        
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "You must be logged in to create a shop", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val managedShopsResult = ShopManager.getManagedShops(userId)
                binding.progressBar.visibility = View.GONE
                
                if (managedShopsResult.isSuccess) {
                    val managedShops = managedShopsResult.getOrNull() ?: emptyMap()
                    
                    // Check if the user has reached the shop limit (5 shops)
                    if (managedShops.size >= 5) {
                        Toast.makeText(
                            requireContext(),
                            "You can only create up to 5 shops",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Navigate to create shop screen
                        findNavController().navigate(
                            R.id.action_shopSelectionFragment_to_createShopFragment,
                            null,
                            AnimationUtils.getSlideNavOptions()
                        )
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Error checking shop limit: ${managedShopsResult.exceptionOrNull()?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    requireContext(),
                    "Error checking shop limit: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun loadShops() {
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewEmptyState.visibility = View.GONE
        binding.recyclerViewShops.visibility = View.GONE
        
        // Get the user ID from FirebaseAuth
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        
        if (userId == null) {
            // If no user ID, try to get phone number from SessionManager as fallback
            val phoneNumber = SessionManager.getPhoneNumber(requireContext())
            
            if (phoneNumber == null) {
                showEmptyState("No user found. Please log in again.")
                return
            }
            
            // Try to load shops by phone number
            lifecycleScope.launch {
                loadShopsByPhoneNumber(phoneNumber)
            }
            return
        }
        
        lifecycleScope.launch {
            try {
                // First try to get shops from user's managed shops
                val managedShopsResult = ShopManager.getManagedShops(userId, preferCache = true)
                
                if (managedShopsResult.isSuccess) {
                    val shopsMap = managedShopsResult.getOrNull() ?: emptyMap()
                    
                    if (shopsMap.isNotEmpty()) {
                        // Convert map of shop IDs to list of ShopDetails
                        val shopDetailsList = mutableListOf<ShopDetails>()
                        
                        // For each shop ID in the map, get the shop details
                        for (shopId in shopsMap.keys) {
                            val shopDetailsResult = ShopManager.getShopDetails(shopId)
                            if (shopDetailsResult.isSuccess) {
                                val shopDetails = shopDetailsResult.getOrNull()
                                if (shopDetails != null) {
                                    shopDetailsList.add(shopDetails)
                                }
                            }
                        }
                        
                        if (shopDetailsList.isNotEmpty()) {
                            updateShopList(shopDetailsList)
                            return@launch
                        }
                    }
                }
                
                // If no shops found by user ID, try by phone number as a fallback
                val phoneNumber = SessionManager.getPhoneNumber(requireContext())
                if (phoneNumber != null) {
                    loadShopsByPhoneNumber(phoneNumber)
                } else {
                    // No phone number available, show empty state
                    showEmptyState()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ShopSelectionFragment", "Error loading shops", e)
                showEmptyState("Error loading shops: ${e.message}")
            }
        }
    }
    
    private suspend fun loadShopsByPhoneNumber(phoneNumber: String) {
        try {
            val shopsByPhoneResult = ShopManager.getShopsByPhoneNumber(phoneNumber)
            
            if (shopsByPhoneResult.isSuccess) {
                val shopsList = shopsByPhoneResult.getOrNull() ?: emptyList()
                
                if (shopsList.isNotEmpty()) {
                    updateShopList(shopsList)
                } else {
                    showEmptyState()
                }
            } else {
                showEmptyState("Error loading shops: ${shopsByPhoneResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ShopSelectionFragment", "Error loading shops by phone number", e)
            showEmptyState("Error loading shops: ${e.message}")
        }
    }
    
    private fun updateShopList(shopsList: List<ShopDetails>) {
        // If there's exactly one shop, automatically select it
        if (shopsList.size == 1) {
            val shopId = shopsList[0].shopId
            if (shopId != null) {
                android.util.Log.d("ShopSelectionFragment", "Only one shop found, automatically selecting: $shopId")
                selectShop(shopId)
                return
            }
        }
        
        // Otherwise, show the list
        adapter.submitList(shopsList)
        binding.progressBar.visibility = View.GONE
        binding.textViewEmptyState.visibility = View.GONE
        binding.recyclerViewShops.visibility = View.VISIBLE
        AnimationUtils.fadeIn(binding.recyclerViewShops)
    }
    
    private fun showEmptyState(message: String = "No shops found. Create your first shop!") {
        // If this is the default message (no shops found), navigate to CreateShopFragment
        if (message == "No shops found. Create your first shop!") {
            findNavController().navigate(
                R.id.action_shopSelectionFragment_to_createShopFragment,
                null,
                AnimationUtils.getSlideNavOptions()
            )
            return
        }

        // Otherwise, show the error message
        binding.progressBar.visibility = View.GONE
        binding.recyclerViewShops.visibility = View.GONE
        binding.textViewEmptyState.text = message
        binding.textViewEmptyState.visibility = View.VISIBLE
        AnimationUtils.fadeIn(binding.textViewEmptyState)
    }
    
    private fun selectShop(shopId: String) {
        // Show loading animation
        binding.progressBar.visibility = View.VISIBLE
        AnimationUtils.fadeIn(binding.progressBar)
        
        // Set the selected shop in SessionManager
        context?.let { ctx ->
            // Set active shop ID in SessionManager - this will trigger the LiveData
            // to notify observers (like DashboardFragment) of the change
            SessionManager.setActiveShopId(ctx, shopId)
            
            // Update the adapter to show the active shop
            adapter.setActiveShopId(shopId)
            
            // Log the shop switch for debugging
            android.util.Log.d("ShopSelectionFragment", "Shop switched to: $shopId")
            
            // Navigate back to main screen, which will reload with the new shop
            findNavController().navigate(
                R.id.action_shopSelectionFragment_to_mainScreenFragment,
                null,
                AnimationUtils.getSlideNavOptions()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}