package com.example.phshing.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.example.phshing.service.UrlAccessibilityService

/**
 * Utility class for accessibility service related functions
 */
object AccessibilityUtil {
    
    // Flag to track if we've already shown the prompt in this session
    private var promptShownThisSession = false
    
    /**
     * Checks if the URL monitoring accessibility service is enabled
     * @param context Application context
     * @return true if the service is enabled, false otherwise
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        
        val packageName = context.packageName
        for (service in enabledServices) {
            val serviceInfo = service.resolveInfo.serviceInfo
            if (serviceInfo.packageName == packageName && 
                serviceInfo.name == UrlAccessibilityService::class.java.name) {
                return true
            }
        }
        return false
    }
    
    /**
     * Shows a dialog prompting the user to enable the accessibility service
     * @param context Application context
     */
    fun showAccessibilityPromptDialog(context: Context) {
        // Skip if we've already shown the prompt in this session
        if (promptShownThisSession) {
            return
        }
        
        // Mark that we've shown the prompt
        promptShownThisSession = true
        AlertDialog.Builder(context)
            .setTitle("Enable URL Monitoring")
            .setMessage("To protect you from phishing attacks, GuardianAI needs permission to monitor screen content for suspicious URLs. Please enable the accessibility service.")
            .setPositiveButton("Enable Now") { _, _ ->
                openAccessibilitySettings(context)
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
                // Toast removed as requested
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Opens the accessibility settings screen
     * @param context Application context
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
        // Toast removed as requested
    }
}
