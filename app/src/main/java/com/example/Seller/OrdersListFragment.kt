// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
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
 * A Fragment that displays a list of orders for the seller.
 * This fragment is used within the OrdersActivity to show the initial list of orders.
 * It is very similar to OrdersFragment but is designed to be used within a specific activity context (OrdersActivity).
 */
class OrdersListFragment : Fragment() {

    // UI elements.
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    // Adapter for the RecyclerView.
    private lateinit var adapter: OrderAdapter
    // The list of orders.
    private lateinit var orderList: MutableList<Order>
    
    // Firebase Authentication and Database references.
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var ordersListener: ValueEventListener? = null
    // Helper for showing notifications.
    private lateinit var notificationHelper: NotificationHelper
    // The previous count of pending orders.
    private var previousPendingCount = 0

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
        return inflater.inflate(R.layout.fragment_orders_list, container, false)
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
            recyclerView = view.findViewById(R.id.recycler_orders)
            progressBar = view.findViewById(R.id.ordersProgress)
            emptyText = view.findViewById(R.id.emptyText)

            // Initialize Firebase Auth.
            auth = FirebaseAuth.getInstance()
            val currentSellerId = auth.currentUser?.uid

            // If the user is not logged in, show a message and return.
            if (currentSellerId == null) {
                if (isAdded) {
                    Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
                }
                return
            }

            if (!isAdded) return

            // Initialize the notification helper.
            notificationHelper = NotificationHelper(requireContext())

            // Set up the RecyclerView.
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            orderList = mutableListOf()
            adapter = OrderAdapter(orderList) { order ->
                // When an order is clicked, show the order details.
                if (isAdded && activity is OrdersActivity) {
                    (activity as OrdersActivity).showOrderDetails(order)
                }
            }
            recyclerView.adapter = adapter

            // Get a reference to the "Orders" node in the database and load the orders.
            database = FirebaseDatabase.getInstance().getReference("Orders")
            loadOrders(currentSellerId)
        } catch (e: Exception) {
            e.printStackTrace()
            if (isAdded) {
                Toast.makeText(requireContext(), "Error loading orders: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Loads the orders for the specified seller from the Firebase Realtime Database.
     * @param sellerId The ID of the seller whose orders are to be loaded.
     */
    private fun loadOrders(sellerId: String) {
        if (!isAdded || view == null) return
        
        // Show the progress bar and hide the empty text.
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        // Create a ValueEventListener to listen for data changes.
        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || view == null) return
                
                try {
                    // Clear the current list of orders.
                    orderList.clear()

                    // If there are no orders, show the empty text view.
                    if (!snapshot.exists()) {
                        if (isAdded && view != null) {
                            adapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE
                            emptyText.visibility = View.VISIBLE
                        }
                        return
                    }

                    // Iterate over each order snapshot.
                    for (orderSnapshot in snapshot.children) {
                        try {
                            // Get the order data from the snapshot.
                            val orderId = orderSnapshot.key
                            val buyerId = orderSnapshot.child("buyerId").getValue(String::class.java)
                            val buyerName = orderSnapshot.child("buyerName").getValue(String::class.java)
                            val buyerAddress = orderSnapshot.child("buyerAddress
").getValue(String::class.java)
                            val status = orderSnapshot.child("status").getValue(String::class.java)
                            val deliveryType = orderSnapshot.child("deliveryType").getValue(String::class.java)
                            val deliveryAddress = orderSnapshot.child("deliveryAddress").getValue(String::class.java)
                                ?: if (deliveryType == "delivery") buyerAddress else null
                            val deliveryPrice = orderSnapshot.child("deliveryPrice").getValue(Double::class.java)
                                ?: orderSnapshot.child("deliveryPrice").getValue(Int::class.java)?.toDouble()
                            
                            // Get the items in the order.
                            val itemsNode = orderSnapshot.child("items")
                            val itemsList = mutableListOf<OrderItem>()
                            var orderSellerId: String? = null
                            var totalAmount = 0.0
                            
                            // Iterate over the items in the order.
                            for (itemSnapshot in itemsNode.children) {
                                try {
                                    val item = itemSnapshot.getValue(OrderItem::class.java)
                                    if (item != null) {
                                        // Handle different field names for image URLs.
                                        if (item.photoUrl == null && item.imageUrl != null) {
                                            item.photoUrl = item.imageUrl
                                        }
                                        
                                        // Handle different data types for price.
                                        if (item.price == null) {
                                            val priceInt = itemSnapshot.child("price").getValue(Int::class.java)
                                            item.price = priceInt?.toDouble()
                                        }
                                        
                                        // Handle different data types for total price.
                                        if (item.totalPrice == null) {
                                            val totalPriceInt = itemSnapshot.child("totalPrice").getValue(Int::class.java)
                                            item.totalPrice = totalPriceInt?.toDouble()
                                        }
                                        
                                        itemsList.add(item)
                                        
                                        // Get the seller ID from the first item.
                                        if (orderSellerId == null) {
                                            orderSellerId = item.sellerId
                                        }
                                        
                                        // Calculate the total amount.
                                        totalAmount += (item.totalPrice ?: 0.0)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            // If the order belongs to the current seller, create an Order object and add it to the list.
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

                    // Sort the orders by timestamp in descending order.
                    orderList.sortByDescending { it.timestamp ?: 0L }

                    if (isAdded && view != null) {
                        // Notify the adapter that the data has changed.
                        adapter.notifyDataSetChanged()
                        progressBar.visibility = View.GONE

                        // Show the empty text view if the list is empty.
                        if (orderList.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                        } else {
                            emptyText.visibility = View.GONE
                        }

                        // Check for new pending orders and show a notification.
                        val pendingCount = orderList.count { it.status == "pending" }
                        if (pendingCount > 0 && isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "You have $pendingCount pending order(s)",
                                Toast.LENGTH_SHORT
                            ).show()
                            
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

        // Add the listener to the database reference.
        database.addValueEventListener(ordersListener!!)
    }

    /**
     * Called when the view previously created by onCreateView() has been detached from the fragment.
     */
    override fun onDestroyView() {
        // Call the superclass implementation.
        super.onDestroyView()
        // Remove the database listener to prevent memory leaks.
        ordersListener?.let {
            database.removeEventListener(it)
        }
    }
}
