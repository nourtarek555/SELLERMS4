// The package name for the seller application.
package com.example.Seller

// Imports for Android framework classes.
import android.os.Bundle
// Import for AppCompatActivity, a base class for activities that use the support library action bar features.
import androidx.appcompat.app.AppCompatActivity
// Import for Fragment class from AndroidX.
import androidx.fragment.app.Fragment
// Import for FragmentStateAdapter, an adapter for providing fragments to a ViewPager2.
import androidx.viewpager2.adapter.FragmentStateAdapter
// Import for ViewPager2, a widget that allows the user to swipe left or right to see the next or previous page.
import androidx.viewpager2.widget.ViewPager2
// Imports for Material Design components.
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * The main activity for the seller's home screen.
 * This activity hosts a ViewPager2 with a TabLayout to navigate between different fragments:
 * Dashboard, Products, Orders, and Profile.
 */
class HomeActivity : AppCompatActivity() {

    // The ViewPager2 widget that handles fragment navigation.
    private lateinit var viewPager: ViewPager2
    // The TabLayout that displays the tabs for navigation.
    private lateinit var tabLayout: TabLayout

    /**
     * Called when the activity is first created.
     * This is where you should do all of your normal static set up:
     * create views, bind data to lists, etc.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass implementation.
        super.onCreate(savedInstanceState)
        // Set the content view for the activity.
        setContentView(R.layout.activity_home)

        // Initialize the ViewPager2 and TabLayout from the layout.
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        // Create an adapter for the fragments.
        val adapter = ViewPagerAdapter(this)
        // Set the adapter on the ViewPager2.
        viewPager.adapter = adapter

        // Connect the TabLayout with the ViewPager2.
        // This will automatically update the tabs when the ViewPager2 is scrolled.
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // Set the text for each tab based on its position.
            tab.text = when (position) {
                0 -> "Dashboard"
                1 -> "Products"
                2 -> "Orders"
                3 -> "Profile"
                else -> ""
            }
            // Set the icon for each tab based on its position.
            tab.icon = when (position) {
                0 -> getDrawable(R.drawable.ic_home)
                1 -> getDrawable(R.drawable.ic_store)
                2 -> getDrawable(R.drawable.ic_orders)
                3 -> getDrawable(R.drawable.ic_person)
                else -> null
            }
        }.attach() // Attach the TabLayoutMediator to the TabLayout and ViewPager2.
    }

    /**
     * An adapter that provides fragments to the ViewPager2.
     * This class is responsible for creating the correct fragment for each page.
     * @param activity The AppCompatActivity that hosts the adapter.
     */
    private class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        /**
         * Returns the total number of items in the data set held by the adapter.
         * @return The total number of items in this adapter.
         */
        override fun getItemCount(): Int = 4 // We have 4 tabs: Dashboard, Products, Orders, Profile.

        /**
         * Provide a new Fragment for the given position.
         * @param position The position of the item within the adapter's data set.
         * @return A new Fragment to be displayed at the specified position.
         */
        override fun createFragment(position: Int): Fragment {
            // Return the appropriate fragment for each position.
            return when (position) {
                0 -> DashboardFragment()
                1 -> ProductsFragment()
                2 -> OrdersFragment()
                3 -> ProfileFragment()
                // The default case should ideally not be reached, but we return DashboardFragment as a fallback.
                else -> DashboardFragment()
            }
        }
    }
}
