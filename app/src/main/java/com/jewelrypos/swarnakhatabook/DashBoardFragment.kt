package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Factorys.NotificationViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.Utilitys.NotificationScheduler
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentDashBoardBinding


class DashBoardFragment : Fragment() {

    private var _binding: FragmentDashBoardBinding? = null
    private val binding get() = _binding!!

    // Add these fields to your DashboardFragment class
    private lateinit var notificationViewModel: NotificationViewModel
    private var notificationMenuItem: MenuItem? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDashBoardBinding.inflate(inflater, container, false)


        // Initialize notification ViewModel
        val repository = NotificationRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
        notificationViewModel = ViewModelProvider(requireActivity(),
            NotificationViewModelFactory(
                repository,
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            )
        )[NotificationViewModel::class.java]

        // Schedule periodic notification checks
        NotificationScheduler.scheduleNotificationCheck(requireContext())

        binding.topAppBar.menu.findItem(R.id.action_notifications).setOnMenuItemClickListener { menuItem ->
            val parentNavController = requireActivity().findNavController(R.id.nav_host_fragment)
            parentNavController.navigate(R.id.action_mainScreenFragment_to_notificationFragment)
            true
        }

        // Setup notification badge observation
        notificationViewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            updateNotificationBadge(count)
        }

        // Inflate the layout for this fragment
        return binding.root
    }

    private fun updateNotificationBadge(count: Int) {
        binding.topAppBar.menu.findItem(R.id.action_notifications)?.let { menuItem ->
            if (count > 0) {
                // Create or update badge
                val actionView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.menu_item_notification, null)

                val iconView = actionView.findViewById<ImageView>(R.id.notificationIcon)
                val badgeTextView = actionView.findViewById<TextView>(R.id.badgeCount)

                // Set the count
                if (count > 99) {
                    badgeTextView.text = "99+"
                } else {
                    badgeTextView.text = count.toString()
                }

                // Show badge
                badgeTextView.visibility = View.VISIBLE

                // Set click listener
                actionView.setOnClickListener {
                    onOptionsItemSelected(menuItem)
                }

                // Set as action view
                menuItem.setActionView(actionView)
            } else {
                // No notifications, remove badge
                menuItem.actionView = null
            }
        }
    }

    // Don't forget to clear the ViewModel observation in onDestroyView
    override fun onDestroyView() {
        super.onDestroyView()
        notificationViewModel.unreadCount.removeObservers(viewLifecycleOwner)
    }
}