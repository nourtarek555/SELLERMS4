// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.content.Intent
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
 * This class fetches the orders from Firebase Realtime Database and displays them in a RecyclerView.
 * It also handles showing notifications for new orders.
 */
class OrdersFragment : Fragment() {

    // UI elements.
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    // RecyclerView adapter for the list of orders.
    private lateinit var adapter: OrderAdapter
    // The list of orders to be displayed.
    private lateinit var orderList: MutableList<Order>

    // Firebase Authentication and Database references.
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var ordersListener: ValueEventListener? = null

    // Helper for showing notifications.
    private lateinit var notificationHelper: NotificationHelper
    // The previous count of pending orders, used to determine if a new order notification should be shown.
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

        // If the fragment is not attached to an activity, do nothing.
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

            // Initialize the NotificationHelper.
            notificationHelper = NotificationHelper(requireContext())

            // Set up the RecyclerView.
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            orderList = mutableListOf()
            adapter = OrderAdapter(orderList) { order ->
                // When an order is clicked, show its details.
                showOrderDetailsDialog(order)
            }
            recyclerView.adapter = adapter

            // Get a reference to the "Orders" node in the database and start loading orders.
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
     * Shows the details of a given order.
     * This is done by starting the OrdersActivity and passing the order as an extra.
     * @param order The order to show the details of.
     */
    private fun showOrderDetailsDialog(order: Order) {
        val intent = Intent(requireContext(), OrdersActivity::class.java)
        intent.putExtra("order", order)
        startActivity(intent)
    }

    /**
     * Loads the orders for the current seller from the Firebase Realtime Database.
     * @param sellerId The ID of the current seller.
     */
    private fun loadOrders(sellerId: String) {
        // If the fragment is not attached or has no view, do nothing.
        if (!isAdded || view == null) return

        // Show the progress bar and hide the empty text view.
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        // Create a ValueEventListener to listen for changes to the orders data.
        ordersListener = object : ValueEventListener {
            /**
             * This method is called with a snapshot of the orders data.
             * @param snapshot The data snapshot of the orders.
             */
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded || view == null) return

                try {
                    // Clear the existing list of orders.
                    orderList.clear()

                    // If there are no orders, show the empty text view and return.
                    if (!snapshot.exists()) {
                        if (isAdded && view != null) {
                            adapter.notifyDataSetChanged()
                            progressBar.visibility = View.GONE
                            emptyText.visibility = View.VISIBLE
                        }
                        return
                    }

                    // Iterate over each order in the snapshot.
                    for (orderSnapshot in snapshot.children) {
                        try {
                            // Get the details of the order from the snapshot.
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

                            // Get the items in the order.
                            val itemsNode = orderSnapshot.child("items")
                            val itemsList = mutableListOf<OrderItem>()
                            var orderSellerId: String? = null
                            var totalAmount = 0.0

                            // Iterate over each item in the order.
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

                                        // Calculate the total amount of the order.
                                        totalAmount += (item.totalPrice ?: 0.0)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            // If the order belongs to the current seller and has items, add it to the list.
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

                    // Sort the orders by timestamp in descending order (newest first).
                    orderList.sortByDescending { it.timestamp ?: 0L }

                    // If the fragment is still attached, update the UI.
                    if (isAdded && view != null) {
                        adapter.notifyDataSetChanged()
                        progressBar.visibility = View.GONE

                        // Show the empty text view if the order list is empty.
                        if (orderList.isEmpty()) {
                            emptyText.visibility = View.VISIBLE
                        } else {
                            emptyText.visibility = View.GONE
                        }

                        // Check for new pending orders and show a notification if necessary.
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

            /**
             * This method is called if the database read is cancelled.
             * @param error The database error.
             */
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
