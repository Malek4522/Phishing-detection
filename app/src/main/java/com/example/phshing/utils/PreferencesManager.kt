package com.example.phshing.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility class to manage app preferences
 */
object PreferencesManager {
    private const val PREFS_NAME = "phishing_detector_prefs"
    private const val KEY_PROTECTION_ACTIVE = "protection_active"
    private const val KEY_LAYER1_ACTIVE = "layer1_active"
    private const val KEY_LAYER2_ACTIVE = "layer2_active"
    private const val KEY_BOTH_LAYERS_ACTIVE = "both_layers_active"
    private const val KEY_TOTAL_SCANNED_LINKS = "total_scanned_links"
    private const val KEY_PHISHING_DETECTED_COUNT = "phishing_detected_count"
    private const val KEY_USERNAME = "username"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save the protection active status
     */
    fun setProtectionActive(context: Context, active: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_PROTECTION_ACTIVE, active).apply()
    }
    
    /**
     * Get the protection active status
     */
    fun isProtectionActive(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_PROTECTION_ACTIVE, false)
    }
    
    /**
     * Save Layer 1 (Accessibility Monitoring) active status
     */
    fun setLayer1Active(context: Context, active: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_LAYER1_ACTIVE, active).apply()
        // If Layer 1 is activated, ensure protection is active
        if (active) {
            setProtectionActive(context, true)
        }
    }
    
    /**
     * Get Layer 1 (Accessibility Monitoring) active status
     */
    fun isLayer1Active(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_LAYER1_ACTIVE, false)
    }
    
    /**
     * Save Layer 2 (Universal Link Hook) active status
     */
    fun setLayer2Active(context: Context, active: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_LAYER2_ACTIVE, active).apply()
        // If Layer 2 is activated, ensure protection is active
        if (active) {
            setProtectionActive(context, true)
        }
    }
    
    /**
     * Get Layer 2 (Universal Link Hook) active status
     */
    fun isLayer2Active(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_LAYER2_ACTIVE, false)
    }
    
    /**
     * Save Both Layers active status
     */
    fun setBothLayersActive(context: Context, active: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_BOTH_LAYERS_ACTIVE, active).apply()
        // If both layers are activated, ensure individual layers and protection are active
        if (active) {
            setLayer1Active(context, true)
            setLayer2Active(context, true)
            setProtectionActive(context, true)
        }
    }
    
    /**
     * Get Both Layers active status
     */
    fun areBothLayersActive(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_BOTH_LAYERS_ACTIVE, false)
    }
    
    /**
     * Deactivate all protection layers
     */
    fun deactivateAllLayers(context: Context) {
        val editor = getPreferences(context).edit()
        editor.putBoolean(KEY_LAYER1_ACTIVE, false)
        editor.putBoolean(KEY_LAYER2_ACTIVE, false)
        editor.putBoolean(KEY_BOTH_LAYERS_ACTIVE, false)
        editor.putBoolean(KEY_PROTECTION_ACTIVE, false)
        editor.apply()
    }
    
    /**
     * Get the total number of links scanned
     */
    fun getTotalScannedLinks(context: Context): Int {
        return getPreferences(context).getInt(KEY_TOTAL_SCANNED_LINKS, 0)
    }
    
    /**
     * Increment the total number of links scanned
     */
    fun incrementTotalScannedLinks(context: Context) {
        val current = getTotalScannedLinks(context)
        getPreferences(context).edit().putInt(KEY_TOTAL_SCANNED_LINKS, current + 1).apply()
    }
    
    /**
     * Set the total number of links scanned (for testing/reset purposes)
     */
    fun setTotalScannedLinks(context: Context, count: Int) {
        getPreferences(context).edit().putInt(KEY_TOTAL_SCANNED_LINKS, count).apply()
    }
    
    /**
     * Get the number of phishing links detected
     */
    fun getPhishingDetectedCount(context: Context): Int {
        return getPreferences(context).getInt(KEY_PHISHING_DETECTED_COUNT, 0)
    }
    
    /**
     * Increment the number of phishing links detected
     */
    fun incrementPhishingDetectedCount(context: Context) {
        val current = getPhishingDetectedCount(context)
        getPreferences(context).edit().putInt(KEY_PHISHING_DETECTED_COUNT, current + 1).apply()
    }
    
    /**
     * Set the number of phishing links detected (for testing/reset purposes)
     */
    fun setPhishingDetectedCount(context: Context, count: Int) {
        getPreferences(context).edit().putInt(KEY_PHISHING_DETECTED_COUNT, count).apply()
    }
    
    /**
     * Get the username
     */
    fun getUsername(context: Context): String? {
        return getPreferences(context).getString(KEY_USERNAME, null)
    }
    
    /**
     * Set the username
     */
    fun setUsername(context: Context, username: String) {
        getPreferences(context).edit().putString(KEY_USERNAME, username).apply()
    }
    
    /**
     * Check if onboarding has been completed
     */
    fun hasCompletedOnboarding(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }
    
    /**
     * Set onboarding completion status
     */
    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}
