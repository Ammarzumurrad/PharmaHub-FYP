package com.example.pharmahub11.fragments.shopping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.BestProductsAdapter
import com.example.pharmahub11.databinding.FragmentSearchBinding
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.util.hideKeyboard
import com.example.pharmahub11.util.showBottomNavigationView
import com.example.pharmahub11.viewmodel.SearchViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.fragment_search) {
    private lateinit var binding: FragmentSearchBinding
    private val viewModel by viewModels<SearchViewModel>()
    private val searchAdapter by lazy { BestProductsAdapter() }

    // Root view for Snackbar
    private lateinit var snackbarView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchBinding.inflate(inflater, container, false)
        snackbarView = binding.root // Use the root view for Snackbar
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSearchView()
        setupRecyclerView()
        setupFilterChips()
        observeSearchResults()

        searchAdapter.onClick = { product ->
            hideKeyboard()
            val action = SearchFragmentDirections.actionSearchFragmentToProductDetailsFragment(product)
            findNavController().navigate(action)
        }
    }

    private fun setupSearchView() {
        binding.searchView.isIconified = false
        binding.searchView.requestFocus()

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let {
                    viewModel.searchProducts(it)
                    hideKeyboard()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    if (it.length >= 2) {
                        viewModel.searchProducts(it)
                    } else {
                        searchAdapter.differ.submitList(emptyList())
                        binding.tvNoResults.visibility = View.GONE
                    }
                }
                return true
            }
        })

        binding.searchView.setOnCloseListener {
            searchAdapter.differ.submitList(emptyList())
            binding.tvNoResults.visibility = View.GONE
            false
        }
    }

    private fun setupRecyclerView() {
        binding.rvSearchResults.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = searchAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }
    }

    private fun setupFilterChips() {
        val categories = listOf(
            "All" to null,
            "Prescription" to "Prescription",
            "OTC" to "Over The Counter",
            "Vitamins" to "Vitamins",
            "First Aid" to "First Aid"
        )

        categories.forEach { (displayName, categoryValue) ->
            val chip = Chip(requireContext()).apply {
                text = displayName
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.setCategoryFilter(categoryValue)
                        binding.searchView.query?.toString()?.let { currentQuery ->
                            if (currentQuery.length >= 2) {
                                viewModel.searchProducts(currentQuery)
                            }
                        }
                    }
                }
            }
            binding.filterChipGroup.addView(chip)
        }

        (binding.filterChipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }

    private fun observeSearchResults() {
        viewModel.searchResults.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.searchProgressBar.visibility = View.VISIBLE
                    binding.tvNoResults.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.searchProgressBar.visibility = View.GONE
                    searchAdapter.differ.submitList(resource.data)
                    binding.tvNoResults.visibility = if (resource.data.isNullOrEmpty()) View.VISIBLE else View.GONE
                }
                is Resource.Error -> {
                    binding.searchProgressBar.visibility = View.GONE
                    binding.tvNoResults.visibility = View.VISIBLE
                    showError(resource.message ?: "Unknown error occurred")
                }
                else -> Unit
            }
        }
    }

    private fun showError(message: String) {
        // Use the root view for Snackbar to avoid NestedScrollView issues
        Snackbar.make(snackbarView, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        showBottomNavigationView()
    }
}