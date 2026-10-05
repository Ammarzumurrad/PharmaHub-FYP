package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.pharmahub11.data.order.Order
import com.example.pharmahub11.data.order.OrderStatus
import com.example.pharmahub11.databinding.OrderItemBinding

class AllOrdersAdapter : Adapter<AllOrdersAdapter.OrdersViewHolder>() {

    inner class OrdersViewHolder(private val binding: OrderItemBinding) : ViewHolder(binding.root) {
        fun bind(order: Order) {
            binding.apply {
                tvOrderId.text = "Order #" + order.orderId.toString()
                tvOrderDate.text = order.date

                // Find the enum by string value instead of using valueOf directly
                val status = getOrderStatusByString(order.orderStatus)

                // Set the color based on the status color resource
                val statusColor = ContextCompat.getColor(itemView.context, status.colorRes)
                imageOrderState.setImageResource(status.iconRes)
                imageOrderState.setColorFilter(statusColor)

                // Optionally set the background color of the parent layout based on status
                // parentLinear.setBackgroundResource(status.backgroundRes)
            }
        }

        // Helper method to find the enum by string value
        private fun getOrderStatusByString(statusString: String): OrderStatus {
            // First try direct valueOf (in case it's already in uppercase)
            return try {
                OrderStatus.valueOf(statusString)
            } catch (e: IllegalArgumentException) {
                // If that fails, try to find by case-insensitive comparison
                OrderStatus.values().find {
                    it.status.equals(statusString, ignoreCase = true)
                } ?: OrderStatus.ORDERED // Default fallback
            }
        }
    }

    private val diffUtil = object : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.products == newItem.products
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffUtil)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrdersViewHolder {
        return OrdersViewHolder(
            OrderItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: OrdersViewHolder, position: Int) {
        val order = differ.currentList[position]
        holder.bind(order)

        holder.itemView.setOnClickListener {
            onClick?.invoke(order)
        }
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    var onClick: ((Order) -> Unit)? = null
}