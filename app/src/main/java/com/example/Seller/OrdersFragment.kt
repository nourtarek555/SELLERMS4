package com.example.Seller

import android.content.Intent
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

class OrdersFragment : Fragment() {

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

            notificationHelper = NotificationHelper(requireContext())

            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            orderList = mutableListOf()
            adapter = OrderAdapter(orderList) { order ->
                // Show order details in a dialog or navigate
                showOrderDetailsDialog(order)
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

    private fun showOrderDetailsDialog(order: Order) {
        // Navigate to OrdersActivity to show order details
        // OrdersActivity will handle showing the order details fragment
        val intent = Intent(requireContext(), OrdersActivity::class.java)
        intent.putExtra("order", order)
        startActivity(intent)
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
                            val orderId = orderSnapshot.key

                            val buyerId = orderSnapshot.child("buyerId").getValue(String::class.java)
                            val buyerName = orderSnapshot.child("buyerName").getValue(String::class.java)
                            val buyerAddress = orderSnapshot.child("buyerAddress").getValue(String::class.java)

                            val status = orderSnapshot.child("status").getValue(String::class.java)

                            val deliveryType = orderSnapshot.child("deliveryType").getValue(String::class.java)
                            val deliveryAddress = orderSnapshot.child("deliveryAddress").getValue(String::class.java)
                                ?: if (deliveryType == "delivery") buyerAddress else null
                            val deliveryPrice = orderSnapshot.child("deliveryPrice").getValue(Double::class.java)
                                ?: orderSnapshot.child("deliveryPrice").getValue(Int::class.java)?.toDouble()

                            val itemsNode = orderSnapshot.child("items")
                            val itemsList = mutableListOf<OrderItem>()
                            var orderSellerId: String? = null
                            var totalAmount = 0.0

                            for (itemSnapshot in itemsNode.children) {
                                try {
                                    val item = itemSnapshot.getValue(OrderItem::class.java)
                                    if (item != null) {
                                        if (item.photoUrl == null && item.imageUrl != null) {
                                            item.photoUrl = item.imageUrl
                                        }

                                        if (item.price == null) {
                                            val priceInt = itemSnapshot.child("price").getValue(Int::class.java)
                                            item.price = priceInt?.toDouble()
                                        }

                                        if (item.totalPrice == null) {
                                            val totalPriceInt = itemSnapshot.child("totalPrice").getValue(Int::class.java)
                                            item.totalPrice = totalPriceInt?.toDouble()
                                        }

                                        itemsList.add(item)

                                        if (orderSellerId == null) {
                                            orderSellerId = item.sellerId
                                        }

                                        totalAmount += (item.totalPrice ?: 0.0)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            if (orderSellerId == sellerId && itemsList.isNotEmpty()) {
                                val order = Order().apply {
                                    this.orderId = orderId
                                    this.sellerId = orderSellerId
                                    this.buyerId = buyerId
                                    this.buyerName = buyerName
                                    this.buyerAddress = buyerAddress
                                    this.status = status ?: "pending"
                                    this.deliveryType = deliveryType ?: "pickup"
                                    this.deliveryAddress = deliveryAddress
                                    this.deliveryPrice = deliveryPrice
                                    this.items = itemsList
                                    this.totalAmount = totalAmount + (deliveryPrice ?: 0.0)
                                }

                                orderList.add(order)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    orderList.sortByDescending { it.timestamp ?: 0L }

                    if (isAdded && view != null) {
                        adapter.notifyDataSetChanged()
                        progressBar.visibility = View.GONE

                        if (orderList.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                        } else {
                            emptyText.visibility = View.GONE
                        }

                        val pendingCount = orderList.count { it.status == "pending" }
                        if (pendingCount > 0 && isAdded) {
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

