package com.example.pharmahub11.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.adapters.AllOrdersAdapter
import com.example.pharmahub11.databinding.FragmentOrdersBinding
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.viewmodel.AllOrdersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class AllOrdersFragment : Fragment() {
    private lateinit var binding: FragmentOrdersBinding
    private val viewModel by viewModels<AllOrdersViewModel>()
    private val ordersAdapter by lazy { AllOrdersAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOrdersBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOrdersRv()

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.allOrders.collectLatest { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.progressbarAllOrders.visibility = View.VISIBLE
                        binding.tvEmptyOrders.visibility = View.GONE
                    }
                    is Resource.Success -> {
                        binding.progressbarAllOrders.visibility = View.GONE
                        resource.data?.let { orders ->
                            ordersAdapter.differ.submitList(orders)
                            binding.tvEmptyOrders.visibility =
                                if (orders.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                    is Resource.Error -> {
                        binding.progressbarAllOrders.visibility = View.GONE
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                resource.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    else -> Unit
                }
            }
        }

        ordersAdapter.onClick = { order ->
            if (isAdded) {
                findNavController().navigate(
                    AllOrdersFragmentDirections.actionOrdersFragmentToOrderDetailFragment(order)
                )
            }
        }
    }

    private fun setupOrdersRv() {
        binding.rvAllOrders.apply {
            adapter = ordersAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        }
    }
}