package com.example.pharmahub11.fragments.shopping

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.CartProductAdapter
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.data.PrescriptionData
import com.example.pharmahub11.data.Product
import com.example.pharmahub11.databinding.FragmentCartBinding
import com.example.pharmahub11.firebase.FirebaseCommon
import com.example.pharmahub11.util.Resource
import com.example.pharmahub11.util.VerticalItemDecoration
import com.example.pharmahub11.viewmodel.CartViewModel
import kotlinx.coroutines.flow.collectLatest

class CartFragment : Fragment(R.layout.fragment_cart) {
    private lateinit var binding: FragmentCartBinding
    private val cartAdapter by lazy { CartProductAdapter() }
    private val viewModel by activityViewModels<CartViewModel>()
    private var totalPrice = 0f
    private var hasPrescriptionItems = false
    private var prescriptionItems = emptyList<CartProduct>()
    private var shouldAutoCheckout = false
    private val args: CartFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCartBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCartRv()
        setupObservers()
        setupClickListeners()

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Map<String, Any>>(
            "prescriptionUploaded"
        )?.observe(viewLifecycleOwner) { data ->
            val imageUrl = data["imageUrl"] as String
            val prescriptionId = data["prescriptionId"] as String
            val productDetails = data["productDetails"] as? List<PrescriptionData.ProductInfo>

            showPrescriptionStatus(imageUrl, productDetails)
            viewModel.loadPrescriptions()
        }

