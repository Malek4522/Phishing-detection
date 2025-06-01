package com.example.phshing.fragments

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.phshing.R
import com.example.phshing.api.PhishingApiClient
import com.example.phshing.api.PhishingResult
import com.example.phshing.database.ScanDataManager
import com.example.phshing.service.RealTimeProtectionService
import com.example.phshing.utils.AccessibilityUtil
import com.example.phshing.utils.NotificationHelper
import com.example.phshing.utils.NotificationHelper.RiskLevel
import com.example.phshing.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.random.Random

class UrlCheckFragment : Fragment() {
    
    // Database manager for storing scan results
    private lateinit var scanDataManager: ScanDataManager
    
    companion object {
        private const val ARG_URL = "url"
        private const val ARG_AUTO_SCAN = "auto_scan"
        private const val PERMISSION_REQUEST_CODE = 100
        
        /**
         * Creates a new instance of UrlCheckFragment with optional URL to scan
         * @param url The URL to scan (optional)
         * @param autoScan Whether to automatically scan the URL when fragment is created
         * @return A new instance of UrlCheckFragment
         */
        fun newInstance(): UrlCheckFragment {
            return UrlCheckFragment()
        }
        
        fun newInstance(url: String, autoScan: Boolean = false): UrlCheckFragment {
            val fragment = UrlCheckFragment()
            val args = Bundle()
            args.putString(ARG_URL, url)
            args.putBoolean(ARG_AUTO_SCAN, autoScan)
            fragment.arguments = args
            return fragment
        }
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
    private lateinit var scanningProgress: TextView
    
    private var isProtectionActive = false
    private var pulseAnimation: Animation? = null
    private var rotateAnimation: Animation? = null
    private var flipAnimation: Animation? = null
    private var flipReverseAnimation: Animation? = null
    
    // Variables to handle URL from notifications or other sources
    private var urlFromArgs: String? = null
    private var autoScan: Boolean = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_url_check, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize scan data manager for database operations
        scanDataManager = ScanDataManager(requireContext())
        
        // Get URL from arguments if available
        arguments?.let { args ->
            urlFromArgs = args.getString(ARG_URL)
            autoScan = args.getBoolean(ARG_AUTO_SCAN, false)
        }
        
        // Also check activity's intent for URL (for backward compatibility)
        activity?.intent?.let { intent ->
            if (urlFromArgs == null) {
                urlFromArgs = intent.getStringExtra("url")
                autoScan = intent.getBooleanExtra("autoScan", false)
            }
        }

        // Initialize views
        urlInput = view.findViewById(R.id.url_input)
        checkButton = view.findViewById(R.id.check_button)
        backButton = view.findViewById(R.id.back_button)
        aiProtectionIcon = view.findViewById(R.id.ai_protection_icon)
        protectionStatus = view.findViewById(R.id.protection_status)
        protectionDesc = view.findViewById(R.id.protection_desc)
        protectionGlow = view.findViewById(R.id.protection_glow)
        resultsContainer = view.findViewById(R.id.results_container)
        scanningContainer = view.findViewById(R.id.scanning_animation_container)
        defaultMessage = view.findViewById(R.id.default_message)
        scanningIcon = view.findViewById(R.id.scanning_icon)
        scanningProgress = view.findViewById(R.id.scanning_progress)
        
        // Create notification channel
        context?.let { NotificationHelper.createNotificationChannel(it) }
        
        // Load animations first
        loadAnimations()
        
        // Restore protection state
        isProtectionActive = context?.let { PreferencesManager.isProtectionActive(it) } ?: false
        if (isProtectionActive) {
            updateUIForActiveProtection(false) // Don't animate on restore
        }

        // Set up check button with futuristic scanning animation
        checkButton.setOnClickListener {
            val url = urlInput.text.toString()
            if (url.isNotEmpty()) {
                performUrlScan(url)
            }
        }

        // Set up AI protection icon with flipping animation
        aiProtectionIcon.setOnClickListener {
            toggleAIProtection(!isProtectionActive)
        }
        
        // Set up back button
        backButton.setOnClickListener {
            // Navigate back to the previous fragment or handle navigation
            activity?.onBackPressed()
        }
        
        // If we have a URL from arguments or intent, set it in the input field and check it
        urlFromArgs?.let { url ->
            urlInput.setText(url)
            // If autoScan is true (from Layer 1 detection or notification), automatically check the URL
            if (autoScan) {
                // Use a short delay to ensure the UI is fully loaded
                Handler(Looper.getMainLooper()).postDelayed({
                    performUrlScan(url)
                }, 300)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh protection state when fragment becomes visible
        context?.let {
            isProtectionActive = PreferencesManager.isProtectionActive(it)
            if (isProtectionActive) {
                updateUIForActiveProtection(false)
            } else {
                updateUIForInactiveProtection(false)
            }
        }
    }

    /**
     * Loads all animations used in the fragment
     */
    private fun loadAnimations() {
        context?.let { ctx ->
            // Load animations if they haven't been loaded yet
            if (pulseAnimation == null) {
                pulseAnimation = AnimationUtils.loadAnimation(ctx, R.anim.pulse_animation)
            }
            
            if (rotateAnimation == null) {
                rotateAnimation = AnimationUtils.loadAnimation(ctx, R.anim.rotate_animation)
            }
            
            if (flipAnimation == null) {
                flipAnimation = AnimationUtils.loadAnimation(ctx, R.anim.flip_animation)
            }
            
            if (flipReverseAnimation == null) {
                flipReverseAnimation = AnimationUtils.loadAnimation(ctx, R.anim.flip_reverse_animation)
            }
        }
    }
    
    private fun toggleAIProtection(active: Boolean) {
        isProtectionActive = active
        
        context?.let { ctx ->
            // Save the protection state
            PreferencesManager.setProtectionActive(ctx, isProtectionActive)
            
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
        
        // Start pulse animation on the glow
        if (pulseAnimation != null) {
            protectionGlow.startAnimation(pulseAnimation)
        }
        
        // Update text
        protectionStatus.text = "ACTIVE"
        protectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_accent))
        protectionDesc.text = "GuardianAI is actively monitoring URLs and protecting you from phishing attempts in real-time"
        
        // Hide tap instruction
        view?.findViewById<TextView>(R.id.tap_instruction)?.text = "TAP THE GUARDIAN AI ICON TO DEACTIVATE"
    }
    
    /**
     * Updates the UI to show protection is inactive
     * @param animate Whether to animate the UI changes
     */
    private fun updateUIForInactiveProtection(animate: Boolean = true) {
        // Stop any ongoing animations
        protectionGlow.clearAnimation()
        
        // Apply animations if requested
        if (animate && flipReverseAnimation != null) {
            aiProtectionIcon.startAnimation(flipReverseAnimation)
        }
        
        // Hide glow
        protectionGlow.visibility = View.GONE
        
        // Update text
        protectionStatus.text = "INACTIVE"
        protectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        protectionDesc.text = "Activate GuardianAI's advanced protection to scan URLs in real-time and block phishing attempts before they happen"
        
        // Show tap instruction
        view?.findViewById<TextView>(R.id.tap_instruction)?.text = "TAP THE GUARDIAN AI ICON TO ACTIVATE"
    }
    
    /**
     * Starts the real-time protection service
     */
    private fun startRealTimeProtection() {
        context?.let { ctx ->
            // Check if Layer 1 is active in preferences
            val layer1Active = PreferencesManager.isLayer1Active(ctx)
            
            // If Layer 1 is active, check if accessibility service is enabled
            if (layer1Active && !AccessibilityUtil.isAccessibilityServiceEnabled(ctx)) {
                // Show accessibility prompt dialog with direct navigation to settings
                val dialog = AlertDialog.Builder(ctx)
                    .setTitle("Accessibility Permission Required")
                    .setMessage("Layer 1 protection requires accessibility permission to monitor for phishing URLs. Would you like to enable it now?")
                    .setIcon(R.drawable.ic_security)
                    .setCancelable(false)
                    .setPositiveButton("Enable Now") { dialog: DialogInterface, which: Int ->
                        // Direct the user to accessibility settings
                        AccessibilityUtil.openAccessibilitySettings(ctx)
                    }
                    .setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
                        // User declined, continue with service start anyway
                        // Toast removed as requested
                        startProtectionService(ctx)
                    }
                    .create()
                
                dialog.show()
            } else {
                // Accessibility is either not needed or already enabled, start the service
                startProtectionService(ctx)
            }
        }
    }
    
