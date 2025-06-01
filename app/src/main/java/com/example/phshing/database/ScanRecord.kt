package com.example.phshing.database

import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing a URL scan record
 */
data class ScanRecord(
    val id: Long = 0,
    val url: String,
    val isPhishing: Boolean,
    val severity: String,
    val scanDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create a scan record with calculated severity based on phishing status
         */
        fun createScan(url: String, isPhishing: Boolean, severityScore: Float = 0f): ScanRecord {
            // Determine severity based on phishing status and score
            val severity = when {
                !isPhishing -> ScanDatabaseHelper.SEVERITY_SAFE
                severityScore > 0.8f -> ScanDatabaseHelper.SEVERITY_CRITICAL
                severityScore > 0.5f -> ScanDatabaseHelper.SEVERITY_HIGH
                else -> ScanDatabaseHelper.SEVERITY_MEDIUM
            }
            
            return ScanRecord(
                url = url,
                isPhishing = isPhishing,
                severity = severity
            )
        }
    }
}
