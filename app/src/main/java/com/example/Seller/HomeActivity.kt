package com.example.Seller

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class HomeActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        // Create adapter for fragments
        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Dashboard"
                1 -> "Products"
                2 -> "Orders"
                3 -> "Profile"
                else -> ""
            }
            tab.icon = when (position) {
                0 -> getDrawable(R.drawable.ic_home)
                1 -> getDrawable(R.drawable.ic_store)
                2 -> getDrawable(R.drawable.ic_orders)
                3 -> getDrawable(R.drawable.ic_person)
                else -> null
            }
        }.attach()
    }

    private class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> DashboardFragment()
                1 -> ProductsFragment()
                2 -> OrdersFragment()
                3 -> ProfileFragment()
                else -> DashboardFragment()
            }
        }
    }
}
