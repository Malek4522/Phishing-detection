package com.example.phshing.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.phshing.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * AccessibilityService that monitors screen content for URLs and checks them for phishing
 */
class UrlAccessibilityService : AccessibilityService() {

    companion object {
        // Action for broadcasting scan results
        const val ACTION_URL_SCANNED = "com.example.phshing.URL_SCANNED"
        const val EXTRA_URL = "url"
        const val EXTRA_IS_PHISHING = "is_phishing"
        const val EXTRA_CONFIDENCE = "confidence"
        
        // URL detection patterns
        private val HTTP_URL_PATTERN = Pattern.compile("https?://[\\w\\.-]+\\.[a-zA-Z]{2,}[\\w\\.-/]*")
        private val WWW_URL_PATTERN = Pattern.compile("www\\.[\\w\\.-]+\\.[a-zA-Z]{2,}[\\w\\.-/]*")
        
        // Domain pattern for common TLDs
        private val DOMAIN_URL_PATTERN = Pattern.compile(
            "[\\w\\.-]+\\.(" + // Domain name part
            "com|net|org|edu|gov|io|app|co|me|" + // Common TLDs
            "info|biz|tv|mobi|" + // Less common TLDs
            "[a-zA-Z]{2,})" + // Country codes and other TLDs
            "[\\w\\.-/]*" // Path after domain
        )
        
        // Pattern for href attributes in HTML/markdown
        private val HREF_PATTERN = Pattern.compile("href=[\"'](https?://[^\"']+|www\\.[^\"']+|[\\w\\.-]+\\.[a-zA-Z]{2,}[^\"']*)[\"']")
        
        // Package names to exclude from detection
        private const val OWN_PACKAGE = "com.example.phshing"
        
        // List of common browser package names to skip
        private val BROWSER_PACKAGES = listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.touch",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser",
            "com.amazon.cloud9",
            "com.UCMobile.intl",
            "com.android.browser",
            "com.huawei.browser",
            "com.mi.globalbrowser",
            "mark.via.gp"
        )
        
