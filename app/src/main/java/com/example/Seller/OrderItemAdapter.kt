// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
// Import for RecyclerView.
import androidx.recyclerview.widget.RecyclerView
// Import for Glide, an image loading and caching library.
import com.bumptech.glide.Glide

/**
 * An adapter for displaying a list of order items in a RecyclerView.
 * This class is used in the OrderDetailsFragment to show the products included in an order.
 * @param itemList The list of OrderItem objects to be displayed.
 */
class OrderItemAdapter(private val itemList: List<OrderItem>) :
    RecyclerView.Adapter<OrderItemAdapter.OrderItemViewHolder>() {

    /**
     * A ViewHolder that describes an item view and metadata about its place within the RecyclerView.
     * @param itemView The view for a single order item.
     */
    inner class OrderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // UI elements for displaying the order item information.
        val itemImage: ImageView = itemView.findViewById(R.id.itemImage)
        val itemName: TextView = itemView.findViewById(R.id.itemName)
        val itemQuantity: TextView = itemView.findViewById(R.id.itemQuantity)
        val itemPrice: TextView = itemView.findViewById(R.id.itemPrice)
    }

    /**
     * Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A new OrderItemViewHolder that holds a View of the given view type.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderItemViewHolder {
        // Inflate the layout for a single order item row.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.order_item_row, parent, false)
        // Return a new OrderItemViewHolder.
        return OrderItemViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * @param holder The ViewHolder which should be updated to represent the contents of the item at the given position.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: OrderItemViewHolder, position: Int) {
        // Get the order item at the current position.
        val item = itemList[position]

        // Set the product name.
        holder.itemName.text = item.productName ?: "Unknown Product"
        // Set the quantity of the item.
        holder.itemQuantity.text = "Quantity: ${item.quantity ?: 0}"
        
        // Calculate and set the total price for the item.
        val itemTotal = item.totalPrice ?: ((item.price ?: 0.0) * (item.quantity ?: 0))
        holder.itemPrice.text = "Total: $${String.format("%.2f", itemTotal)}"

        // Get the image URL for the product, using a fallback field name if necessary.
        val imageUrl = item.photoUrl ?: item.imageUrl
        // Load the product image using Glide.
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_person) // Show a placeholder image while loading.
            .error(R.drawable.ic_person) // Show an error image if the image fails to load.
            .into(holder.itemImage)
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of items in this adapter.
     */
    override fun getItemCount(): Int = itemList.size
}
