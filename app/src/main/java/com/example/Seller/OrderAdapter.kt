// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
// Import for RecyclerView, a flexible view for providing a limited window into a large data set.
import androidx.recyclerview.widget.RecyclerView

/**
 * An adapter for displaying a list of orders in a RecyclerView.
 * This class is responsible for creating the views for each order item and binding the data to them.
 * @param orderList The list of orders to be displayed.
 * @param onOrderClick A lambda function to be called when an order item is clicked.
 */
class OrderAdapter(
    private val orderList: List<Order>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    /**
     * A ViewHolder that describes an item view and metadata about its place within the RecyclerView.
     * @param itemView The view for a single order item.
     */
    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // TextViews for displaying order information.
        val orderIdText: TextView = itemView.findViewById(R.id.orderIdText)
        val buyerNameText: TextView = itemView.findViewById(R.id.buyerNameText)
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val totalAmountText: TextView = itemView.findViewById(R.id.totalAmountText)
        val itemCountText: TextView = itemView.findViewById(R.id.itemCountText)
    }

    /**
     * Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
     * @param parent The ViewGroup into which the new View will be added after it is bound to an adapter position.
     * @param viewType The view type of the new View.
     * @return A new OrderViewHolder that holds a View of the given view type.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        // Inflate the layout for a single order item.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.order_item, parent, false)
        // Return a new OrderViewHolder.
        return OrderViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * This method should update the contents of the itemView to reflect the item at the given position.
     * @param holder The ViewHolder which should be updated to represent the contents of the item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        // Get the order at the current position.
        val order = orderList[position]

        // Display the first 12 characters of the order ID for readability.
        val orderIdDisplay = order.orderId?.take(12) ?: "N/A"
        holder.orderIdText.text = "Order: $orderIdDisplay..."

        // Display the buyer's name.
        holder.buyerNameText.text = "Buyer: ${order.buyerName ?: "Unknown"}"

        // Get the status and delivery type of the order.
        val status = order.status?.uppercase() ?: "N/A"
        val deliveryType = order.deliveryType?.uppercase() ?: "PICKUP"
        
        // If the order is ready, specify whether it's for delivery or pickup.
        val statusDisplay = if (status == "READY") {
            if (deliveryType == "DELIVERY") "READY (DELIVERY)" else "READY (PICKUP)"
        } else {
            status
        }
        holder.statusText.text = statusDisplay
        
        // Set the background color of the status text based on the order status.
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

        // Display the total amount of the order, formatted to two decimal places.
        holder.totalAmountText.text = "$${String.format("%.2f", order.totalAmount ?: 0.0)}"
        
        // Display the number of items in the order.
        val itemCount = order.items?.size ?: 0
        holder.itemCountText.text = "$itemCount item${if (itemCount != 1) "s" else ""}"

        // Set an OnClickListener for the entire item view.
        holder.itemView.setOnClickListener {
            // When the item is clicked, call the onOrderClick lambda function with the corresponding order.
            onOrderClick(order)
        }
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of items in this adapter.
     */
    override fun getItemCount(): Int = orderList.size
}
