package com.example.Seller

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ProductsFragment : Fragment() {

    private lateinit var btnAddProduct: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ProductAdapter
    private lateinit var productList: MutableList<Product>

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var notificationHelper: NotificationHelper
    private val lowStockThreshold = 5
    private var productsListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_products, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!isAdded) return

        try {
            btnAddProduct = view.findViewById(R.id.btn_add_product)
            recyclerView = view.findViewById(R.id.recycler_products)
            progressBar = view.findViewById(R.id.shopProgress)

            auth = FirebaseAuth.getInstance()
            val uid = auth.currentUser?.uid

            notificationHelper = NotificationHelper(requireContext())

            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            productList = mutableListOf()
            adapter = ProductAdapter(
                productList,
                onEditClick = { product ->
                    // Open ProductsActivity in edit mode
                    val intent = Intent(requireContext(), ProductsActivity::class.java)
                    intent.putExtra("product", product)
                    startActivity(intent)
                },
                onDeleteClick = { product ->
                    // Show delete confirmation and delete product
                    if (uid != null) {
                        deleteProduct(product, uid)
                    } else {
                        Toast.makeText(requireContext(), "User not found", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            recyclerView.adapter = adapter

            btnAddProduct.setOnClickListener {
                try {
                    startActivity(Intent(requireContext(), ProductsActivity::class.java))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

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

    private fun loadProducts() {
        if (!isAdded || view == null) return

        try {
            progressBar.visibility = View.VISIBLE

            productsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded || view == null) return

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
                                    
                                    val stockValue = when (val stockAny = productSnapshot.child("stock").value) {
                                        is Long -> stockAny.toInt()
                                        is Int -> stockAny
                                        is String -> stockAny.toIntOrNull() ?: 0
                                        else -> 0
                                    }

                                    if (stockValue > 0) {
                                        productList.add(product)

                                        if (stockValue <= lowStockThreshold) {
                                            showLowStockAlert(product.name ?: "Unknown Product", stockValue)
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
                            }
                        }

                        adapter.notifyDataSetChanged()
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

            database.addValueEventListener(productsListener!!)
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun deleteProduct(product: Product, uid: String) {
        val productId = product.productId ?: return
        
        AlertDialog.Builder(requireContext())
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
                        Toast.makeText(requireContext(), "Product deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Failed to delete product: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLowStockAlert(productName: String, stockLevel: Int) {
        val alertKey = "low_stock_$productName"
        val prefs = requireContext().getSharedPreferences("stock_alerts", android.content.Context.MODE_PRIVATE)

        if (!prefs.getBoolean(alertKey, false)) {
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("⚠️ Low Stock Alert")
            builder.setMessage("$productName is running low!\n\nCurrent stock: $stockLevel\n\nConsider restocking soon to avoid running out.")
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                prefs.edit().putBoolean(alertKey, true).apply()
            }
            builder.setIcon(android.R.drawable.ic_dialog_alert)
            builder.setCancelable(true)
            builder.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        productsListener?.let {
            database.removeEventListener(it)
        }
    }
}

