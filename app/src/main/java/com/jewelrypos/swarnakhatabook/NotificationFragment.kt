package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.NotificationAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Factorys.NotificationViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentNotificationBinding

class NotificationFragment : Fragment(), NotificationAdapter.OnNotificationActionListener {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter
    private val TAG = "NotificationFragment"

    private val viewModel: NotificationViewModel by viewModels {
        val repository = NotificationRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        NotificationViewModelFactory(repository, connectivityManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupEmptyState()

        // Observe ViewModel data
        observeNotifications()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    binding.swipeRefreshLayout.isRefreshing = true
                    viewModel.loadNotifications()
                    true
                }
                R.id.action_settings -> {
                    try {
                        findNavController().navigate(R.id.action_notificationFragment_to_notificationSettingsFragment)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating to settings", e)
                        Toast.makeText(requireContext(), "Settings screen not available", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(emptyList())
        adapter.setOnNotificationActionListener(this)

        binding.notificationRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NotificationFragment.adapter

            // Pagination support
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    // Load more when near the end
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= 20
                    ) {
                        viewModel.loadNextPage()
                    }
                }
            })
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadNotifications()
        }
    }

    private fun setupEmptyState() {
        binding.checkNotificationsButton.setOnClickListener {
            binding.swipeRefreshLayout.isRefreshing = true
            viewModel.loadNotifications()
        }
    }

    private fun observeNotifications() {
        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            adapter.updateNotifications(notifications)
            binding.swipeRefreshLayout.isRefreshing = false

            // Show/hide empty state
            if (notifications.isEmpty()) {
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.notificationRecyclerView.visibility = View.GONE
            } else {
                binding.emptyStateLayout.visibility = View.GONE
                binding.notificationRecyclerView.visibility = View.VISIBLE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }

        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            // Update badge count in app (if applicable)
            // This would be implemented in the main activity
        }
    }

    override fun onNotificationClick(notification: AppNotification) {
        // Mark as read
        viewModel.markAsRead(notification.id)

        // Navigate to appropriate screen based on notification type and related IDs
        try {
            when (notification.type) {
                NotificationType.CREDIT_LIMIT -> {
                    navigateToCustomerDetails(notification.customerId)
                }
                NotificationType.PAYMENT_DUE,
                NotificationType.PAYMENT_OVERDUE -> {
                    // If we have a specific invoice ID, go to that invoice
                    if (notification.relatedInvoiceId != null) {
                        navigateToInvoiceDetail(notification.relatedInvoiceId)
                    } else {
                        // Otherwise go to payment screen for this customer
                        navigateToPaymentScreen(notification.customerId)
                    }
                }
                NotificationType.GENERAL -> {
                    // Check if this is a stock notification
                    if (notification.relatedItemId != null) {
                        navigateToItemDetail(notification.relatedItemId)
                    }
                    // For other general notifications, no specific navigation
                }
                else -> {
                    // Handle other notification types
                    if (notification.customerId.isNotEmpty()) {
                        navigateToCustomerDetails(notification.customerId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating from notification", e)
            Toast.makeText(requireContext(), "Could not navigate: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActionButtonClick(notification: AppNotification) {
        // Mark as read and that action was taken
        viewModel.markAsRead(notification.id)
        viewModel.markActionTaken(notification.id)

        // Navigate based on action button and type
        try {
            when (notification.type) {
                NotificationType.CREDIT_LIMIT -> {
                    navigateToCustomerDetails(notification.customerId)
                }
                NotificationType.PAYMENT_DUE,
                NotificationType.PAYMENT_OVERDUE -> {
                    if (notification.relatedInvoiceId != null) {
                        navigateToInvoiceDetail(notification.relatedInvoiceId)
                    } else {
                        navigateToPaymentScreen(notification.customerId)
                    }
                }
                NotificationType.GENERAL -> {
                    if (notification.relatedItemId != null) {
                        navigateToItemDetail(notification.relatedItemId)
                    }
                }
                NotificationType.BIRTHDAY,
                NotificationType.ANNIVERSARY -> {
                    navigateToCustomerDetails(notification.customerId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating from action button", e)
            Toast.makeText(requireContext(), "Could not navigate: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDismissButtonClick(notification: AppNotification) {
        // Mark as read
        viewModel.markAsRead(notification.id)
        // Optionally delete the notification
        // viewModel.deleteNotification(notification.id)
    }

    private fun navigateToCustomerDetails(customerId: String) {
        try {
            val action = NotificationFragmentDirections
                .actionNotificationFragmentToMainScreenFragment()
            findNavController().navigate(action)

            Handler(Looper.getMainLooper()).postDelayed({
                val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
                val bundle = Bundle().apply {
                    putString("customerId", customerId)
                }
                mainNavController.navigate(
                    R.id.action_mainScreenFragment_to_customerDetailFragment,
                    bundle
                )
            }, 300) // Small delay to ensure navigation completes
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to customer details", e)
            Toast.makeText(
                requireContext(),
                "Error navigating to customer details: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun navigateToInvoiceDetail(invoiceId: String) {
        try {
            val action = NotificationFragmentDirections
                .actionNotificationFragmentToMainScreenFragment()
            findNavController().navigate(action)

            Handler(Looper.getMainLooper()).postDelayed({
                val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
                val bundle = Bundle().apply {
                    putString("invoiceId", invoiceId)
                }
                mainNavController.navigate(
                    R.id.action_mainScreenFragment_to_invoiceDetailFragment,
                    bundle
                )
            }, 300)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to invoice detail", e)
            Toast.makeText(
                requireContext(),
                "Error navigating to invoice details: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun navigateToItemDetail(itemId: String) {
        try {
            val action = NotificationFragmentDirections
                .actionNotificationFragmentToMainScreenFragment()
            findNavController().navigate(action)

            Handler(Looper.getMainLooper()).postDelayed({
                val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
                val bundle = Bundle().apply {
                    putString("itemId", itemId)
                }
                mainNavController.navigate(
                    R.id.action_mainScreenFragment_to_itemDetailFragment,
                    bundle
                )
            }, 300)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to item detail", e)
            Toast.makeText(
                requireContext(),
                "Error navigating to item details: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun navigateToPaymentScreen(customerId: String) {
        try {
            val action = NotificationFragmentDirections
                .actionNotificationFragmentToMainScreenFragment()
            findNavController().navigate(action)

            Handler(Looper.getMainLooper()).postDelayed({
                val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
                val bundle = Bundle().apply {
                    putString("customerId", customerId)
                }
                mainNavController.navigate(
                    R.id.action_mainScreenFragment_to_paymentsFragment,
                    bundle
                )
            }, 300)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to payment screen", e)
            Toast.makeText(
                requireContext(),
                "Error navigating to payment screen: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}