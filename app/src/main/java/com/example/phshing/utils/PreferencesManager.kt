package com.example.phshing.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

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
    private const val KEY_LAST_DEFAULT_HANDLER_PROMPT_TIME = "last_default_handler_prompt_time"
    private const val KEY_LAST_DEFAULT_BROWSER_PROMPT_TIME = "last_default_browser_prompt_time"
    private const val KEY_HAS_SHOWN_DEFAULT_BROWSER_PROMPT = "has_shown_default_browser_prompt"
    private const val KEY_USERNAME = "username"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_APPROVED_URLS = "approved_urls"
    private const val KEY_APPROVED_URL_EXPIRY = "approved_url_expiry_"
    private const val KEY_AUTO_CLEAR_CACHE_INTERVAL = "auto_clear_cache_interval"
    
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
    
    /**
     * Get the timestamp of when we last prompted the user to set the app as default link handler
     */
    fun getLastDefaultHandlerPromptTime(context: Context): Long {
        return getPreferences(context).getLong(KEY_LAST_DEFAULT_HANDLER_PROMPT_TIME, 0)
    }
    
    /**
     * Set the timestamp of when we last prompted the user to set the app as default link handler
     */
    fun setLastDefaultHandlerPromptTime(context: Context, timestamp: Long) {
        getPreferences(context).edit().putLong(KEY_LAST_DEFAULT_HANDLER_PROMPT_TIME, timestamp).apply()
    }
    
    /**
     * Check if we've shown the default browser prompt to the user
     */
    fun hasShownDefaultBrowserPrompt(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_HAS_SHOWN_DEFAULT_BROWSER_PROMPT, false)
    }
    
    /**
     * Set whether we've shown the default browser prompt to the user
     */
    fun setHasShownDefaultBrowserPrompt(context: Context, hasShown: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_HAS_SHOWN_DEFAULT_BROWSER_PROMPT, hasShown).apply()
    }
    
    /**
     * Get the timestamp of when we last prompted the user to set the app as default browser
     */
    fun getLastDefaultBrowserPromptTime(context: Context): Long {
        return getPreferences(context).getLong(KEY_LAST_DEFAULT_BROWSER_PROMPT_TIME, 0L)
    }
    
    /**
     * Set the timestamp of when we last prompted the user to set the app as default browser
     */
    fun setLastDefaultBrowserPromptTime(context: Context, timestamp: Long) {
        getPreferences(context).edit().putLong(KEY_LAST_DEFAULT_BROWSER_PROMPT_TIME, timestamp).apply()
    }
    
    /**
     * Generate a SHA-256 hash for a URL
     * This provides a reliable, collision-resistant hash for URL caching
     */
    private fun hashUrl(url: String): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(url.toByteArray(StandardCharsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e("PreferencesManager", "Error hashing URL: ${e.message}")
            // Fallback to a simple hash if SHA-256 fails
            return url.hashCode().toString()
        }
    }
    
    /**
     * Add a URL to the approved list
     * URLs in this list will not be re-scanned when clicked, preventing infinite loops
     * when a user clicks "Open Anyway" on a phishing URL
     */
    fun addApprovedUrl(context: Context, url: String) {
        // Generate a reliable hash for the URL
        val urlHash = hashUrl(url)
        
        // First check if the URL is already in the approved list
        val approvedUrls = getApprovedUrlsSet(context)
        
        // Store both the URL and its hash for verification
        val urlEntry = "$urlHash:$url"
        
        // If URL is already in the list, remove it first to ensure we don't have duplicates
        val existingEntry = approvedUrls.find { it.endsWith(":$url") }
        if (existingEntry != null) {
            Log.d("PreferencesManager", "URL already in approved list, updating: $url")
            val updatedUrls = approvedUrls.toMutableSet()
            updatedUrls.remove(existingEntry)
            saveApprovedUrlsSet(context, updatedUrls)
            
            // Remove any existing expiration time
            getPreferences(context).edit().remove(KEY_APPROVED_URL_EXPIRY + urlHash).apply()
        }
        
        // Store the URL with an expiration time (24 hours from now)
        val expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        
        // Store the URL entry in the set
        val newApprovedUrls = getApprovedUrlsSet(context)
        newApprovedUrls.add(urlEntry)
        saveApprovedUrlsSet(context, newApprovedUrls)
        
        // Store the expiration time separately using the hash as the key
        getPreferences(context).edit()
            .putLong(KEY_APPROVED_URL_EXPIRY + urlHash, expiryTime)
            .apply()
        
        Log.d("PreferencesManager", "Added URL to approved list: $url with hash: $urlHash (expires in 24h)")
    }
    
    /**
     * Get the set of approved URLs
     */
    private fun getApprovedUrlsSet(context: Context): MutableSet<String> {
        return getPreferences(context).getStringSet(KEY_APPROVED_URLS, mutableSetOf()) ?: mutableSetOf()
    }
    
    /**
     * Save the set of approved URLs
     */
    private fun saveApprovedUrlsSet(context: Context, urls: Set<String>) {
        getPreferences(context).edit().putStringSet(KEY_APPROVED_URLS, urls).apply()
    }
    
    /**
     * Check if a URL is in the approved list and not expired
     */
    fun isUrlApproved(context: Context, url: String): Boolean {
        // Generate hash for the URL
        val urlHash = hashUrl(url)
        
        // First check if the URL is in our approved set - must be EXACT match
        val approvedUrls = getApprovedUrlsSet(context)
        
        // Find entry that matches this URL exactly
        val matchingEntry = approvedUrls.find { it.endsWith(":$url") }
        if (matchingEntry == null) {
            Log.d("PreferencesManager", "URL not in approved list: $url")
            return false
        }
        
        // Verify the hash matches to prevent collisions
        val storedHash = matchingEntry.substringBefore(":")
        if (storedHash != urlHash) {
            Log.d("PreferencesManager", "URL hash mismatch. Expected: $urlHash, Found: $storedHash")
            return false
        }
        
        // Then check expiration
        val prefs = getPreferences(context)
        val expiryTime = prefs.getLong(KEY_APPROVED_URL_EXPIRY + urlHash, 0)
        
        // If expiry time is 0, URL was never approved
        if (expiryTime == 0L) {
            // Clean up the inconsistent state
            Log.d("PreferencesManager", "URL found in approved list but has no expiry time: $url")
            val updatedUrls = approvedUrls.toMutableSet()
            updatedUrls.remove(matchingEntry)
            saveApprovedUrlsSet(context, updatedUrls)
            return false
        }
        
        // Check if approval has expired
        val currentTime = System.currentTimeMillis()
        if (currentTime > expiryTime) {
            // Expired, remove it from preferences
            Log.d("PreferencesManager", "URL approval has expired: $url")
            val updatedUrls = approvedUrls.toMutableSet()
            updatedUrls.remove(matchingEntry)
            saveApprovedUrlsSet(context, updatedUrls)
            prefs.edit().remove(KEY_APPROVED_URL_EXPIRY + urlHash).apply()
            return false
        }
        
        // URL is approved and not expired
        Log.d("PreferencesManager", "URL is approved and not expired: $url")
        return true
    }
    
    /**
     * Clear all approved URLs
     */
    fun clearApprovedUrls(context: Context) {
        val prefs = getPreferences(context)
        val editor = prefs.edit()
        
        // Get the current set of approved URLs
        val approvedUrls = getApprovedUrlsSet(context)
        
        // Remove each URL's expiry time
        for (urlHash in approvedUrls) {
            editor.remove("$KEY_APPROVED_URL_EXPIRY$urlHash")
        }
        
        // Clear the approved URLs set
        editor.putStringSet(KEY_APPROVED_URLS, emptySet())
        
        editor.apply()
        Log.d("PreferencesManager", "Cleared all approved URLs and expiration data")
    }
    
    /**
     * Get the auto-clear cache interval setting
     * 0 = Never (Manual only)
     * 1 = Daily
     * 2 = Weekly
     * 3 = Monthly
     */
    fun getAutoClearCacheInterval(context: Context): Int {
        return getPreferences(context).getInt(KEY_AUTO_CLEAR_CACHE_INTERVAL, 0) // Default to Never
    }
    
    /**
     * Set the auto-clear cache interval setting
     * @param interval 0 = Never, 1 = Daily, 2 = Weekly, 3 = Monthly
     */
    fun setAutoClearCacheInterval(context: Context, interval: Int) {
        getPreferences(context).edit().putInt(KEY_AUTO_CLEAR_CACHE_INTERVAL, interval).apply()
    }
}
