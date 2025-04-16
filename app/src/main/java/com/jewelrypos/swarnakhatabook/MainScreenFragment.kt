package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.jewelrypos.swarnakhatabook.Repository.ShopManager
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.databinding.FragmentMainScreenBinding
import kotlinx.coroutines.launch

class MainScreenFragment : Fragment() {

    private var _binding: FragmentMainScreenBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupInnerNavigation()
        loadActiveShopDetails()
        
        // Observe changes to active shop
        SessionManager.activeShopIdLiveData.observe(viewLifecycleOwner) { shopId ->
            if (shopId != null) {
                loadActiveShopDetails()
            }
        }
    }
    
    private fun setupInnerNavigation() {
        val innerNavController = childFragmentManager.findFragmentById(R.id.inner_nav_host_fragment)
            ?.findNavController()

        if (innerNavController != null) {
            // Set up the bottom bar with the nav controller
            binding.bottomNavigation.onItemSelected = { position ->
                when (position) {
                    0 -> innerNavController.navigate(R.id.dashBoardFragment)
                    1 -> innerNavController.navigate(R.id.salesFragment)
                    2 -> innerNavController.navigate(R.id.inventoryFragment)
                    3 -> innerNavController.navigate(R.id.customerFragment)
                    4 -> innerNavController.navigate(R.id.moreSettingFragment)
                }
            }

            innerNavController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.dashBoardFragment -> binding.bottomNavigation.itemActiveIndex = 0
                    R.id.salesFragment -> binding.bottomNavigation.itemActiveIndex = 1
                    R.id.inventoryFragment -> binding.bottomNavigation.itemActiveIndex = 2
                    R.id.customerFragment -> binding.bottomNavigation.itemActiveIndex = 3
                    R.id.moreSettingFragment -> binding.bottomNavigation.itemActiveIndex = 4
                }
            }
        }
    }
    
    private fun loadActiveShopDetails() {
        context?.let { ctx ->
            val activeShopId = SessionManager.getActiveShopId(ctx)
            
            if (activeShopId != null) {
                lifecycleScope.launch {
                    val shopDetailsResult = ShopManager.getShopDetails(activeShopId)
                    
                    if (shopDetailsResult.isSuccess) {
                        val shopDetails = shopDetailsResult.getOrNull()
                        if (shopDetails != null) {
                            // Set the shop name in the activity title
                            (requireActivity() as AppCompatActivity).supportActionBar?.title = shopDetails.shopName
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}