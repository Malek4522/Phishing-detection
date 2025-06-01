package com.example.phshing.database

import android.content.Context
import com.example.phshing.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager class for handling scan data operations
 */
class ScanDataManager(private val context: Context) {
    
    private val dbHelper = ScanDatabaseHelper(context)
    
    /**
     * Add a new scan to the database
     */
    fun addScan(url: String, isPhishing: Boolean, severityScore: Float = 0f): Long {
        val scan = ScanRecord.createScan(url, isPhishing, severityScore)
        val id = dbHelper.addScan(scan)
        
        // Also update the preferences for backward compatibility
        // This ensures the old preferences-based counters stay in sync with the database
        PreferencesManager.incrementTotalScannedLinks(context)
        if (isPhishing) {
            PreferencesManager.incrementPhishingDetectedCount(context)
        }
        
        return id
    }
    
    /**
     * Add a ScanRecord directly to the database
     * This is used by Layer 2 - Universal Link Hook functionality
     */
    fun addScan(scanRecord: ScanRecord): Long {
        val id = dbHelper.addScan(scanRecord)
        
        // Also update the preferences for backward compatibility
        PreferencesManager.incrementTotalScannedLinks(context)
        if (scanRecord.isPhishing) {
            PreferencesManager.incrementPhishingDetectedCount(context)
        }
        
        return id
    }
    
    /**
     * Get total number of scanned links
     */
    fun getTotalScannedCount(): Int {
        return dbHelper.getTotalScannedCount()
    }
    
    /**
     * Get number of phishing links detected
     */
    fun getPhishingCount(): Int {
        return dbHelper.getPhishingCount()
    }
    
    /**
     * Get number of safe links (total - phishing)
     */
    fun getSafeLinksCount(): Int {
        val total = getTotalScannedCount()
        val phishing = getPhishingCount()
        return total - phishing
    }
    
    /**
     * Calculate safety percentage
     */
    fun getSafetyPercentage(): Int {
        val total = getTotalScannedCount()
        val safe = getSafeLinksCount()
        return if (total > 0) {
            (safe.toFloat() / total.toFloat() * 100).toInt()
        } else {
            100 // Default to 100% if no scans
        }
    }
    
    /**
     * Get count of links by severity
     */
    fun getCountBySeverity(severity: String): Int {
        return dbHelper.getCountBySeverity(severity)
    }
    
    /**
     * Get percentage change for a specific severity
     */
    fun getPercentageChange(severity: String): Int {
        return dbHelper.getPhishingPercentageChange(severity)
    }
    
    /**
     * Get count of links scanned today
     */
    fun getDailyScannedCount(): Int {
        return dbHelper.getDailyScannedCount()
    }
    
    /**
     * Get count of links scanned in the last 7 days
     */
    fun getWeeklyScannedCount(): Int {
        return dbHelper.getWeeklyScannedCount()
    }
    
    /**
     * Get formatted last scan time
     */
    fun getLastScanTime(): String {
        val timestamp = dbHelper.getLastScanTimestamp()
        return if (timestamp > 0) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        } else {
            "00:00"
        }
    }
    
    /**
     * Get formatted last scan date
     */
    fun getLastScanDate(): String {
        val timestamp = dbHelper.getLastScanTimestamp()
        return if (timestamp > 0) {
            val today = Calendar.getInstance()
            val scanDate = Calendar.getInstance().apply { timeInMillis = timestamp }
            
            when {
                // If scan was today
                today.get(Calendar.YEAR) == scanDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == scanDate.get(Calendar.DAY_OF_YEAR) -> "Today"
                
                // If scan was yesterday
                today.get(Calendar.YEAR) == scanDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) - scanDate.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
                
                // Otherwise show date
                else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
            }
        } else {
            "Today"
        }
    }
    
    /**
     * Clear all data from the database and reset counters
     */
    fun clearAllData() {
        // Clear database
        dbHelper.clearAllData()
        
        // Reset preferences counters
        PreferencesManager.setTotalScannedLinks(context, 0)
        PreferencesManager.setPhishingDetectedCount(context, 0)
    }
    
    /**
     * Add sample data for testing
     */
    fun addSampleData() {
        // Clear existing data first to ensure fresh start
        clearAllData()
        
        // Add a small set of sample data (just 10 items total)
        // Add some critical threats
        repeat(2) {
            addScan("https://malicious-site-${it}.com", true, 0.9f)
        }
        
        // Add some high threats
        repeat(3) {
            addScan("https://suspicious-site-${it}.com", true, 0.7f)
        }
        
        // Add some medium threats
        repeat(1) {
            addScan("https://questionable-site-${it}.com", true, 0.4f)
        }
        
        // Add some safe sites
        repeat(4) {
            addScan("https://safe-site-${it}.com", false)
        }
    }
}
