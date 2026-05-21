package com.example.eyetab

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class EyeAccessibilityService : AccessibilityService() {

    private var gestures: GestureController? = null
    private var screenWidth = 1920
    private var screenHeight = 1200

    override fun onServiceConnected() {
        try {
            instance = this
            gestures = GestureController(this)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        } catch (e: Exception) {
            Log.e(TAG, "onServiceConnected failed", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        gestures = null
    }

    fun performTap(x: Float, y: Float) {
        try { gestures?.tap(x, y) } catch (e: Exception) { Log.e(TAG, "performTap", e) }
    }

    fun performScroll(direction: EyeTrackingManager.ScrollDirection, x: Float, y: Float) {
        try {
            val cx = screenWidth / 2f
            val cy = screenHeight / 2f
            val delta = screenHeight * 0.35f
            when (direction) {
                EyeTrackingManager.ScrollDirection.UP ->
                    gestures?.swipe(cx, cy - delta, cx, cy + delta)
                EyeTrackingManager.ScrollDirection.DOWN ->
                    gestures?.swipe(cx, cy + delta, cx, cy - delta)
            }
        } catch (e: Exception) {
            Log.e(TAG, "performScroll", e)
        }
    }

    companion object {
        private const val TAG = "EyeTab"
        @Volatile var instance: EyeAccessibilityService? = null
    }
}
