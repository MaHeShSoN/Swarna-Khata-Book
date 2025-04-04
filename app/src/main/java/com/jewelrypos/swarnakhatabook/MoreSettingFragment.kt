package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jewelrypos.swarnakhatabook.Adapters.SettingsAdapter
import com.jewelrypos.swarnakhatabook.DataClasses.SettingsItem
import com.jewelrypos.swarnakhatabook.databinding.FragmentMoreSettingBinding


class MoreSettingFragment : Fragment() {
    private var _binding: FragmentMoreSettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsAdapter: SettingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMoreSettingBinding.inflate(inflater, container, false)


        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup settings list
        setupSettingsList()
    }

    private fun setupSettingsList() {
        val settingsItems = listOf(
            SettingsItem(
                id = "shop_details",
                title = "Shop Details",
                subtitle = "Configure your shop information for invoices",
                iconResId = R.drawable.ic_store
            ),
            SettingsItem(
                id = "invoice_format",
                title = "Invoice PDF Format",
                subtitle = "Customize the appearance of your invoice PDFs",
                iconResId = R.drawable.ic_invoice
            ),
            SettingsItem(
                id = "invoice_template",
                title = "Invoice Template",
                subtitle = "Choose from multiple professional templates",
                iconResId = R.drawable.ic_template,
                badgeText = "NEW"
            )
        )

        settingsAdapter = SettingsAdapter(settingsItems) { item ->
            when (item.id) {
                "shop_details" -> {
                    val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_shopSettingsFragment)

                }
                "invoice_format" -> {
                    val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_invoicePdfSettingsFragment)
                }
                "invoice_template" -> {
                    val mainNavController = requireActivity().findNavController(R.id.nav_host_fragment)
                    mainNavController.navigate(R.id.action_mainScreenFragment_to_templateSelectionFragment)
                }
            }
        }

        binding.settingsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = settingsAdapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}