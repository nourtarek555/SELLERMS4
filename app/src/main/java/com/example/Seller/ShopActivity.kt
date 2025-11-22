package com.example.Seller

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.bumptech.glide.Glide

class ShopActivity : AppCompatActivity() {

    private lateinit var btnAddProduct: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ProductAdapter
    private lateinit var productList: MutableList<Product>

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var notificationHelper: NotificationHelper
    private val lowStockThreshold = 5 // Alert when stock is 5 or less

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_products)

        try {
            btnAddProduct = findViewById(R.id.btn_add_product)
            recyclerView = findViewById(R.id.recycler_products)
            progressBar = findViewById(R.id.shopProgress)

            auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid
            
            // Initialize notification helper
            notificationHelper = NotificationHelper(this)

            recyclerView.layoutManager = LinearLayoutManager(this)
            productList = mutableListOf()
            adapter = ProductAdapter(
                productList,
                onEditClick = { product ->
                    // Open ProductsActivity in edit mode
                    val intent = Intent(this, ProductsActivity::class.java)
                    intent.putExtra("product", product)
                    startActivity(intent)
                },
                onDeleteClick = { product ->
                    // Show delete confirmation and delete product
                    deleteProduct(product, uid ?: "")
                }
            )
            recyclerView.adapter = adapter

            btnAddProduct.setOnClickListener {
                try {
                    startActivity(Intent(this, ProductsActivity::class.java))
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

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

    private fun loadProducts() {
        try {
            if (!::database.isInitialized) {
                Toast.makeText(this, "Database not initialized", Toast.LENGTH_SHORT).show()
                return
            }
            
            progressBar.visibility = View.VISIBLE

            database.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        productList.clear()

                        if (!snapshot.exists()) {
                            adapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE
                            return
                        }

                        for (productSnapshot in snapshot.children) {
                            try {
                                val product = productSnapshot.getValue(Product::class.java)

                                if (product != null) {
                                    // Set productId from snapshot key if not already set
                                    if (product.productId.isNullOrEmpty()) {
                                        product.productId = productSnapshot.key
                                    }
                                    
                                    // Safely handle stock whether it's a Long, String, or Int
                                    val stockValue = when (val stockAny = productSnapshot.child("stock").value) {
                                        is Long -> stockAny.toInt()
                                        is Int -> stockAny
                                        is String -> stockAny.toIntOrNull() ?: 0
                                        else -> 0
                                    }

                                    // Keep only products with stock > 0
                                    if (stockValue > 0) {
                                        productList.add(product)
                                        
                                        // Check for low stock and show alert
                                        if (stockValue <= lowStockThreshold) {
                                            showLowStockAlert(product.name ?: "Unknown Product", stockValue)
                                            // Also show notification (only once)
                                            notificationHelper.showStockAlertNotification(
                                                product.name ?: "Unknown Product",
                                                stockValue,
                                                product.productId
                                            )
                                        } else {
                                            // Stock is above threshold, clear the alert flag
                                            notificationHelper.clearStockAlert(product.productId, product.name)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                // Skip invalid products
                            }
                        }

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

    private fun deleteProduct(product: Product, uid: String) {
        val productId = product.productId ?: return
        
        AlertDialog.Builder(this)
            .setTitle("Delete Product")
            .setMessage("Are you sure you want to delete \"${product.name}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                val productRef = FirebaseDatabase.getInstance()
                    .getReference("Seller")
                    .child(uid)
                    .child("Products")
                    .child(productId)
                
                progressBar.visibility = View.VISIBLE
                productRef.removeValue()
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Product deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Failed to delete product: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLowStockAlert(productName: String, stockLevel: Int) {
        // Only show alert dialog once per product to avoid spam
        val alertKey = "low_stock_$productName"
        val prefs = getSharedPreferences("stock_alerts", MODE_PRIVATE)
        
        if (!prefs.getBoolean(alertKey, false)) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("⚠️ Low Stock Alert")
            builder.setMessage("$productName is running low!\n\nCurrent stock: $stockLevel\n\nConsider restocking soon to avoid running out.")
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                // Mark as shown
                prefs.edit().putBoolean(alertKey, true).apply()
            }
            builder.setNegativeButton("View Products") { dialog, _ ->
                dialog.dismiss()
                // Already in ShopActivity, just dismiss
                prefs.edit().putBoolean(alertKey, true).apply()
            }
            builder.setIcon(android.R.drawable.ic_dialog_alert)
            builder.setCancelable(true)
            builder.show()
        }
    }

}

