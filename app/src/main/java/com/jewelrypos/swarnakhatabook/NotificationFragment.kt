package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
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
import com.jewelrypos.swarnakhatabook.Utilitys.AnimationUtils
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationPermissionHelper
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentNotificationBinding
import com.jewelrypos.swarnakhatabook.Utilitys.FCMHelper

class NotificationFragment : Fragment(), NotificationAdapter.OnNotificationActionListener {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!! // Use this only between onCreateView and onDestroyView

    private lateinit var adapter: NotificationAdapter
    private val TAG = "NotificationFragment"

    // Handler for delayed operations
    private var handler: Handler? = Handler(Looper.getMainLooper())
    private var navigationRunnable: Runnable? = null

    // ViewModel initialization using Factory
    private val viewModel: NotificationViewModel by viewModels {
        // Ensure context is available during ViewModel creation
        val safeContext = requireContext().applicationContext // Use application context if possible
        val repository = NotificationRepository(
            FirebaseFirestore.getInstance(), 
            FirebaseAuth.getInstance(),
            safeContext // Pass the context here
        )
        val connectivityManager =
            safeContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        NotificationViewModelFactory(repository, connectivityManager)
    }

    // Permission launcher for notification permission
    private val notificationPermissionLauncher =
        NotificationPermissionHelper.createPermissionLauncher(
            this, onPermissionResult = { isGranted ->
                // Use context safely after permission result
                context?.let { safeContext ->
                    if (isGranted) {
                        Toast.makeText(
                            safeContext, "Notification permission granted", Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            safeContext,
                            "Notification permission denied. You may miss important alerts.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        
        // Set the current shop ID for filtering
        setupShopFiltering()

        // Apply entrance animation
        AnimationUtils.fadeIn(binding.root)

        // Observe ViewModel data
        observeNotifications()
    }

    private fun setupShopFiltering() {
        // Get the active shop ID from SessionManager
        val activeShopId = SessionManager.getActiveShopId(requireContext())
        
        // Set the shop ID in the ViewModel for filtering
        viewModel.setCurrentShop(activeShopId!!)
        
        // Observe changes to the active shop ID
        SessionManager.activeShopIdLiveData.observe(viewLifecycleOwner) { shopId ->
            Log.d(TAG, "Active shop ID changed to: $shopId")
            viewModel.setCurrentShop(shopId!!)
        }
    }

    private fun checkNotificationPermission() {
        // Use context safely
        context?.let { safeContext ->
            if (!NotificationPermissionHelper.hasNotificationPermission(safeContext)) {
                // Request permission using the activity result launcher
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            // Use findNavController safely within the fragment's lifecycle
            if (isAdded) {
                findNavController().navigateUp()
            }
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
        // Check fragment state before navigating
        if (!isAdded || context == null) {
            Log.w(TAG, "navigateToNotificationSettings: Fragment not attached or context is null.")
            return
        }
        try {
            val navController = findNavController()
            // Check if the current destination is still the NotificationFragment
            if (navController.currentDestination?.id == R.id.notificationFragment) {
                val action =
                    NotificationFragmentDirections.actionNotificationFragmentToNotificationSettingsFragment()
                navController.navigate(action)
            } else {
                Log.e(
                    TAG,
                    "Navigation action to settings not available from current destination: ${navController.currentDestination?.label}"
                )
                Toast.makeText(context, "Settings screen not available", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { // Catch specific exceptions if possible (IllegalStateException, IllegalArgumentException)
            Log.e(TAG, "Error navigating to settings", e)
            context?.let {
                Toast.makeText(it, "Error navigating to settings", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun setupRecyclerView() {
        // Use context safely
        context?.let { safeContext ->
            adapter = NotificationAdapter(emptyList())
            adapter.setOnNotificationActionListener(this)

            binding.notificationRecyclerView.apply {
                layoutManager = LinearLayoutManager(safeContext)
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
                        // ** CORRECTION HERE: Access .value for LiveData **
                        if (viewModel.isLoading.value == false && viewModel.isLastPage.value == false && // Check loading state and last page
                            visibleItemCount + firstVisibleItemPosition >= totalItemCount - 5 && firstVisibleItemPosition >= 0 && totalItemCount >= viewModel.getPageSize()
                        ) {
                            Log.d(TAG, "Approaching end of list, loading next page.") // Add log
                            viewModel.loadNextPage()
                        }
                    }
                })
            }
        } ?: Log.e(TAG, "Context was null during setupRecyclerView")
        
        // Apply animations to RecyclerView items
        AnimationUtils.animateRecyclerView(binding.notificationRecyclerView)
    }


    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadNotifications() // Triggers loading state and fetches data
        }
    }

    private fun setupEmptyState() {
        binding.checkNotificationsButton.setOnClickListener {
            binding.swipeRefreshLayout.isRefreshing = true
            viewModel.loadNotifications()
        }
    }

    private fun observeNotifications() {
        // Observe notifications list
        viewModel.notifications.observe(viewLifecycleOwner) { notifications ->
            adapter.updateNotifications(notifications)
            binding.swipeRefreshLayout.isRefreshing = false // Stop refresh indicator

            // Show/hide empty state based on the observed list
            if (notifications.isEmpty() && viewModel.isLoading.value == false) { // Only show empty state if not loading
                AnimationUtils.fadeIn(binding.emptyStateLayout)
                binding.emptyStateLayout.visibility = View.VISIBLE
                binding.notificationRecyclerView.visibility = View.GONE
            } else {
                AnimationUtils.fadeOut(binding.emptyStateLayout)
                binding.emptyStateLayout.visibility = View.GONE
                binding.notificationRecyclerView.visibility = View.VISIBLE
                AnimationUtils.fadeIn(binding.notificationRecyclerView)
            }
        }

        // Observe loading state to show/hide progress bar and manage refresh indicator
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show progress bar only if not refreshing via swipe
            binding.progressBar.visibility =
                if (isLoading && !binding.swipeRefreshLayout.isRefreshing) View.VISIBLE else View.GONE
            if (!isLoading) {
                binding.swipeRefreshLayout.isRefreshing =
                    false // Ensure refresh stops when loading finishes
            }
            
            if (isLoading) {
                AnimationUtils.fadeIn(binding.progressBar)
            } else {
                AnimationUtils.fadeOut(binding.progressBar)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            // Check if errorMessage is not null or empty before showing toast
            if (!errorMessage.isNullOrEmpty()) {
                // Use context safely
                context?.let { safeContext ->
                    // Show error with Material design alerts for better visibility for critical errors
                    if (errorMessage.contains("delete") || errorMessage.contains("dismiss") || 
                        errorMessage.contains("permission") || errorMessage.contains("failed")) {
                        
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            errorMessage,
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).setAction("Refresh") {
                            viewModel.loadNotifications()
                        }.show()
                    } else {
                        // Regular toast for less critical messages
                        Toast.makeText(safeContext, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                binding.swipeRefreshLayout.isRefreshing = false // Stop refresh on error
            }
        }

        viewModel.navigationEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { navAction ->
                // Now navigate only after Firebase operations have completed
                navigateBasedOnNotification(navAction.notification, navAction.isActionButton)
            }
        }
    }

    // --- Notification Action Callbacks ---

    override fun onNotificationClick(notification: AppNotification) {
        // Just call ViewModel method, it will handle the status change and navigation
        viewModel.handleNotificationClick(notification, false)
        
        // Apply animation to the clicked item
        AnimationUtils.pulse(binding.notificationRecyclerView)
    }

    override fun onActionButtonClick(notification: AppNotification) {
        // Just call ViewModel method, it will handle the status change and navigation
        viewModel.handleNotificationClick(notification, true)
        
        // Apply animation to the clicked item
        AnimationUtils.pulse(binding.notificationRecyclerView)
    }

    override fun onDismissButtonClick(notification: AppNotification) {
        // Use context safely for Dialog
        context?.let { safeContext ->
            MaterialAlertDialogBuilder(safeContext)
                .setTitle("Delete Notification")
                .setMessage("Do you want to delete this notification?")
                .setPositiveButton("Yes") { _, _ ->
                    // Show a temporary "Deleting..." toast for better UX
                    Toast.makeText(safeContext, "Deleting notification...", Toast.LENGTH_SHORT).show()
                    viewModel.handleNotificationDismiss(notification.id)
                }
                .setNegativeButton("No", null)
                .show()
        } ?: run {
            // If context is null, show a debug log and try the operation anyway
            Log.w(TAG, "Context was null when trying to show delete confirmation dialog")
            viewModel.handleNotificationDismiss(notification.id) // Delete even if dialog can't be shown
        }
    }
    // --- Navigation Logic ---

    /**
     * Central method to handle navigation based on notification type.
     * This now calls the specific safe navigation methods.
     */
    private fun navigateBasedOnNotification(
        notification: AppNotification, isActionButton: Boolean
    ) {
        // Check fragment state before attempting navigation
        if (!isAdded || context == null) {
            Log.w(TAG, "navigateBasedOnNotification: Fragment not attached or context is null.")
            return
        }
        try {
            when (notification.type) {
                NotificationType.CREDIT_LIMIT -> {
                    if (notification.customerId.isNotEmpty()) {
//                        navigateToCustomerDetails(notification.customerId)
                        val parentNavController =
                            requireActivity().findNavController(R.id.nav_host_fragment)
                        val action =
                            NotificationFragmentDirections.actionNotificationFragmentToCustomerDetailFragment(
                                notification.customerId
                            )
                        parentNavController.navigate(action)
                    }
                }

                NotificationType.PAYMENT_DUE, NotificationType.PAYMENT_OVERDUE -> {
                    if (notification.relatedInvoiceId != null) {
                        val parentNavController =
                            requireActivity().findNavController(R.id.nav_host_fragment)
                        val action =
                            NotificationFragmentDirections.actionNotificationFragmentToInvoiceDetailFragment(
                                notification.relatedInvoiceId
                            )
                        parentNavController.navigate(action)
//                        navigateToInvoiceDetail(notification.relatedInvoiceId)
                    } else if (notification.customerId.isNotEmpty()) {
//                        navigateToPaymentScreen(notification.customerId)
                        val parentNavController =
                            requireActivity().findNavController(R.id.nav_host_fragment)
                        val action =
                            NotificationFragmentDirections.actionNotificationFragmentToCustomerDetailFragment(
                                notification.customerId
                            )
                        parentNavController.navigate(action)
                    }
                }

                NotificationType.GENERAL -> {
                    if (notification.relatedItemId != null) {
                        val parentNavController =
                            requireActivity().findNavController(R.id.nav_host_fragment)
                        val action =
                            NotificationFragmentDirections.actionNotificationFragmentToItemDetailFragment(
                                notification.relatedItemId
                            )
                        parentNavController.navigate(action)
                    }
                    // No specific navigation for other general types needed here
                }

                NotificationType.APP_UPDATE -> {
                    // For app updates, we can handle external links or specific actions
                    notification.actionUrl?.let { url ->
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            intent.data = android.net.Uri.parse(url)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error opening URL: ${e.message}")
                            context?.let { ctx ->
                                Toast.makeText(ctx, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                NotificationType.BIRTHDAY, NotificationType.ANNIVERSARY -> {
                    if (notification.customerId.isNotEmpty()) {
//                        navigateToCustomerDetails(notification.customerId)
                        val parentNavController =
                            requireActivity().findNavController(R.id.nav_host_fragment)
                        val action =
                            NotificationFragmentDirections.actionNotificationFragmentToCustomerDetailFragment(
                                notification.customerId
                            )
                        parentNavController.navigate(action)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error determining navigation path", e)
            context?.let {
                Toast.makeText(
                    it, "Could not navigate: ${e.message}", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    /**
     * Performs the initial navigation back to the MainScreenFragment and then, after a delay,
     * executes the final navigation step using the main activity's NavController.
     *
     * @param destinationActionId The action ID to navigate to FROM the MainScreenFragment.
     * @param args Arguments to pass to the final destination.
     */
    private fun safeNavigateToMainScreenThenNavigate(destinationActionId: Int, args: Bundle?) {
        if (!isAdded) {
            Log.w(TAG, "safeNavigateToMainScreenThenNavigate: Fragment not attached.")
            return
        }
        try {
            // 1. Navigate from NotificationFragment back to MainScreenFragment
            // This assumes MainScreenFragment is the intended intermediate step.
            val backToMainAction =
                NotificationFragmentDirections.actionNotificationFragmentToMainScreenFragment()
            val navOptions = NavOptions.Builder().setPopUpTo(
                R.id.notificationFragment, true
            ) // Pop NotificationFragment off the stack
                .build()
            findNavController().navigate(backToMainAction, navOptions)

            // 2. Post the final navigation step with a delay
            // Cancel any previous runnable first
            navigationRunnable?.let { handler?.removeCallbacks(it) }

            navigationRunnable = Runnable {
                // Check if fragment is still attached and activity exists AFTER the delay
                if (isAdded && activity != null) {
                    try {
                        // Get the NavController from the Activity's NavHostFragment
                        // Ensure R.id.nav_host_fragment is the correct ID in your MainActivity layout
                        val mainNavController =
                            requireActivity().findNavController(R.id.nav_host_fragment)

                        // Check if the current destination of the main NavController allows the action
                        if (mainNavController.currentDestination?.getAction(destinationActionId) != null) {
                            mainNavController.navigate(destinationActionId, args)
                        } else {
                            Log.e(
                                TAG,
                                "Final navigation action ID $destinationActionId not available from main NavController's current destination: ${mainNavController.currentDestination?.label}"
                            )
                            // Show error using application context if possible, as fragment context might be risky here
                            activity?.applicationContext?.let { appCtx ->
                                Toast.makeText(appCtx, "Navigation failed", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    } catch (e: IllegalStateException) {
                        Log.e(
                            TAG,
                            "Error finding/using main NavController after delay: Fragment might be detached or Activity destroyed.",
                            e
                        )
                        activity?.applicationContext?.let { appCtx ->
                            Toast.makeText(
                                appCtx,
                                "Navigation error (IllegalStateException)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e(
                            TAG,
                            "Invalid final navigation action ID $destinationActionId or arguments.",
                            e
                        )
                        activity?.applicationContext?.let { appCtx ->
                            Toast.makeText(
                                appCtx,
                                "Navigation error (IllegalArgumentException)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Log.w(
                        TAG,
                        "Fragment detached or Activity null before final navigation could execute."
                    )
                }
            }
            handler?.postDelayed(navigationRunnable!!, 300) // Post the new runnable

        } catch (e: Exception) { // Catch errors during the initial navigation step
            Log.e(TAG, "Error during initial navigation to main screen", e)
            context?.let {
                Toast.makeText(it, "Error navigating: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Debug method to check Firestore permissions
     * This is for development only
     */
    private fun debugFirestorePermissions() {
        try {
            // Get the current shop ID from shared preferences or another source
            val sharedPreferences = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val currentShopId = sharedPreferences.getString("active_shop_id", null)
            
            if (currentShopId.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No active shop ID found", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Call the ViewModel's method to check Firestore rules
            viewModel.checkFirestoreRules(currentShopId)
            
            // Print FCM token to logcat
            FCMHelper.printTokenToLogcat()
            Toast.makeText(requireContext(), "FCM Token printed to logcat", Toast.LENGTH_SHORT).show()
            
            Toast.makeText(requireContext(), "Checking Firestore permissions...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error during Firestore permission check", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        // Remove all callbacks from handler to prevent memory leaks
        handler?.removeCallbacksAndMessages(null)
        handler = null
        
        super.onDestroyView()
        _binding = null
    }
}
