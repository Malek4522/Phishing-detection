package com.example.phshing.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.phshing.R
import com.example.phshing.utils.AccessibilityUtil
import com.example.phshing.utils.NotificationHelper
import com.example.phshing.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Service that runs in the background to provide real-time protection.
 * This service maintains a foreground notification to indicate active protection,
 * but does not send additional notifications for URL scans.
 * URL scanning is performed directly in the app when needed.
 */
class RealTimeProtectionService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Coroutine scope for background tasks
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // How often to send heartbeat logs (in milliseconds)
    private val HEARTBEAT_INTERVAL = 30000L // 30 seconds
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
    
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        try {
            // Acquire wake lock to keep service running
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PhishingDetector::ProtectionWakeLock"
            )
            // Acquire wake lock with timeout to prevent battery drain (10 minutes)
            wakeLock?.acquire(10*60*1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock: ${e.message}")
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // IMPORTANT: When started with startForegroundService(), we MUST call startForeground() immediately
        // to avoid ANR crashes. So we start in foreground mode right away with a notification.
        val notification = NotificationHelper.createProtectionNotification(this, true).build()
        startForeground(NotificationHelper.NOTIFICATION_ID_PROTECTION_ACTIVE, notification)
        
        if (intent?.action == ACTION_STOP) {
            // Save the protection status as inactive
            PreferencesManager.setProtectionActive(this, false)
            
            // Release wake lock safely before stopping
            try {
                if (wakeLock != null && wakeLock!!.isHeld) {
                    wakeLock!!.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock on stop: ${e.message}")
            }
            
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Check if any protection layers are enabled in preferences
        val layer1Active = PreferencesManager.isLayer1Active(this)
        val layer2Active = PreferencesManager.isLayer2Active(this)
        
        // If no protection layers are active, stop the service
        if (!layer1Active && !layer2Active) {
            Log.d(TAG, "No protection layers are active in preferences, stopping service")
            PreferencesManager.setProtectionActive(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        
        // If Layer 1 is active in preferences, check if accessibility service is enabled
        if (layer1Active && !AccessibilityUtil.isAccessibilityServiceEnabled(this)) {
            Log.d(TAG, "Layer 1 is active but missing accessibility permission")
            
            // Instead of showing a dialog (which causes crashes), create a notification
            // that will take the user to accessibility settings when tapped
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Create an intent to open accessibility settings
            val settingsIntent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val pendingIntent = PendingIntent.getActivity(
                this, 
                0, 
                settingsIntent, 
                PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create notification channel for Android O and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "accessibility_channel",
                    "Accessibility Permissions",
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = "Notifications for required permissions"
                notificationManager.createNotificationChannel(channel)
            }
            
            // Build the notification
            val notification = NotificationCompat.Builder(this, "accessibility_channel")
                .setContentTitle("Accessibility Permission Required")
                .setContentText("Layer 1 protection requires accessibility permission")
                .setSmallIcon(R.drawable.ic_shield)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            
            // Show the notification
            notificationManager.notify(PERMISSION_NOTIFICATION_ID, notification)
        }
        
        // Save the protection status as active
        PreferencesManager.setProtectionActive(this, true)
        
        // Start the service heartbeat
        startHeartbeat()
        
        // Log which protection layers are active
        logActiveProtectionLayers(layer1Active, layer2Active)
        
        return START_STICKY
    }
    
    /**
     * Logs which protection layers are currently active
     */
    private fun logActiveProtectionLayers(layer1Active: Boolean, layer2Active: Boolean) {
        val layer1Status = if (layer1Active && AccessibilityUtil.isAccessibilityServiceEnabled(this)) {
            "ACTIVE"
        } else if (layer1Active) {
            "INACTIVE (missing accessibility permission)"
        } else {
            "DISABLED"
        }
        
        val layer2Status = if (layer2Active) "ACTIVE" else "DISABLED"
        
        Log.d(TAG, "Protection Layer 1 (Accessibility): $layer1Status")
        Log.d(TAG, "Protection Layer 2 (Link Hook): $layer2Status")
    }
    
    /**
     * Starts a simple heartbeat to keep the service alive and log its status
     */
    private fun startHeartbeat() {
        Log.d(TAG, "Real-time protection service started")
        
        serviceScope.launch {
            while (isRunning) {
                try {
                    // Check current protection settings
                    val layer1Active = PreferencesManager.isLayer1Active(this@RealTimeProtectionService)
                    val layer2Active = PreferencesManager.isLayer2Active(this@RealTimeProtectionService)
                    
                    // Log active protection layers
                    logActiveProtectionLayers(layer1Active, layer2Active)
                    
                    // If no protection layers are active, stop the service
                    if (!layer1Active && !layer2Active) {
                        Log.d(TAG, "No protection layers are active, stopping service")
                        PreferencesManager.setProtectionActive(this@RealTimeProtectionService, false)
                        stopSelf()
                        break
                    }
                    delay(HEARTBEAT_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in protection service: ${e.message}")
                    delay(HEARTBEAT_INTERVAL)
                }
            }
        }
    }
    

    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel() // Cancel all coroutines
        
        // Release wake lock safely
        try {
            if (wakeLock != null && wakeLock!!.isHeld) {
                wakeLock!!.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }
        
        handler.removeCallbacksAndMessages(null)
    }
    
    companion object {
        const val ACTION_STOP = "com.example.phshing.service.STOP"
        const val NOTIFICATION_ID = 1001
        const val PERMISSION_NOTIFICATION_ID = 1002
        private const val TAG = "RealTimeProtection"
    }
}
