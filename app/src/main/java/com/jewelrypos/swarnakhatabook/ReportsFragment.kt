package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.jewelrypos.swarnakhatabook.Adapters.ReportTypeAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.ReportType
import com.jewelrypos.swarnakhatabook.Factorys.ReportViewModelFactory
import com.jewelrypos.swarnakhatabook.Utilitys.PremiumFeatureHelper
import com.jewelrypos.swarnakhatabook.Utilitys.ThemedM3Dialog
import com.jewelrypos.swarnakhatabook.ViewModle.ReportViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentReportsBinding

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.jewelrypos.swarnakhatabook.Enums.SubscriptionPlan

class ReportsFragment : Fragment() {

    companion object {
        private const val TAG = "ReportsFragment"
    }

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private lateinit var reportAdapter: ReportTypeAdapter

    // Track active coroutines
    private val activeJobs = mutableListOf<Job>()

    // This flag prevents multiple navigations if user taps quickly.
    // It must be reset after navigation is complete.
    private var isNavigating = false // Make sure this is a class member
    private val isUserPremium: Boolean
        get() = reportAdapter.isUserPremium // Access through adapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Fragment created")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Creating view")
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Starting setup")
        val startTime = System.currentTimeMillis()

        setupViewModel()
        Log.d(
            TAG,
            "onViewCreated: ViewModel setup completed in ${System.currentTimeMillis() - startTime}ms"
        )

        setupUI()
        Log.d(
            TAG,
            "onViewCreated: UI setup completed in ${System.currentTimeMillis() - startTime}ms"
        )

        setupObservers()
        Log.d(
            TAG,
            "onViewCreated: Observers setup completed in ${System.currentTimeMillis() - startTime}ms"
        )

        // This check determines if the banner is visible and sets reportAdapter.isUserPremium.
        // It's crucial for controlling item clickability.
        checkPremiumStatus()

