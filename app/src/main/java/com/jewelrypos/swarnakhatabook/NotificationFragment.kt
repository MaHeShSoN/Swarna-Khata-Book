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
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.NotificationAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.AppNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Factorys.NotificationViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationPermissionHelper
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

    // Permission launcher for notification permission
    private val notificationPermissionLauncher =
        NotificationPermissionHelper.createPermissionLauncher(
            this,
            onPermissionResult = { isGranted ->
                if (isGranted) {
                    Toast.makeText(
                        requireContext(),
                        "Notification permission granted",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Notification permission denied. You may miss important alerts.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )

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

        // Check for notification permission on Android 13+
        checkNotificationPermission()

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupEmptyState()

        // Observe ViewModel data
        observeNotifications()
    }

    private fun checkNotificationPermission() {
        if (!NotificationPermissionHelper.hasNotificationPermission(requireContext())) {
            // Request permission using the activity result launcher
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
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
                    navigateToNotificationSettings()
                    true
                }

                else -> false
            }
        }
    }

    private fun navigateToNotificationSettings() {
        try {
            // Check if the destination is valid
            val navController = findNavController()
            val currentDestination = navController.currentDestination
            val action =
                currentDestination?.getAction(R.id.action_notificationFragment_to_notificationSettingsFragment)

            if (action != null) {
                navController.navigate(R.id.action_notificationFragment_to_notificationSettingsFragment)
            } else {
                Log.e(TAG, "Navigation action not found")
                Toast.makeText(
                    requireContext(),
                    "Settings screen not available",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to settings", e)
            Toast.makeText(requireContext(), "Error navigating to settings", Toast.LENGTH_SHORT)
                .show()
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
    }

    override fun onNotificationClick(notification: AppNotification) {
        // Mark as read
        viewModel.markAsRead(notification.id)

        // Navigate to appropriate screen based on notification type and related IDs
        navigateBasedOnNotification(notification, false)
    }

    override fun onActionButtonClick(notification: AppNotification) {
        // Mark as read and that action was taken
        viewModel.markAsRead(notification.id)
        viewModel.markActionTaken(notification.id)

        // Navigate based on notification
        navigateBasedOnNotification(notification, true)
    }

    override fun onDismissButtonClick(notification: AppNotification) {
        // Mark as read
        viewModel.markAsRead(notification.id)

        // Optionally show confirmation before deletion
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Notification")
            .setMessage("Do you want to delete this notification?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.deleteNotification(notification.id)
            }
            .setNegativeButton("No", null)
            .show()
    }

    /**
     * Central method to handle navigation based on notification type
     * @param notification The notification to navigate from
     * @param isActionButton Whether this is triggered from the action button (may affect behavior)
     */
    private fun navigateBasedOnNotification(
        notification: AppNotification,
        isActionButton: Boolean
    ) {
        try {
            when (notification.type) {
                NotificationType.CREDIT_LIMIT -> {
                    if (notification.customerId.isNotEmpty()) {
                        navigateToCustomerDetails(notification.customerId)
                    }
                }

                NotificationType.PAYMENT_DUE,
                NotificationType.PAYMENT_OVERDUE -> {
                    // If we have a specific invoice ID, go to that invoice
                    if (notification.relatedInvoiceId != null) {
                        navigateToInvoiceDetail(notification.relatedInvoiceId)
                    } else if (notification.customerId.isNotEmpty()) {
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

                NotificationType.BIRTHDAY,
                NotificationType.ANNIVERSARY -> {
                    if (notification.customerId.isNotEmpty()) {
                        navigateToCustomerDetails(notification.customerId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating from notification", e)
            Toast.makeText(requireContext(), "Could not navigate: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    /**
     * Safe navigation to customer details
     */
    private fun navigateToCustomerDetails(customerId: String) {
        safeNavigateToMainScreen { mainNavController ->
            val bundle = Bundle().apply {
                putString("customerId", customerId)
            }

            // Check if the action exists
            if (mainNavController.currentDestination?.getAction(R.id.action_mainScreenFragment_to_customerDetailFragment) != null) {
                mainNavController.navigate(
                    R.id.action_mainScreenFragment_to_customerDetailFragment,
                    bundle
                )
            } else {
                Log.e(TAG, "Customer details action not available from current destination")
                Toast.makeText(requireContext(), "Navigation not available", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Safe navigation to invoice details
     */
    private fun navigateToInvoiceDetail(invoiceId: String) {
        safeNavigateToMainScreen { mainNavController ->
            val bundle = Bundle().apply {
                putString("invoiceId", invoiceId)
            }

            // Check if the action exists
            if (mainNavController.currentDestination?.getAction(R.id.action_mainScreenFragment_to_invoiceDetailFragment) != null) {
                mainNavController.navigate(
                    R.id.action_mainScreenFragment_to_invoiceDetailFragment,
                    bundle
                )
            } else {
                Log.e(TAG, "Invoice details action not available from current destination")
                Toast.makeText(requireContext(), "Navigation not available", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Safe navigation to item details
     */
    private fun navigateToItemDetail(itemId: String) {
        safeNavigateToMainScreen { mainNavController ->
            val bundle = Bundle().apply {
                putString("itemId", itemId)
            }

            // Check if the action exists
            if (mainNavController.currentDestination?.getAction(R.id.action_mainScreenFragment_to_itemDetailFragment) != null) {
                mainNavController.navigate(
                    R.id.action_mainScreenFragment_to_itemDetailFragment,
                    bundle
                )
            } else {
                Log.e(TAG, "Item details action not available from current destination")
                Toast.makeText(requireContext(), "Navigation not available", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Safe navigation to payments screen
     */
    private fun navigateToPaymentScreen(customerId: String) {
        safeNavigateToMainScreen { mainNavController ->
            val bundle = Bundle().apply {
                putString("customerId", customerId)
            }

            // Check if the action exists
            if (mainNavController.currentDestination?.getAction(R.id.action_mainScreenFragment_to_paymentsFragment) != null) {
                mainNavController.navigate(
                    R.id.action_mainScreenFragment_to_paymentsFragment,
                    bundle
                )
            } else {
                Log.e(TAG, "Payments action not available from current destination")
                Toast.makeText(requireContext(), "Navigation not available", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    /**
     * Helper method to safely navigate to main screen first, then perform additional navigation
     */
    private fun safeNavigateToMainScreen(onMainScreenReached: (NavController) -> Unit) {
        try {
            // Navigate back to the main screen first
            val action = NotificationFragmentDirections
                .actionNotificationFragmentToMainScreenFragment()

            // Create navigation options to clear the back stack when going to the main screen
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.notificationFragment, true)
                .build()

            findNavController().navigate(action, navOptions)

            // Add a short delay to ensure navigation completes
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val mainNavController =
                        requireActivity().findNavController(R.id.nav_host_fragment)
                    onMainScreenReached(mainNavController)
                } catch (e: Exception) {
                    Log.e(TAG, "Error accessing main nav controller", e)
                    Toast.makeText(
                        requireContext(),
                        "Navigation error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }, 300)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to main screen", e)
            Toast.makeText(
                requireContext(),
                "Error navigating to main screen: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}