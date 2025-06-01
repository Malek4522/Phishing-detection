package com.example.phshing.utils

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity

/**
 * Helper class for managing app links and default browser settings
 */
object AppLinkHelper {

    // Request code for browser role request
    private const val ROLE_BROWSER_REQUEST_CODE = 123

    /**
     * Check if the app is set as the default handler for links
     */
    fun isDefaultLinkHandler(context: Context): Boolean {
        // On Android 10+ we can use RoleManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) ?: false
        }
        
        // On older versions, we check if our app resolves browser intents
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, 0)
        }
        
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
    
    /**
     * Request browser role directly using system dialog (Android 10+)
     * This will show a system dialog asking the user to set this app as the default browser
     */
    fun requestBrowserRole(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                activity.startActivityForResult(intent, ROLE_BROWSER_REQUEST_CODE)
            }
        } else {
            // For older Android versions, show instructions dialog
            promptForDefaultLinkHandler(activity)
        }
    }
    
    /**
     * Modern way to request browser role using ActivityResultContracts (for use with AppCompatActivity)
     */
    fun registerBrowserRoleLauncher(activity: FragmentActivity): ActivityResultLauncher<Intent> {
        return activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // User accepted our app as the default browser
                android.widget.Toast.makeText(
                    activity,
                    "Thank you! This app is now your default browser for enhanced protection.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                // User declined, show a message explaining why it's important
                android.widget.Toast.makeText(
                    activity,
                    "For full link protection, please consider setting this app as your default browser.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Request browser role using the registered launcher
     */
    fun requestBrowserRoleWithLauncher(context: Context, launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                launcher.launch(intent)
            }
        } else {
            // For older Android versions, show instructions dialog
            promptForDefaultLinkHandler(context)
        }
    }

    /**
     * Show dialog to prompt user to set app as default link handler
     */
    fun promptForDefaultLinkHandler(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Set as Browser Alternative")
            .setMessage("For Layer 2 protection to work properly when clicking links directly in Gmail and other apps, this app needs to be selected when opening links. Would you like to see how to set this up?")
            .setPositiveButton("Show Me How") { _, _ ->
                showBrowserSelectionInstructions(context)
            }
            .setNegativeButton("Later", null)
            .show()
    }
    
    /**
     * Show instructions for selecting this app when opening links
     */
    private fun showBrowserSelectionInstructions(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("How to Set Up Link Protection")
            .setMessage(
                "1. When you click a link in Gmail or other apps, Android will show a dialog asking which app to use\n\n" +
                "2. Select '${context.applicationInfo.loadLabel(context.packageManager)}' from the list\n\n" +
                "3. Choose 'Always' to make this the default for all links\n\n" +
                "Would you like to open browser settings now to clear any existing defaults?"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                openDefaultAppsSettings(context)
            }
            .setNegativeButton("I'll Do It Later", null)
            .show()
    }

    /**
     * Open the default apps settings screen
     */
    private fun openDefaultAppsSettings(context: Context) {
        try {
            // Try to open browser default settings directly
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            context.startActivity(intent)
            
            // Show follow-up instructions
            android.widget.Toast.makeText(
                context,
                "Select 'Browser app' and clear or change the default",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            // If direct settings intent fails, try to open app details
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", context.packageName, null)
                context.startActivity(intent)
            } catch (e: Exception) {
                // If all else fails, show a toast with instructions
                android.widget.Toast.makeText(
                    context,
                    "Please open Settings > Apps > Default apps > Browser app and clear the default browser",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Clear this app as the default browser when Layer 2 is disabled
     * This will prompt the user to change their default browser settings
     */
    fun clearDefaultBrowser(context: Context) {
        // Check if we're currently the default browser
        if (isDefaultLinkHandler(context)) {
            AlertDialog.Builder(context)
                .setTitle("Change Default Browser")
                .setMessage("Since Layer 2 protection is now disabled, would you like to clear this app as your default browser? This will allow Chrome or your preferred browser to handle links again.")
                .setPositiveButton("Yes, Change Default") { _, _ ->
                    openDefaultAppsSettings(context)
                }
                .setNegativeButton("No, Keep as Default", null)
                .show()
        }
    }
}
