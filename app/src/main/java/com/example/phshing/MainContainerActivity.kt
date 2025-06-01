package com.example.phshing

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.phshing.api.ApiException
import com.example.phshing.api.ErrorType
import com.example.phshing.api.PhishingApiClient
import com.example.phshing.database.ScanRecord
import com.example.phshing.database.ScanDataManager
import com.example.phshing.database.UrlCacheDatabase
import com.example.phshing.fragments.DashboardFragment
import com.example.phshing.fragments.SettingsFragment
import com.example.phshing.fragments.UrlCheckFragment
import com.example.phshing.utils.AppLinkHelper
import com.example.phshing.utils.PreferencesManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Main container activity that hosts all fragments and handles universal link interception (Layer 2)
 */
class MainContainerActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var scanDataManager: ScanDataManager
    
    // Track URL handling count to prevent infinite loops
    private val urlHandlingCount = ConcurrentHashMap<String, Int>()
    private val MAX_HANDLING_COUNT = 3
    
    // Broadcast receiver for Layer 1 URL scan results
    private val urlScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val url = intent.getStringExtra("url") ?: return
            val isPhishing = intent.getBooleanExtra("is_phishing", false)
            val confidence = intent.getDoubleExtra("confidence", 0.0)
            
            Log.d(TAG, "Received URL scan result from Layer 1: $url, isPhishing: $isPhishing, confidence: $confidence")
            
            // If it's a phishing URL, show the URL check fragment with the result
            if (isPhishing) {
                // Navigate to URL check fragment and pass the URL
                val fragment = UrlCheckFragment.newInstance(url, true)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
                
                // Update bottom navigation to show URL check tab as selected
                bottomNavigationView.selectedItemId = R.id.navigation_url_check
            }
        }
    }

    companion object {
        private const val TAG = "MainContainerActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_container)

        // Initialize scan data manager
        scanDataManager = ScanDataManager(this)

        // Initialize bottom navigation
        bottomNavigationView = findViewById(R.id.bottom_navigation)
        setupBottomNavigation()

        // Handle intent if activity was launched with one
        handleIntent(intent)

        // Check if we're the default link handler
        checkDefaultLinkHandlerStatus()
    }

    override fun onResume() {
        super.onResume()
        
        // Register broadcast receiver for Layer 1 URL scan results
        LocalBroadcastManager.getInstance(this).registerReceiver(
            urlScanReceiver,
            IntentFilter("com.example.phshing.URL_SCANNED")
        )
        
        // Check if we should prompt for default browser role
        checkDefaultLinkHandlerStatus()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister broadcast receiver
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(urlScanReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    /**
     * Checks if our app is the default link handler and prompts the user if not
     */
    private fun checkDefaultLinkHandlerStatus() {
        // Only check if Layer 2 protection is enabled
        if (!PreferencesManager.isLayer2Active(this)) {
            Log.d("MainContainer", "Layer 2 protection is disabled, skipping default browser check")
            return
        }

        // Check if we've shown the default browser prompt before
        if (PreferencesManager.hasShownDefaultBrowserPrompt(this)) {
            // We've shown it before, check if we should show it again
            val currentTime = System.currentTimeMillis()
            val lastPromptTime = PreferencesManager.getLastDefaultBrowserPromptTime(this)
            val dayInMillis = 24 * 60 * 60 * 1000L

            // Only show the prompt once per day
            if ((currentTime - lastPromptTime) < dayInMillis) {
                Log.d("MainContainer", "Skipping default browser prompt, already shown today")
                return
            }
        }

        // Check if we're the default link handler
        if (!AppLinkHelper.isDefaultLinkHandler(this)) {
            // We're not the default link handler, show a dialog
            AlertDialog.Builder(this)
                .setTitle("Set as Default Browser")
                .setMessage("For optimal phishing protection, please set this app as your default browser. This will allow us to scan links before they're opened.")
                .setPositiveButton("Set as Default") { _, _ ->
                    // Update the last prompt time
                    PreferencesManager.setLastDefaultBrowserPromptTime(this, System.currentTimeMillis())
                    PreferencesManager.setHasShownDefaultBrowserPrompt(this, true)
                    
                    // Open the default apps settings
                    requestDefaultBrowser()
                }
                .setNegativeButton("Not Now") { _, _ ->
                    // Update the last prompt time even if they decline
                    PreferencesManager.setLastDefaultBrowserPromptTime(this, System.currentTimeMillis())
                    PreferencesManager.setHasShownDefaultBrowserPrompt(this, true)
                }
                .show()
        }
    }

    private fun setupBottomNavigation() {
        // Load default fragment
        loadFragment(DashboardFragment())

        // Set up navigation item selection
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.navigation_url_check -> {
                    loadFragment(UrlCheckFragment())
                    true
                }
                R.id.navigation_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /**
     * Handle both navigation intents, universal links (Layer 2), and shared URLs
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_MAIN -> {
                // Normal app launch, do nothing special
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    val url = uri.toString()
                    bottomNavigationView.selectedItemId = R.id.navigation_url_check
                    loadFragment(UrlCheckFragment.newInstance(url, true))
                    scanUrlAndHandleResult(url)
                }
            }
            Intent.ACTION_SEND -> {
                // Handle shared URLs
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedText != null && (sharedText.startsWith("http://") || sharedText.startsWith("https://"))) {
                        // This is a URL, handle it
                        bottomNavigationView.selectedItemId = R.id.navigation_url_check
                        loadFragment(UrlCheckFragment.newInstance(sharedText, true))
                        scanUrlAndHandleResult(sharedText)
                    }
                }
            }
            "com.example.phshing.ACTION_SHOW_PHISHING_WARNING" -> {
                val url = intent.getStringExtra("url")
                val confidence = intent.getDoubleExtra("confidence", 0.0)
                if (url != null) {
                    bottomNavigationView.selectedItemId = R.id.navigation_url_check
                    loadFragment(UrlCheckFragment.newInstance(url, true))
                    showPhishingWarningDialog(url, confidence)
                }
            }
        }
    }

    /**
     * Handle Layer 2 - Universal Link Hook functionality
     * Intercepts URLs and scans them before allowing redirection
     */
    private fun handleUniversalLink(url: String) {
        // Check if Layer 2 protection is active
        if (!PreferencesManager.isLayer2Active(this@MainContainerActivity)) {
            // Layer 2 is disabled, open URL directly
            openUrlInBrowser(url)
            finish()
            return
        }

        // First check if URL is in our unified cache (faster than SharedPreferences)
        if (UrlCacheDatabase.isUrlCached(this@MainContainerActivity, url)) {
            Log.d("MainContainer", "URL found in unified cache, opening directly: $url")
            // Make sure it's also in the legacy cache for backward compatibility
            if (!PreferencesManager.isUrlApproved(this@MainContainerActivity, url)) {
                PreferencesManager.addApprovedUrl(this@MainContainerActivity, url)
            }
            openUrlInBrowser(url)

            // Navigate to URL check page with the cached URL
            loadFragment(UrlCheckFragment.newInstance(url, false))
            // Update bottom navigation to show URL check tab as selected
            bottomNavigationView.selectedItemId = R.id.navigation_url_check
            return
        }

        // Then check if URL is in approved list (legacy support)
        if (PreferencesManager.isUrlApproved(this@MainContainerActivity, url)) {
            Log.d("MainContainer", "URL found in legacy cache, adding to unified cache: $url")
            // URL is approved in legacy cache, add to unified cache for future lookups
            UrlCacheDatabase.cacheUrl(this@MainContainerActivity, url)
            openUrlInBrowser(url)

            // Navigate to URL check page with the cached URL
            loadFragment(UrlCheckFragment.newInstance(url, false))
            // Update bottom navigation to show URL check tab as selected
            bottomNavigationView.selectedItemId = R.id.navigation_url_check
            return
        }

        // Check if we've handled this URL too many times to prevent loops
        val count = urlHandlingCount.getOrDefault(url, 0)
        if (count >= MAX_HANDLING_COUNT) {
            Log.d("MainContainer", "URL handling count exceeded for: $url, count: $count")
            // Use WebView as a fallback to break the loop
            openInWebView(url)
            finish()
            return
        }

        // Increment the handling count for this URL
        urlHandlingCount[url] = count + 1

        // Show a dialog with a progress bar
        val dialogBuilder = AlertDialog.Builder(this@MainContainerActivity)
        val dialogView = layoutInflater.inflate(R.layout.simple_url_result_card, null)
        dialogBuilder.setView(dialogView)
        dialogBuilder.setCancelable(false)

        val dialog = dialogBuilder.create()
        dialog.show()

        // Get references to dialog views
        val urlTextView = dialogView.findViewById<TextView>(R.id.url_text)
        val statusTextView = dialogView.findViewById<TextView>(R.id.status_text)
        val cancelButton = dialogView.findViewById<Button>(R.id.more_details_button)

        // Set the URL text
        urlTextView.text = url

        // Set up cancel button
        cancelButton.text = "CANCEL"
        cancelButton.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        // Use coroutine to perform the scan
        lifecycleScope.launch {
            try {
                // Update status
                statusTextView.text = "Scanning URL..."

                // Call the API to scan the URL
                val result = PhishingApiClient.scanUrl(url, cancelOnError = true)

                // Dismiss the dialog
                dialog.dismiss()

                if (result.isPhishing) {
                    // URL is suspicious, show warning
                    Log.d(
                        "MainContainer",
                        "Phishing detected: $url, confidence: ${result.confidence}"
                    )

                    // Save the scan result to the database
                    val scanRecord = ScanRecord.createScan(
                        url = url,
                        isPhishing = true,
                        severityScore = result.confidence.toFloat()
                    )
                    scanDataManager.addScan(scanRecord)

                    // Increment phishing detection count
                    PreferencesManager.incrementPhishingDetectedCount(this@MainContainerActivity)

                    // Show warning dialog
                    val confidence = result.confidence
                    showPhishingWarningDialog(url, confidence)
                } else {
                    // URL is safe
                    Log.d("MainContainer", "URL is safe: $url")

                    // Save the scan result to the database
                    val scanRecord = ScanRecord.createScan(
                        url = url,
                        isPhishing = false,
                        severityScore = 0f
                    )
                    scanDataManager.addScan(scanRecord)

                    // Add to caches
                    UrlCacheDatabase.cacheUrl(this@MainContainerActivity, url)
                    PreferencesManager.addApprovedUrl(this@MainContainerActivity, url)

                    // Open the URL in the browser
                    openUrlInBrowser(url)
                    finish()
                }
            } catch (e: Exception) {
                // Handle error
                Log.e("MainContainer", "Error scanning URL: ${e.message}")
                dialog.dismiss()

                // Create error dialog
                val errorDialogBuilder = AlertDialog.Builder(this@MainContainerActivity)
                val errorDialogView = layoutInflater.inflate(R.layout.error_result_layout, null)
                errorDialogBuilder.setView(errorDialogView)

                val errorDialog = errorDialogBuilder.create()

                // Get references to error dialog views
                val errorTitleTextView = errorDialogView.findViewById<TextView>(R.id.result_title)
                val errorMessageTextView =
                    errorDialogView.findViewById<TextView>(R.id.error_message)
                val retryButton = errorDialogView.findViewById<Button>(R.id.retry_button)

                // Set error title based on error type
                when (e) {
                    is ApiException -> {
                        when (e.errorType) {
                            ErrorType.API_ERROR -> errorTitleTextView.text = "API Error"
                            ErrorType.NETWORK -> errorTitleTextView.text = "Network Error"
                            ErrorType.TIMEOUT -> errorTitleTextView.text = "Connection Timeout"
                            ErrorType.CANCELLED -> errorTitleTextView.text = "Request Cancelled"
                            ErrorType.UNKNOWN -> errorTitleTextView.text = "Unknown Error"
                            else -> errorTitleTextView.text = "Unknown Error Type"
                        }
                    }
                    else -> errorTitleTextView.text = "Error"
                }

                // Set error message
                errorMessageTextView.text =
                    "We couldn't verify if this URL is safe: ${e.message}\n\nFor your security, we've canceled opening this link."

                // Update retry button to act as cancel button
                try {
                    retryButton.text = "Cancel"
                    retryButton.setOnClickListener {
                        errorDialog.dismiss()
                        finish()
                    }
                } catch (e: Exception) {
                    // If updating button fails, log the error
                    Log.e("MainContainerActivity", "Error updating button: ${e.message}")
                }

                errorDialog.show()
            }
        }
    }

    /**
     * Shows a warning dialog for phishing URLs with option to open anyway
     */
    private fun showPhishingWarningDialog(url: String, confidence: Double) {
        val riskPercent = (confidence * 100).toInt().coerceIn(1, 100)
        AlertDialog.Builder(this@MainContainerActivity)
            .setTitle("⚠️ Phishing Detected!")
            .setMessage("This URL has been identified as a potential phishing attempt with $riskPercent% confidence.\n\nURL: $url\n\nDo you still want to open it?")
            .setPositiveButton("Open Anyway") { _, _ ->
                // Add URL to both caches to prevent rescanning
                UrlCacheDatabase.cacheUrl(this@MainContainerActivity, url)
                // Keep legacy support for backward compatibility
                PreferencesManager.addApprovedUrl(this@MainContainerActivity, url)

                // Open in Chrome with special flags to prevent loops
                openUrlInChrome(url)

                // Don't finish if this is the root task (main app screen)
                if (!isTaskRoot) {
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(true)
            .show()
    }

    private fun scanUrlAndHandleResult(url: String) {
        // Check if URL is in our unified cache (faster than SharedPreferences)
        if (UrlCacheDatabase.isUrlCached(this@MainContainerActivity, url)) {
            Log.d("MainContainer", "URL found in unified cache, opening directly: $url")
            // URL is already verified, open it directly
            openUrlInChrome(url)
            // Finish this activity to prevent loops
            finish()
            return
        }

        // Use coroutine to perform the scan
        lifecycleScope.launch {
            try {
                // Call the API to scan the URL
                val result = PhishingApiClient.scanUrl(url, cancelOnError = true)

                // Save the scan result to the database
                val scanRecord = ScanRecord.createScan(
                    url = url,
                    isPhishing = result.isPhishing,
                    severityScore = result.confidence.toFloat()
                )
                scanDataManager.addScan(scanRecord)

                if (result.isPhishing) {
                    // URL is suspicious, show warning
                    Log.d(
                        "MainContainer",
                        "Phishing detected: $url, confidence: ${result.confidence}"
                    )

                    // Show warning dialog asking if user wants to open anyway
                    withContext(Dispatchers.Main) {
                        val confidence = result.confidence
                        showPhishingWarningDialog(url, confidence)
                    }
                } else {
                    // URL is safe, open it directly in Chrome
                    Log.d("MainContainer", "URL is safe: $url")

                    // Add to unified cache
                    UrlCacheDatabase.cacheUrl(this@MainContainerActivity, url)

                    // Open the URL in Chrome
                    openUrlInChrome(url)

                    // Finish this activity to prevent loops
                    finish()
                }
            } catch (e: Exception) {
                // Handle errors
                Log.e("MainContainer", "Error scanning URL: ${e.message}")

                // Show error dialog
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainContainerActivity)
                        .setTitle("Error Scanning URL")
                        .setMessage("We couldn't verify if this URL is safe due to an error: ${e.message}\n\nDo you still want to open it?")
                        .setPositiveButton("Open Anyway") { _, _ ->
                            openUrlInChrome(url)
                            finish()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    /**
     * Opens a URL in an external browser (not Chrome)
     */
    private fun openUrlInExternalBrowser(url: String) {
        try {
            // Get a list of all browser packages except our own
            val browsers = getBrowserPackages()

            if (browsers.isNotEmpty()) {
                // Create intent for first available browser
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.setPackage(browsers[0])
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this@MainContainerActivity.startActivity(browserIntent)
                Log.d("MainContainer", "Opened URL in external browser: ${browsers[0]}")
            } else {
                // No browsers available, use WebView as fallback
                openInWebView(url)
            }
        } catch (e: Exception) {
            // If opening in external browser fails, try WebView
            Log.e("MainContainer", "Error opening URL in external browser: ${e.message}")
            openInWebView(url)
        }
    }

    /**
     * Opens a URL in our internal WebView to avoid infinite loops
     */
    private fun openInWebView(url: String) {
        Log.d("MainContainer", "Opening URL in WebView: $url")

        // Mark this URL as approved to prevent future scanning
        PreferencesManager.addApprovedUrl(this@MainContainerActivity, url)

        // Open in our WebView activity
        val intent = Intent(this@MainContainerActivity, WebViewActivity::class.java)
        intent.putExtra("url", url)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this@MainContainerActivity.startActivity(intent)
    }

    /**
     * Opens a URL in the browser
     */
    private fun openUrlInBrowser(url: String) {
        // Add the URL to a set of approved URLs in preferences
        // This will prevent re-scanning when the user has explicitly chosen to open it
        PreferencesManager.addApprovedUrl(this@MainContainerActivity, url)

        // Check if we're the default browser
        if (AppLinkHelper.isDefaultLinkHandler(this@MainContainerActivity)) {
            Log.d(
                "MainContainer",
                "We are the default browser, using alternative method to open URL"
            )
            // We're the default browser, so we need to use a different approach
            // to avoid an infinite loop

            // Try Chrome first
            try {
                val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                chromeIntent.setPackage("com.android.chrome")
                chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this@MainContainerActivity.startActivity(chromeIntent)
                Log.d("MainContainer", "Opened URL with Chrome")
                return
            } catch (e: Exception) {
                Log.e("MainContainer", "Failed to open with Chrome: ${e.message}")
                // Chrome not available, try other browsers
            }

            // Try to find an explicit browser package
            val browserPackages = getBrowserPackages()
            if (browserPackages.isNotEmpty()) {
                // Use the first available browser that isn't our app
                for (i in 0 until browserPackages.size) {
                    val packageName = browserPackages[i]
                    if (packageName != this@MainContainerActivity.packageName) {
                        try {
                            val explicitIntent =
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            explicitIntent.setPackage(packageName)
                            explicitIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            this@MainContainerActivity.startActivity(explicitIntent)
                            Log.d(
                                "MainContainer",
                                "Opened URL with explicit browser: $packageName"
                            )
                            return
                        } catch (e: Exception) {
                            Log.e(
                                "MainContainer",
                                "Failed to open with browser: $packageName",
                                e
                            )
                            // Try the next browser
                        }
                    }
                }
            }

            // If we couldn't find a browser or all attempts failed, use WebView
            Log.d(
                "MainContainer",
                "No other browsers found, using WebView"
            )
            openInWebView(url)
        } else {
            // We're not the default browser, so we can use a normal intent
            Log.d("MainContainer", "We are not the default browser, using normal intent")
            
            // Try Chrome first for consistency
            openUrlInChrome(url)
        }
    }

    /**
     * Get a list of installed browser packages
     */
    private fun getBrowserPackages(): List<String> {
        val browsers = ArrayList<String>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
        val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
        
        // Use explicit for loop to avoid ambiguous iterator
        for (i in 0 until resolveInfoList.size) {
            val resolveInfo = resolveInfoList[i]
            val packageName = resolveInfo.activityInfo.packageName
            
            // Skip our own package
            if (packageName != this.packageName) {
                browsers.add(packageName)
            }
        }
        
        return browsers
    }

    /**
     * Opens a URL directly in Chrome or falls back to default browser
     * Makes sure to bypass our app to prevent loops
     */
    private fun openUrlInChrome(url: String) {
        try {
            // Try to open in Chrome first
            val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            chromeIntent.setPackage("com.android.chrome")
            
            // Add flags to prevent our app from intercepting this intent
            chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            chromeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            
            // Add component to explicitly target Chrome's main activity
            val componentName = ComponentName(
                "com.android.chrome",
                "com.google.android.apps.chrome.Main"
            )
            chromeIntent.component = componentName
            
            startActivity(chromeIntent)
            Log.d("MainContainer", "Opened URL in Chrome: $url")
        } catch (e: Exception) {
            // Chrome not available or failed, try other browsers
            Log.e("MainContainer", "Failed to open in Chrome: ${e.message}")
            openUrlInExternalBrowser(url)
        }
    }

    /**
     * Handle internal navigation intents
     */
    private fun handleNavigationIntent(intent: Intent) {
        val action = intent.action
        if (action == "com.example.phshing.ACTION_OPEN_DASHBOARD") {
            bottomNavigationView.selectedItemId = R.id.navigation_dashboard
            loadFragment(DashboardFragment())
        } else if (action == "com.example.phshing.ACTION_OPEN_URL_CHECK") {
            bottomNavigationView.selectedItemId = R.id.navigation_url_check
            loadFragment(UrlCheckFragment())
        } else if (action == "com.example.phshing.ACTION_OPEN_SETTINGS") {
            bottomNavigationView.selectedItemId = R.id.navigation_settings
            loadFragment(SettingsFragment())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Add a menu option to request browser role directly
     */
    private fun requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ has a dedicated API for this
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) &&
                !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
            ) {
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                startActivityForResult(intent, 123)
            }
        } else {
            // For older Android versions, open the default apps settings
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_set_default -> {
                requestDefaultBrowser()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
