package com.example.eyetab

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class EyeAccessibilityService : AccessibilityService() {

    private val tag = "EyeTab"
    private var gestures: GestureController? = null
    private var screenWidth = 1920
    private var screenHeight = 1200

    override fun onServiceConnected() {
        try {
            instance = this
            gestures = GestureController(this)
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                screenWidth = bounds.width()
                screenHeight = bounds.height()
            } else {
                @Suppress("DEPRECATION")
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                screenWidth = metrics.widthPixels
                screenHeight = metrics.heightPixels
            }
        } catch (e: Exception) {
            Log.e(tag, "onServiceConnected", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        try {
            if (instance === this) instance = null
        } catch (e: Exception) {
            Log.e(tag, "onDestroy", e)
        }
        super.onDestroy()
    }

    fun performTap(x: Float, y: Float) {
        try {
            gestures?.tap(x, y)
        } catch (e: Exception) {
            Log.e(tag, "performTap", e)
        }
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
            Log.e(tag, "performScroll", e)
        }
    }

    companion object {
        @Volatile
        var instance: EyeAccessibilityService? = null
    }
}
