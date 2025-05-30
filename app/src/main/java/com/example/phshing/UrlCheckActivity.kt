package com.example.phshing

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.AnimatedVectorDrawable
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.phshing.api.PhishingApiClient
import com.example.phshing.api.PhishingResult
import com.example.phshing.service.RealTimeProtectionService
import com.example.phshing.utils.NotificationHelper
import com.example.phshing.utils.NotificationHelper.RiskLevel
import com.example.phshing.utils.PreferencesManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import kotlin.random.Random

class UrlCheckActivity : AppCompatActivity() {
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var urlInput: EditText
    private lateinit var checkButton: Button
    private lateinit var backButton: ImageView
    private lateinit var aiProtectionIcon: ImageView
    private lateinit var protectionStatus: TextView
    private lateinit var protectionDesc: TextView
    private lateinit var protectionGlow: View
    private lateinit var resultsContainer: CardView
    private lateinit var scanningContainer: ConstraintLayout
    private lateinit var defaultMessage: TextView
    private lateinit var scanningIcon: ImageView
    private lateinit var tapInstruction: TextView
    
    private var isProtectionActive = false
    private var pulseAnimation: Animation? = null
    private var rotateAnimation: Animation? = null
    private var flipAnimation: Animation? = null
    private var flipReverseAnimation: Animation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_url_check)

        // Initialize views
        urlInput = findViewById(R.id.url_input)
        checkButton = findViewById(R.id.check_button)
        backButton = findViewById(R.id.back_button)
        aiProtectionIcon = findViewById(R.id.ai_protection_icon)
        protectionStatus = findViewById(R.id.protection_status)
        protectionDesc = findViewById(R.id.protection_desc)
        protectionGlow = findViewById(R.id.protection_glow)
        resultsContainer = findViewById(R.id.results_container)
        scanningContainer = findViewById(R.id.scanning_animation_container)
        defaultMessage = findViewById(R.id.default_message)
        scanningIcon = findViewById(R.id.scanning_icon)
        tapInstruction = findViewById(R.id.tap_instruction)
        
        // Create notification channel
        NotificationHelper.createNotificationChannel(this)
        
        // Load animations first
        loadAnimations()
        
        // Restore protection state
        isProtectionActive = PreferencesManager.isProtectionActive(this)
        if (isProtectionActive) {
            updateUIForActiveProtection(false) // Don't animate on restore
        }
        
        // Set up back button
        backButton.setOnClickListener {
            finish() // Go back to previous activity
        }

        // Set up back button
        backButton.setOnClickListener {
            finish() // Go back to previous activity
        }

        // Set up check button with futuristic scanning animation
        checkButton.setOnClickListener {
            val url = urlInput.text.toString()
            if (url.isNotEmpty()) {
                simulateScan(url)
            }
        }

        // Set up AI protection icon with flipping animation
        aiProtectionIcon.setOnClickListener {
            toggleAIProtection(!isProtectionActive)
        }

        // Set up bottom navigation
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.selectedItemId = R.id.navigation_scan // Set the scan tab as selected

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Navigate to Dashboard Activity
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                    startActivity(intent)
                    false // Don't select this item as we're leaving this activity
                }
                R.id.navigation_scan -> {
                    // Already on URL Check Activity
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Loads all animations used in the activity
     */
    private fun loadAnimations() {
        // Load animations if they haven't been loaded yet
        if (pulseAnimation == null) {
            pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        }
        
        if (rotateAnimation == null) {
            rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.rotate_animation)
        }
        
        if (flipAnimation == null) {
            flipAnimation = AnimationUtils.loadAnimation(this, R.anim.flip_animation)
        }
        
        if (flipReverseAnimation == null) {
            flipReverseAnimation = AnimationUtils.loadAnimation(this, R.anim.flip_reverse_animation)
        }
    }
    
    private fun toggleAIProtection(active: Boolean) {
        isProtectionActive = active
        
        // Save the protection state
        PreferencesManager.setProtectionActive(this, isProtectionActive)
        
        // Make sure animations are loaded
        loadAnimations()
        
        if (isProtectionActive) {
            updateUIForActiveProtection(true)
            
            // Start the real-time protection service
            startRealTimeProtection()
        } else {
            updateUIForInactiveProtection(true)
            
            // Stop the real-time protection service
            stopRealTimeProtection()
        }
    }
    
    /**
     * Updates the UI to show protection is active
     * @param animate Whether to animate the UI changes
     */
    private fun updateUIForActiveProtection(animate: Boolean = true) {
        // Make sure animations are loaded
        loadAnimations()
        
        // Make glow visible
        protectionGlow.visibility = View.VISIBLE
        
        // Apply animations if requested
        if (animate && flipAnimation != null) {
            aiProtectionIcon.startAnimation(flipAnimation)
        }
        
        if (animate && pulseAnimation != null) {
            protectionGlow.startAnimation(pulseAnimation)
        }
        
        // Update UI to show protection is active
        protectionStatus.text = "ACTIVE"
        protectionStatus.setTextColor(getColor(R.color.positive_green))
        protectionDesc.text = "GuardianAI is actively monitoring URLs in real-time to protect you from phishing attacks"
        tapInstruction.text = "TAP THE GUARDIAN AI ICON TO DEACTIVATE"
    }
    
    /**
     * Updates the UI to show protection is inactive
     * @param animate Whether to animate the UI changes
     */
    private fun updateUIForInactiveProtection(animate: Boolean = true) {
        // Make sure animations are loaded
        loadAnimations()
        
        // Apply reverse flip animation if requested
        if (animate && flipReverseAnimation != null) {
            aiProtectionIcon.startAnimation(flipReverseAnimation)
        }
        
        // Fade out the glow if requested
        if (animate) {
            ObjectAnimator.ofFloat(protectionGlow, "alpha", 1f, 0f)
                .apply {
                    duration = 500
                    interpolator = AccelerateDecelerateInterpolator()
                    protectionGlow.alpha = 1f  // Reset alpha for next time
                    protectionGlow.clearAnimation()
                }
                .start()
        } else {
            // Just hide it without animation
            protectionGlow.visibility = View.GONE
        }
        
        // Update UI to show protection is inactive
        protectionStatus.text = "INACTIVE"
        protectionStatus.setTextColor(getColor(R.color.text_secondary))
        protectionDesc.text = "Activate GuardianAI's advanced protection to scan URLs in real-time and block phishing attempts before they happen"
        tapInstruction.text = "TAP THE GUARDIAN AI ICON TO ACTIVATE"
    }
    
    /**
     * Starts the real-time protection service in the background
     */
    private fun startRealTimeProtection() {
        // Check and request necessary permissions before starting the service
        if (checkAndRequestPermissions()) {
            // Start the real-time protection service
            val serviceIntent = Intent(this, RealTimeProtectionService::class.java)
            startService(serviceIntent)
        }
    }
    
    private fun checkAndRequestPermissions(): Boolean {
        val requiredPermissions = mutableListOf<String>()
        
        // Check for notification permission (for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != 
                PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // For Android 14+, check for foreground service data sync permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC) != 
                PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
            }
        }
        
        // If we need to request permissions
        if (requiredPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            return false // We'll start the service after permission result
        }
        
        return true // All permissions granted
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all permissions were granted
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }
            
            if (allGranted) {
                // All permissions granted, start the service
                val serviceIntent = Intent(this, RealTimeProtectionService::class.java)
                startService(serviceIntent)
            } else {
                // Permission denied, show a message and reset the toggle
                Toast.makeText(
                    this,
                    "Permissions are required for real-time protection",
                    Toast.LENGTH_LONG
                ).show()
                
                // Reset the protection state
                isProtectionActive = false
                PreferencesManager.setProtectionActive(this, false)
                updateUIForInactiveProtection(false)
            }
        }
    }
    
    /**
     * Stops the real-time protection service
     */
    private fun stopRealTimeProtection() {
        val intent = Intent(this, RealTimeProtectionService::class.java)
        intent.action = RealTimeProtectionService.ACTION_STOP
        startService(intent)
        
        // Show a toast message for demo purposes
        Toast.makeText(this, "Real-time protection deactivated", Toast.LENGTH_SHORT).show()
    }

    private fun simulateScan(url: String) {
        // Hide default message
        defaultMessage.visibility = View.GONE

        // Show scanning animation
        scanningContainer.visibility = View.VISIBLE
        resultsContainer.visibility = View.GONE

        // Apply rotation animation to the scanning icon
        scanningIcon.startAnimation(rotateAnimation)

        // Use the API to check the URL
        checkUrlWithApi(url)
    }
    
    /**
     * Checks a URL using the phishing detection API
     */
    private fun checkUrlWithApi(url: String) {
        lifecycleScope.launch {
            try {
                // Call the API
                val result = PhishingApiClient.checkUrl(url)
                
                // Process the result on the main thread
                Handler(Looper.getMainLooper()).post {
                    // Hide scanning animation
                    scanningContainer.visibility = View.GONE
                    scanningIcon.clearAnimation()
                    
                    // Show results
                    resultsContainer.visibility = View.VISIBLE
                    val resultsText = findViewById<TextView>(R.id.results_placeholder)
                    
                    when (result) {
                        is PhishingResult.Success -> {
                            // Determine risk level based on API response
                            val riskLevel = when {
                                result.isPhishing && result.confidence > 0.7 -> RiskLevel.CRITICAL
                                result.isPhishing -> RiskLevel.MEDIUM
                                else -> RiskLevel.SAFE
                            }
                            
                            // Format confidence as percentage
                            val confidencePercent = (result.confidence * 100).toInt()
                            
                            // Update UI based on risk level
                            when (riskLevel) {
                                RiskLevel.SAFE -> {
                                    resultsText.text = "✅ ${url}\n\nNo phishing threats detected.\nThis URL appears to be safe.\n\nConfidence: ${confidencePercent}%"
                                    resultsText.setTextColor(getColor(R.color.positive_green))
                                }
                                RiskLevel.MEDIUM -> {
                                    resultsText.text = "⚠️ ${url}\n\nPotential security concerns detected.\nProceed with caution.\n\nConfidence: ${confidencePercent}%"
                                    resultsText.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
                                }
                                RiskLevel.CRITICAL -> {
                                    resultsText.text = "❌ ${url}\n\nCRITICAL SECURITY THREAT DETECTED!\nAccess to this site has been blocked.\n\nConfidence: ${confidencePercent}%"
                                    resultsText.setTextColor(resources.getColor(android.R.color.holo_red_light))
                                }
                            }
                            
                            // Show notification with the scan result
                            NotificationHelper.showUrlScanNotification(this@UrlCheckActivity, url, riskLevel)
                        }
                        is PhishingResult.Error -> {
                            // Show error message
                            resultsText.text = "Error checking URL: ${result.message}"
                            resultsText.setTextColor(resources.getColor(android.R.color.holo_red_light))
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle any unexpected errors
                Handler(Looper.getMainLooper()).post {
                    // Hide scanning animation
                    scanningContainer.visibility = View.GONE
                    scanningIcon.clearAnimation()
                    
                    // Show error message
                    resultsContainer.visibility = View.VISIBLE
                    val resultsText = findViewById<TextView>(R.id.results_placeholder)
                    resultsText.text = "Error: ${e.message ?: "Unknown error occurred"}"
                    resultsText.setTextColor(resources.getColor(android.R.color.holo_red_light))
                }
            }
        }
    }
    
    
    
    override fun onPause() {
        super.onPause()
        // Clear animations when activity is paused
        protectionGlow.clearAnimation()
        aiProtectionIcon.clearAnimation()
        scanningIcon.clearAnimation()
        protectionGlow.animate().cancel()
    }
}