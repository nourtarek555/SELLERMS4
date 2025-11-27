// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
// Import for AppCompatActivity.
import androidx.appcompat.app.AppCompatActivity
// Imports for RecyclerView and its layout manager.
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// Imports for Firebase Authentication and Realtime Database.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
// Import for Glide, an image loading library.
import com.bumptech.glide.Glide

/**
 * An activity that displays the seller's shop, showing a list of their products.
 * This activity is very similar to ProductsFragment but is implemented as a standalone activity.
 * It allows the seller to view, add, edit, and delete their products.
 */
class ShopActivity : AppCompatActivity() {

    // UI elements.
    private lateinit var btnAddProduct: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    // Adapter for the RecyclerView.
    private lateinit var adapter: ProductAdapter
    // The list of products.
    private lateinit var productList: MutableList<Product>

    // Firebase Authentication and Database references.
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    // Helper for showing notifications.
    private lateinit var notificationHelper: NotificationHelper
    // The threshold for low stock alerts.
    private val lowStockThreshold = 5

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onCreate(savedInstanceState)
        // Set the content view for the activity.
        setContentView(R.layout.activity_products)

        try {
            // Initialize UI elements.
            btnAddProduct = findViewById(R.id.btn_add_product)
            recyclerView = findViewById(R.id.recycler_products)
            progressBar = findViewById(R.id.shopProgress)

            // Initialize Firebase Auth and get the current user's ID.
            auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid
            
            // Initialize the notification helper.
            notificationHelper = NotificationHelper(this)

            // Set up the RecyclerView.
            recyclerView.layoutManager = LinearLayoutManager(this)
            productList = mutableListOf()
            adapter = ProductAdapter(
                productList,
                onEditClick = { product ->
                    // When the edit button is clicked, open the ProductsActivity in edit mode.
                    val intent = Intent(this, ProductsActivity::class.java)
                    intent.putExtra("product", product)
                    startActivity(intent)
                },
                onDeleteClick = { product ->
                    // When the delete button is clicked, show a confirmation dialog and delete the product.
                    deleteProduct(product, uid ?: "")
                }
            )
            recyclerView.adapter = adapter

            // Set an OnClickListener for the "Add Product" button.
            btnAddProduct.setOnClickListener {
                try {
                    startActivity(Intent(this, ProductsActivity::class.java))
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

            // If the user is logged in, load their products.
            if (uid != null) {
                database = FirebaseDatabase.getInstance().getReference("Seller").child(uid).child("Products")
                loadProducts()
            } else {
                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    /**
     * Loads the products for the current seller from the Firebase Realtime Database.
     */
    private fun loadProducts() {
        try {
            // Check if the database reference has been initialized.
            if (!::database.isInitialized) {
                Toast.makeText(this, "Database not initialized", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Show the progress bar.
            progressBar.visibility = View.VISIBLE

            // Add a ValueEventListener to the database reference to listen for data changes.
            database.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        // Clear the current list of products.
                        productList.clear()

                        // If there are no products, hide the progress bar and return.
                        if (!snapshot.exists()) {
                            adapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE
                            return
                        }

                        // Iterate over each product snapshot.
                        for (productSnapshot in snapshot.children) {
                            try {
                                val product = productSnapshot.getValue(Product::class.java)

                                if (product != null) {
                                    // Set the product ID from the snapshot key if it's not already set.
                                    if (product.productId.isNullOrEmpty()) {
                                        product.productId = productSnapshot.key
                                    }
                                    
                                    // Get the stock value, handling different data types.
                                    val stockValue = when (val stockAny = productSnapshot.child("stock").value) {
                                        is Long -> stockAny.toInt()
                                        is Int -> stockAny
                                        is String -> stockAny.toIntOrNull() ?: 0
                                        else -> 0
                                    }

                                    // Only add the product to the list if it has a positive stock.
                                    if (stockValue > 0) {
                                        productList.add(product)
                                        
                                        // Check for low stock and show an alert.
                                        if (stockValue <= lowStockThreshold) {
                                            showLowStockAlert(product.name ?: "Unknown Product", stockValue)
                                            // Also show a notification (only once).
                                            notificationHelper.showStockAlertNotification(
                                                product.name ?: "Unknown Product",
                                                stockValue,
                                                product.productId
                                            )
                                        } else {
                                            // If the stock is above the threshold, clear any existing alert for the product.
                                            notificationHelper.clearStockAlert(product.productId, product.name)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Notify the adapter that the data has changed and hide the progress bar.
                        adapter.notifyDataSetChanged()
                        progressBar.visibility = View.GONE
                    } catch (e: Exception) {
                        e.printStackTrace()
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@ShopActivity, "Error loading products: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@ShopActivity, "Failed to load products: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    /**
     * Deletes a product from the database.
     * @param product The product to be deleted.
     * @param uid The ID of the current user.
     */
    private fun deleteProduct(product: Product, uid: String) {
        val productId = product.productId ?: return
        
        // Show a confirmation dialog before deleting the product.
        AlertDialog.Builder(this)
            .setTitle("Delete Product")
            .setMessage("Are you sure you want to delete \"${product.name}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // Get a reference to the product in the database.
                val productRef = FirebaseDatabase.getInstance()
                    .getReference("Seller")
                    .child(uid)
                    .child("Products")
                    .child(productId)
                
                // Show the progress bar.
                progressBar.visibility = View.VISIBLE
                // Remove the product from the database.
                productRef.removeValue()
                    .addOnSuccessListener {
                        // If the deletion is successful, hide the progress bar and show a success message.
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Product deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        // If the deletion fails, hide the progress bar and show an error message.
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Failed to delete product: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Shows a low stock alert dialog.
     * @param productName The name of the product with low stock.
     * @param stockLevel The current stock level.
     */
    private fun showLowStockAlert(productName: String, stockLevel: Int) {
        // Only show the alert dialog once per product to avoid spamming the user.
        val alertKey = "low_stock_$productName"
        val prefs = getSharedPreferences("stock_alerts", MODE_PRIVATE)
        
        if (!prefs.getBoolean(alertKey, false)) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("⚠️ Low Stock Alert")
            builder.setMessage("$productName is running low!\n\nCurrent stock: $stockLevel\n\nConsider restocking soon to avoid running out.")
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // Mark the alert as shown.
                prefs.edit().putBoolean(alertKey, true).apply()
            }
            builder.setNegativeButton("View Products") { dialog, _ ->
                dialog.dismiss()
                // The user is already in the ShopActivity, so just dismiss the dialog.
                prefs.edit().putBoolean(alertKey, true).apply()
            }
            builder.setIcon(android.R.drawable.ic_dialog_alert)
            builder.setCancelable(true)
            builder.show()
        }
    }

}