        // Set to track recently checked URLs to avoid duplicates
        private val recentlyCheckedUrls = mutableSetOf<String>()
        private const val MAX_RECENT_URLS = 100 // Cache size
    }
    
    // Coroutine scope for background tasks
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // Last detected URL to avoid repeated checks
    private var lastDetectedUrl: String? = null
    
    // Email pattern to exclude from URL detection
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    
    override fun onServiceConnected() {
        // Service connected
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Skip events from our own package
        if (packageName == OWN_PACKAGE) {
            return
        }
        
        // Skip events from browser apps
        if (BROWSER_PACKAGES.contains(packageName)) {
            return
        }
        
        // Process content changes
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            processAccessibilityEvent(event)
        }
    }
    
    /**
     * Processes an accessibility event
     */
    private fun processAccessibilityEvent(event: AccessibilityEvent) {
        // Get the source node
        val source = event.source ?: return
        
        try {
            // Scan the node tree for URLs
            scanNodeTreeForUrls(source)
        } finally {
            // Always recycle the node to prevent memory leaks
            try {
                @Suppress("DEPRECATION")
                source.recycle()
            } catch (e: Exception) {
                // Error recycling node
            }
        }
    }
    
    override fun onInterrupt() {
        // Service interrupted
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel all coroutines when service is destroyed
        serviceJob.cancel()
    }
    
    /**
     * Recursively scans the node tree for text containing URLs
     */
    private fun scanNodeTreeForUrls(node: AccessibilityNodeInfo?) {
        if (node == null) return
        
        // Check if this node has text
        val nodeText = node.text?.toString() ?: ""
        if (nodeText.isNotEmpty()) {
            extractAndCheckUrls(nodeText)
        }
        
        // Check content description (often contains URLs in buttons and images)
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (contentDesc.isNotEmpty()) {
            extractAndCheckUrls(contentDesc)
        }
        
        // Check for clickable elements that might be buttons with URLs
        if (node.isClickable) {
            // Extract any URLs from the node's properties
            val clickableText = node.text?.toString() ?: ""
            if (clickableText.isNotEmpty()) {
                extractAndCheckUrls(clickableText)
            }
            
            // Check if this is a link or button with URL
            val className = node.className?.toString() ?: ""
            if (className.contains("Button") || 
                className.contains("Link") || 
                className.contains("Hyperlink")) {
                // Extract any URLs from child nodes
                for (i in 0 until node.childCount) {
                    val childNode = node.getChild(i)
                    if (childNode != null) {
                        val childText = childNode.text?.toString() ?: ""
                        if (childText.isNotEmpty()) {
                            extractAndCheckUrls(childText)
                        }
                        try {
                            @Suppress("DEPRECATION")
                            childNode.recycle()
                        } catch (e: Exception) {
                            // Error recycling child node
                        }
                    }
                }
            }
        }
        
        // Check child nodes
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                try {
                    scanNodeTreeForUrls(childNode)
                } finally {
                    // Always recycle child nodes
                    try {
                        @Suppress("DEPRECATION")
                        childNode.recycle()
                    } catch (e: Exception) {
                        // Error recycling child node
                    }
                }
            }
        }
    }
    
    /**
     * Extracts URLs from text and checks them for phishing
     */
    private fun extractAndCheckUrls(text: String) {
        val detectedUrls = mutableSetOf<String>()
        
        try {
            // Replace newlines with spaces to ensure URLs don't get broken across lines
            val processedText = text.replace("\n", " ").replace("\r", " ")
            
            // Find all email addresses to exclude them
            val emailMatches = mutableSetOf<String>()
            val emailMatcher = EMAIL_PATTERN.matcher(processedText)
            while (emailMatcher.find()) {
                emailMatches.add(emailMatcher.group())
            }
            
            // Check for href attributes in HTML/markdown
            val hrefMatcher = HREF_PATTERN.matcher(processedText)
            while (hrefMatcher.find()) {
                // Extract the URL from the href attribute
                val fullMatch = hrefMatcher.group()
                val url = fullMatch.substring(fullMatch.indexOf('=') + 2, fullMatch.length - 1)
                
                // Skip if it's an email address
                if (url.contains("@") || emailMatches.any { url.contains(it) }) {
                    continue
                }
                
                // Add appropriate prefix if needed
                if (url.startsWith("http")) {
                    detectedUrls.add(url)
                } else if (url.startsWith("www.")) {
                    detectedUrls.add("http://$url")
                } else if (url.contains(".")) {
                    detectedUrls.add("http://$url")
                }
            }
            
            // Check for HTTP/HTTPS URLs
            val httpMatcher = HTTP_URL_PATTERN.matcher(processedText)
            while (httpMatcher.find()) {
                val url = httpMatcher.group()
                // Skip if it's an email address
                if (url.contains("@") || emailMatches.any { url.contains(it) }) {
                    continue
                }
                detectedUrls.add(url)
            }
            
            // Check for WWW URLs and add http:// prefix
            val wwwMatcher = WWW_URL_PATTERN.matcher(processedText)
            while (wwwMatcher.find()) {
                val url = wwwMatcher.group()
                // Skip if it's an email address
                if (url.contains("@") || emailMatches.any { url.contains(it) }) {
                    continue
                }
                if (!url.startsWith("http")) {
                    detectedUrls.add("http://$url")
                }
            }
            
            // Check for domain URLs and add http:// prefix
            val domainMatcher = DOMAIN_URL_PATTERN.matcher(processedText)
            while (domainMatcher.find()) {
                val url = domainMatcher.group()
                // Skip if it's an email address
                if (url.contains("@") || emailMatches.any { url.contains(it) }) {
                    continue
                }
                if (!url.startsWith("http") && !url.startsWith("www.")) {
                    detectedUrls.add("http://$url")
                }
            }
        } catch (e: Exception) {
            // Error extracting URLs
        }
        
        // Process each unique URL found in this text
        for (url in detectedUrls) {
            // Skip if this URL was recently checked
            if (url == lastDetectedUrl || recentlyCheckedUrls.contains(url)) {
                continue
            }
            
            // Update last detected URL and add to recently checked
            lastDetectedUrl = url
            addToRecentlyChecked(url)
            
            // For testing: directly show notification without API call
            showTestNotification(url)
            
            // Broadcast the detection (for testing)
            broadcastTestResult(url)
        }
    }
    
    /**
     * Adds a URL to the recently checked set, maintaining max size
     */
    private fun addToRecentlyChecked(url: String) {
        synchronized(recentlyCheckedUrls) {
            // Remove oldest URL if at capacity
            if (recentlyCheckedUrls.size >= MAX_RECENT_URLS) {
                recentlyCheckedUrls.iterator().let {
                    if (it.hasNext()) it.next(); it.remove()
                }
            }
            recentlyCheckedUrls.add(url)
        }
    }
    
    /**
     * Shows a test notification for a detected URL (without API call)
     */
    private fun showTestNotification(url: String) {
        // Create and show a notification
        val notification = NotificationHelper.createPhishingAlertNotification(
            this,
            url,
            "URL detected for testing purposes"
        ).build()
        
        NotificationHelper.showNotification(
            this,
            NotificationHelper.NOTIFICATION_ID_PHISHING_ALERT,
            notification
        )
    }
    
    /**
     * Broadcasts the test result to the app (without API call)
     */
    private fun broadcastTestResult(url: String) {
        val intent = Intent(ACTION_URL_SCANNED).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_IS_PHISHING, true) // Always true for testing
            putExtra(EXTRA_CONFIDENCE, 0.95) // High confidence for testing
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}
