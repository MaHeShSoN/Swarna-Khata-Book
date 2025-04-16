package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.content.res.ColorStateList
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

    private val customerViewModel: CustomerViewModel by viewModels {
        // Use the same factory as in your CustomerFragment
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = CustomerRepository(firestore, auth,requireContext())
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
    }

    // Add this method to the CustomerDashboardFragment class
    private fun setupFinancialInfo() {
        // Format currency amounts with proper formatting
        val formatter = DecimalFormat("#,##,##0.00")

        // Set the balance type (Credit/Debit)
        binding.balanceTypeText.text = customer.balanceType

        // Determine balance status and color
        val balanceText = when {
            customer.currentBalance != 0.0 -> {
                when {
                    customer.balanceType == "Credit" && customer.currentBalance > 0 ->
                        "To Receive: ₹${formatter.format(customer.currentBalance)}"

                    customer.balanceType == "Debit" && customer.currentBalance > 0 ->
                        "To Pay: ₹${formatter.format(customer.currentBalance)}"

                    else -> "Balance: ₹${formatter.format(customer.currentBalance)}"
                }
            }

            else -> "Balance: ₹0.00"
        }

        // Set balance text color based on balance type and amount
        val balanceColor = when {
            customer.balanceType == "Credit" && customer.currentBalance > 0 ->
                ContextCompat.getColor(
                    requireContext(),
                    R.color.status_paid
                ) // Green for money to receive
            customer.balanceType == "Debit" && customer.currentBalance > 0 ->
                ContextCompat.getColor(
                    requireContext(),
                    R.color.status_unpaid
                ) // Red for money to pay
            else ->
                ContextCompat.getColor(
                    requireContext(),
                    R.color.my_light_secondary
                ) // Neutral color for zero balance
        }

        binding.currentBalanceText.text = balanceText
        binding.currentBalanceText.setTextColor(balanceColor)

        // Color the balance based on balance type and amount
        val textColor = when {
            (customer.balanceType == "Credit" && customer.currentBalance > 0) ||
                    (customer.balanceType == "Debit" && customer.currentBalance <= 0) ->
                ContextCompat.getColor(requireContext(), R.color.status_paid)

            else ->
                ContextCompat.getColor(requireContext(), R.color.status_unpaid)
        }
        binding.currentBalanceText.setTextColor(textColor)

        // Set the opening balance
        binding.openingBalanceText.text = "₹${formatter.format(customer.openingBalance)}"

        // Show balance notes if available
        if (customer.balanceNotes.isNotEmpty()) {
            binding.balanceNotesContainer.visibility = View.VISIBLE
            binding.balanceNotesText.text = customer.balanceNotes
        } else {
            binding.balanceNotesContainer.visibility = View.GONE
        }
    }

    // Modify the setupDashboard method to call our new method
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

        // Set up financial information
        setupFinancialInfo()

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


    fun updateCustomer(updatedCustomer: Customer) {
        this.customer = updatedCustomer
        setupDashboard() // Refresh UI with new customer data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}