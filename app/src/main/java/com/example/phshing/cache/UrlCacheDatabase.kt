package com.example.phshing.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.util.LruCache
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

/**
 * Unified URL caching system for both Layer 1 and Layer 2 protection
 * Combines an in-memory LRU cache for fast lookups with a SQLite database for persistence
 */
object UrlCacheDatabase {
    private const val TAG = "UrlCacheDatabase"
    
    // In-memory cache for very fast lookups (shared between layers)
    private val memoryCache = LruCache<String, CacheEntry>(300) // 300 most recent URLs
    
    // Database helper for persistent storage
    private var dbHelper: UrlCacheDatabaseHelper? = null
    
    // Cache entry data class
    private data class CacheEntry(
        val url: String,
        val expiryTime: Long
    )
    
    /**
     * Initialize the cache database
     * Must be called before using any other methods
     */
    fun initialize(context: Context) {
        if (dbHelper == null) {
            dbHelper = UrlCacheDatabaseHelper(context.applicationContext)
            // Pre-load recent URLs from DB into memory cache for faster access
            preloadRecentUrls(context)
            Log.d(TAG, "URL cache database initialized")
        }
    }
    
    /**
     * Check if a URL is in the cache and not expired
     * @return true if the URL is cached and not expired
     */
    fun isUrlCached(context: Context, url: String): Boolean {
        ensureInitialized(context)
        val urlHash = hashUrl(url)
        val currentTime = System.currentTimeMillis()
        
        // First check memory cache (fastest)
        memoryCache.get(urlHash)?.let { entry ->
            if (entry.expiryTime > currentTime) {
                Log.d(TAG, "URL found in memory cache: $url")
                return true
            } else {
                // Expired, remove from memory cache
                memoryCache.remove(urlHash)
            }
        }
        
        // Then check database
        val helper = dbHelper ?: return false
        val isCached = helper.isUrlCached(urlHash, url, currentTime)
        
        if (isCached) {
            Log.d(TAG, "URL found in database cache: $url")
            // Add back to memory cache for faster future lookups
            val expiryTime = helper.getUrlExpiryTime(urlHash)
            if (expiryTime > currentTime) {
                memoryCache.put(urlHash, CacheEntry(url, expiryTime))
            }
        }
        
        return isCached
    }
    
    /**
     * Add a URL to the cache with specified expiration time
     * @param expiryHours Number of hours until the URL expires (default: 24)
     */
    fun cacheUrl(context: Context, url: String, expiryHours: Int = 24) {
        ensureInitialized(context)
        val urlHash = hashUrl(url)
        val expiryTime = System.currentTimeMillis() + (expiryHours * 60 * 60 * 1000L)
        
        // Add to memory cache
        memoryCache.put(urlHash, CacheEntry(url, expiryTime))
        
        // Add to persistent cache
        dbHelper?.addCachedUrl(urlHash, url, expiryTime)
        
        Log.d(TAG, "URL added to cache: $url (expires in $expiryHours hours)")
    }
    
    /**
     * Clear expired URLs from the cache
     * Should be called periodically (e.g., daily)
     */
    fun clearExpiredUrls(context: Context) {
        ensureInitialized(context)
        val currentTime = System.currentTimeMillis()
        
        // Clear from database
        val count = dbHelper?.clearExpiredUrls(currentTime) ?: 0
        
        // Clear from memory cache (iterate through and remove expired)
        val keysToRemove = mutableListOf<String>()
        for (i in 0 until memoryCache.size()) {
            val key = memoryCache.snapshot().keys.elementAtOrNull(i) ?: continue
            val entry = memoryCache.get(key) ?: continue
            
            if (entry.expiryTime <= currentTime) {
                keysToRemove.add(key)
            }
        }
        
        keysToRemove.forEach { memoryCache.remove(it) }
        
        Log.d(TAG, "Cleared $count expired URLs from database and ${keysToRemove.size} from memory cache")
    }
    
    /**
     * Clear all cached URLs
     */
    fun clearAllUrls(context: Context) {
        ensureInitialized(context)
        
        // Clear memory cache
        memoryCache.evictAll()
        
        // Clear database
        dbHelper?.clearAllUrls()
        
        Log.d(TAG, "Cleared all cached URLs")
    }
    
    /**
     * Get the number of URLs in the cache
     */
    fun getCachedUrlCount(context: Context): Int {
        ensureInitialized(context)
        return dbHelper?.getUrlCount() ?: 0
    }
    
