package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.NotificationAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.PaymentNotification
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Factorys.NotificationViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentNotificationBinding

class NotificationFragment : Fragment(), NotificationAdapter.OnNotificationActionListener {

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter

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

    override fun onNotificationClick(notification: PaymentNotification) {
        // Mark as read
        viewModel.markAsRead(notification.id)

        // Navigate to appropriate screen based on notification type
        when (notification.type) {
            NotificationType.CREDIT_LIMIT -> {
                navigateToCustomerDetails(notification.customerId)
            }
            NotificationType.PAYMENT_DUE,
            NotificationType.PAYMENT_OVERDUE -> {
                navigateToPaymentScreen(notification.customerId)
            }
            else -> {
                // Handle other notification types
            }
        }
    }

    override fun onActionButtonClick(notification: PaymentNotification) {
        // Mark as read and that action was taken
        viewModel.markAsRead(notification.id)
        viewModel.markActionTaken(notification.id)

        // Navigate based on action button
        when (notification.type) {
            NotificationType.CREDIT_LIMIT -> {
                navigateToCustomerDetails(notification.customerId)
            }
            NotificationType.PAYMENT_DUE,
            NotificationType.PAYMENT_OVERDUE -> {
                navigateToPaymentScreen(notification.customerId)
            }
            else -> {
                // Handle other notification types
            }
        }
    }

    override fun onDismissButtonClick(notification: PaymentNotification) {
        // Mark as read
        viewModel.markAsRead(notification.id)
        // Optionally delete the notification
        // viewModel.deleteNotification(notification.id)
    }

    private fun navigateToCustomerDetails(customerId: String) {
        // You would implement navigation to customer details here
        // For example:
        // val action = NotificationFragmentDirections.actionNotificationFragmentToCustomerDashboardFragment(customerId)
        // findNavController().navigate(action)

        Toast.makeText(requireContext(), "Navigate to customer details: $customerId", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToPaymentScreen(customerId: String) {
        // You would implement navigation to payment screen here
        // For example:
        // val action = NotificationFragmentDirections.actionNotificationFragmentToAddPaymentFragment(customerId)
        // findNavController().navigate(action)

        Toast.makeText(requireContext(), "Navigate to payment screen: $customerId", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}