        shouldAutoCheckout = args.returnToCheckout
        binding.imageCloseCart.setOnClickListener { findNavController().navigateUp() }
        viewModel.loadPrescriptions()
    }

    private fun setupCartRv() {
        binding.rvCart.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            adapter = cartAdapter
            addItemDecoration(VerticalItemDecoration())
        }
    }

    private fun setupCartProducts() {
        cartAdapter.differ.submitList(viewModel.cartProducts.value?.data)
        prescriptionItems = cartAdapter.differ.currentList.filter { it.product.requiresPrescription }

        if (prescriptionItems.isNotEmpty()) {
            binding.layoutPrescriptionStatus.visibility = View.VISIBLE
            viewModel.prescriptions.value?.data?.let { prescriptions ->
                getLatestRelevantPrescription(prescriptions)?.let {
                    binding.ivPrescriptionPreview.visibility = View.VISIBLE
                    Glide.with(this).load(it.prescriptionImageUrl).into(binding.ivPrescriptionPreview)
                }
            }
        } else {
            binding.layoutPrescriptionStatus.visibility = View.GONE
        }
    }

    private fun showPrescriptionStatus(imageUrl: String, productDetails: List<PrescriptionData.ProductInfo>? = null) {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.tvPrescriptionStatus.text = "Prescription uploaded - pending review"

        productDetails?.let { products ->
            val productsText = buildString {
                append("Products:\n")
                products.forEachIndexed { index, product ->
                    append("${index + 1}. ${product.name}\n")
                    append("   - Generic: ${product.genericName}\n")
                    append("   - Dosage: ${product.dosageForm}\n")
                    append("   - Strength: ${product.strength}\n\n")
                }
            }
            binding.tvPrescriptionProducts.text = productsText
            binding.tvPrescriptionProducts.visibility = View.VISIBLE
        } ?: run {
            binding.tvPrescriptionProducts.visibility = View.GONE
        }

        Glide.with(this).load(imageUrl).into(binding.ivPrescriptionPreview)
    }

    private fun setupObservers() {
        lifecycleScope.launchWhenStarted {
            viewModel.productsPrice.collectLatest { price ->
                price?.let {
                    totalPrice = it
                    binding.tvTotalPrice.text = "PKR $it"
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.deleteDialog.collectLatest {
                showDeleteConfirmationDialog(it)
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.cartProducts.collectLatest {
                when (it) {
                    is Resource.Loading -> showLoading()
                    is Resource.Success -> {
                        hideLoading()
                        if (it.data.isNullOrEmpty()) showEmptyCart() else {
                            hideEmptyCart()
                            setupCartProducts()
                        }
                    }
                    is Resource.Error -> showError(it.message)
                    else -> Unit
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.prescriptions.collect { state ->
                when (state) {
                    is Resource.Loading -> showPrescriptionLoading()
                    is Resource.Success -> state.data?.let { handlePrescriptions(it) }
                    is Resource.Error -> showPrescriptionError(state.message)
                    else -> Unit
                }
            }
        }
    }

    private fun setupClickListeners() {
        cartAdapter.onProductClick = { navigateToProductDetails(it.product) }
        cartAdapter.onPlusClick = { viewModel.changeQuantity(it, FirebaseCommon.QuantityChanging.INCREASE) }
        cartAdapter.onMinusClick = { viewModel.changeQuantity(it, FirebaseCommon.QuantityChanging.DECREASE) }
        binding.buttonCheckout.setOnClickListener { checkPrescriptionRequirements() }
    }

    private fun checkPrescriptionRequirements() {
        prescriptionItems = cartAdapter.differ.currentList.filter {
            it.product.requiresPrescription || it.product.category.equals("prescription", true)
        }
        hasPrescriptionItems = prescriptionItems.isNotEmpty()

        if (!hasPrescriptionItems) {
            proceedToCheckout()
            return
        }

        when (val state = viewModel.prescriptions.value) {
            is Resource.Success -> handlePrescriptionState(state.data)
            is Resource.Error -> showError(state.message)
            else -> viewModel.loadPrescriptions()
        }
    }

    private fun handlePrescriptionState(prescriptions: List<PrescriptionData>?) {
        prescriptions ?: run {
            showPrescriptionUploadDialog()
            return
        }

        val relevantPrescription = getLatestRelevantPrescription(prescriptions) ?: run {
            showPrescriptionUploadDialog()
            return
        }

        when (relevantPrescription.status) {
            PrescriptionData.STATUS_APPROVED -> proceedToCheckout()
            PrescriptionData.STATUS_REJECTED -> {
                showPrescriptionStatus(relevantPrescription.prescriptionImageUrl, relevantPrescription.products)
                Toast.makeText(requireContext(), "Prescription rejected. Please upload a new one.", Toast.LENGTH_LONG).show()
            }
            else -> {
                showPrescriptionStatus(relevantPrescription.prescriptionImageUrl, relevantPrescription.products)
                Toast.makeText(requireContext(), "Prescription under review", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handlePrescriptions(prescriptions: List<PrescriptionData>) {
        if (prescriptions.isEmpty()) {
            binding.layoutPrescriptionStatus.visibility = View.GONE
            if (hasPrescriptionItems) showPrescriptionUploadDialog()
            return
        }

        val latestPrescription = getLatestRelevantPrescription(prescriptions) ?: run {
            binding.layoutPrescriptionStatus.visibility = View.GONE
            if (hasPrescriptionItems) showPrescriptionUploadDialog()
            return
        }

        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.tvPrescriptionStatus.text = "Prescription: ${latestPrescription.status.replaceFirstChar { it.uppercase() }}"

        // Show product details if available
        if (latestPrescription.products.isNotEmpty()) {
            binding.tvPrescriptionProducts.text = buildString {
                append("Products:\n")
                latestPrescription.products.forEachIndexed { index, product ->
                    append("${index + 1}. ${product.name}\n")
                    append("   - Generic: ${product.genericName}\n")
                    append("   - Dosage: ${product.dosageForm}\n")
                    append("   - Strength: ${product.strength}\n\n")
                }
            }
            binding.tvPrescriptionProducts.visibility = View.VISIBLE
        } else {
            binding.tvPrescriptionProducts.visibility = View.GONE
        }

        when (latestPrescription.status) {
            PrescriptionData.STATUS_APPROVED -> {
                binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_green))
                binding.buttonCheckout.isEnabled = true
                binding.buttonCheckout.text = getString(R.string.checkout)
                binding.ivPrescriptionPreview.visibility = View.VISIBLE
                Glide.with(this).load(latestPrescription.prescriptionImageUrl).into(binding.ivPrescriptionPreview)
                if (shouldAutoCheckout) proceedToCheckout()
            }
            PrescriptionData.STATUS_REJECTED -> {
                binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_red))
                binding.buttonCheckout.isEnabled = false
                binding.buttonCheckout.text = getString(R.string.prescription_rejected)
                binding.ivPrescriptionPreview.visibility = View.GONE
            }
            else -> {
                binding.tvPrescriptionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.g_orange_yellow))
                binding.buttonCheckout.isEnabled = false
                binding.buttonCheckout.text = getString(R.string.waiting_approval)
                binding.ivPrescriptionPreview.visibility = View.GONE
            }
        }
    }

    private fun showPrescriptionUploadDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.prescription_required))
            .setMessage(prescriptionItems.joinToString("\n") { "• ${it.product.name}" })
            .setPositiveButton(getString(R.string.upload_prescription)) { _, _ ->
                navigateToPrescriptionUpload()
            }
            .setNegativeButton(getString(R.string.remove_items)) { _, _ ->
                removePrescriptionItems()
            }
            .show()
    }

    private fun navigateToPrescriptionUpload() {
        val action = CartFragmentDirections.actionCartFragmentToPrescriptionFragment(
            productIds = prescriptionItems.map { it.product.id }.toTypedArray(),
            returnToCheckout = true
        )
        findNavController().navigate(action)
    }

    private fun removePrescriptionItems() {
        prescriptionItems.forEach { viewModel.deleteCartProduct(it) }
        Toast.makeText(requireContext(), getString(R.string.prescription_items_removed), Toast.LENGTH_SHORT).show()
    }

    private fun proceedToCheckout() {
        val cartProducts = cartAdapter.differ.currentList
        val prescriptions = (viewModel.prescriptions.value as? Resource.Success)?.data ?: emptyList()
        val latestPrescription = getLatestRelevantPrescription(prescriptions)
        val isPrescriptionVerified = !hasPrescriptionItems || latestPrescription?.status == PrescriptionData.STATUS_APPROVED

        if (!isPrescriptionVerified) {
            Toast.makeText(requireContext(), "Please wait for prescription approval", Toast.LENGTH_LONG).show()
            return
        }

        try {
            findNavController().navigate(
                CartFragmentDirections.actionCartFragmentToBillingFragment(
                    totalPrice = totalPrice,
                    products = cartProducts.toTypedArray(),
                    isPrescriptionVerified = isPrescriptionVerified,
                    prescriptionData = if (latestPrescription != null) arrayOf(latestPrescription) else emptyArray()
                )
            )
        } catch (e: Exception) {
            Log.e("CartFragment", "Navigation failed", e)
            Toast.makeText(requireContext(), "Checkout failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLatestRelevantPrescription(prescriptions: List<PrescriptionData>): PrescriptionData? {
        return viewModel.getLatestRelevantPrescription(
            prescriptions,
            cartAdapter.differ.currentList.filter {
                it.product.requiresPrescription || it.product.category.equals("prescription", true)
            }
        )
    }

    private fun showLoading() { binding.progressbarCart.visibility = View.VISIBLE }
    private fun hideLoading() { binding.progressbarCart.visibility = View.INVISIBLE }
    private fun showEmptyCart() { binding.layoutCartEmpty.visibility = View.VISIBLE }
    private fun hideEmptyCart() { binding.layoutCartEmpty.visibility = View.GONE }
    private fun showError(message: String?) {
        Toast.makeText(requireContext(), message ?: "Error occurred", Toast.LENGTH_SHORT).show()
    }
    private fun showPrescriptionLoading() {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.tvPrescriptionStatus.text = getString(R.string.checking_prescription)
    }
    private fun showPrescriptionError(message: String?) {
        binding.layoutPrescriptionStatus.visibility = View.VISIBLE
        binding.tvPrescriptionStatus.text = message ?: getString(R.string.prescription_error)
    }
    private fun navigateToProductDetails(product: Product) {
        findNavController().navigate(
            R.id.action_cartFragment_to_productDetailsFragment,
            Bundle().apply { putParcelable("product", product) }
        )
    }
    private fun showDeleteConfirmationDialog(item: CartProduct) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_item))
            .setMessage(getString(R.string.delete_item_confirmation))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(getString(R.string.yes)) { _, _ -> viewModel.deleteCartProduct(item) }
            .show()
    }
}