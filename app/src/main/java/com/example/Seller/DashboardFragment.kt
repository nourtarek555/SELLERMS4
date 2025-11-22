package com.example.Seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DashboardFragment : Fragment() {

    private lateinit var welcomeText: TextView
    private lateinit var statsText: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    
    private var productsListener: ValueEventListener? = null
    private var ordersListener: ValueEventListener? = null
    private var userListener: ValueEventListener? = null
    private var productsRef: DatabaseReference? = null
    private var ordersRef: DatabaseReference? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        welcomeText = view.findViewById(R.id.tvWelcome)
        statsText = view.findViewById(R.id.tvStats)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid

        if (uid != null) {
            loadUserData(uid)
            loadStats(uid)
        }
    }

    private fun loadUserData(uid: String) {
        database = FirebaseDatabase.getInstance().getReference("Seller").child(uid)
        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || view == null) return
                val user = snapshot.getValue(UserProfile::class.java)
                if (user != null) {
                    welcomeText.text = "Welcome back, ${user.name ?: "Seller"}!"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error silently
            }
        }
        database.addValueEventListener(userListener!!)
    }

    private fun loadStats(uid: String) {
        productsRef = FirebaseDatabase.getInstance().getReference("Seller").child(uid).child("Products")
        ordersRef = FirebaseDatabase.getInstance().getReference("Orders")

        productsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || view == null) return
                
                // Count only products with stock > 0 (active products)
                var productCount = 0
                for (productSnapshot in snapshot.children) {
                    val stockValue = when (val stockAny = productSnapshot.child("stock").value) {
                        is Long -> stockAny.toInt()
                        is Int -> stockAny
                        is String -> stockAny.toIntOrNull() ?: 0
                        else -> 0
                    }
                    if (stockValue > 0) {
                        productCount++
                    }
                }
                
                // Update orders count
                updateOrdersCount(uid, productCount)
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded && view != null) {
                    statsText.text = "Unable to load product statistics"
                }
            }
        }
        
        productsRef?.addValueEventListener(productsListener!!)
    }
    
    private fun updateOrdersCount(uid: String, productCount: Int) {
        ordersListener = object : ValueEventListener {
            override fun onDataChange(orderSnapshot: DataSnapshot) {
                if (!isAdded || view == null) return
                
                var pendingOrders = 0
                var totalOrders = 0
                
                for (order in orderSnapshot.children) {
                    val items = order.child("items")
                    for (item in items.children) {
                        val itemData = item.getValue(OrderItem::class.java)
                        if (itemData?.sellerId == uid) {
                            totalOrders++
                            val status = order.child("status").getValue(String::class.java) ?: "pending"
                            if (status == "pending") {
                                pendingOrders++
                            }
                            break
                        }
                    }
                }
                
                val stats = """
                    📦 Products: $productCount
                    📋 Total Orders: $totalOrders
                    ⏳ Pending Orders: $pendingOrders
                """.trimIndent()
                statsText.text = stats
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded && view != null) {
                    val stats = """
                        📦 Products: $productCount
                        📋 Total Orders: Unable to load
                        ⏳ Pending Orders: Unable to load
                    """.trimIndent()
                    statsText.text = stats
                }
            }
        }
        
        ordersRef?.addValueEventListener(ordersListener!!)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Remove listeners to prevent memory leaks
        productsListener?.let { productsRef?.removeEventListener(it) }
        ordersListener?.let { ordersRef?.removeEventListener(it) }
        userListener?.let { database.removeEventListener(it) }
    }
}

