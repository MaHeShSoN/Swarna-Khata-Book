package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.JewelleryAdapter
import com.jewelrypos.swarnakhatabook.BottomSheet.ItemBottomSheetFragment
import com.jewelrypos.swarnakhatabook.DataClasses.JewelleryItem
import com.jewelrypos.swarnakhatabook.Factorys.InventoryViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InventoryRepository

import com.jewelrypos.swarnakhatabook.ViewModle.InventoryViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentInventoryBinding

class InventoryFragment : Fragment(), ItemBottomSheetFragment.OnItemAddedListener {




    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    private val inventoryViewModel: InventoryViewModel by viewModels {
        // Create the repository and pass it to the factory
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InventoryRepository(firestore, auth)
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        InventoryViewModelFactory(repository,connectivityManager)
    }
    private lateinit var adapter: JewelleryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentInventoryBinding.inflate(inflater,container,false)

        binding.addItemFab.setOnClickListener {
            addItemButton()
        }



        setUpRecyclerView()
        setUpObserver()
        setupSwipeRefresh()
        // Inflate the layout for this fragment
        return binding.root
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            inventoryViewModel.refreshData() // Call a refresh function in your ViewModel
        }
    }
    private fun setUpObserver() {

        inventoryViewModel.jewelleryItems.observe(viewLifecycleOwner) { items ->
            binding.swipeRefreshLayout.isRefreshing = false // Stop refreshing
            adapter.updateList(items)
        }
        inventoryViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (errorMessage != null) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
            binding.swipeRefreshLayout.isRefreshing = false // Stop refreshing
        }
        inventoryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }



    private fun setUpRecyclerView() {

        val recyclerView: RecyclerView = binding.recyclerViewInventory
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = JewelleryAdapter(emptyList()) // Initialize with an empty list
        recyclerView.adapter = adapter


        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Load more when user is near the end of the list
                if (!inventoryViewModel.isLoading.value!! &&
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 12 && // Load earlier
                    firstVisibleItemPosition >= 0) {
                    inventoryViewModel.loadNextPage()
                }

            }
        })

    }

    private fun addItemButton() {
        //make a bottom sheet that appear and show inputs
        showBottomSheetDialog()
    }

    override fun onItemAdded(item: JewelleryItem) {
        inventoryViewModel.addJewelleryItem(item)
    }

    private fun showBottomSheetDialog() {
        val bottomSheetFragment = ItemBottomSheetFragment.newInstance()
        bottomSheetFragment.setOnItemAddedListener(this)
        bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
    }


}