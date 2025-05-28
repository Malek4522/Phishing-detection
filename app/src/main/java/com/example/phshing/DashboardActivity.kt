package com.example.phshing

import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Calendar

class DashboardActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        

        }
        

        
        // Populate dynamic data
        // This would typically come from your data source or viewmodel
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
