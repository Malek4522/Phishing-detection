package com.example.phshing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.example.phshing.utils.PreferencesManager

/**
 * A simple WebView activity to use as a fallback when no other browsers are available
 * This is used when the app is set as the default browser but we need to open a URL
 * without triggering an infinite loop
 */
class WebViewActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    
    companion object {
        // Flag to indicate the URL is being opened in our WebView
        const val EXTRA_INTERNAL_WEBVIEW = "internal_webview"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a WebView programmatically
        webView = WebView(this)
        setContentView(webView)
        
        // Set up the WebView
        webView.settings.javaScriptEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Handle URL navigation within the WebView
                request?.url?.let { uri ->
                    val url = uri.toString()
                    Log.d("WebViewActivity", "Navigation to: $url")
                    
                    // Mark this URL as approved to prevent scanning loops
                    PreferencesManager.addApprovedUrl(this@WebViewActivity, url)
                    
                    // For external links, we want to handle them in our WebView
                    // to avoid the infinite loop with LinkInterceptorActivity
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.putExtra(EXTRA_INTERNAL_WEBVIEW, true) // Set the flag
                    
                    // Continue loading in our WebView
                    return false
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Set the title when the page is loaded
                supportActionBar?.title = view?.title ?: "Web View"
            }
        }
        
        // Enable back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Loading..."
        
        // Get the URL from the intent
        val url = intent.getStringExtra("url")
        if (url != null) {
            Log.d("WebViewActivity", "Loading URL: $url")
            
            // Mark this URL as approved to prevent scanning loops
            PreferencesManager.addApprovedUrl(this, url)
            
            webView.loadUrl(url)
        } else {
            Log.e("WebViewActivity", "No URL provided")
            finish()
        }
    }
    
    override fun onBackPressed() {
        // Handle back button to navigate within the WebView if possible
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks
        return when (item.itemId) {
            android.R.id.home -> {
                // Respond to the action bar's Up/Home button
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
