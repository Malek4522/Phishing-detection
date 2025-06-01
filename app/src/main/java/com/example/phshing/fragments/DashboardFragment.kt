package com.example.phshing.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.phshing.R
import com.example.phshing.database.ScanDataManager
import com.example.phshing.database.ScanDatabaseHelper
import com.example.phshing.database.ScanRecord
import com.example.phshing.utils.AccessibilityUtil
import com.example.phshing.utils.PreferencesManager
import java.text.NumberFormat
import java.util.Locale

class DashboardFragment : Fragment() {
    
    private lateinit var scanDataManager: ScanDataManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    private lateinit var userAvatar: ImageView
    private lateinit var usernameText: TextView
    private lateinit var statusBar: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var totalLinksCount: TextView
    private lateinit var gaugePercentage: TextView
    private lateinit var gaugeStatus: TextView
    private lateinit var gaugeIndicator: ProgressBar
    private lateinit var criticalCount: TextView
    private lateinit var criticalPercentage: TextView
    private lateinit var highCount: TextView
    private lateinit var highPercentage: TextView
    private lateinit var mediumCount: TextView
    private lateinit var dailyScannedCount: TextView
    private lateinit var weeklyScannedCount: TextView
    private lateinit var statsCard: CardView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize scan data manager
        scanDataManager = ScanDataManager(requireContext())
        
        // Initialize UI elements
        initializeViews(view)

        // Set up statistics data
        setupStatisticsData()

        // Check protection status and update UI
        updateProtectionStatus()

