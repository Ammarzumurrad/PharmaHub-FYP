package com.example.pharmahub11.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.adapters.BillingProductsAdapter
import com.example.pharmahub11.data.order.OrderStatus
import com.example.pharmahub11.databinding.FragmentOrderDetailBinding
import com.example.pharmahub11.util.VerticalItemDecoration
import com.example.pharmahub11.util.hideBottomNavigationView

class OrderDetailFragment : Fragment() {
    private lateinit var binding: FragmentOrderDetailBinding
    private val billingProductsAdapter by lazy { BillingProductsAdapter() }
    private val args by navArgs<OrderDetailFragmentArgs>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOrderDetailBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val order = args.order
        hideBottomNavigationView()
        setupOrderRv()

        binding.apply {
            tvOrderId.text = "Order #${order.orderId}"

            // Set dynamic steps from visible statuses
            val visibleStatuses = OrderStatus.getCustomerVisibleStatuses()
            stepView.setSteps(visibleStatuses.map { it.status })

            // Convert order.orderStatus (String) to OrderStatus enum
            val currentOrderStatus = getOrderStatus(order.orderStatus)

            // Get current index for step view
            val currentIndex = visibleStatuses.indexOf(currentOrderStatus).takeIf { it >= 0 } ?: 0
            stepView.go(currentIndex, false)

            if (currentIndex == visibleStatuses.lastIndex) {
                stepView.done(true)
            }

            // Bind address and price info
            tvFullName.text = order.address.fullName
            tvAddress.text = "${order.address.street} ${order.address.city}"
            tvPhoneNumber.text = order.address.phone
            tvTotalPrice.text = "Rs ${order.totalPrice}"
        }

        billingProductsAdapter.differ.submitList(order.products)
    }

    private fun setupOrderRv() {
        binding.rvProducts.apply {
            adapter = billingProductsAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            addItemDecoration(VerticalItemDecoration())
        }
    }

    // Converts a string to the corresponding OrderStatus enum
    private fun getOrderStatus(status: String): OrderStatus {
        return try {
            OrderStatus.valueOf(status.uppercase())
        } catch (e: IllegalArgumentException) {
            OrderStatus.ORDERED // default fallback
        }
    }
}
