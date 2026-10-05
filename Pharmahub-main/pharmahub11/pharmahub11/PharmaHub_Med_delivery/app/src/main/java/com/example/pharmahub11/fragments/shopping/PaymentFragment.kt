package com.example.pharmahub11.fragments.shopping

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pharmahub11.R
import com.example.pharmahub11.adapters.PaymentMethodAdapter
import com.example.pharmahub11.data.PaymentMethod
import com.example.pharmahub11.data.PaymentType
import com.example.pharmahub11.databinding.FragmentPaymentBinding

class PaymentFragment : Fragment() {
    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!
    private lateinit var paymentAdapter: PaymentMethodAdapter
    private var selectedMethod: PaymentMethod? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPaymentMethods()
        setupConfirmButton()
    }

    private fun setupPaymentMethods() {
        val paymentMethods = listOf(
            PaymentMethod(
                id = "cod",
                type = PaymentType.COD,
                name = "Cash on Delivery",
                info = "Pay in cash when you receive your order",
                iconRes = R.drawable.ic_credit_card_24,
                note = "Please keep exact change ready for delivery"
            ),
            PaymentMethod(
                id = "card",
                type = PaymentType.CARD,
                name = "Credit/Debit Card",
                info = "Pay with your Visa, Mastercard, etc.",
                iconRes = R.drawable.ic_cards
            )
            // Add other payment methods as needed
        )

        paymentAdapter = PaymentMethodAdapter(paymentMethods) { method ->
            selectedMethod = method
            updatePaymentNote(method)
        }

        binding.rvPaymentMethods.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = paymentAdapter
            setHasFixedSize(true)
        }
    }

    private fun updatePaymentNote(method: PaymentMethod) {
        binding.tvPaymentNote.text = when (method.type) {
            PaymentType.COD -> method.note
            else -> ""
        }
    }

    private fun setupConfirmButton() {
        binding.btnConfirmPayment.setOnClickListener {
            selectedMethod?.let { method ->
                when (method.type) {
                    PaymentType.COD -> processCodOrder()
                    else -> processOnlinePayment()
                }
            } ?: run {
                Toast.makeText(
                    requireContext(),
                    "Please select a payment method",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun processCodOrder() {
        // Navigate to order confirmation with COD details
        findNavController().navigate(
            PaymentFragmentDirections.actionPaymentFragmentToProfileFragment(
                paymentMethod = selectedMethod?.name ?: "Cash on Delivery"
            )
        )
    }

    private fun processOnlinePayment() {
        // Handle online payment processing
        Toast.makeText(
            requireContext(),
            "Processing ${selectedMethod?.name} payment",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}