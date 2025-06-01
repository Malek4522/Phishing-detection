package com.example.phshing.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

/**
 * Database helper class for managing URL scan data
 */
class ScanDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "phishing_scan.db"
        private const val DATABASE_VERSION = 1

        // Table name
        const val TABLE_SCANS = "scans"

        // Column names
        const val COLUMN_ID = "id"
        const val COLUMN_URL = "url"
        const val COLUMN_IS_PHISHING = "is_phishing"
        const val COLUMN_SEVERITY = "severity"
        const val COLUMN_SCAN_DATE = "scan_date"
        const val COLUMN_TIMESTAMP = "timestamp"

        // Severity levels
        const val SEVERITY_CRITICAL = "critical"
        const val SEVERITY_HIGH = "high"
        const val SEVERITY_MEDIUM = "medium"
        const val SEVERITY_SAFE = "safe"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create the scans table
        val createTableQuery = "CREATE TABLE $TABLE_SCANS ("
            .plus("$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, ")
            .plus("$COLUMN_URL TEXT, ")
            .plus("$COLUMN_IS_PHISHING INTEGER, ")
            .plus("$COLUMN_SEVERITY TEXT, ")
            .plus("$COLUMN_SCAN_DATE TEXT, ")
            .plus("$COLUMN_TIMESTAMP INTEGER)")
        
        db.execSQL(createTableQuery)
        android.util.Log.d("ScanDatabaseHelper", "Database created with table: $TABLE_SCANS")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // On upgrade, drop the old table and recreate
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SCANS")
        onCreate(db)
    }
    
    /**
     * Clear all data from the database
     */
    fun clearAllData() {
        val db = this.writableDatabase
        try {
            // Begin transaction
            db.beginTransaction()
            
            // Delete all records
            val count = db.delete(TABLE_SCANS, null, null)
            
            // Mark transaction as successful
            db.setTransactionSuccessful()
            
            // Log the deletion
            android.util.Log.d("ScanDatabaseHelper", "Cleared database: $count records deleted")
        } catch (e: Exception) {
            android.util.Log.e("ScanDatabaseHelper", "Error clearing database: ${e.message}")
            e.printStackTrace()
        } finally {
            // End transaction
            db.endTransaction()
        }
    }

    /**
     * Add a new scan record to the database
     */
    fun addScan(scan: ScanRecord): Long {
        val db = this.writableDatabase
        
        try {
            // Begin transaction for better performance and reliability
            db.beginTransaction()
            
            val values = ContentValues().apply {
                put(COLUMN_URL, scan.url)
                put(COLUMN_IS_PHISHING, if (scan.isPhishing) 1 else 0)
                put(COLUMN_SEVERITY, scan.severity)
                put(COLUMN_SCAN_DATE, scan.scanDate)
                put(COLUMN_TIMESTAMP, scan.timestamp)
            }

            // Insert row
            val id = db.insert(TABLE_SCANS, null, values)
            
            // Mark transaction as successful
            db.setTransactionSuccessful()
            
            // Log the insertion
            android.util.Log.d("ScanDatabaseHelper", "Added scan: ID=$id, URL=${scan.url}, isPhishing=${scan.isPhishing}")
            
            return id
        } catch (e: Exception) {
            android.util.Log.e("ScanDatabaseHelper", "Error adding scan: ${e.message}")
            e.printStackTrace()
            return -1
        } finally {
            // End transaction
            db.endTransaction()
        }
    }

    /**
     * Get total count of scanned links
     */
    fun getTotalScannedCount(): Int {
        val db = this.readableDatabase
        var cursor: android.database.Cursor? = null
        var count = 0
        
        try {
            val query = "SELECT COUNT(*) FROM $TABLE_SCANS"
            cursor = db.rawQuery(query, null)
            
            if (cursor?.moveToFirst() == true) {
                count = cursor.getInt(0)
            }
            
            // Log the count for debugging
            android.util.Log.d("ScanDatabaseHelper", "Total scanned count: $count")
        } catch (e: Exception) {
            android.util.Log.e("ScanDatabaseHelper", "Error getting total count: ${e.message}")
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        
        return count
    }

    /**
     * Get count of phishing links detected
     */
    fun getPhishingCount(): Int {
        val db = this.readableDatabase
        val query = "SELECT COUNT(*) FROM $TABLE_SCANS WHERE $COLUMN_IS_PHISHING = 1"
        val cursor = db.rawQuery(query, null)
        var count = 0
        
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        db.close()
        return count
    }

    /**
     * Get count of safe links
     */
    fun getSafeLinksCount(): Int {
        val db = this.readableDatabase
        val query = "SELECT COUNT(*) FROM $TABLE_SCANS WHERE $COLUMN_IS_PHISHING = 0"
        val cursor = db.rawQuery(query, null)
        var count = 0
        
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }
    
    /**
     * Get all records from the database
     */
    fun getAllRecords(): List<ScanRecord> {
        val records = mutableListOf<ScanRecord>()
        val db = this.readableDatabase
        val query = "SELECT * FROM $TABLE_SCANS ORDER BY $COLUMN_TIMESTAMP DESC"
        
        val cursor = db.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val url = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_URL))
                val isPhishing = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_PHISHING)) == 1
                val severity = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SEVERITY))
                val scanDate = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SCAN_DATE))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                
                val record = ScanRecord(
                    id = id,
                    url = url,
                    isPhishing = isPhishing,
                    severity = severity,
                    scanDate = scanDate,
                    timestamp = timestamp
                )
                records.add(record)
            } while (cursor.moveToNext())
        }
        cursor.close()
        
        // Log the number of records found
        android.util.Log.d("ScanDatabaseHelper", "Retrieved ${records.size} records from database")
        
        return records
    }

    /**
     * Get count of links by severity
     */
    fun getCountBySeverity(severity: String): Int {
        val db = this.readableDatabase
        val query = "SELECT COUNT(*) FROM $TABLE_SCANS WHERE $COLUMN_SEVERITY = ?"
        val cursor = db.rawQuery(query, arrayOf(severity))
        var count = 0
        
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        db.close()
        return count
    }

    /**
     * Get count of links scanned today
     */
    fun getDailyScannedCount(): Int {
        val db = this.readableDatabase
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val query = "SELECT COUNT(*) FROM $TABLE_SCANS WHERE $COLUMN_SCAN_DATE LIKE '$today%'"
        val cursor = db.rawQuery(query, null)
        var count = 0
        
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        db.close()
        return count
    }

    /**
     * Get count of links scanned in the last 7 days
     */
    fun getWeeklyScannedCount(): Int {
        val db = this.readableDatabase
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekAgo = calendar.timeInMillis
        
        val query = "SELECT COUNT(*) FROM $TABLE_SCANS WHERE $COLUMN_TIMESTAMP >= $weekAgo"
        val cursor = db.rawQuery(query, null)
        var count = 0
        
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        db.close()
        return count
    }

    /**
     * Get percentage change in phishing links compared to previous period
     */
    fun getPhishingPercentageChange(severity: String): Int {
        val db = this.readableDatabase
        val calendar = Calendar.getInstance()
        
        // Current period (last 7 days)
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekAgo = calendar.timeInMillis
        
        // Previous period (7-14 days ago)
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val twoWeeksAgo = calendar.timeInMillis
        
        // Count for current period
        val currentQuery = "SELECT COUNT(*) FROM $TABLE_SCANS WHERE $COLUMN_SEVERITY = ? AND $COLUMN_TIMESTAMP >= $weekAgo"
        val currentCursor = db.rawQuery(currentQuery, arrayOf(severity))
        var currentCount = 0
        if (currentCursor.moveToFirst()) {
            currentCount = currentCursor.getInt(0)
        }
        currentCursor.close()
        
        // Count for previous period
        val previousQuery = "SELECT COUNT(*) FROM $TABLE_SCANS WHERE $COLUMN_SEVERITY = ? AND $COLUMN_TIMESTAMP >= $twoWeeksAgo AND $COLUMN_TIMESTAMP < $weekAgo"
        val previousCursor = db.rawQuery(previousQuery, arrayOf(severity))
        var previousCount = 0
        if (previousCursor.moveToFirst()) {
            previousCount = previousCursor.getInt(0)
        }
        previousCursor.close()
        
        // Calculate percentage change
        return if (previousCount > 0) {
            ((currentCount - previousCount) * 100) / previousCount
        } else if (currentCount > 0) {
            100 // If previous count was 0 and current count is positive, that's a 100% increase
        } else {
            0 // If both counts are 0, there's no change
        }
    }

    /**
     * Get the last scan timestamp
     */
    fun getLastScanTimestamp(): Long {
        val db = this.readableDatabase
        val query = "SELECT MAX($COLUMN_TIMESTAMP) FROM $TABLE_SCANS"
        val cursor = db.rawQuery(query, null)
        var timestamp = 0L
        
        if (cursor.moveToFirst() && !cursor.isNull(0)) {
            timestamp = cursor.getLong(0)
        }
        cursor.close()
        db.close()
        return timestamp
    }
}
