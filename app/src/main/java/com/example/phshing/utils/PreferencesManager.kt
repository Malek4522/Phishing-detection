package com.example.phshing.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Utility class to manage app preferences
 */
object PreferencesManager {
    private const val PREFS_NAME = "phishing_detector_prefs"
    private const val KEY_PROTECTION_ACTIVE = "protection_active"
    
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
}
