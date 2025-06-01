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
        
        // Build a high-priority alert notification
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
    }
    
    /**
     * Shows a notification with proper permission checking
     */
    fun showNotification(context: Context, notificationId: Int, notification: Notification) {
        showNotificationWithPermissionCheck(context, notificationId, notification)
    }
}
