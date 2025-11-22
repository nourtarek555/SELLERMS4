package com.example.Seller

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class OrdersActivity : AppCompatActivity() {

    private lateinit var fragmentContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)
        
        fragmentContainer = findViewById(R.id.fragmentContainer)

        // Listen for back stack changes to ensure OrdersListFragment is shown when back stack is empty
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                // Back stack is empty, ensure OrdersListFragment is shown
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                val listFragment = supportFragmentManager.findFragmentByTag("ordersList")
                
                // If current fragment is not OrdersListFragment and we don't have one in the container, show it
                if (currentFragment !is OrdersListFragment && (listFragment == null || !listFragment.isAdded)) {
                    val newListFragment = OrdersListFragment()
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, newListFragment, "ordersList")
                        .commit()
                }
            }
        }

        if (savedInstanceState == null) {
            // Check if an order was passed via intent
            val order = intent.getSerializableExtra("order") as? Order
            
            if (order != null) {
                // If order is passed, show list first, then details
                val listFragment = OrdersListFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, listFragment, "ordersList")
                    .commit()
                
                // Show details after a short delay to ensure list is loaded
                fragmentContainer.postDelayed({
                    showOrderDetails(order)
                }, 100)
            } else {
                // No order passed, just show list
                val listFragment = OrdersListFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, listFragment, "ordersList")
                    .commit()
            }
        }
    }

    fun showOrderDetails(order: Order) {
        try {
            // Ensure orders list is in the container before showing details
            var listFragment = supportFragmentManager.findFragmentByTag("ordersList")
            if (listFragment == null) {
                listFragment = OrdersListFragment()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, listFragment, "ordersList")
                    .commit()
            }
            
            // Now show order details on top
            val fragment = OrderDetailsFragment.newInstance(order)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack("orderDetails")
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onBackPressed() {
        // If there are fragments in back stack, pop them
        // The OnBackStackChangedListener will handle showing OrdersListFragment when back stack becomes empty
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            // No back stack, finish activity to return to previous screen
            finish()
        }
    }
}

