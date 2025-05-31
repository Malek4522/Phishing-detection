package com.example.phshing

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.phshing.utils.AccessibilityUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Calendar

class DashboardActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        
        // Check if accessibility service is enabled and prompt user if needed
        checkAccessibilityService()
        
        // Set up bottom navigation
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.selectedItemId = R.id.navigation_home // Set statistics tab as selected
        
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Already on statistics, do nothing
                    true
                }
                R.id.navigation_scan -> {
                    // Navigate to URL Check Activity
                    val intent = Intent(this, UrlCheckActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                    startActivity(intent)
                    false // Don't select this item as we're leaving this activity
                }
                else -> false
            }
        }
    }
    
    private fun setupStatisticsData() {
        // In a real app, this data would come from a repository or viewmodel
        // For now, we're using the hardcoded values from the UI mockup
        
        // Additional functionality could be added here:
        // - Format numbers with commas
        // - Calculate percentages
        // - Update gauge position based on percentage
        // - etc.
    }
    
    /**
     * Checks if the accessibility service is enabled and prompts the user if needed
     */
    private fun checkAccessibilityService() {
        if (!AccessibilityUtil.isAccessibilityServiceEnabled(this)) {
            AccessibilityUtil.showAccessibilityPromptDialog(this)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check again when returning to the app, in case the user enabled the service
        checkAccessibilityService()
    }
}
