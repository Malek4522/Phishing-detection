package com.example.phshing

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.phshing.api.ApiException
import com.example.phshing.api.ErrorType
import com.example.phshing.api.PhishingApiClient
import com.example.phshing.database.ScanDataManager
import com.example.phshing.database.ScanRecord
import com.example.phshing.utils.AppLinkHelper
import com.example.phshing.utils.NotificationHelper
import com.example.phshing.utils.PreferencesManager
import kotlinx.coroutines.launch

/**
 * Lightweight activity that intercepts clicked links, scans them, and shows a notification
 * without disrupting the user experience. This activity has no UI and finishes immediately
 * after processing the intent.
 */
class LinkInterceptorActivity : AppCompatActivity() {
    
    // Track URL handling attempts to detect and break loops
    companion object {
        private val urlHandlingCount = mutableMapOf<String, Int>()
        private const val MAX_HANDLING_COUNT = 2 // Maximum times to handle the same URL
    }

    private lateinit var scanDataManager: ScanDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize scan data manager
        scanDataManager = ScanDataManager(this)
        
        // Process the intent
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent) {
        // Check if this is a URL being opened from our WebView
        // If so, we should just finish to avoid an infinite loop
        val fromWebView = intent.getBooleanExtra(WebViewActivity.EXTRA_INTERNAL_WEBVIEW, false)
        if (fromWebView) {
            Log.d("LinkInterceptor", "URL from WebView, finishing to avoid loop")
            finish()
            return
        }
        
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && (uri.scheme == "http" || uri.scheme == "https")) {
                val url = uri.toString()
                
                // Increment and check the handling count for this URL to detect loops
                val count = urlHandlingCount.getOrDefault(url, 0) + 1
                urlHandlingCount[url] = count
                
                // Log for debugging
                Log.d("LinkInterceptor", "Handling URL: $url (attempt $count)")
                Log.d("LinkInterceptor", "Layer2 active: ${PreferencesManager.isLayer2Active(this)}")
                Log.d("LinkInterceptor", "Both layers active: ${PreferencesManager.areBothLayersActive(this)}")
                
                // If we've handled this URL too many times, force open in Chrome to break the loop
                if (count > MAX_HANDLING_COUNT) {
                    Log.d("LinkInterceptor", "Detected loop for URL: $url, forcing Chrome")
                    // Force Chrome directly
                    try {
                        val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        chromeIntent.setPackage("com.android.chrome")
                        chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(chromeIntent)
                        Log.d("LinkInterceptor", "Forced URL open with Chrome")
                    } catch (e: Exception) {
                        Log.e("LinkInterceptor", "Failed to force Chrome: ${e.message}")
                        // Only use WebView as absolute last resort
                        openInWebView(url)
                    }
                    finish()
                    return
                }
                
                // Check if this URL was already approved by the user
                // Only skip scanning for exact URL matches with SHA-256 hash verification
                val exactMatch = PreferencesManager.isUrlApproved(this, url)
                if (exactMatch) {
                    // User already approved this exact URL, just open it without scanning
                    Log.d("LinkInterceptor", "URL is approved with secure hash verification, opening directly: $url")
                    // Always try to use Chrome or another browser, not WebView
                    openUrlInBrowser(url)
                    finish()
                    return
                } else {
                    // URL not approved or hash verification failed, scan it
                    Log.d("LinkInterceptor", "URL not in approved list or hash verification failed, will be scanned: $url")
                }
                
                // Check if Layer 2 is active
                val layer2Active = PreferencesManager.isLayer2Active(this)
                val bothLayersActive = PreferencesManager.areBothLayersActive(this)
                
                if (layer2Active || bothLayersActive) {
                    // Process the URL
                    Log.d("LinkInterceptor", "Layer 2 is active, processing URL")
                    processUrl(uri)
                } else {
                    // Layer 2 is not active, just open the URL in browser
                    Log.d("LinkInterceptor", "Layer 2 is NOT active, opening URL directly")
                    
                    // If we're the default browser but Layer 2 is off, we should
                    // just pass the URL to another browser
                    if (AppLinkHelper.isDefaultLinkHandler(this)) {
                        // We're the default browser but Layer 2 is off
                        // Show a toast explaining why we're skipping scanning
                        Toast.makeText(
                            this,
                            "Layer 2 protection is disabled. Opening URL in browser.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    openUrlInBrowser(url)
                    finish()
                }
            } else {
                // Not a web URL, finish the activity
                Log.d("LinkInterceptor", "Not a web URL, finishing activity")
                finish()
            }
        } else {
            // Not a VIEW action, finish the activity
            Log.d("LinkInterceptor", "Not a VIEW action, finishing activity")
            finish()
        }
    }
    
    private fun processUrl(uri: Uri) {
        val url = uri.toString()
        
        // Show a brief toast to indicate scanning
        Toast.makeText(this, "Redirecting to main app...", Toast.LENGTH_SHORT).show()
        Log.d("LinkInterceptor", "Delegating URL handling to MainContainerActivity: $url")
        
        // Instead of handling the URL here, delegate to MainContainerActivity
        val mainIntent = Intent(this, MainContainerActivity::class.java)
        mainIntent.action = Intent.ACTION_VIEW
        mainIntent.data = uri
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(mainIntent)
        
        // Finish this activity immediately to prevent loops
        finish()
    }
    
    /**
     * Opens a URL in our internal WebView to avoid infinite loops
     */
    private fun openInWebView(url: String) {
        Log.d("LinkInterceptor", "Opening URL in WebView: $url")
        
        // Mark this URL as approved to prevent future scanning
        PreferencesManager.addApprovedUrl(this@LinkInterceptorActivity, url)
        
        // Open in our WebView activity
        val intent = Intent(this@LinkInterceptorActivity, WebViewActivity::class.java)
        intent.putExtra("url", url)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this@LinkInterceptorActivity.startActivity(intent)
    }
    
    /**
     * Opens a URL in the browser
     */
    private fun openUrlInBrowser(url: String) {
        // Add the URL to a set of approved URLs in preferences
        // This will prevent re-scanning when the user has explicitly chosen to open it
        PreferencesManager.addApprovedUrl(this@LinkInterceptorActivity, url)
        
        // Check if we're the default browser
        if (AppLinkHelper.isDefaultLinkHandler(this@LinkInterceptorActivity)) {
            Log.d("LinkInterceptor", "We are the default browser, using alternative method to open URL")
            // We're the default browser, so we need to use a different approach
            // to avoid an infinite loop
            
            // Try Chrome first
            try {
                val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                chromeIntent.setPackage("com.android.chrome")
                chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this@LinkInterceptorActivity.startActivity(chromeIntent)
                Log.d("LinkInterceptor", "Opened URL with Chrome")
                return
            } catch (e: Exception) {
                Log.e("LinkInterceptor", "Failed to open with Chrome: ${e.message}")
                // Chrome not available, try other browsers
            }
            
            // Try to find an explicit browser package
            val browserPackages = getBrowserPackages()
            if (browserPackages.isNotEmpty()) {
                // Use the first available browser that isn't our app
                for (packageName in browserPackages) {
                    if (packageName != this@LinkInterceptorActivity.packageName) {
                        try {
                            val explicitIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            explicitIntent.setPackage(packageName)
                            explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            this@LinkInterceptorActivity.startActivity(explicitIntent)
                            Log.d("LinkInterceptor", "Opened URL with explicit browser: $packageName")
                            return
                        } catch (e: Exception) {
                            Log.e("LinkInterceptor", "Failed to open with browser: $packageName", e)
                            // Try the next browser
                        }
                    }
                }
            }
            
            // If we couldn't find a browser or all attempts failed, try one more time with Chrome
            Log.d("LinkInterceptor", "No other browsers found, trying Chrome specifically")
            
            // Try Chrome again with a more direct approach
            try {
                val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                chromeIntent.setPackage("com.android.chrome")
                chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this@LinkInterceptorActivity.startActivity(chromeIntent)
                Log.d("LinkInterceptor", "Opened URL with Chrome (direct attempt)")
                return
            } catch (e: Exception) {
                Log.e("LinkInterceptor", "Failed direct Chrome attempt: ${e.message}")
            }
            
            // Try Chrome variants (some devices use different package names)
            val chromeVariants = arrayOf(
                "com.chrome.beta",
                "com.chrome.dev",
                "com.chrome.canary",
                "com.google.android.apps.chrome"
            )
            
            for (chromePackage in chromeVariants) {
                try {
                    val variantIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    variantIntent.setPackage(chromePackage)
                    variantIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    this@LinkInterceptorActivity.startActivity(variantIntent)
                    Log.d("LinkInterceptor", "Opened URL with Chrome variant: $chromePackage")
                    return
                } catch (e: Exception) {
                    Log.e("LinkInterceptor", "Failed Chrome variant: $chromePackage")
                }
            }
            
            // Create a chooser intent as fallback
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val chooser = Intent.createChooser(intent, "Open with")
                this@LinkInterceptorActivity.startActivity(chooser)
                Log.d("LinkInterceptor", "Opened URL with chooser dialog")
            } catch (e: Exception) {
                Log.e("LinkInterceptor", "Failed to open chooser: ${e.message}")
                
                // Absolute last resort: WebView
                try {
                    val webViewIntent = Intent(this@LinkInterceptorActivity, WebViewActivity::class.java)
                    webViewIntent.putExtra("url", url)
                    webViewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    this@LinkInterceptorActivity.startActivity(webViewIntent)
                    Log.d("LinkInterceptor", "Opened URL in WebViewActivity")
                } catch (e: Exception) {
                    Log.e("LinkInterceptor", "Failed to open WebViewActivity", e)
                    Toast.makeText(this@LinkInterceptorActivity, "Please install Chrome or another browser", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // We're not the default browser, so we can just use the normal approach
            Log.d("LinkInterceptor", "We are NOT the default browser, using standard method to open URL")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            this@LinkInterceptorActivity.startActivity(browserIntent)
        }
    }
    
    /**
     * Shows a warning dialog when a phishing URL is detected
     */
    private fun showPhishingWarningDialog(url: String, confidence: Double) {
        val riskPercent = (confidence * 100).toInt().coerceIn(1, 100)
        
        // Create and launch intent for MainContainerActivity to handle the dialog
        val dialogIntent = Intent(this@LinkInterceptorActivity, MainContainerActivity::class.java)
        dialogIntent.action = "com.example.phshing.ACTION_SHOW_PHISHING_WARNING"
        dialogIntent.putExtra("url", url)
        dialogIntent.putExtra("confidence", confidence)
        dialogIntent.putExtra("autoScan", true)
        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this@LinkInterceptorActivity.startActivity(dialogIntent)
    }
    
    /**
     * Get a list of installed browser packages
     */
    private fun getBrowserPackages(): List<String> {
        val browsers = ArrayList<String>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
        val resolveInfoList = this@LinkInterceptorActivity.packageManager.queryIntentActivities(intent, 0)
        
        // Use explicit iterator to avoid ambiguity
        val iterator = resolveInfoList.iterator()
        while (iterator.hasNext()) {
            val resolveInfo = iterator.next()
            val packageName = resolveInfo.activityInfo.packageName
            // Skip our own package
            if (packageName != this@LinkInterceptorActivity.packageName) {
                browsers.add(packageName)
                Log.d("LinkInterceptor", "Found browser: $packageName")
            }
        }
        
        return browsers
    }
}
