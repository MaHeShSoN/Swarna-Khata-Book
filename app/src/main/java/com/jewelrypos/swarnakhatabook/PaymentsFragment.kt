package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jewelrypos.swarnakhatabook.Adapters.PaymentWithContextAdapter
import com.jewelrypos.swarnakhatabook.Factorys.PaymentsViewModelFactory
import com.jewelrypos.swarnakhatabook.Repository.InvoiceRepository
import com.jewelrypos.swarnakhatabook.ViewModle.PaymentsViewModel
import com.jewelrypos.swarnakhatabook.ViewModle.PaymentsViewModel.PaymentWithContext
import com.jewelrypos.swarnakhatabook.databinding.FragmentPaymentsBinding
import java.text.DecimalFormat
import java.util.Calendar

class PaymentsFragment : Fragment(), PaymentWithContextAdapter.OnPaymentClickListener {

    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var paymentsAdapter: PaymentWithContextAdapter

    private val paymentsViewModel: PaymentsViewModel by viewModels {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val repository = InvoiceRepository(firestore, auth)
        val connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        PaymentsViewModelFactory(repository, connectivityManager)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupPeriodSpinner()
        setupRecyclerView()
        setupObservers()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupPeriodSpinner() {
        val periods = arrayOf("Today", "This Week", "This Month", "This Year", "All Time")

        // Create adapter with appropriate styling
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            periods
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.periodSelector.adapter = adapter

        // Default to "This Month"
        binding.periodSelector.setSelection(2)

        // Set listener after adapter is set to avoid initial callback
        binding.periodSelector.post {
            binding.periodSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // Show progress indicator
                    binding.progressBar.visibility = View.VISIBLE

                    // Filter payments based on selected period
                    paymentsViewModel.filterPaymentsByPeriod(periods[position])
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun setupRecyclerView() {
        paymentsAdapter = PaymentWithContextAdapter(emptyList())
        paymentsAdapter.setOnPaymentClickListener(this)
        binding.paymentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = paymentsAdapter
        }
    }

    private fun setupObservers() {
        // Observe payments list
        paymentsViewModel.filteredPayments.observe(viewLifecycleOwner) { payments ->
            paymentsAdapter.updatePayments(payments)
            updatePaymentMetrics(payments)

            // Show empty state if needed
            binding.emptyStateLayout.visibility = if (payments.isEmpty()) View.VISIBLE else View.GONE

            // Hide progress indicator
            binding.progressBar.visibility = View.GONE
        }

        // Observe total amount
        paymentsViewModel.totalCollected.observe(viewLifecycleOwner) { totalCollected ->
            val formatter = DecimalFormat("#,##,##0.00")
            binding.totalCollectedValue.text = "₹${formatter.format(totalCollected)}"
        }

        // Observe loading state
        paymentsViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe error messages
        paymentsViewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onPaymentClick(payment: PaymentWithContext) {
        // Navigate to invoice detail screen using the invoice number from the payment
        if (!payment.invoiceNumber.isNullOrEmpty()) {
            val action = PaymentsFragmentDirections.actionPaymentsFragmentToInvoiceDetailFragment(payment.invoiceNumber)
            findNavController().navigate(action)
        } else {
            Toast.makeText(requireContext(), "Invoice information not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePaymentMetrics(payments: List<PaymentWithContext>) {
        // Group payments by method and sum the amounts
        val paymentMethodBreakdown = payments.groupBy {
            it.payment.method.lowercase() // Normalize method names to lowercase
        }
            .mapValues { it.value.sumOf { paymentContext -> paymentContext.payment.amount } }

        // Display amounts for each payment method, handling both upper and lowercase variants
        binding.cashPaymentValue.text = "₹${formatAmount(
            paymentMethodBreakdown["cash"] ?: paymentMethodBreakdown["Cash"] ?: 0.0
        )}"
        binding.upiPaymentValue.text = "₹${formatAmount(
            paymentMethodBreakdown["upi"] ?: paymentMethodBreakdown["UPI"] ?: 0.0
        )}"
        binding.cardPaymentValue.text = "₹${formatAmount(
            paymentMethodBreakdown["card"] ?: paymentMethodBreakdown["Card"] ?: 0.0
        )}"

        // Calculate "Other" as total of all methods not explicitly handled
        val knownMethods = setOf("cash", "upi", "card", "Cash", "UPI", "Card")
        val otherAmount = paymentMethodBreakdown
            .filterKeys { it !in knownMethods }
            .values.sum()

        binding.otherPaymentValue.text = "₹${formatAmount(otherAmount)}"
    }

    private fun formatAmount(amount: Double): String {
        return DecimalFormat("#,##,##0.00").format(amount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}