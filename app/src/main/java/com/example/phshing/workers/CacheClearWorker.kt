package com.example.phshing.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.phshing.cache.UrlCacheDatabase

/**
 * WorkManager Worker class to handle periodic URL cache clearing
 */
class CacheClearWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "CacheClearWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Starting scheduled cache clearing task")
        
        try {
            // Clear the URL cache
            val cacheCleared = UrlCacheDatabase.clearCache(applicationContext)
            
            return if (cacheCleared) {
                Log.d(TAG, "URL cache cleared successfully")
                Result.success()
            } else {
                Log.e(TAG, "Failed to clear URL cache")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing URL cache: ${e.message}")
            return Result.failure()
        }
    }
}