    /**
     * Generate a SHA-256 hash for a URL
     */
    private fun hashUrl(url: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Clear all URL caches (both in-memory and database)
     * This will remove all cached URLs from both the memory cache and the database
     */
    fun clearAllCaches(context: Context) {
        ensureInitialized(context)
        
        // Clear the in-memory cache
        memoryCache.evictAll()
        
        // Clear the database using the helper
        dbHelper?.clearAllUrls()
        
        Log.d(TAG, "All URL caches cleared successfully")
    }
    
    /**
     * Clear the URL cache (alias for clearAllCaches)
     * @return true if the cache was cleared successfully
     */
    fun clearCache(context: Context): Boolean {
        try {
            clearAllCaches(context)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing URL cache: ${e.message}")
            return false
        }
    }
    
    /**
     * Preload recent URLs from the database into memory cache
     */
    private fun preloadRecentUrls(context: Context) {
        val currentTime = System.currentTimeMillis()
        val recentUrls = dbHelper?.getRecentUrls(200) ?: return
        
        var loadedCount = 0
        recentUrls.forEach { (hash, url, expiryTime) ->
            if (expiryTime > currentTime) {
                memoryCache.put(hash, CacheEntry(url, expiryTime))
                loadedCount++
            }
        }
        
        Log.d(TAG, "Preloaded $loadedCount recent URLs into memory cache")
    }
    
    /**
     * Ensure the database is initialized
     */
    private fun ensureInitialized(context: Context) {
        if (dbHelper == null) {
            initialize(context)
        }
    }
    
    /**
     * SQLite helper for the URL cache database
     */
    private class UrlCacheDatabaseHelper(context: Context) : 
            SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        
        companion object {
            private const val DATABASE_NAME = "url_cache.db"
            private const val DATABASE_VERSION = 1
            
            private const val TABLE_CACHED_URLS = "cached_urls"
            private const val COLUMN_HASH = "hash"
            private const val COLUMN_URL = "url"
            private const val COLUMN_EXPIRY = "expiry_time"
            private const val COLUMN_TIMESTAMP = "timestamp"
        }
        
        override fun onCreate(db: SQLiteDatabase) {
            val createTable = """
                CREATE TABLE $TABLE_CACHED_URLS (
                    $COLUMN_HASH TEXT PRIMARY KEY,
                    $COLUMN_URL TEXT,
                    $COLUMN_EXPIRY INTEGER,
                    $COLUMN_TIMESTAMP INTEGER
                )
            """.trimIndent()
            db.execSQL(createTable)
            
            // Create index for faster lookups
            db.execSQL("CREATE INDEX idx_hash ON $TABLE_CACHED_URLS ($COLUMN_HASH)")
            
            Log.d(TAG, "Created URL cache database table")
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CACHED_URLS")
            onCreate(db)
        }
        
        /**
         * Add URL to cache
         */
        fun addCachedUrl(hash: String, url: String, expiryTime: Long) {
            val db = writableDatabase
            
            try {
                val values = ContentValues().apply {
                    put(COLUMN_HASH, hash)
                    put(COLUMN_URL, url)
                    put(COLUMN_EXPIRY, expiryTime)
                    put(COLUMN_TIMESTAMP, System.currentTimeMillis())
                }
                
                // Insert with REPLACE strategy to handle duplicates
                db.insertWithOnConflict(
                    TABLE_CACHED_URLS, 
                    null, 
                    values, 
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error adding URL to cache: ${e.message}")
            }
        }
        
        /**
         * Check if URL is cached and not expired
         */
        fun isUrlCached(hash: String, url: String, currentTime: Long): Boolean {
            val db = readableDatabase
            
            try {
                val query = """
                    SELECT $COLUMN_URL FROM $TABLE_CACHED_URLS 
                    WHERE $COLUMN_HASH = ? AND $COLUMN_EXPIRY > ?
                """.trimIndent()
                
                db.rawQuery(query, arrayOf(hash, currentTime.toString())).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val storedUrl = cursor.getString(0)
                        // Double-check URL to prevent hash collisions
                        return url == storedUrl
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if URL is cached: ${e.message}")
            }
            
            return false
        }
        
        /**
         * Get URL expiry time
         */
        fun getUrlExpiryTime(hash: String): Long {
            val db = readableDatabase
            
            try {
                val query = "SELECT $COLUMN_EXPIRY FROM $TABLE_CACHED_URLS WHERE $COLUMN_HASH = ?"
                
                db.rawQuery(query, arrayOf(hash)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getLong(0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting URL expiry time: ${e.message}")
            }
            
            return 0
        }
        
        /**
         * Get recent URLs for preloading
         */
        fun getRecentUrls(limit: Int): List<Triple<String, String, Long>> {
            val db = readableDatabase
            val results = mutableListOf<Triple<String, String, Long>>()
            
            try {
                val query = """
                    SELECT $COLUMN_HASH, $COLUMN_URL, $COLUMN_EXPIRY FROM $TABLE_CACHED_URLS 
                    ORDER BY $COLUMN_TIMESTAMP DESC LIMIT ?
                """.trimIndent()
                
                db.rawQuery(query, arrayOf(limit.toString())).use { cursor ->
                    while (cursor.moveToNext()) {
                        val hash = cursor.getString(0)
                        val url = cursor.getString(1)
                        val expiryTime = cursor.getLong(2)
                        results.add(Triple(hash, url, expiryTime))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting recent URLs: ${e.message}")
            }
            
            return results
        }
        
        /**
         * Clear expired URLs
         * @return Number of URLs removed
         */
        fun clearExpiredUrls(currentTime: Long): Int {
            val db = writableDatabase
            
            try {
                return db.delete(
                    TABLE_CACHED_URLS, 
                    "$COLUMN_EXPIRY <= ?", 
                    arrayOf(currentTime.toString())
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing expired URLs: ${e.message}")
            }
            
            return 0
        }
        
        /**
         * Clear all URLs
         */
        fun clearAllUrls() {
            val db = writableDatabase
            
            try {
                db.delete(TABLE_CACHED_URLS, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing all URLs: ${e.message}")
            }
        }
        
        /**
         * Get the number of URLs in the cache
         */
        fun getUrlCount(): Int {
            val db = readableDatabase
            
            try {
                val query = "SELECT COUNT(*) FROM $TABLE_CACHED_URLS"
                
                db.rawQuery(query, null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getInt(0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting URL count: ${e.message}")
            }
            
            return 0
        }
    }
}
