package com.jewelrypos.swarnakhatabook

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.jewelrypos.swarnakhatabook.databinding.FragmentMainScreenBinding


class MainScreenFragment : Fragment() {



    private var _binding: FragmentMainScreenBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMainScreenBinding.inflate(inflater,container,false)
        val innerNavController = childFragmentManager.findFragmentById(R.id.inner_nav_host_fragment)
            ?.findNavController()

        if (innerNavController != null) {
            binding.bottomNavigation.setupWithNavController(innerNavController)
        }
        // Inflate the layout for this fragment
        return binding.root
    }
}