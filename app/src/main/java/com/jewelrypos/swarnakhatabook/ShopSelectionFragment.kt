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
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentShopSelectionBinding
import kotlinx.coroutines.launch
import java.lang.Exception

class ShopSelectionFragment : Fragment() {

    private var _binding: FragmentShopSelectionBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: ShopSelectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShopSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        loadShops()
    }
    
    private fun setupRecyclerView() {
        adapter = ShopSelectionAdapter { shopId ->
            selectShop(shopId)
        }
        
        binding.recyclerViewShops.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ShopSelectionFragment.adapter
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonCreateNewShop.setOnClickListener {
            findNavController().navigate(R.id.action_shopSelectionFragment_to_createShopFragment)
        }
    }
    
    private fun loadShops() {
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewEmptyState.visibility = View.GONE
        
        val userId = SessionManager.getCurrentUserId() ?: return
        
        lifecycleScope.launch {
            try {
                val managedShopsResult = ShopManager.getManagedShops(userId)
                
                if (!managedShopsResult.isSuccess) {
                    throw managedShopsResult.exceptionOrNull() ?: Exception("Failed to get shops")
                }
                
                val managedShops = managedShopsResult.getOrNull() ?: emptyMap()
                
                if (managedShops.isEmpty()) {
                    showEmptyState()
                    return@launch
                }
                
                val shopDetailsList = mutableListOf<ShopDetails>()
                
                for (shopId in managedShops.keys) {
                    val shopDetailsResult = ShopManager.getShopDetails(shopId)
                    
                    if (shopDetailsResult.isSuccess) {
                        val shopDetails = shopDetailsResult.getOrNull()
                        if (shopDetails != null) {
                            shopDetailsList.add(shopDetails)
                        }
                    }
                }
                
                if (shopDetailsList.isEmpty()) {
                    showEmptyState()
                } else {
                    adapter.submitList(shopDetailsList)
                    binding.progressBar.visibility = View.GONE
                    binding.textViewEmptyState.visibility = View.GONE
                }
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading shops: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                showEmptyState()
            }
        }
    }
    
    private fun showEmptyState() {
        binding.progressBar.visibility = View.GONE
        binding.textViewEmptyState.visibility = View.VISIBLE
    }
    
    private fun selectShop(shopId: String) {
        context?.let { ctx ->
            SessionManager.setActiveShopId(ctx, shopId)
            findNavController().navigate(R.id.action_shopSelectionFragment_to_mainScreenFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 