        // Set up click listeners
        setupClickListeners()
    }

    private fun initializeViews(view: View) {
        userAvatar = view.findViewById(R.id.user_avatar)
        usernameText = view.findViewById(R.id.username)
        statusBar = view.findViewById(R.id.status_bar)
        statusText = view.findViewById(R.id.status_text)
        totalLinksCount = view.findViewById(R.id.total_links_count)
        gaugePercentage = view.findViewById(R.id.gauge_percentage)
        gaugeStatus = view.findViewById(R.id.gauge_status)
        gaugeIndicator = view.findViewById(R.id.gauge_indicator)
        criticalCount = view.findViewById(R.id.critical_count)
        criticalPercentage = view.findViewById(R.id.critical_percentage)
        highCount = view.findViewById(R.id.high_count)
        highPercentage = view.findViewById(R.id.high_percentage)
        mediumCount = view.findViewById(R.id.medium_count)
        dailyScannedCount = view.findViewById(R.id.daily_scanned_count)
        weeklyScannedCount = view.findViewById(R.id.weekly_scanned_count)
        statsCard = view.findViewById(R.id.stats_card)

        // Set username from preferences
        val username = PreferencesManager.getUsername(requireContext())
        if (username != null && username.isNotEmpty()) {
            usernameText.text = username
        }
    }

    private fun setupStatisticsData() {
        // Force refresh by recreating the data manager to ensure fresh data
        scanDataManager = ScanDataManager(requireContext())
        
        // Get data from database
        val totalLinks = scanDataManager.getTotalScannedCount()
        val safeLinks = scanDataManager.getSafeLinksCount()
        val safetyPercentage = scanDataManager.getSafetyPercentage()
        
        // Log current data for debugging
        android.util.Log.d("DashboardFragment", "Refreshing dashboard data: total=$totalLinks, safe=$safeLinks, safety=$safetyPercentage%")
        
        // Format numbers with commas for thousands
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

        // Update total links count
        totalLinksCount.text = numberFormat.format(totalLinks)

        // Update digital gauge percentage and progress bar
        gaugePercentage.text = "+$safetyPercentage%"
        
        // Animate the progress bar smoothly
        val animator = android.animation.ObjectAnimator.ofInt(gaugeIndicator, "progress", 0, safetyPercentage.toInt())
        animator.duration = 1000
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.start()
        
        // Update the gauge status text based on safety level
        val statusText = when {
            safetyPercentage >= 80 -> "SAFE"
            safetyPercentage >= 50 -> "MEDIUM"
            else -> "RISK"
        }
        
        // Update the gauge color based on safety level
        val gaugeColor = when {
            safetyPercentage >= 80 -> R.color.status_green
            safetyPercentage >= 50 -> R.color.status_yellow
            else -> R.color.status_red
        }
        
        // Apply colors to gauge elements
        gaugeStatus.text = statusText
        gaugePercentage.setTextColor(resources.getColor(gaugeColor, null))
        gaugeStatus.setTextColor(resources.getColor(gaugeColor, null))

        // Get threat counts from database
        val criticalLinksCount = scanDataManager.getCountBySeverity(ScanDatabaseHelper.SEVERITY_CRITICAL)
        val highLinksCount = scanDataManager.getCountBySeverity(ScanDatabaseHelper.SEVERITY_HIGH)
        val mediumLinksCount = scanDataManager.getCountBySeverity(ScanDatabaseHelper.SEVERITY_MEDIUM)
        
        // Get percentage changes
        val criticalChange = scanDataManager.getPercentageChange(ScanDatabaseHelper.SEVERITY_CRITICAL)
        val highChange = scanDataManager.getPercentageChange(ScanDatabaseHelper.SEVERITY_HIGH)
        
        // Update threat level counts and percentages
        criticalCount.text = "${numberFormat.format(criticalLinksCount)} Link"
        criticalPercentage.text = if (criticalChange >= 0) "+$criticalChange%" else "$criticalChange%"
        criticalPercentage.setTextColor(resources.getColor(
            if (criticalChange < 0) R.color.status_green else R.color.status_red, null))

        highCount.text = "${numberFormat.format(highLinksCount)} Link"
        highPercentage.text = if (highChange >= 0) "+$highChange%" else "$highChange%"
        highPercentage.setTextColor(resources.getColor(
            if (highChange < 0) R.color.status_green else R.color.status_red, null))

        mediumCount.text = "${numberFormat.format(mediumLinksCount)} Link"

        // Update daily and weekly scanned counts
        val dailyCount = scanDataManager.getDailyScannedCount()
        val weeklyCount = scanDataManager.getWeeklyScannedCount()
        
        // Format with timestamp
        val lastScanDate = scanDataManager.getLastScanDate()
        val lastScanTime = scanDataManager.getLastScanTime()
        
        dailyScannedCount.text = "${numberFormat.format(dailyCount)} Links"
        weeklyScannedCount.text = "${numberFormat.format(weeklyCount)} Links"
        
        // Log for debugging
        android.util.Log.d("DashboardFragment", "Data refreshed: Total links = $totalLinks")
        
        // We're not updating the timestamps in the UI since we don't have those IDs yet
        // Just using the static timestamps in the layout for now
    }

    private fun updateProtectionStatus() {
        val isProtectionActive = AccessibilityUtil.isAccessibilityServiceEnabled(requireContext()) && PreferencesManager.isProtectionActive(requireContext())
        if (isProtectionActive) {
            statusBar.setBackgroundResource(R.drawable.status_active_background)
            statusText.setText(R.string.protection_active)
            statusText.setTextColor(resources.getColor(R.color.status_green, null))
        } else {
            statusBar.setBackgroundResource(R.drawable.status_inactive_background)
            statusText.setText(R.string.protection_inactive)
            statusText.setTextColor(resources.getColor(R.color.status_red, null))
            statusText.text = getString(
                if (isProtectionActive) R.string.protection_active 
                else R.string.protection_inactive
            )
            statusText.setTextColor(resources.getColor(
                if (isProtectionActive) R.color.status_green 
                else R.color.status_red, 
                null
            ))
        }
    }
    
    /**
     * Sets up click listeners for interactive elements
     */
    private fun setupClickListeners() {
        // Status bar click to enable/disable protection
        statusBar.setOnClickListener {
            context?.let { ctx ->
                if (!AccessibilityUtil.isAccessibilityServiceEnabled(ctx)) {
                    AccessibilityUtil.showAccessibilityPromptDialog(ctx)
                } else {
                    // Toggle protection status
                    val currentStatus = PreferencesManager.isProtectionActive(ctx)
                    PreferencesManager.setProtectionActive(ctx, !currentStatus)
                    updateProtectionStatus()
                }
            }
        }
        
        // Stats card click listener can be used for other features if needed
        statsCard.setOnLongClickListener {
            // Long press functionality removed - no longer needed for debugging
            false
        }
    }
    
    // Debug methods removed - no longer needed for production
    
    /**
     * Checks if the accessibility service is enabled and prompts the user if needed
     */
    private fun checkAccessibilityService() {
        context?.let { ctx ->
            if (!AccessibilityUtil.isAccessibilityServiceEnabled(ctx)) {
                AccessibilityUtil.showAccessibilityPromptDialog(ctx)
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check again when returning to the fragment, in case the user enabled the service
        checkAccessibilityService()
        
        // Refresh data and UI
        setupStatisticsData()
        updateProtectionStatus()
    }
    
    companion object {
        fun newInstance(): DashboardFragment {
            return DashboardFragment()
        }
    }
}
