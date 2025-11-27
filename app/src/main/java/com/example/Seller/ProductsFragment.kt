// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
// Import for Fragment class from AndroidX.
import androidx.fragment.app.Fragment
// Imports for RecyclerView and its layout manager.
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// Imports for Firebase Authentication and Realtime Database.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * A Fragment that displays the seller's products.
 * This class fetches the products from Firebase Realtime Database and displays them in a RecyclerView.
 * It allows the seller to add, edit, and delete products.
 */
class ProductsFragment : Fragment() {

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
    // The threshold for considering stock to be low.
    private val lowStockThreshold = 5
    // The listener for product data changes.
    private var productsListener: ValueEventListener? = null

    /**
     * Called to have the fragment instantiate its user interface view.
     * @param inflater The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container If non-null, this is the parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state as given here.
     * @return Return the View for the fragment's UI, or null.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment.
        return inflater.inflate(R.layout.fragment_products, container, false)
    }

    /**
     * Called immediately after onCreateView() has returned, but before any saved state has been restored in to the view.
     * @param view The View returned by onCreateView().
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onViewCreated(view, savedInstanceState)

        if (!isAdded) return

        try {
            // Initialize UI elements.
            btnAddProduct = view.findViewById(R.id.btn_add_product)
            recyclerView = view.findViewById(R.id.recycler_products)
            progressBar = view.findViewById(R.id.shopProgress)

            // Initialize Firebase Auth.
            auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid

            // Initialize the notification helper.
            notificationHelper = NotificationHelper(requireContext())

            // Set up the RecyclerView.
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            productList = mutableListOf()
            adapter = ProductAdapter(
                productList,
                onEditClick = { product ->
                    // When the edit button is clicked, open the ProductsActivity in edit mode.
                    val intent = Intent(requireContext(), ProductsActivity::class.java)
                    intent.putExtra("product", product)
                    startActivity(intent)
                },
                onDeleteClick = { product ->
                    // When the delete button is clicked, show a confirmation dialog and delete the product.
                    if (uid != null) {
                        deleteProduct(product, uid)
                    } else {
                        Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            recyclerView.adapter = adapter

            // Set an OnClickListener for the "Add Product" button.
            btnAddProduct.setOnClickListener {
                try {
                    startActivity(Intent(requireContext(), ProductsActivity::class.java))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

            // If the user is logged in, load their products.
            if (uid != null) {
                database = FirebaseDatabase.getInstance().getReference("Seller").child(uid).child("Products")
                loadProducts()
            } else {
                Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    /**
     * Loads the products for the current seller from the Firebase Realtime Database.
     */
    private fun loadProducts() {
        if (!isAdded || view == null) return

        try {
            // Show the progress bar.
            progressBar.visibility = View.VISIBLE

            // Create a ValueEventListener to listen for data changes.
            productsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || view == null) return

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

                                        // If the stock is below the low stock threshold, show a low stock alert.
                                        if (stockValue <= lowStockThreshold) {
                                            showLowStockAlert(product.name ?: "Unknown Product", stockValue)
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

                        // Notify the adapter that the data has changed.
                        adapter.notifyDataSetChanged()
                        // Hide the progress bar.
                        progressBar.visibility = View.GONE
                    } catch (e: Exception) {
                        e.printStackTrace()
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Error loading products: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (isAdded && view != null) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Failed to load products: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Add the listener to the database reference.
            database.addValueEventListener(productsListener!!)
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
        AlertDialog.Builder(requireContext())
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
                        Toast.makeText(requireContext(), "Product deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        // If the deletion fails, hide the progress bar and show an error message.
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Failed to delete product: ${e.message}", Toast.LENGTH_SHORT).show()
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
        val alertKey = "low_stock_$productName"
        val prefs = requireContext().getSharedPreferences("stock_alerts", android.content.Context.MODE_PRIVATE)

        // Only show the alert if it hasn't been shown before.
        if (!prefs.getBoolean(alertKey, false)) {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("⚠️ Low Stock Alert")
            builder.setMessage("$productName is running low!\n\nCurrent stock: $stockLevel\n\nConsider restocking soon to avoid running out.")
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // Mark the alert as shown so it doesn't appear again.
                prefs.edit().putBoolean(alertKey, true).apply()
            }
            builder.setIcon(android.R.drawable.ic_dialog_alert)
            builder.setCancelable(true)
            builder.show()
        }
    }

    /**
     * Called when the view previously created by onCreateView() has been detached from the fragment.
     */
    override fun onDestroyView() {
        // Call the superclass implementation.
        super.onDestroyView()
        // Remove the database listener to prevent memory leaks.
        productsListener?.let {
            database.removeEventListener(it)
        }
    }
}
