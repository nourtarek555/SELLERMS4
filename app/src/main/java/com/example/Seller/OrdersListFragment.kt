package com.example.Seller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class OrdersListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var adapter: OrderAdapter
    private lateinit var orderList: MutableList<Order>
    
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var ordersListener: ValueEventListener? = null
    private lateinit var notificationHelper: NotificationHelper
    private var previousPendingCount = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_orders_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!isAdded) return

        try {
            recyclerView = view.findViewById(R.id.recycler_orders)
            progressBar = view.findViewById(R.id.ordersProgress)
            emptyText = view.findViewById(R.id.emptyText)

                        auth = FirebaseAuth.getInstance()
                        val currentSellerId = auth.currentUser?.uid

                        if (currentSellerId == null) {
                            if (isAdded) {
                                Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                            }
                            return
                        }

                        if (!isAdded) return

                        // Initialize notification helper
                        notificationHelper = NotificationHelper(requireContext())

                        recyclerView.layoutManager = LinearLayoutManager(requireContext())
                        orderList = mutableListOf()
                        adapter = OrderAdapter(orderList) { order ->
                            // Navigate to order details
                            if (isAdded && activity is OrdersActivity) {
                                (activity as OrdersActivity).showOrderDetails(order)
                            }
                        }
                        recyclerView.adapter = adapter

                        database = FirebaseDatabase.getInstance().getReference("Orders")
                        loadOrders(currentSellerId)
        } catch (e: Exception) {
            e.printStackTrace()
            if (isAdded) {
                Toast.makeText(requireContext(), "Error loading orders: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadOrders(sellerId: String) {
        if (!isAdded || view == null) return
        
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || view == null) return
                
                try {
                    orderList.clear()

                    if (!snapshot.exists()) {
                        if (isAdded && view != null) {
                            adapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE
                            emptyText.visibility = View.VISIBLE
                        }
                        return
                    }

                    for (orderSnapshot in snapshot.children) {
                        try {
                            // Get the order ID from the snapshot key
                            val orderId = orderSnapshot.key
                            
                            // Read basic order fields
                            val buyerId = orderSnapshot.child("buyerId").getValue(String::class.java)
                            val buyerName = orderSnapshot.child("buyerName").getValue(String::class.java)
                            val buyerAddress = orderSnapshot.child("buyerAddress").getValue(String::class.java)
                            
                            // Read status from order level (if it exists there)
                            val status = orderSnapshot.child("status").getValue(String::class.java)
                            
                            // Read delivery information
                            val deliveryType = orderSnapshot.child("deliveryType").getValue(String::class.java)
                            // deliveryAddress might be stored as deliveryAddress or buyerAddress (if delivery type is delivery)
                            val deliveryAddress = orderSnapshot.child("deliveryAddress").getValue(String::class.java)
                                ?: if (deliveryType == "delivery") buyerAddress else null
                            val deliveryPrice = orderSnapshot.child("deliveryPrice").getValue(Double::class.java)
                                ?: orderSnapshot.child("deliveryPrice").getValue(Int::class.java)?.toDouble()
                            
                            // Parse items - items is a map where each item has sellerId
                            val itemsNode = orderSnapshot.child("items")
                            val itemsList = mutableListOf<OrderItem>()
                            var orderSellerId: String? = null
                            var totalAmount = 0.0
                            
                            // Iterate through items to find sellerId and parse items
                            for (itemSnapshot in itemsNode.children) {
                                try {
                                    val item = itemSnapshot.getValue(OrderItem::class.java)
                                    if (item != null) {
                                        // Use imageUrl if photoUrl is null
                                        if (item.photoUrl == null && item.imageUrl != null) {
                                            item.photoUrl = item.imageUrl
                                        }
                                        
                                        // Handle price conversion (Firebase might store as Int)
                                        if (item.price == null) {
                                            val priceInt = itemSnapshot.child("price").getValue(Int::class.java)
                                            item.price = priceInt?.toDouble()
                                        }
                                        
                                        // Convert totalPrice from Int to Double if needed
                                        if (item.totalPrice == null) {
                                            val totalPriceInt = itemSnapshot.child("totalPrice").getValue(Int::class.java)
                                            item.totalPrice = totalPriceInt?.toDouble()
                                        }
                                        
                                        itemsList.add(item)
                                        
                                        // Get sellerId from first item (all items in an order should have same sellerId)
                                        if (orderSellerId == null) {
                                            orderSellerId = item.sellerId
                                        }
                                        
                                        // Calculate total amount
                                        totalAmount += (item.totalPrice ?: 0.0)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            // Check if this order belongs to the current seller
                            if (orderSellerId == sellerId && itemsList.isNotEmpty()) {
                                // Create order object
                                val order = Order().apply {
                                    this.orderId = orderId
                                    this.sellerId = orderSellerId
                                    this.buyerId = buyerId
                                    this.buyerName = buyerName
                                    this.buyerAddress = buyerAddress
                                    this.status = status ?: "pending" // Default to pending if not set
                                    this.deliveryType = deliveryType ?: "pickup" // Default to pickup
                                    this.deliveryAddress = deliveryAddress
                                    this.deliveryPrice = deliveryPrice
                                    this.items = itemsList
                                    // Add delivery price to total if it exists
                                    this.totalAmount = totalAmount + (deliveryPrice ?: 0.0)
                                }
                                
                                orderList.add(order)
                            }
                        } catch (e: Exception) {
                            // Skip invalid orders
                            e.printStackTrace()
                        }
                    }

                    // Sort by timestamp (newest first)
                    orderList.sortByDescending { it.timestamp ?: 0L }

                    if (isAdded && view != null) {
                        adapter.notifyDataSetChanged()
                        progressBar.visibility = View.GONE

                        if (orderList.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                        } else {
                            emptyText.visibility = View.GONE
                        }

                        // Show notification for new pending orders
                        val pendingCount = orderList.count { it.status == "pending" }
                        if (pendingCount > 0 && isAdded) {
                            // Show toast
                            Toast.makeText(
                                requireContext(),
                                "You have $pendingCount pending order(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                            
                            // Show notification if pending count increased
                            if (pendingCount > previousPendingCount && ::notificationHelper.isInitialized) {
                                notificationHelper.showNewOrderNotification(pendingCount)
                            }
                        }
                        previousPendingCount = pendingCount
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (isAdded && view != null) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            "Error loading orders: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded && view != null) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Failed to load orders: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        database.addValueEventListener(ordersListener!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ordersListener?.let {
            database.removeEventListener(it)
        }
    }
}

