package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.CreditLimitHistoryAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.Customer
import com.jewelrypos.swarnakhatabook.Factorys.CustomerViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.CustomerRepository
import com.jewelrypos.swarnakhatabook.ViewModle.CustomerViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentCustomerDashboardBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class CustomerDashboardFragment : Fragment() {

    private var _binding: FragmentCustomerDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var customer: Customer

    private lateinit var creditHistoryAdapter: CreditLimitHistoryAdapter
    private val customerViewModel: CustomerViewModel by viewModels {
        // Use the same factory as in your CustomerFragment
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        CustomerViewModelFactory(repository, connectivityManager)
    }

    companion object {
        private const val ARG_CUSTOMER = "customer"

        fun newInstance(customer: Customer): CustomerDashboardFragment {
            val fragment = CustomerDashboardFragment()
            val args = Bundle()
            args.putSerializable(ARG_CUSTOMER, customer)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            customer = it.getSerializable(ARG_CUSTOMER) as Customer
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Populate dashboard with customer info
        setupDashboard()
        // Setup credit limit history
        setupCreditLimitHistory()

        // Observe credit limit history
        observeCreditLimitHistory()
    }

    private fun setupCreditLimitHistory() {
        // Initialize adapter
        creditHistoryAdapter = CreditLimitHistoryAdapter(emptyList())
        binding.creditHistoryRecyclerView.adapter = creditHistoryAdapter

        // Load credit limit history
        customerViewModel.loadCreditLimitHistory(customer.id)

        // Setup update button
        binding.updateCreditLimitButton.setOnClickListener {
            showUpdateCreditLimitDialog()
        }
    }

    private fun observeCreditLimitHistory() {
        customerViewModel.creditLimitHistory.observe(viewLifecycleOwner) { history ->
            creditHistoryAdapter.updateChanges(history)

            // Show/hide empty state
            if (history.isEmpty()) {
                binding.noCreditHistoryText.visibility = View.VISIBLE
                binding.creditHistoryRecyclerView.visibility = View.GONE
            } else {
                binding.noCreditHistoryText.visibility = View.GONE
                binding.creditHistoryRecyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun showUpdateCreditLimitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_credit_limit_change, null)

        // Get references to dialog views
        val currentLimitField = dialogView.findViewById<TextInputEditText>(R.id.currentLimitField)
        val newLimitField = dialogView.findViewById<TextInputEditText>(R.id.newLimitField)
        val reasonField = dialogView.findViewById<TextInputEditText>(R.id.reasonField)

        // Set current limit
        val formatter = DecimalFormat("#,##,##0.00")
        currentLimitField.setText(formatter.format(customer.creditLimit))
        newLimitField.setText(formatter.format(customer.creditLimit))

        // Create and show the dialog
        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Update") { dialog, _ ->
                // Get the new values
                val newLimit = newLimitField.text.toString().replace(",", "").toDoubleOrNull() ?: customer.creditLimit
                val reason = reasonField.text.toString().trim()

                // Validate input
                if (reason.isEmpty()) {
                    Toast.makeText(requireContext(), "Please provide a reason for the change", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Update credit limit
                customerViewModel.updateCustomerCreditLimit(
                    customerId = customer.id,
                    currentLimit = customer.creditLimit,
                    newLimit = newLimit,
                    reason = reason
                )
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }



    private fun setupDashboard() {
        // Contact information
        binding.customerName.text = "${customer.firstName} ${customer.lastName}"
        binding.customerType.text = customer.customerType
        binding.customerPhone.text = customer.phoneNumber
        binding.customerEmail.text =
            if (customer.email.isNotEmpty()) customer.email else "Not provided"

        // Address information
        val fullAddress = buildString {
            if (customer.streetAddress.isNotEmpty()) append("${customer.streetAddress}, ")
            append("${customer.city}, ${customer.state}")
            if (customer.postalCode.isNotEmpty()) append(", ${customer.postalCode}")
            append(", ${customer.country}")
        }
        binding.customerAddress.text = fullAddress


        // Business information (for wholesalers)
        if (customer.customerType.equals("Wholesaler", ignoreCase = true)) {
            binding.businessInfoCard.visibility = View.VISIBLE
            binding.businessName.text = customer.businessName
            binding.gstNumber.text =
                if (customer.gstNumber.isNotEmpty()) customer.gstNumber else "Not provided"
            binding.taxId.text = if (customer.taxId.isNotEmpty()) customer.taxId else "Not provided"
        } else {
            binding.businessInfoCard.visibility = View.GONE
        }

        // Relationship information
        binding.customerSince.text = formatDate(customer.customerSince)
        binding.referredBy.text =
            if (customer.referredBy.isNotEmpty()) customer.referredBy else "Not specified"

        // Important dates
        if (customer.birthday.isNotEmpty() || customer.anniversary.isNotEmpty()) {
            binding.importantDatesCard.visibility = View.VISIBLE

            if (customer.birthday.isNotEmpty()) {
                binding.birthdayContainer.visibility = View.VISIBLE
                binding.birthday.text = formatDate(customer.birthday)
            } else {
                binding.birthdayContainer.visibility = View.GONE
            }

            if (customer.anniversary.isNotEmpty()) {
                binding.anniversaryContainer.visibility = View.VISIBLE
                binding.anniversary.text = formatDate(customer.anniversary)
            } else {
                binding.anniversaryContainer.visibility = View.GONE
            }
        } else {
            binding.importantDatesCard.visibility = View.GONE
        }

        // Notes
        if (customer.notes.isNotEmpty()) {
            binding.notesCard.visibility = View.VISIBLE
            binding.customerNotes.text = customer.notes
        } else {
            binding.notesCard.visibility = View.GONE
        }

        updateCreditLimitSection(customer)

    }

    private fun formatDate(dateString: String): String {
        if (dateString.isEmpty()) return ""

        try {
            // Assuming date format is dd/MM/yyyy
            val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

            val date = inputFormat.parse(dateString)
            return if (date != null) outputFormat.format(date) else dateString
        } catch (e: Exception) {
            return dateString
        }
    }

    /**
     * Updates the credit limit section in the customer detail view
     * Uses current balance for all calculations for consistency
     */
    private fun updateCreditLimitSection(customer: Customer) {
        val formatter = DecimalFormat("#,##,##0.00")

        // Only show credit section for Credit type customers with a limit
        if (customer.balanceType == "Credit" && customer.creditLimit > 0.0) {
            binding.creditLimitDetailCard.visibility = View.VISIBLE

            // Calculate available credit and usage percentage
            val availableCredit = maxOf(0.0, customer.creditLimit - customer.currentBalance)
            val usagePercentage = com.jewelrypos.swarnakhatabook.Utilitys.CustomerBalanceUtils
                .calculateCreditUsagePercentage(customer)

            // Update text fields
            binding.currentBalanceValue.text = "₹${formatter.format(customer.currentBalance)}"
            binding.creditLimitValue.text = "₹${formatter.format(customer.creditLimit)}"
            binding.availableCreditValue.text = "₹${formatter.format(availableCredit)}"
            binding.creditLimitDetailPercentage.text = "$usagePercentage%"

            // Update progress bar
            binding.creditLimitDetailProgress.progress = usagePercentage

            // Set progress color based on percentage
            val progressColor = when {
                usagePercentage >= 90 -> R.color.status_unpaid // Red for > 90%
                usagePercentage >= 75 -> R.color.status_partial // Orange for > 75%
                else -> R.color.my_light_primary // Default gold color
            }

            binding.creditLimitDetailProgress.setIndicatorColor(
                ContextCompat.getColor(requireContext(), progressColor)
            )

            // Set credit limit percentage text color to match progress
            binding.creditLimitDetailPercentage.setTextColor(
                ContextCompat.getColor(requireContext(), progressColor)
            )

            // Set available credit text color based on amount
            val availableCreditColor = when {
                availableCredit <= 0 -> R.color.status_unpaid // Red when no credit left
                availableCredit < (customer.creditLimit * 0.25) -> R.color.status_partial // Orange when < 25% left
                else -> R.color.status_paid // Green otherwise
            }

            binding.availableCreditValue.setTextColor(
                ContextCompat.getColor(requireContext(), availableCreditColor)
            )

            // If over credit limit, make the current balance text red
            if (customer.currentBalance > customer.creditLimit) {
                binding.currentBalanceValue.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.status_unpaid)
                )
            } else {
                binding.currentBalanceValue.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.my_light_on_surface)
                )
            }
        } else {
            binding.creditLimitDetailCard.visibility = View.GONE
        }
    }

    /**
     * Calculate the percentage of credit limit used
     * @return Percentage of credit used (0-100)
     */
    private fun calculateCreditUsagePercentage(customer: Customer): Int {
        if (customer.creditLimit <= 0.0 || customer.currentBalance <= 0.0) {
            return 0
        }

        val percentage = (customer.currentBalance / customer.creditLimit) * 100
        // Cap at 100% for display purposes
        return percentage.coerceAtMost(100.0).toInt()
    }


    fun updateCustomer(updatedCustomer: Customer) {
        this.customer = updatedCustomer
        setupDashboard() // Refresh UI with new customer data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}