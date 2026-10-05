package com.example.pharmahub11.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.example.pharmahub11.data.CartProduct
import com.example.pharmahub11.databinding.BillingProductsRvItemBinding
import com.example.pharmahub11.helper.getProductPrice

class BillingProductsAdapter: Adapter<BillingProductsAdapter.BillingProductsViewHolder>() {

    inner class BillingProductsViewHolder(val binding: BillingProductsRvItemBinding): ViewHolder(binding.root) {

        fun bind(billingProduct: CartProduct) {
            binding.apply {
                // Load product image
                Glide.with(itemView)
                    .load(billingProduct.product.images.firstOrNull())
                    .into(imageCartProduct)

                // Set product info
                tvProductCartName.text = billingProduct.product.name
                tvBillingProductQuantity.text = billingProduct.quantity.toString()

                // Calculate and display price
                val priceAfterPercentage = billingProduct.product.offerPercentage.getProductPrice(billingProduct.product.price)
                tvProductCartPrice.text = "PKR ${String.format("%.2f", priceAfterPercentage)}"

                // Display strength if available
                billingProduct.selectedStrength?.let { strength ->
                    tvCartProductStrength.text = strength
                    tvCartProductStrength.visibility = View.VISIBLE
                } ?: run {
                    tvCartProductStrength.visibility = View.GONE
                }


            }
        }
    }

    private val diffUtil = object : DiffUtil.ItemCallback<CartProduct>(){
        override fun areItemsTheSame(oldItem: CartProduct, newItem: CartProduct): Boolean {
            return oldItem.product == newItem.product
        }

        override fun areContentsTheSame(oldItem: CartProduct, newItem: CartProduct): Boolean {
            return oldItem == newItem
        }
    }

    val differ = AsyncListDiffer(this, diffUtil)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillingProductsViewHolder {
        return BillingProductsViewHolder(
            BillingProductsRvItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: BillingProductsViewHolder, position: Int) {
        val billingProduct = differ.currentList[position]
        holder.bind(billingProduct)
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }
}