package com.jewelrypos.swarnakhatabook

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.jewelrypos.swarnakhatabook.ViewModle.SplashViewModel
import com.jewelrypos.swarnakhatabook.databinding.FragmentLauncherBinding


class launcherFragment : Fragment() {

    private var _binding: FragmentLauncherBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: SplashViewModel
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLauncherBinding.inflate(inflater, container, false)
        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel using ViewModelProvider.Factory for AndroidViewModel
        val factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        viewModel = ViewModelProvider(this, factory).get(SplashViewModel::class.java)

        // Observe navigation events
        viewModel.navigationEvent.observe(viewLifecycleOwner) { event ->
            Handler(Looper.getMainLooper()).postDelayed({
                when (event) {
                    is SplashViewModel.NavigationEvent.NavigateToDashboard -> {
                        findNavController().navigate(R.id.action_launcherFragment_to_mainScreenFragment)
                    }

                    is SplashViewModel.NavigationEvent.NavigateToRegistration -> {
                        findNavController().navigate(R.id.action_launcherFragment_to_getDetailsFragment)
                    }

                    is SplashViewModel.NavigationEvent.NoInternet -> {
                        showNoInternetDialog()
                    }
                }
            }, 3000) // 3 second delay
        }

        // Trigger connectivity and auth check
        viewModel.checkInternetAndAuth()
    }

    private fun showNoInternetDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("No Internet Connection")
            .setMessage("Please check your internet connection and try again.")
            .setPositiveButton("Retry") { _, _ ->
                // Retry connectivity and auth check
                viewModel.checkInternetAndAuth()
            }
            .setNegativeButton("Exit") { _, _ ->
                requireActivity().finish()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

}