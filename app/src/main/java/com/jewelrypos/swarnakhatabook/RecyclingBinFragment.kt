package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.RecycledItemsAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.RecycledItem
import com.jewelrypos.swarnakhatabook.Factorys.RecyclingBinViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.RecyclingBinViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentRecyclingBinBinding

class RecyclingBinFragment : Fragment() {

    private var _binding: FragmentRecyclingBinBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecyclingBinViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        RecyclingBinViewModelFactory(firestore, auth, connectivityManager)
    }

    private lateinit var adapter: RecycledItemsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecyclingBinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupTabLayout()
        setupSwipeRefresh()
        setupObservers()

        // Initially load all recycled items
        viewModel.loadRecycledItems()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_empty_bin -> {
                    showEmptyBinConfirmation()
                    true
                }
                R.id.action_help -> {
//                    showHelpDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = RecycledItemsAdapter(viewModel)
        adapter.setOnItemActionListener(object : RecycledItemsAdapter.OnItemActionListener {
            override fun onRestoreItem(item: RecycledItem) {
                showRestoreConfirmation(item)
            }

            override fun onDeleteItem(item: RecycledItem) {
                showDeleteConfirmation(item)
            }
        })

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> viewModel.loadRecycledItems() // All items
                    1 -> viewModel.loadRecycledInvoices() // Only invoices
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            val selectedTabPosition = binding.tabLayout.selectedTabPosition
            when (selectedTabPosition) {
                0 -> viewModel.loadRecycledItems() // All items
                1 -> viewModel.loadRecycledInvoices() // Only invoices
                else -> viewModel.loadRecycledItems()
            }
        }
    }

    private fun setupObservers() {
        viewModel.recycledItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.swipeRefreshLayout.isRefreshing = false

            // Update empty state visibility
            binding.emptyStateView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                // Add logging before showing the toast
                Log.e("RecyclingBinFragment", "Error: $message")
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.restoreSuccess.observe(viewLifecycleOwner) { (success, message) ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRestoreConfirmation(item: RecycledItem) {
        ThemedM3Dialog(requireContext())
            .setTitle("Restore Item")
            .setLayout(R.layout.dialog_confirmation) // Use the existing confirmation dialog layout
            .apply {
                // Find and set the message text in the custom layout
                findViewById<TextView>(R.id.confirmationMessage)?.text =
                    "Are you sure you want to restore this item? It will be moved back to your active items."
            }
            .setPositiveButton("Restore") { dialog, _ ->
                viewModel.restoreInvoice(item.itemId)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog ->
                dialog.dismiss()
            }
            .show()
    }
    private fun showDeleteConfirmation(item: RecycledItem) {
        ThemedM3Dialog(requireContext())
            .setTitle("Delete Permanently")
            .setLayout(R.layout.dialog_confirmation)
            .apply {
                findViewById<TextView>(R.id.confirmationMessage)?.text =
                    "This item will be permanently deleted and cannot be recovered. Are you sure?"
            }
            .setPositiveButton("Delete") { dialog, _ ->
                viewModel.permanentlyDeleteItem(item.itemId)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showEmptyBinConfirmation() {
        ThemedM3Dialog(requireContext())
            .setTitle("Empty Recycling Bin")
            .setLayout(R.layout.dialog_confirmation)
            .apply {
                findViewById<TextView>(R.id.confirmationMessage)?.text =
                    "All items in the recycling bin will be permanently deleted and cannot be recovered. Are you sure?"
            }
            .setPositiveButton("Empty Bin") { dialog, _ ->
                // Implement empty bin functionality
                Toast.makeText(
                    requireContext(),
                    "Empty bin functionality will be implemented later",
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog ->
                dialog.dismiss()
            }
            .show()
    }

//    private fun showHelpDialog() {
//        ThemedM3Dialog(requireContext())
//            .setLayout(R.layout.dialog_information) // You might need a different layout for longer text
//            .setTitle("Recycling Bin Help")
//            .apply {
//                findViewById<TextView>(R.id.informationMessage)?.text =
//                    "Items you delete are moved to the Recycling Bin for 30 days before being permanently deleted.\n\n" +
//                            "• You can restore items by tapping the restore icon\n" +
//                            "• You can permanently delete items by tapping the delete icon\n" +
//                            "• Items are automatically removed after 30 days\n\n" +
//                            "Currently, only invoices are supported in the Recycling Bin."
//            }
//            .setPositiveButton("OK") { dialog, _ ->
//                dialog.dismiss()
//            }
//            .show()
//    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}