        Log.d(
            TAG,
            "onViewCreated: Initial setup completed in ${System.currentTimeMillis() - startTime}ms"
        )
    }

    private fun checkPremiumStatus() {
        Log.d(TAG, "checkPremiumStatus: Checking premium status")
        PremiumFeatureHelper.checkPremiumAccess(
            fragment = this,
            featureName = "Business Reports",
            minimumPlan = SubscriptionPlan.STANDARD, // Specify minimum plan for Reports
            premiumAction = {
                Log.d(TAG, "checkPremiumStatus: User has premium access")
                binding.premiumBanner.visibility = View.GONE
                reportAdapter.isUserPremium = true // User is premium, items are clickable

            },
            nonPremiumAction = {
                Log.d(TAG, "checkPremiumStatus: User does not have premium access")
                binding.premiumBanner.visibility = View.VISIBLE
                // Show a non-blocking message that report generation requires Standard plan
                Toast.makeText(
                    requireContext(),
                    "Standard or Premium subscription required to generate reports",
                    Toast.LENGTH_LONG
                ).show()
                reportAdapter.isUserPremium = false // User is not premium, items are not clickable
            }
        )
    }

    private fun setupViewModel() {
        Log.d(TAG, "setupViewModel: Initializing ViewModel")
        val factory = ReportViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory)[ReportViewModel::class.java]
        Log.d(TAG, "setupViewModel: ViewModel initialized successfully")
    }

    private fun setupUI() {
        Log.d(TAG, "setupUI: Setting up UI components")
        // Setup toolbar
        binding.topAppBar.setNavigationOnClickListener {
            Log.d(TAG, "setupUI: Navigation back button clicked")
            findNavController().navigateUp()
        }


        // Setup report types recycler view
        // The adapter's isUserPremium flag controls whether `onItemClick` is triggered.
        // The `isNavigating` flag here prevents *multiple* navigations from very rapid clicks.
        reportAdapter = ReportTypeAdapter { reportType ->
            Log.d(TAG, "setupUI: Report type selected: ${reportType.id}")
            if (!isNavigating) { // Only allow navigation if not already navigating
                isNavigating = true // Set flag to true to prevent further clicks
                navigateToReportDetail(reportType)
            } else {
                Log.d(TAG, "setupUI: Navigation already in progress, ignoring click")
            }
        }

        binding.reportsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = reportAdapter
        }
        Log.d(TAG, "setupUI: RecyclerView setup completed")

        // Set report types
        val reportTypes = listOf(
            ReportType(
                id = "sales_report",
                title = getString(R.string.sales_report),
                description = getString(R.string.sales_report_description),
                iconResId = R.drawable.hugeicons__chart_up
            ),
//            ReportType(
//                id = "inventory_valuation",
//                title = "Inventory Valuation",
//                description = "Current inventory value and stock levels by product category",
//                iconResId = R.drawable.mingcute__inventory_line
//            ),
            ReportType(
                id = "customer_statement",
                title = getString(R.string.customer_account_statement),
                description = getString(R.string.customer_statement_description),
                iconResId = R.drawable.f7__doc_person
            ),
            ReportType(
                id = "gst_report",
                title = getString(R.string.gst_report),
                description = getString(R.string.gst_report_description),
                iconResId = R.drawable.hugeicons__taxes
            ),
            ReportType(
                id = "low_stock",
                title = getString(R.string.low_stock_report),
                description = getString(R.string.low_stock_description),
                iconResId = R.drawable.lets_icons__box_light
            )
        )

        reportAdapter.submitList(reportTypes)
        Log.d(TAG, "setupUI: Report types submitted to adapter")
    }

    private fun setupObservers() {
        Log.d(TAG, "setupObservers: Setting up LiveData observers")
        
        // Observe selected chip
        viewModel.selectedChipId.observe(viewLifecycleOwner) { chipId ->
            chipId?.let {
                Log.d(TAG, "setupObservers: Selected chip changed to: $chipId")
                // If we're coming back from a report fragment, we might want to navigate to the same report
                // with the selected chip's date range
                if (isNavigating) {
                    // Get the current destination
                    val currentDestination = findNavController().currentDestination?.id
                    when (currentDestination) {
                        R.id.salesReportFragment -> {
                            // Already on sales report, just update the date range
                            viewModel.loadSalesReport()
                        }
                        R.id.gstReportFragment -> {
                            // Already on GST report, just update the date range
                            viewModel.generateGstReport()
                        }
                        // Add other report types as needed
                    }
                }
            }
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "setupObservers: Loading state changed: $isLoading")
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Log.e(TAG, "setupObservers: Error occurred: $message")
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }
        Log.d(TAG, "setupObservers: All observers setup completed")
    }




    private fun navigateToReportDetail(reportType: ReportType) {
        Log.d(TAG, "navigateToReportDetail: Navigating to ${reportType.id}")

        when (reportType.id) {
            "sales_report" -> findNavController().navigate(R.id.action_reportsFragment_to_salesReportFragment)
            "inventory_valuation" -> findNavController().navigate(R.id.action_reportsFragment_to_inventoryReportFragment)
            "customer_statement" -> findNavController().navigate(R.id.action_reportsFragment_to_customerStatementFragment)
            "gst_report" -> findNavController().navigate(R.id.action_reportsFragment_to_gstReportFragment)
            "low_stock" -> findNavController().navigate(R.id.action_reportsFragment_to_lowStockReportFragment)
        }

        // Corrected: Reset the navigation flag after a short delay.
        // This allows the navigation to begin before the flag is reset,
        // preventing immediate multiple navigations from rapid taps.
        lifecycleScope.launch {
            delay(50) // Adjust this delay as needed (e.g., 200-500ms)
            isNavigating = false
            Log.d(TAG, "navigateToReportDetail: isNavigating reset to false")
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Cleaning up resources")
        super.onDestroyView()
        // Cancel all running coroutines
        activeJobs.forEach {
            Log.d(TAG, "onDestroyView: Cancelling coroutine job")
            it.cancel()
        }
        activeJobs.clear()
        _binding = null
        Log.d(TAG, "onDestroyView: Cleanup completed")
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Fragment being destroyed")
        super.onDestroy()
    }
}