package com.example.pharmahub11.fragments.shopping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.StrengthAdapter
import com.example.pharmahub11.adapters.ViewPager2Images
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.databinding.FragmentProductDetailsBinding
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.util.hideBottomNavigationView
import com.example.pharmahub11.viewmodel.DetailsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class ProductDetailsFragment : Fragment() {

    private val args by navArgs<ProductDetailsFragmentArgs>()
    private lateinit var binding: FragmentProductDetailsBinding
    private val viewPagerAdapter by lazy { ViewPager2Images() }
    private val strengthAdapter by lazy { StrengthAdapter() }
    private var selectedStrength: String? = null
    private val viewModel by viewModels<DetailsViewModel>()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        hideBottomNavigationView()
        binding = FragmentProductDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val product = args.product

        setupViewpager()
        setupStrengthRv()

        binding.imageClose.setOnClickListener {
            findNavController().navigateUp()
        }

        strengthAdapter.onItemClick = { strength ->
            selectedStrength = strength
        }

        binding.buttonAddToCart.setOnClickListener {
            val cartProduct = CartProduct(product, 1,"", selectedStrength = selectedStrength)
            viewModel.addUpdateProductInCart(cartProduct)
        }

        lifecycleScope.launchWhenStarted {
            viewModel.addToCart.collectLatest {
                when (it) {
                    is Resource.Loading -> binding.buttonAddToCart.startAnimation()
                    is Resource.Success -> {
                        binding.buttonAddToCart.revertAnimation()
                        binding.buttonAddToCart.setBackgroundColor(
                            ContextCompat.getColor(requireContext(), R.color.black)
                        )
                    }
                    is Resource.Error -> {
                        binding.buttonAddToCart.stopAnimation()
                        Toast.makeText(requireContext(), it.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
        }

        // Display prescription info
        binding.tvPrescriptionInfo.apply {
            text = if (product.requiresPrescription) "Prescription Required" else "No Prescription Needed"
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(
                requireContext(),
                if (product.requiresPrescription)
                    R.drawable.bg_prescription_required
                else
                    R.drawable.bg_no_prescription
            )
        }

        // Display product details
        binding.apply {
            tvProductName.text = product.name
            tvProductPrice.text = "PKR ${"%.2f".format(product.price)}"
            tvProductDescription.text = product.description
            tvDosageForm.text = product.dosageForm
            tvManufacturer.text = product.manufacturer ?: "N/A"
            tvGenericName.text = "Generic: ${product.genericName}"

            when {
                product.strengths.isEmpty() -> {
                    tvStrengthLabel.visibility = View.GONE
                    rvStrengths.visibility = View.GONE
                    tvStrengthValue.visibility = View.GONE
                }
                product.strengths.size == 1 -> {
                    tvStrengthValue.visibility = View.VISIBLE
                    tvStrengthValue.text = product.strengths.first()
                    selectedStrength = product.strengths.first()
                    rvStrengths.visibility = View.GONE
                }
                else -> {
                    tvStrengthValue.visibility = View.GONE
                    rvStrengths.visibility = View.VISIBLE
                    strengthAdapter.differ.submitList(product.strengths)
                }
            }

            viewPagerAdapter.differ.submitList(product.images)
        }
    }

    private fun setupViewpager() {
        binding.viewPagerProductImages.adapter = viewPagerAdapter
    }

    private fun setupStrengthRv() {
        binding.rvStrengths.apply {
            adapter = strengthAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        }
    }
}