    /**
     * Actually starts the protection service
     */
    private fun startProtectionService(context: Context) {
        val serviceIntent = Intent(context, RealTimeProtectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
    
    /**
     * Stops the real-time protection service
     */
    private fun stopRealTimeProtection() {
        context?.let { ctx ->
            val serviceIntent = Intent(ctx, RealTimeProtectionService::class.java)
            ctx.stopService(serviceIntent)
        }
    }
    
    /**
     * Performs a URL scan with animations and saves results to database
     */
    private fun performUrlScan(url: String) {
        // Increment total scanned links counter
        context?.let { ctx ->
            PreferencesManager.incrementTotalScannedLinks(ctx)
        }
        
        // First, completely reset the UI state
        resultsContainer.removeAllViews()
        
        // Make sure containers are visible
        resultsContainer.visibility = View.VISIBLE
        defaultMessage.visibility = View.GONE
        
        // Re-add the scanning container to the results container
        scanningContainer.parent?.let { parent ->
            if (parent is ViewGroup) {
                parent.removeView(scanningContainer)
            }
        }
        resultsContainer.addView(scanningContainer)
        scanningContainer.visibility = View.VISIBLE
        
        // Reset scanning progress text
        scanningProgress.text = "Preparing scan..."
        
        // Force layout update
        resultsContainer.requestLayout()
        scanningContainer.requestLayout()
        
        // Reset and restart scanning animation
        scanningIcon.clearAnimation()
        if (scanningIcon.drawable is AnimatedVectorDrawable) {
            val avd = scanningIcon.drawable as AnimatedVectorDrawable
            avd.stop()
            avd.start()
        }
        
        // Apply rotation animation to the scanning icon
        if (rotateAnimation != null) {
            rotateAnimation?.reset()
            scanningIcon.startAnimation(rotateAnimation)
        }
        
        // Create a sequence of scanning progress updates
        val scanningSteps = listOf(
            "Initializing scan...",
            "Analyzing URL structure...",
            "Checking domain reputation...",
            "Scanning for phishing patterns...",
            "Verifying with threat database...",
            "Applying AI analysis...",
            "Finalizing results..."
        )
        
        // Show scanning progress with delays
        val handler = Handler(Looper.getMainLooper())
        for (i in scanningSteps.indices) {
            handler.postDelayed({
                if (isAdded) { // Check if fragment is still attached
                    scanningProgress.text = scanningSteps[i]
                }
            }, i * 500L)
        }
        
        // Make the actual API call to check the URL
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Create JSON request body
                val jsonObject = JSONObject()
                jsonObject.put("url", url)
                val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaType())
                
                // Create the request
                val request = Request.Builder()
                    .url("https://site-phishing.onrender.com/predict")
                    .post(requestBody)
                    .build()
                
                // Execute the request
                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                
                // Parse the response
                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody)
                val confidence = jsonResponse.getDouble("confidence")
                val isPhishing = jsonResponse.getBoolean("is_phishing")
                
                // Save scan result to database
                val severityScore = if (isPhishing) confidence.toFloat() else 0f
                scanDataManager.addScan(url, isPhishing, severityScore)
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    if (isAdded) { // Check if fragment is still attached
                        // Stop animations
                        scanningIcon.clearAnimation()
                        
                        // Hide scanning container
                        scanningContainer.visibility = View.GONE
                        
                        // Show appropriate result based on API response
                        if (!isPhishing) {
                            showSafeResult(url, confidence)
                        } else {
                            showPhishingResult(url, confidence)
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle errors on main thread
                withContext(Dispatchers.Main) {
                    if (isAdded) { // Check if fragment is still attached
                        // Stop animations
                        scanningIcon.clearAnimation()
                        
                        // Hide scanning container
                        scanningContainer.visibility = View.GONE
                        
                        // Show error result
                        showErrorResult(e.message ?: "Unknown error occurred")
                    }
                }
            }
        }
    }
    
