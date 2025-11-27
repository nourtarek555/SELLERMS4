// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.os.Bundle
import android.widget.FrameLayout
// Import for AppCompatActivity.
import androidx.appcompat.app.AppCompatActivity

/**
 * An activity that hosts the fragments related to orders.
 * This activity is responsible for displaying the list of orders and the details of a specific order.
 * It manages the fragment transactions between the OrdersListFragment and the OrderDetailsFragment.
 */
class OrdersActivity : AppCompatActivity() {

    // The FrameLayout that will contain the fragments.
    private lateinit var fragmentContainer: FrameLayout

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onCreate(savedInstanceState)
        // Set the content view for the activity.
        setContentView(R.layout.activity_orders)
        
        // Initialize the fragment container.
        fragmentContainer = findViewById(R.id.fragmentContainer)

        // Add a listener to the back stack to handle navigation between fragments.
        supportFragmentManager.addOnBackStackChangedListener {
            // If the back stack is empty, it means we should be showing the orders list.
            if (supportFragmentManager.backStackEntryCount == 0) {
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                val listFragment = supportFragmentManager.findFragmentByTag("ordersList")
                
                // If the current fragment is not the OrdersListFragment, we need to show it.
                if (currentFragment !is OrdersListFragment && (listFragment == null || !listFragment.isAdded)) {
                    val newListFragment = OrdersListFragment()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, newListFragment, "ordersList")
                        .commit()
                }
            }
        }

        // If this is the first time the activity is created (not from a saved state)...
        if (savedInstanceState == null) {
            // Check if an order was passed to this activity via an intent extra.
            val order = intent.getSerializableExtra("order") as? Order
            
            // If an order was passed...
            if (order != null) {
                // Show the orders list fragment first.
                val listFragment = OrdersListFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, listFragment, "ordersList")
                    .commit()
                
                // Then, after a short delay, show the details of the specific order.
                fragmentContainer.postDelayed({
                    showOrderDetails(order)
                }, 100)
            } else {
                // If no order was passed, just show the orders list.
                val listFragment = OrdersListFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, listFragment, "ordersList")
                    .commit()
            }
        }
    }

    /**
     * Shows the OrderDetailsFragment for a given order.
     * This function replaces the current fragment with the OrderDetailsFragment and adds the transaction to the back stack.
     * @param order The order to be displayed.
     */
    fun showOrderDetails(order: Order) {
        try {
            // Ensure the orders list fragment is in the container before showing the details.
            var listFragment = supportFragmentManager.findFragmentByTag("ordersList")
            if (listFragment == null) {
                listFragment = OrdersListFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, listFragment, "ordersList")
                    .commit()
            }
            
            // Create a new instance of the OrderDetailsFragment and show it.
            val fragment = OrderDetailsFragment.newInstance(order)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("orderDetails") // Add to back stack so the user can navigate back.
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Called when the user presses the back button.
     */
    override fun onBackPressed() {
        // If there are fragments in the back stack, pop the back stack to navigate to the previous fragment.
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            // If the back stack is empty, finish the activity to return to the previous screen.
            finish()
        }
    }
}
