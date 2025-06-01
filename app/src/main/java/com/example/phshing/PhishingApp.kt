package com.example.phshing

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.phshing.cache.CacheCleanupWorker
import com.example.phshing.cache.UrlCacheDatabase
import java.util.concurrent.TimeUnit

/**
 * Application class for the Phishing Detector app
 * Handles initialization of app-wide components
 */
class PhishingApp : Application() {

    companion object {
        private const val TAG = "PhishingDetectorApp"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize URL cache database
        UrlCacheDatabase.initialize(this)
        Log.d(TAG, "URL cache system initialized")
        
        // Schedule daily cache cleanup
        scheduleCacheCleanup()
    }
    
    /**
     * Schedule periodic cleanup of the URL cache
     * Runs once per day to remove expired URLs
     */
    private fun scheduleCacheCleanup() {
        val workManager = WorkManager.getInstance(this)
        
        // Create a periodic work request that runs once per day
        val cleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(
            1, TimeUnit.DAYS
        ).build()
        
        // Enqueue the work request, replacing any existing one
        workManager.enqueueUniquePeriodicWork(
            "url_cache_cleanup",
            ExistingPeriodicWorkPolicy.REPLACE,
            cleanupRequest
        )
        
        Log.d(TAG, "Scheduled daily URL cache cleanup")
    }
}
