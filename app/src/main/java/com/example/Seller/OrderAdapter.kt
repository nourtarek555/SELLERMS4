package com.example.Seller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OrderAdapter(
    private val orderList: List<Order>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val orderIdText: TextView = itemView.findViewById(R.id.orderIdText)
        val buyerNameText: TextView = itemView.findViewById(R.id.buyerNameText)
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val totalAmountText: TextView = itemView.findViewById(R.id.totalAmountText)
        val itemCountText: TextView = itemView.findViewById(R.id.itemCountText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.order_item, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orderList[position]

        // Show order ID (first 12 characters for readability)
        val orderIdDisplay = order.orderId?.take(12) ?: "N/A"
        holder.orderIdText.text = "Order: $orderIdDisplay..."

        // Buyer name
        holder.buyerNameText.text = "Buyer: ${order.buyerName ?: "Unknown"}"

        // Status with color coding
        val status = order.status?.uppercase() ?: "N/A"
        val deliveryType = order.deliveryType?.uppercase() ?: "PICKUP"
        
        // Show status with delivery type if ready
        val statusDisplay = if (status == "READY") {
            if (deliveryType == "DELIVERY") "READY (DELIVERY)" else "READY (PICKUP)"
        } else {
            status
        }
        holder.statusText.text = statusDisplay
        
        // Set status background color dynamically
        val statusColor = when (status.lowercase()) {
            "pending" -> android.graphics.Color.parseColor("#FB8C00") // Orange
            "accepted" -> android.graphics.Color.parseColor("#4CAF50") // Green
            "preparing" -> android.graphics.Color.parseColor("#FF9800") // Orange
            "ready" -> android.graphics.Color.parseColor("#2196F3") // Blue
            "delivered" -> android.graphics.Color.parseColor("#4CAF50") // Green
            "rejected" -> android.graphics.Color.parseColor("#F44336") // Red
            else -> android.graphics.Color.parseColor("#9E9E9E") // Gray
        }
        holder.statusText.setBackgroundColor(statusColor)

        // Total amount
        holder.totalAmountText.text = "$${String.format("%.2f", order.totalAmount ?: 0.0)}"
        
        // Item count
        val itemCount = order.items?.size ?: 0
        holder.itemCountText.text = "$itemCount item${if (itemCount != 1) "s" else ""}"

        // Make entire item clickable
        holder.itemView.setOnClickListener {
            onOrderClick(order)
        }
    }

    override fun getItemCount(): Int = orderList.size
}

