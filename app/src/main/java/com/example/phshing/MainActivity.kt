package com.example.phshing

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var skipButton: TextView
    private lateinit var nextButton: Button
    private lateinit var getStartedButton: Button
    private lateinit var dots: Array<View>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Make system bars transparent and handle insets properly
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_main)
        
        // Set up the system bars insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Initialize views
        viewPager = findViewById(R.id.viewPager)
        skipButton = findViewById(R.id.skip_button)
        nextButton = findViewById(R.id.next_button)
        getStartedButton = findViewById(R.id.get_started_button)
        
        // Initialize dot indicators
        dots = arrayOf(
            findViewById(R.id.dot_1),
            findViewById(R.id.dot_2),
            findViewById(R.id.dot_3)
        )
        
        // Set up the ViewPager with adapter
        val onboardingAdapter = OnboardingPagerAdapter()
        viewPager.adapter = onboardingAdapter
        
        // Handle page changes
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateButtons(position)
                super.onPageSelected(position)
            }
        })
        
        // Set up button click listeners
        skipButton.setOnClickListener {
            // Skip to the last page
            viewPager.currentItem = 2
        }
        
        nextButton.setOnClickListener {
            // Go to next page
            val currentItem = viewPager.currentItem
            if (currentItem < 2) {
                viewPager.currentItem = currentItem + 1
            }
        }
        
        getStartedButton.setOnClickListener {
            // Navigate to the Dashboard
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            finish() // Optional: close the onboarding activity
        }
    }
    
    private fun updateDots(position: Int) {
        for (i in 0..dots.size-1) {
            if (i == position) {
                dots[i].layoutParams.width = resources.getDimensionPixelSize(R.dimen.dot_active_width)
                dots[i].setBackgroundResource(R.drawable.dot_active)
            } else {
                dots[i].layoutParams.width = resources.getDimensionPixelSize(R.dimen.dot_inactive_width)
                dots[i].setBackgroundResource(R.drawable.dot_inactive)
            }
            dots[i].requestLayout()
        }
    }
    
    private fun updateButtons(position: Int) {
        when (position) {
            2 -> {
                // On last page
                nextButton.visibility = View.GONE
                getStartedButton.visibility = View.VISIBLE
                skipButton.visibility = View.GONE
            }
            else -> {
                // On other pages
                nextButton.visibility = View.VISIBLE
                getStartedButton.visibility = View.GONE
                skipButton.visibility = View.VISIBLE
            }
        }
    }
}