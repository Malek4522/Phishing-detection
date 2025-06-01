package com.example.phshing

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.phshing.fragments.DashboardFragment
import com.example.phshing.fragments.SettingsFragment
import com.example.phshing.fragments.UrlCheckFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainContainerActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_container)

        bottomNavigationView = findViewById(R.id.bottom_navigation)
        
        // Set up bottom navigation
        setupBottomNavigation()
        
        // Set the default selected item
        bottomNavigationView.selectedItemId = R.id.navigation_dashboard
        
        // Check if we're being restored from a previous state
        if (savedInstanceState == null) {
            // Load default fragment
            loadFragment(DashboardFragment.newInstance())
        }
        
        // Check if we should navigate to a specific fragment based on intent
        handleNavigationIntent()
    }
    
    private fun setupBottomNavigation() {
        bottomNavigationView.setOnItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    loadFragment(DashboardFragment.newInstance())
                    return@setOnItemSelectedListener true
                }
                R.id.navigation_url_check -> {
                    loadFragment(UrlCheckFragment.newInstance())
                    return@setOnItemSelectedListener true
                }
                R.id.navigation_settings -> {
                    loadFragment(SettingsFragment.newInstance())
                    return@setOnItemSelectedListener true
                }
            }
            false
        }
    }
    
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    private fun handleNavigationIntent() {
        // Get the fragment to navigate to from the intent
        val fragmentToLoad = intent.getStringExtra("fragment")
        
        // Get URL parameters if they exist
        val url = intent.getStringExtra("url")
        val fromNotification = intent.getBooleanExtra("fromNotification", false)
        
        // Navigate to the specified fragment if provided
        when (fragmentToLoad) {
            "dashboard" -> {
                bottomNavigationView.selectedItemId = R.id.navigation_dashboard
            }
            "url_check" -> {
                // If we have URL parameters, load the UrlCheckFragment with those parameters
                if (url != null) {
                    loadFragment(UrlCheckFragment.newInstance(url, fromNotification))
                } else {
                    bottomNavigationView.selectedItemId = R.id.navigation_url_check
                }
            }
            "settings" -> {
                bottomNavigationView.selectedItemId = R.id.navigation_settings
            }
        }
    }
}
