package com.jewelrypos.swarnakhatabook

import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
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
        setupSearchView()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSearchView() {
        with(binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView) {
            queryHint = "Search Payments..."
            inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setOnQueryTextListener(object :
                androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let {
                        Log.d("PaymentsFragment", "Search submitted: $it")
                        paymentsViewModel.setSearchQuery(it)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let {
                        paymentsViewModel.setSearchQuery(it)
                    }
                    return true
                }
            })
            setOnCloseListener {
                val searchView = binding.topAppBar.menu.findItem(R.id.action_search).actionView as androidx.appcompat.widget.SearchView
                onActionViewCollapsed()
                searchView.setQuery("", false)
                searchView.clearFocus()
                paymentsViewModel.setSearchQuery("")
                true
            }
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
                    val selectedPeriod = periods[position]
                    paymentsViewModel.setPeriodFilter(selectedPeriod)
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
        // Observe filtered payments list
        paymentsViewModel.filteredPayments.observe(viewLifecycleOwner) { payments ->
            // Update adapter with filtered payments
            paymentsAdapter.updatePayments(payments)

            // Update payment metrics
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

        // Format large numbers in a more compact way for the payment method boxes
        binding.cashPaymentValue.text = formatCompactAmount(
            paymentMethodBreakdown["cash"] ?: 0.0
        )

        binding.upiPaymentValue.text = formatCompactAmount(
            paymentMethodBreakdown["upi"] ?: 0.0
        )

        binding.cardPaymentValue.text = formatCompactAmount(
            paymentMethodBreakdown["card"] ?: 0.0
        )

        // Calculate "Other" as total of all methods not explicitly handled
        val knownMethods = setOf("cash", "upi", "card")
        val otherAmount = paymentMethodBreakdown
            .filterKeys { it !in knownMethods }
            .values.sum()

        binding.otherPaymentValue.text = formatCompactAmount(otherAmount)
    }

    // Helper method for compact currency formatting
    private fun formatCompactAmount(amount: Double): String {
        // For small amounts, show the full value
        if (amount < 1000) {
            return "₹${DecimalFormat("#,##0").format(amount)}"
        }

        // For thousands (K)
        if (amount < 100000) {
            val thousands = amount / 1000.0
            return "₹${DecimalFormat("#,##0.#").format(thousands)}K"
        }

        // For lakhs (L)
        if (amount < 10000000) {
            val lakhs = amount / 100000.0
            return "₹${DecimalFormat("#,##0.#").format(lakhs)}L"
        }

        // For crores (Cr)
        val crores = amount / 10000000.0
        return "₹${DecimalFormat("#,##0.#").format(crores)}Cr"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}