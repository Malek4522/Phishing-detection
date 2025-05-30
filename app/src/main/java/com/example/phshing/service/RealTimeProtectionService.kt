package com.example.phshing.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
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
        
        // Save the protection status as active
        PreferencesManager.setProtectionActive(this, true)
        
        // Get the notification from NotificationHelper
        val notification = NotificationHelper.createProtectionNotification(this, true).build()
        
        // Start as a foreground service with persistent notification
        startForeground(NotificationHelper.NOTIFICATION_ID_PROTECTION_ACTIVE, notification)
        
        // Start the service heartbeat
        startHeartbeat()
        
        return START_STICKY
    }
    
    /**
     * Starts a simple heartbeat to keep the service alive and log its status
     */
    private fun startHeartbeat() {
        Log.d(TAG, "Real-time protection service started")
        
        serviceScope.launch {
            while (isRunning) {
                try {
                    Log.d(TAG, "Protection service is active")
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
        const val ACTION_STOP = "com.example.phshing.STOP_PROTECTION"
        private const val TAG = "RealTimeProtection"
    }
}
