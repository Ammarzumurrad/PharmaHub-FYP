package com.example.pharmahub11.data.order

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.pharmahub11.R

enum class OrderStatus(
    val status: String,

    @StringRes val stringRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val colorRes: Int,
    @DrawableRes val backgroundRes: Int,
    val isCustomerVisible: Boolean = true,
    val isPharmacistActionable: Boolean = true
) {
    ORDERED(
        "Ordered",
        R.string.order_status_ordered,
        R.drawable.ic_ordered,
        R.color.status_ordered,
        R.drawable.bg_status_ordered,
        true,
        true
    ),
    CONFIRMED(
        "Confirmed",
        R.string.order_status_confirmed,
        R.drawable.ic_confirmed,
        R.color.status_confirmed,
        R.drawable.bg_status_approved,
        true,
        true
    ),
    PROCESSING(
        "Processing",
        R.string.order_status_processing,
        R.drawable.ic_processing,
        R.color.status_processing,
        R.drawable.bg_status_waiting,
        false, // Not shown to customers
        true
    ),
    SHIPPED(
        "Shipped",
        R.string.order_status_shipped,
        R.drawable.ic_shipped,
        R.color.status_shipped,
        R.drawable.bg_status_prepared,
        true,
        true
    ),
    DELIVERED(
        "Delivered",
        R.string.order_status_delivered,
        R.drawable.ic_delivered,
        R.color.status_delivered,
        R.drawable.bg_status_delivered,
        true,
        false // Final state
    ),
    CANCELLED(
        "Cancelled",
        R.string.order_status_cancelled,
        R.drawable.ic_cancelled,
        R.color.status_cancelled,
        R.drawable.bg_status_rejected,
        true,
        false // Final state
    ),
    PAID(
        "Paid",
        R.string.order_status_paid,
        R.drawable.ic_paid,
        R.color.status_paid,
        R.drawable.bg_status_paid,
        true,
        false
    );

    companion object {
        fun getCustomerVisibleStatuses(): List<OrderStatus> {
            return values().filter { it.isCustomerVisible }
        }

        fun getPharmacistActionableStatuses(currentStatus: OrderStatus): List<OrderStatus> {
            return when (currentStatus) {
                ORDERED -> listOf(CONFIRMED, PROCESSING, CANCELLED)
                CONFIRMED -> listOf(PROCESSING, SHIPPED, CANCELLED)
                PROCESSING -> listOf(SHIPPED, CANCELLED)
                SHIPPED -> listOf(DELIVERED)
                else -> emptyList() // DELIVERED and CANCELLED are final states
            }
        }
    }
}