package com.example.phshing.utils

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.phshing.MainContainerActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object NotificationHelper {
    
    private const val TAG = "NotificationHelper"

    private const val CHANNEL_ID = "url_scan_channel"
    const val PROTECTION_CHANNEL_ID = "real_time_protection_channel"
    private const val PHISHING_ALERT_CHANNEL_ID = "phishing_alert_channel"
    private const val NOTIFICATION_ID = 1001
    const val NOTIFICATION_ID_PROTECTION_ACTIVE = 1002
    const val NOTIFICATION_ID_PHISHING_ALERT = 1003
    
    // Group key for phishing notifications
    private const val PHISHING_GROUP_KEY = "com.example.phshing.PHISHING_ALERTS"
    private const val PHISHING_SUMMARY_ID = 1004
    
    // Store recent phishing URLs to avoid duplicates and for summary
    private val recentPhishingUrls = mutableListOf<String>()
    private const val MAX_RECENT_URLS = 10

    // Risk levels
    enum class RiskLevel {
        SAFE, MEDIUM, CRITICAL
    }

    fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // URL Scan Results channel
            val scanName = "URL Scan Results"
            val scanDescriptionText = "Shows results of URL security scans"
            val scanImportance = NotificationManager.IMPORTANCE_HIGH
            val scanChannel = NotificationChannel(CHANNEL_ID, scanName, scanImportance).apply {
                description = scanDescriptionText
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(scanChannel)
            
            // Real-time Protection channel
            val protectionName = "Real-time Protection"
            val protectionDescriptionText = "Shows status of real-time URL protection"
            val protectionImportance = NotificationManager.IMPORTANCE_LOW
            val protectionChannel = NotificationChannel(PROTECTION_CHANNEL_ID, protectionName, protectionImportance).apply {
                description = protectionDescriptionText
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(protectionChannel)
            
            // Phishing Alert channel
            val alertName = "Phishing Alerts"
            val alertDescriptionText = "Shows alerts for detected phishing URLs"
            val alertImportance = NotificationManager.IMPORTANCE_HIGH
            val alertChannel = NotificationChannel(PHISHING_ALERT_CHANNEL_ID, alertName, alertImportance).apply {
                description = alertDescriptionText
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    /**
     * This method has been modified to NOT show scan notifications
     * URL scan results are now only displayed in the app UI
     */
    fun showUrlScanNotification(context: Context, url: String, riskLevel: RiskLevel) {
        // Log the scan but don't show a notification
        Log.d(TAG, "URL scan completed for: $url with risk level: $riskLevel")
        Log.d(TAG, "Notifications for URL scans are disabled - results shown in app only")
        
        // No notifications are shown - all scan results should be displayed in the app UI
        // This keeps the foreground service notification but prevents additional scan notifications
    }
    
    /**
     * Fallback method to show a simple notification without custom layout
     * This method has been disabled to prevent scan notifications
     */
    private fun showSimpleNotification(context: Context, url: String, riskLevel: RiskLevel, pendingIntent: PendingIntent) {
        // This method is now disabled to prevent scan notifications
        // All scan results should be displayed in the app UI only
        Log.d(TAG, "Simple notification disabled for URL: $url with risk level: $riskLevel")
        // No notification is shown
    }
    
    /**
     * Creates a persistent notification for the real-time protection service
     */
    fun createProtectionActiveNotification(context: Context) = 
        createProtectionNotification(context, true)
    
    /**
     * Shows a persistent notification indicating that real-time protection is active
     */
    fun showProtectionActiveNotification(context: Context) {
        // Create the notification and show it
        val notification = createProtectionNotification(context, false)
        
        // Show the notification with permission check
        showNotificationWithPermissionCheck(context, NOTIFICATION_ID_PROTECTION_ACTIVE, notification.build())
    }
    
    /**
     * Creates a notification for real-time protection
     * @param forForeground Whether this notification is for a foreground service
     */
    fun createProtectionNotification(context: Context, forForeground: Boolean): NotificationCompat.Builder {
        // Create an intent to open the app with the URL check fragment when notification is tapped
        val contentIntent = Intent(context, MainContainerActivity::class.java).apply {
            putExtra("fragment", "url_check")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create or update notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                PROTECTION_CHANNEL_ID,
                "Real-time Protection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows status of real-time URL protection"
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Build a simple, reliable notification that can't be removed
        val builder = NotificationCompat.Builder(context, PROTECTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock) // Use system shield/lock icon for security app
            .setContentTitle("GUARDIAN AI Protection")
            .setContentText("Real-time protection is active")
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("GUARDIAN AI Protection")
                .bigText("Real-time URL protection is active and monitoring your browsing for phishing threats."))
            .setOngoing(true) // Cannot be dismissed by the user
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Maximum priority
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
        
        // For Android 12+, ensure the notification is properly styled for foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && forForeground) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        
        return builder
    }

    /**
     * Shows a notification with proper permission checking
     */
    private fun showNotificationWithPermissionCheck(context: Context, notificationId: Int, notification: android.app.Notification) {
        val notificationManager = NotificationManagerCompat.from(context)
        
        // Check if we have notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == 
                    PackageManager.PERMISSION_GRANTED) {
                // We have permission, show the notification
                try {
                    notificationManager.notify(notificationId, notification)
                } catch (e: SecurityException) {
                    // Handle security exception
                    Log.e("NotificationHelper", "SecurityException when showing notification: ${e.message}")
                }
            } else {
                // We don't have permission, can't show notification
                Log.w("NotificationHelper", "Cannot show notification: POST_NOTIFICATIONS permission not granted")
            }
        } else {
            // For older Android versions, just try to show the notification
            try {
                notificationManager.notify(notificationId, notification)
            } catch (e: SecurityException) {
                // Handle security exception
                Log.e("NotificationHelper", "SecurityException when showing notification: ${e.message}")
            }
        }
    }
    
    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return dateFormat.format(Date())
    }
    
    /**
     * Creates a notification for phishing alerts
     * @param context The application context
     * @param url The detected phishing URL
     * @param message Additional details about the phishing detection
     */
    fun createPhishingAlertNotification(context: Context, url: String, message: String): NotificationCompat.Builder {
        // Add URL to recent list for summary notification
        synchronized(recentPhishingUrls) {
            // Remove if already exists (to avoid duplicates)
            recentPhishingUrls.remove(url)
            // Add to beginning of list
            recentPhishingUrls.add(0, url)
            // Trim list if too large
            while (recentPhishingUrls.size > MAX_RECENT_URLS) {
                recentPhishingUrls.removeAt(recentPhishingUrls.size - 1)
            }
        }
        
        // Create an intent to open the URL check fragment when notification is tapped
        val contentIntent = Intent(context, MainContainerActivity::class.java).apply {
            putExtra("fragment", "url_check")
            putExtra("url", url)
            putExtra("fromNotification", true)
        }
        
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            Random.nextInt(), // Use random request code to ensure uniqueness
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build a high-priority alert notification that's part of a group
        return NotificationCompat.Builder(context, PHISHING_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Phishing URL Detected")
            .setContentText("A potentially dangerous URL was detected: $url")
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle("⚠️ Phishing URL Detected")
                .bigText("A potentially dangerous URL was detected:\n$url\n\n$message"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setGroup(PHISHING_GROUP_KEY) // Add to phishing notification group
    }
    
    /**
     * Shows a notification with proper permission checking
     */
    fun showNotification(context: Context, notificationId: Int, notification: Notification) {
        // For phishing alerts, also show a summary notification
        if (notificationId == NOTIFICATION_ID_PHISHING_ALERT) {
            // Create and show a summary notification
            showPhishingSummaryNotification(context)
        }
        
        // Show the individual notification
        showNotificationWithPermissionCheck(context, notificationId, notification)
    }
    
    /**
     * Creates and shows a summary notification for grouped phishing alerts
     */
    private fun showPhishingSummaryNotification(context: Context) {
        // Get count of recent phishing URLs
        val urlCount = synchronized(recentPhishingUrls) { recentPhishingUrls.size }
        if (urlCount == 0) return
        
        // Create intent to open URL check fragment
        val contentIntent = Intent(context, MainContainerActivity::class.java).apply {
            putExtra("fragment", "url_check")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            Random.nextInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build summary text
        val summaryText = if (urlCount == 1) {
            "1 phishing URL detected"
        } else {
            "$urlCount phishing URLs detected"
        }
        
        // Create a detailed summary with the recent URLs
        val detailedSummary = StringBuilder("Recently detected phishing URLs:\n")
        synchronized(recentPhishingUrls) {
            // Add up to 5 most recent URLs to the summary
            val urlsToShow = recentPhishingUrls.take(5)
            urlsToShow.forEachIndexed { index, url ->
                detailedSummary.append("${index + 1}. $url\n")
            }
            // If there are more URLs than shown, add a note
            if (urlCount > 5) {
                detailedSummary.append("...and ${urlCount - 5} more")
            }
        }
        
        // Build the summary notification
        val summaryNotification = NotificationCompat.Builder(context, PHISHING_ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Phishing Protection Alert")
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailedSummary))
            .setGroup(PHISHING_GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()
        
        // Show the summary notification
        showNotificationWithPermissionCheck(context, PHISHING_SUMMARY_ID, summaryNotification)
    }
    
    /**
     * Show URL scan notification with action buttons
     * @param openAction If true, adds an "Open Anyway" action button for phishing URLs
     */
    fun showUrlScanNotificationWithActions(
        context: Context,
        url: String,
        riskLevel: RiskLevel,
        riskPercent: Int = 0,
        openAction: Boolean = false
    ) {
        // Create an explicit intent for the MainActivity to view details
        val detailsIntent = Intent(context, MainContainerActivity::class.java).apply {
            putExtra("fragment", "url_check")
            putExtra("url", url)
            putExtra("fromNotification", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val detailsPendingIntent = PendingIntent.getActivity(
            context, 
            Random.nextInt(1000), 
            detailsIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(getIconForRiskLevel(riskLevel))
            .setContentTitle(getTitleForRiskLevel(riskLevel))
            .setContentText(getDetailedNotificationText(url, riskLevel, riskPercent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(detailsPendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(getDetailedNotificationText(url, riskLevel, riskPercent)))
        
        // Add action buttons based on risk level
        if (riskLevel == RiskLevel.CRITICAL && openAction) {
            // Create an intent to open the URL anyway
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val openPendingIntent = PendingIntent.getActivity(
                context, 
                Random.nextInt(1000), 
                openIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Add open anyway action
            builder.addAction(
                android.R.drawable.ic_menu_view,
                "Open Anyway",
                openPendingIntent
            )
        } else if (riskLevel == RiskLevel.SAFE) {
            // For safe URLs, add a view details action
            builder.addAction(
                android.R.drawable.ic_menu_info_details,
                "View Details",
                detailsPendingIntent
            )
        }
        
        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(Random.nextInt(9000), builder.build())
            } else {
                Log.d(TAG, "Notification permission not granted")
            }
        }
    }
    
    /**
     * Show error notification for URL scanning
     * 
     * @param context The application context
     * @param url The URL that failed to scan
     * @param errorMessage Optional custom error message to display (defaults to generic message)
     */
    fun showUrlScanErrorNotification(context: Context, url: String, errorMessage: String? = null) {
        // Log the error
        Log.e(TAG, "URL scan error notification for: $url - ${errorMessage ?: "Unknown error"}")
        
        // Create an intent to view details in the app instead of opening the URL directly
        // This is safer than providing a direct URL open action when there's an error
        val detailsIntent = Intent(context, MainContainerActivity::class.java).apply {
            putExtra("fragment", "url_check")
            putExtra("url", url)
            putExtra("scanError", true)
            putExtra("errorMessage", errorMessage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        val detailsPendingIntent = PendingIntent.getActivity(
            context, 
            Random.nextInt(1000), 
            detailsIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Use the provided error message or fall back to a generic one
        val notificationMessage = errorMessage ?: "Could not scan $url due to an error."
        
        // Build the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Scan Error")
            .setContentText(notificationMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(detailsPendingIntent)
        
        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(Random.nextInt(9000), builder.build())
            } else {
                Log.d(TAG, "Notification permission not granted")
            }
        }
    }
    
    /**
     * Get icon resource for risk level
     */
    private fun getIconForRiskLevel(riskLevel: RiskLevel): Int {
        return when (riskLevel) {
            RiskLevel.SAFE -> android.R.drawable.ic_dialog_info
            RiskLevel.MEDIUM -> android.R.drawable.ic_dialog_alert
            RiskLevel.CRITICAL -> android.R.drawable.ic_dialog_alert
        }
    }
    
    /**
     * Get notification title for risk level
     */
    private fun getTitleForRiskLevel(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.SAFE -> "URL is Safe"
            RiskLevel.MEDIUM -> "Suspicious URL Detected"
            RiskLevel.CRITICAL -> "Phishing URL Detected"
        }
    }
    
    /**
     * Get detailed notification text for risk level and percentage
     */
    private fun getDetailedNotificationText(url: String, riskLevel: RiskLevel, riskPercent: Int): String {
        return when (riskLevel) {
            RiskLevel.SAFE -> "$url appears to be safe ($riskPercent% risk)."
            RiskLevel.MEDIUM -> "$url appears suspicious with $riskPercent% risk. Use caution."
            RiskLevel.CRITICAL -> "WARNING: $url is likely phishing with $riskPercent% risk. Do not proceed!"
        }
    }
    
    /**
     * Get notification text for risk level
     */
    private fun getNotificationText(url: String, riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.SAFE -> "URL is safe: $url"
            RiskLevel.MEDIUM -> "Suspicious URL: $url"
            RiskLevel.CRITICAL -> "Phishing detected: $url"
        }
    }
}
