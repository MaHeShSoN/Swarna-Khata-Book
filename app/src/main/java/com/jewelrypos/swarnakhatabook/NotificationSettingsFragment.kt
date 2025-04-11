// app/src/main/java/com/jewelrypos/swarnakhatabook/NotificationSettingsFragment.kt
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Enums.NotificationType
import com.jewelrypos.swarnakhatabook.Factorys.NotificationSettingsViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.NotificationRepository
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationSettingsViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentNotificationSettingsBinding

class NotificationSettingsFragment : Fragment() {

    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationSettingsViewModel by viewModels {
        val repository = NotificationRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance()
        )
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        NotificationSettingsViewModelFactory(repository, connectivityManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        observePreferences()
        setupSwitches()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun observePreferences() {
        viewModel.preferences.observe(viewLifecycleOwner) { prefs ->
            // Payment notifications
            binding.switchPaymentDue.isChecked = prefs.paymentDue
            binding.switchPaymentOverdue.isChecked = prefs.paymentOverdue
            binding.switchCreditLimit.isChecked = prefs.creditLimit

            // Inventory notifications
            binding.switchLowStock.isChecked = prefs.lowStock

            // Business insights
            binding.switchBusinessInsights.isChecked = prefs.businessInsights

            // Customer events
            binding.switchBirthday.isChecked = prefs.customerBirthday
            binding.switchAnniversary.isChecked = prefs.customerAnniversary
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Preferences saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSwitches() {
        // Payment notifications
        binding.switchPaymentDue.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePreference(NotificationType.PAYMENT_DUE, isChecked)
        }

        binding.switchPaymentOverdue.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePreference(NotificationType.PAYMENT_OVERDUE, isChecked)
        }

        binding.switchCreditLimit.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePreference(NotificationType.CREDIT_LIMIT, isChecked)
        }

        // Inventory notifications
        binding.switchLowStock.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateLowStockAlerts(isChecked)
        }

        // Business insights
        binding.switchBusinessInsights.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePreference(NotificationType.GENERAL, isChecked)
        }

        // Customer events
        binding.switchBirthday.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePreference(NotificationType.BIRTHDAY, isChecked)
        }

        binding.switchAnniversary.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePreference(NotificationType.ANNIVERSARY, isChecked)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}