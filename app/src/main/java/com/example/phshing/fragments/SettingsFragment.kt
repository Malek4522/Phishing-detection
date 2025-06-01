package com.example.phshing.fragments

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.example.phshing.R
import com.example.phshing.service.RealTimeProtectionService
import com.example.phshing.utils.AccessibilityUtil
import com.example.phshing.utils.PreferencesManager

class SettingsFragment : Fragment() {    
    // Handler for UI updates
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var cardLayer1: CardView
    private lateinit var cardLayer2: CardView
    private lateinit var cardBothLayers: CardView
    
    private lateinit var switchLayer1: SwitchCompat
    private lateinit var switchLayer2: SwitchCompat
    private lateinit var switchBothLayers: SwitchCompat
    
    private lateinit var layer1Glow: View
    private lateinit var layer2Glow: View
    private lateinit var bothLayersGlow: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    
    override fun onResume() {
        super.onResume()
        // Reload settings to reflect any changes made in system settings
        loadSettings()
    }
    
    override fun onPause() {
        super.onPause()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        cardLayer1 = view.findViewById(R.id.card_layer1)
        cardLayer2 = view.findViewById(R.id.card_layer2)
        cardBothLayers = view.findViewById(R.id.card_both_layers)
        
        switchLayer1 = view.findViewById(R.id.switch_layer1)
        switchLayer2 = view.findViewById(R.id.switch_layer2)
        switchBothLayers = view.findViewById(R.id.switch_both_layers)
        
        layer1Glow = view.findViewById(R.id.layer1_glow)
        layer2Glow = view.findViewById(R.id.layer2_glow)
        bothLayersGlow = view.findViewById(R.id.both_layers_glow)
        
        // Load current settings
        loadSettings()
        
        // Set up click listeners
        setupClickListeners()
    }
    
    private fun loadSettings() {
        context?.let { ctx ->
            // Simply load the preference values without checking accessibility
            switchLayer1.isChecked = PreferencesManager.isLayer1Active(ctx)
            switchLayer2.isChecked = PreferencesManager.isLayer2Active(ctx)
            switchBothLayers.isChecked = PreferencesManager.areBothLayersActive(ctx)
            
            // Update glow effects
            updateGlowEffects()
        }
    }
    
    private fun setupClickListeners() {
        // Layer 1 card click
        cardLayer1.setOnClickListener {
            toggleLayer1()
        }
        
        // Layer 1 switch - simplified to just update preferences
        switchLayer1.setOnCheckedChangeListener { _, isChecked ->
            context?.let { ctx ->
                // Simply save the preference without checking accessibility
                PreferencesManager.setLayer1Active(ctx, isChecked)
                
                // Update "both layers" switch based on both individual switches
                val bothActive = isChecked && switchLayer2.isChecked
                switchBothLayers.isChecked = bothActive
                PreferencesManager.setBothLayersActive(ctx, bothActive)
                
                updateGlowEffects()
                // Toast notifications removed as requested
                
                // Restart protection service if it's currently active
                restartProtectionServiceIfActive(ctx)
            }
        }
        
        // Layer 2 card click
        cardLayer2.setOnClickListener {
            toggleLayer2()
        }
        
        // Layer 2 switch - simplified to just update preferences
        switchLayer2.setOnCheckedChangeListener { _, isChecked ->
            context?.let { ctx ->
                // Simply save the preference without additional checks
                PreferencesManager.setLayer2Active(ctx, isChecked)
                
                // Update "both layers" switch based on both individual switches
                val bothActive = isChecked && switchLayer1.isChecked
                switchBothLayers.isChecked = bothActive
                PreferencesManager.setBothLayersActive(ctx, bothActive)
                
                updateGlowEffects()
                // Toast notifications removed as requested
                
                // Restart protection service if it's currently active
                restartProtectionServiceIfActive(ctx)
            }
        }
        
        // Both layers card click
        cardBothLayers.setOnClickListener {
            toggleBothLayers()
        }
        
        // Both layers switch - simplified to just update preferences and sync other switches
        switchBothLayers.setOnCheckedChangeListener { _, isChecked ->
            context?.let { ctx ->
                // Update both individual switches
                if (isChecked) {
                    // Both layers should be active
                    switchLayer1.isChecked = true
                    switchLayer2.isChecked = true
                    PreferencesManager.setLayer1Active(ctx, true)
                    PreferencesManager.setLayer2Active(ctx, true)
                    PreferencesManager.setBothLayersActive(ctx, true)
                    // Toast notifications removed as requested
                } else {
                    // Both layers should be inactive
                    PreferencesManager.setBothLayersActive(ctx, false)
                    // Toast notifications removed as requested
                }
                
                updateGlowEffects()
                
                // Restart protection service if it's currently active
                restartProtectionServiceIfActive(ctx)
            }
        }
    }
    
    private fun toggleLayer1() {
        switchLayer1.isChecked = !switchLayer1.isChecked
    }
    
    private fun toggleLayer2() {
        // Simply toggle the switch state - the listener will handle saving the preference
        switchLayer2.isChecked = !switchLayer2.isChecked
    }
    
    private fun toggleBothLayers() {
        // Simply toggle the switch state - the listener will handle saving the preference
        switchBothLayers.isChecked = !switchBothLayers.isChecked
    }
    
    private fun updateGlowEffects() {
        // Update Layer 1 glow
        if (switchLayer1.isChecked) {
            layer1Glow.visibility = View.VISIBLE
            animateGlow(layer1Glow)
        } else {
            layer1Glow.visibility = View.GONE
        }
        
        // Update Layer 2 glow
        if (switchLayer2.isChecked) {
            layer2Glow.visibility = View.VISIBLE
            animateGlow(layer2Glow)
        } else {
            layer2Glow.visibility = View.GONE
        }
        
        // Update Both Layers glow
        if (switchBothLayers.isChecked) {
            bothLayersGlow.visibility = View.VISIBLE
            animateGlow(bothLayersGlow)
        } else {
            bothLayersGlow.visibility = View.GONE
        }
    }
    
    private fun animateGlow(view: View) {
        // Create a pulsating animation for the glow effect
        val animator = ObjectAnimator.ofFloat(view, "alpha", 0.4f, 1.0f)
        animator.duration = 1500
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.repeatMode = ObjectAnimator.REVERSE
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.start()
    }
    
    // Toast method removed as requested
    
    /**
     * Restarts the RealTimeProtectionService if it's currently active
     */
    private fun restartProtectionServiceIfActive(context: Context) {
        // Only restart if protection is currently active
        if (PreferencesManager.isProtectionActive(context)) {
            // Stop the service
            val stopIntent = Intent(context, RealTimeProtectionService::class.java)
            stopIntent.action = RealTimeProtectionService.ACTION_STOP
            context.startService(stopIntent)
            
            // Short delay to ensure service stops properly
            Handler(Looper.getMainLooper()).postDelayed({
                // Start the service again
                val startIntent = Intent(context, RealTimeProtectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
                
                // Toast notification removed as requested
            }, 500) // 500ms delay
        }
    }
    
    // This method is no longer needed as we're not checking accessibility in settings
    // The RealTimeProtectionService will handle accessibility checks when it starts
    private fun checkAccessibilityPermission(): Boolean {
        return true
    }
    
    companion object {
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }
}
