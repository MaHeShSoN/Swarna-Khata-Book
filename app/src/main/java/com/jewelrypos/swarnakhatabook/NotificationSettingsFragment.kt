// app/src/main/java/com/jewelrypos/swarnakhatabook/NotificationSettingsFragment.kt
package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.os.Bundle
import android.util.Log
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
import com.jewelrypos.swarnakhatabook.Utilitys.SessionManager
import com.jewelrypos.swarnakhatabook.ViewModle.NotificationSettingsViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentNotificationSettingsBinding

class NotificationSettingsFragment : Fragment() {

    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationSettingsViewModel by viewModels {
        val repository = NotificationRepository(
            FirebaseFirestore.getInstance(),
            FirebaseAuth.getInstance(),
            requireContext().applicationContext
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

        // Add logging for active shop ID
        val activeShopId = SessionManager.getActiveShopId(requireContext())
        Log.d("NotificationSettings", "Fragment created - Active shop ID: $activeShopId")

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

            binding.dueDaysInput.setText(prefs.paymentDueReminderDays.toString())
            binding.overdueDaysInput.setText(prefs.paymentOverdueAlertDays.toString())

            binding.paymentDueSettingsContainer.visibility = if (prefs.paymentDue) View.VISIBLE else View.GONE
            binding.paymentOverdueSettingsContainer.visibility = if (prefs.paymentOverdue) View.VISIBLE else View.GONE

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

        // Add logging for switch changes
        binding.switchPaymentDue.setOnCheckedChangeListener { _, isChecked ->
            Log.d("NotificationSettings", "Payment due switch changed to: $isChecked")
            viewModel.updatePreference(NotificationType.PAYMENT_DUE, isChecked)
            binding.paymentDueSettingsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.switchPaymentOverdue.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updatePreference(NotificationType.PAYMENT_OVERDUE, isChecked)
            binding.paymentOverdueSettingsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Add setup for EditText focus changes
        binding.dueDaysInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val daysText = binding.dueDaysInput.text.toString()
                val days = daysText.toIntOrNull() ?: 3
                if (days < 1) {
                    binding.dueDaysInput.setText("1")
                    viewModel.updatePaymentDueReminderDays(1)
                } else {
                    viewModel.updatePaymentDueReminderDays(days)
                }
            }
        }

        binding.overdueDaysInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val daysText = binding.overdueDaysInput.text.toString()
                val days = daysText.toIntOrNull() ?: 1
                if (days < 1) {
                    binding.overdueDaysInput.setText("1")
                    viewModel.updatePaymentOverdueAlertDays(1)
                } else {
                    viewModel.updatePaymentOverdueAlertDays(days)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}