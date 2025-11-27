// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
// Import for Fragment class from AndroidX.
import androidx.fragment.app.Fragment
// Imports for Firebase Authentication and Realtime Database.
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * A Fragment that displays the seller's dashboard.
 * This class shows a welcome message and statistics about the seller's products and orders.
 * It fetches data from Firebase Realtime Database to populate the view.
 */
class DashboardFragment : Fragment() {

    // UI elements for displaying welcome text and statistics.
    private lateinit var welcomeText: TextView
    private lateinit var statsText: TextView

    // Firebase Authentication instance.
    private lateinit var auth: FirebaseAuth
    // Firebase Realtime Database reference.
    private lateinit var database: DatabaseReference

    // ValueEventListener for listening to changes in products data.
    private var productsListener: ValueEventListener? = null
    // ValueEventListener for listening to changes in orders data.
    private var ordersListener: ValueEventListener? = null
    // ValueEventListener for listening to changes in user data.
    private var userListener: ValueEventListener? = null

    // DatabaseReference for the seller's products.
    private var productsRef: DatabaseReference? = null
    // DatabaseReference for the orders.
    private var ordersRef: DatabaseReference? = null

    /**
     * Called to have the fragment instantiate its user interface view.
     * This is where the layout for the fragment is inflated.
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
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    /**
     * Called immediately after onCreateView() has returned, but before any saved state has been restored in to the view.
     * This is where you should initialize the views and start loading data.
     * @param view The View returned by onCreateView().
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onViewCreated(view, savedInstanceState)

        // Initialize the TextViews from the layout.
        welcomeText = view.findViewById(R.id.tvWelcome)
        statsText = view.findViewById(R.id.tvStats)

        // Get an instance of FirebaseAuth.
        auth = FirebaseAuth.getInstance()
        // Get the current user's unique ID.
        val uid = auth.currentUser?.uid

        // If the user ID is not null, load user data and stats.
        if (uid != null) {
            loadUserData(uid)
            loadStats(uid)
        }
    }

    /**
     * Loads the seller's user data from Firebase Realtime Database.
     * This function fetches the user's profile to display a personalized welcome message.
     * @param uid The unique ID of the current user.
     */
    private fun loadUserData(uid: String) {
        // Get a reference to the seller's data in the database.
        database = FirebaseDatabase.getInstance().getReference("Seller").child(uid)
        // Create a ValueEventListener to listen for data changes.
        userListener = object : ValueEventListener {
            /**
             * This method will be called with a snapshot of the data at this location.
             * It will also be called each time that data changes.
             * @param snapshot The current data at the location.
             */
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if the fragment is still added to its activity and has a view.
                if (!isAdded || view == null) return
                // Get the UserProfile object from the snapshot.
                val user = snapshot.getValue(UserProfile::class.java)
                // If the user object is not null, update the welcome text.
                if (user != null) {
                    welcomeText.text = "Welcome back, ${user.name ?: "Seller"}!"
                }
            }

            /**
             * This method will be triggered in the event that this listener either failed at the server,
             * or is removed as a result of the security and Firebase Database rules.
             * @param error A description of the error that occurred.
             */
            override fun onCancelled(error: DatabaseError) {
                // Handle the error silently to avoid crashing the app.
            }
        }
        // Add the listener to the database reference.
        database.addValueEventListener(userListener!!)
    }

    /**
     * Loads statistics for the seller, including product count and order count.
     * It fetches data from the "Products" and "Orders" nodes in Firebase.
     * @param uid The unique ID of the current user.
     */
    private fun loadStats(uid: String) {
        // Get a reference to the seller's products.
        productsRef = FirebaseDatabase.getInstance().getReference("Seller").child(uid).child("Products")
        // Get a reference to the orders.
        ordersRef = FirebaseDatabase.getInstance().getReference("Orders")

        // Create a ValueEventListener for product data.
        productsListener = object : ValueEventListener {
            /**
             * This method will be called with a snapshot of the product data.
             * It counts the number of active products.
             * @param snapshot The current product data.
             */
            override fun onDataChange(snapshot: DataSnapshot) {
                // Check if the fragment is still added and has a view.
                if (!isAdded || view == null) return

                // Initialize product count.
                var productCount = 0
                // Iterate through all products.
                for (productSnapshot in snapshot.children) {
                    // Get the stock value, handling different data types.
                    val stockValue = when (val stockAny = productSnapshot.child("stock").value) {
                        is Long -> stockAny.toInt()
                        is Int -> stockAny
                        is String -> stockAny.toIntOrNull() ?: 0
                        else -> 0
                    }
                    // If the stock is greater than 0, it's an active product.
                    if (stockValue > 0) {
                        productCount++
                    }
                }

                // After counting products, update the orders count.
                updateOrdersCount(uid, productCount)
            }

            /**
             * This method will be triggered in the event of a database error.
             * @param error A description of the error.
             */
            override fun onCancelled(error: DatabaseError) {
                // If the fragment is still active, show an error message.
                if (isAdded && view != null) {
                    statsText.text = "Unable to load product statistics"
                }
            }
        }

        // Add the listener to the products reference.
        productsRef?.addValueEventListener(productsListener!!)
    }

    /**
     * Updates the count of total and pending orders for the seller.
     * @param uid The unique ID of the seller.
     * @param productCount The number of active products, to be displayed along with order stats.
     */
    private fun updateOrdersCount(uid: String, productCount: Int) {
        // Create a ValueEventListener for orders data.
        ordersListener = object : ValueEventListener {
            /**
             * This method is called with a snapshot of the orders data.
             * It iterates through orders to count total and pending orders for the specific seller.
             * @param orderSnapshot The current orders data.
             */
            override fun onDataChange(orderSnapshot: DataSnapshot) {
                // Check if the fragment is still added and has a view.
                if (!isAdded || view == null) return

                // Initialize order counts.
                var pendingOrders = 0
                var totalOrders = 0

                // Iterate through all orders.
                for (order in orderSnapshot.children) {
                    // Get the items within the order.
                    val items = order.child("items")
                    // Iterate through the items in the order.
                    for (item in items.children) {
                        // Get the item data as an OrderItem object.
                        val itemData = item.getValue(OrderItem::class.java)
                        // If the item belongs to the current seller...
                        if (itemData?.sellerId == uid) {
                            // Increment total orders for this seller.
                            totalOrders++
                            // Get the order status. Default to "pending" if not present.
                            val status = order.child("status").getValue(String::class.java) ?: "pending"
                            // If the status is "pending", increment the pending orders count.
                            if (status == "pending") {
                                pendingOrders++
                            }
                            // Break the inner loop since we've found an item from this seller in the order.
                            break
                        }
                    }
                }

                // Create the statistics string.
                val stats = """
                    📦 Products: $productCount
                    📋 Total Orders: $totalOrders
                    ⏳ Pending Orders: $pendingOrders
                """.trimIndent()
                // Set the text of the stats TextView.
                statsText.text = stats
            }

            /**
             * This method will be triggered in the event of a database error.
             * @param error A description of the error.
             */
            override fun onCancelled(error: DatabaseError) {
                // If the fragment is still active, show partial stats with an error message.
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

        // Add the listener to the orders reference.
        ordersRef?.addValueEventListener(ordersListener!!)
    }

    /**
     * Called when the view previously created by onCreateView() has been detached from the fragment.
     * This is where we clean up resources, specifically removing database listeners to prevent memory leaks.
     */
    override fun onDestroyView() {
        // Call the superclass implementation.
        super.onDestroyView()
        // Remove the listeners to prevent memory leaks when the view is destroyed.
        productsListener?.let { productsRef?.removeEventListener(it) }
        ordersListener?.let { ordersRef?.removeEventListener(it) }
        userListener?.let { database.removeEventListener(it) }
    }
}