    /**
     * Shows a safe result for the URL
     */
    private fun showSafeResult(url: String, confidence: Double = 0.95) {
        // Clear any previous results first
        clearPreviousResults()
        
        // Create and add the notification-style result view
        val resultView = layoutInflater.inflate(R.layout.notification_url_check, resultsContainer, false)
        
        // Set the URL in the result
        resultView.findViewById<TextView>(R.id.notification_url).text = url
        
        // Configure for safe result
        val statusIndicator = resultView.findViewById<View>(R.id.notification_status_indicator)
        val statusText = resultView.findViewById<TextView>(R.id.notification_status_text)
        val riskPercentage = resultView.findViewById<TextView>(R.id.notification_risk_percentage)
        val riskDescription = resultView.findViewById<TextView>(R.id.notification_risk_description)
        val riskLevel = resultView.findViewById<ProgressBar>(R.id.notification_risk_level)
        
        // Calculate risk percentage (inverse of confidence for safe URLs)
        val riskPercent = ((1 - confidence) * 100).toInt().coerceIn(1, 100)
        
        statusIndicator.setBackgroundResource(R.drawable.notification_status_safe)
        statusText.text = "SAFE"
        statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.positive_green))
        riskPercentage.text = "${riskPercent}%"
        riskDescription.text = "This URL appears to be safe with no signs of phishing or malicious content."
        riskLevel.progress = riskPercent
        riskLevel.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.positive_green))
        
        // Add the new result view
        resultsContainer.addView(resultView)
        
        // Show a notification
        context?.let { ctx ->
            NotificationHelper.showUrlScanNotification(
                ctx,
                url,
                NotificationHelper.RiskLevel.SAFE
            )
        }
    }
    
    /**
     * Shows a phishing result for the URL
     */
    private fun showPhishingResult(url: String, confidence: Double = 0.95) {
        // Increment phishing detected counter
        context?.let { ctx ->
            PreferencesManager.incrementPhishingDetectedCount(ctx)
        }
        
        // Clear any previous results first
        clearPreviousResults()
        
        // Create and add the notification-style result view
        val resultView = layoutInflater.inflate(R.layout.notification_url_check, resultsContainer, false)
        
        // Set the URL in the result
        resultView.findViewById<TextView>(R.id.notification_url).text = url
        
        // Configure for phishing result
        val statusIndicator = resultView.findViewById<View>(R.id.notification_status_indicator)
        val statusText = resultView.findViewById<TextView>(R.id.notification_status_text)
        val riskPercentage = resultView.findViewById<TextView>(R.id.notification_risk_percentage)
        val riskDescription = resultView.findViewById<TextView>(R.id.notification_risk_description)
        val riskLevel = resultView.findViewById<ProgressBar>(R.id.notification_risk_level)
        
        // Calculate risk percentage based on confidence
        val riskPercent = (confidence * 100).toInt().coerceIn(1, 100)
        
        statusIndicator.setBackgroundResource(R.drawable.notification_status_critical)
        statusText.text = "PHISHING DETECTED"
        statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.critical_red))
        riskPercentage.text = "${riskPercent}%"
        riskDescription.text = "This URL has been identified as a potential phishing attempt. It may be trying to steal your personal information."
        riskLevel.progress = riskPercent
        riskLevel.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.critical_red))
        
        // Add the new result view
        resultsContainer.addView(resultView)
        
        // Show a notification
        context?.let { ctx ->
            NotificationHelper.showUrlScanNotification(
                ctx,
                url,
                NotificationHelper.RiskLevel.CRITICAL
            )
        }
    }
    
    /**
     * Shows an error result
     */
    private fun showErrorResult(errorMessage: String) {
        // Clear any previous results first
        clearPreviousResults()
        
        // Create and add the notification-style result view
        val resultView = layoutInflater.inflate(R.layout.notification_url_check, resultsContainer, false)
        
        // Configure for error result
        val statusIndicator = resultView.findViewById<View>(R.id.notification_status_indicator)
        val statusText = resultView.findViewById<TextView>(R.id.notification_status_text)
        val urlText = resultView.findViewById<TextView>(R.id.notification_url)
        val riskPercentage = resultView.findViewById<TextView>(R.id.notification_risk_percentage)
        val riskDescription = resultView.findViewById<TextView>(R.id.notification_risk_description)
        val riskLevel = resultView.findViewById<ProgressBar>(R.id.notification_risk_level)
        
        statusIndicator.setBackgroundResource(R.drawable.notification_status_medium)
        statusText.text = "ERROR"
        statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.medium_yellow))
        urlText.text = errorMessage
        riskPercentage.text = "--"
        riskDescription.text = "An error occurred while scanning the URL. Please try again later."
        riskLevel.progress = 50 // Medium risk level
        riskLevel.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.medium_yellow))
        
        // Add the new result view
        resultsContainer.addView(resultView)
    }
    
    /**
     * Clears any previous results from the results container
     * Note: This is now handled directly in performUrlScan
     */
    private fun clearPreviousResults() {
        // Remove all views from the results container
        resultsContainer.removeAllViews()
        
        // Reset the scanning progress text
        scanningProgress.text = "Preparing scan..."
        
        // Stop any ongoing animations
        scanningIcon.clearAnimation()
    }
}

