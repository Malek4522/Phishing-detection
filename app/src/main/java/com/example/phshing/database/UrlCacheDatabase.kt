package com.example.phshing.database

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * A simple database for caching URLs that have been scanned and approved
 * This provides faster lookups than using SharedPreferences directly
 */
object UrlCacheDatabase {
    private const val PREFS_NAME = "url_cache_db"
    private const val URL_CACHE_KEY_PREFIX = "url_cache_"
    
    /**
     * Cache a URL that has been scanned and approved
     */
    fun cacheUrl(context: Context, url: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = getUrlCacheKey(url)
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            // Silent error handling
        }
    }
    
    /**
     * Check if a URL is in the cache
     */
    fun isUrlCached(context: Context, url: String): Boolean {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = getUrlCacheKey(url)
            return prefs.contains(key)
        } catch (e: Exception) {
            // Silent error handling
            return false
        }
    }
    
    /**
     * Remove a URL from the cache
     */
    fun removeUrlFromCache(context: Context, url: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = getUrlCacheKey(url)
            prefs.edit().remove(key).apply()
        } catch (e: Exception) {
            // Silent error handling
        }
    }
    
    // clearCache method removed as Layer 2 is working well now
    
    /**
     * Get all cached URLs
     */
    fun getAllCachedUrls(context: Context): List<String> {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.all.keys
                .filter { it.startsWith(URL_CACHE_KEY_PREFIX) }
                .map { it.substring(URL_CACHE_KEY_PREFIX.length) }
        } catch (e: Exception) {
            // Silent error handling
            return emptyList()
        }
    }
    
    /**
     * Generate a cache key for a URL
     */
    private fun getUrlCacheKey(url: String): String {
        return URL_CACHE_KEY_PREFIX + url
    }
}
