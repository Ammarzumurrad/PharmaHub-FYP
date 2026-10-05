package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pharmahub11.R
import com.example.pharmahub11.data.PaymentMethod
import com.example.pharmahub11.data.PaymentType
import com.example.pharmahub11.databinding.ItemCodPaymentBinding


class PaymentMethodAdapter(
    private val paymentMethods: List<PaymentMethod>,
    private val onMethodSelected: (PaymentMethod) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedPosition = -1

    sealed class PaymentMethodViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        class CodViewHolder(private val binding: ItemCodPaymentBinding) : PaymentMethodViewHolder(binding.root) {
            fun bind(method: PaymentMethod, isSelected: Boolean) {
                binding.apply {
                    tvPaymentTitle.text = method.name
                    tvPaymentNote.text = method.note
                    ivPaymentIcon.setImageResource(method.iconRes)
                    rbPaymentSelected.isChecked = isSelected
                }
            }
        }

        class DefaultViewHolder(private val binding: ItemCodPaymentBinding) : PaymentMethodViewHolder(binding.root) {
            fun bind(method: PaymentMethod, isSelected: Boolean) {
                binding.apply {
                    tvPaymentTitle.text = method.name
                    ivPaymentIcon.setImageResource(method.iconRes)
                    rbPaymentSelected.isChecked = isSelected
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentMethodViewHolder {
        return when (viewType) {
            R.layout.item_cod_payment -> {
                val binding = ItemCodPaymentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                PaymentMethodViewHolder.CodViewHolder(binding)
            }
            else -> {
                val binding = ItemCodPaymentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                PaymentMethodViewHolder.DefaultViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val method = paymentMethods[position]

        when (holder) {
            is PaymentMethodViewHolder.CodViewHolder -> {
                holder.bind(method, position == selectedPosition)
                holder.itemView.setOnClickListener {
                    selectPosition(position)
                    onMethodSelected(method)
                }
            }
            is PaymentMethodViewHolder.DefaultViewHolder -> {
                holder.bind(method, position == selectedPosition)
                holder.itemView.setOnClickListener {
                    selectPosition(position)
                    onMethodSelected(method)
                }
            }
        }
    }

    private fun selectPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    override fun getItemCount() = paymentMethods.size

    override fun getItemViewType(position: Int): Int {
        return when (paymentMethods[position].type) {
            PaymentType.COD -> R.layout.item_cod_payment
            else -> R.layout.item_cod_payment
        }
    }
}