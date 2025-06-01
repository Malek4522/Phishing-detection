package com.example.phshing.cache

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Worker class for periodic cleanup of expired URLs in the cache
 * This is scheduled to run daily to keep the cache size manageable
 */
class CacheCleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "CacheCleanupWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting URL cache cleanup")
        
        try {
            // Clear expired URLs from the cache
            UrlCacheDatabase.clearExpiredUrls(applicationContext)
            
            // Log cache statistics
            val cacheSize = UrlCacheDatabase.getCachedUrlCount(applicationContext)
            Log.d(TAG, "Cache cleanup completed. Current cache size: $cacheSize URLs")
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup: ${e.message}")
            return Result.retry()
        }
    }
}
