// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
// Import for RecyclerView.
import androidx.recyclerview.widget.RecyclerView
// Import for Glide, an image loading library.
import com.bumptech.glide.Glide

/**
 * An adapter for displaying a list of products in a RecyclerView.
 * This class is responsible for creating the views for each product item and binding the data to them.
 * It also handles clicks on the edit and delete buttons for each product.
 * @param productList The list of products to be displayed.
 * @param onEditClick A lambda function to be called when the edit button for a product is clicked.
 * @param onDeleteClick A lambda function to be called when the delete button for a product is clicked.
 */
class ProductAdapter(
    private val productList: List<Product>,
    private val onEditClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    /**
     * A ViewHolder that describes an item view and metadata about its place within the RecyclerView.
     * @param itemView The view for a single product item.
     */
    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // UI elements for displaying product information.
        val productImage: ImageView = itemView.findViewById(R.id.productImage)
        val productName: TextView = itemView.findViewById(R.id.productName)
        val productPrice: TextView = itemView.findViewById(R.id.productPrice)
        val productStock: TextView = itemView.findViewById(R.id.productStock)
        val editButton: ImageButton = itemView.findViewById(R.id.btnEdit)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    /**
     * Called when RecyclerView needs a new ViewHolder of the given type to represent an item.
     * @param parent The ViewGroup into which the new View will be added.
     * @param viewType The view type of the new View.
     * @return A new ProductViewHolder that holds a View of the given view type.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        // Inflate the layout for a single product item.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.product_item, parent, false)
        // Return a new ProductViewHolder.
        return ProductViewHolder(view)
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * @param holder The ViewHolder which should be updated to represent the contents of the item at the given position.
     * @param position The position of the item within the adapter's data set.
     */
    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        // Get the product at the current position.
        val product = productList[position]

        // Set the product name, price, and stock.
        holder.productName.text = product.name
        holder.productPrice.text = "Price: $${String.format("%.2f", product.price ?: 0.0)}"
        holder.productStock.text = "Stock: ${product.stock ?: 0}"

        // Load the product image using Glide.
        Glide.with(holder.itemView.context)
            .load(product.photoUrl)
            .placeholder(R.drawable.ic_person) // Show a placeholder image while loading.
            .error(R.drawable.ic_person) // Show an error image if the image fails to load.
            .into(holder.productImage)

        // Set an OnClickListener for the edit button.
        holder.editButton.setOnClickListener {
            onEditClick(product)
        }

        // Set an OnClickListener for the delete button.
        holder.deleteButton.setOnClickListener {
            onDeleteClick(product)
        }
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of items in this adapter.
     */
    override fun getItemCount(): Int = productList.size
}
