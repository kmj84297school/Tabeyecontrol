package com.example.eyetab

import android.accessibilityservice.AccessibilityService
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent

class EyeAccessibilityService : AccessibilityService() {

    private lateinit var gestures: GestureController
    private var screenWidth = 1920
    private var screenHeight = 1200

    override fun onServiceConnected() {
        instance = this
        gestures = GestureController(this)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    fun performTap(x: Float, y: Float) = gestures.tap(x, y)

    fun performScroll(direction: EyeTrackingManager.ScrollDirection, x: Float, y: Float) {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val delta = screenHeight * 0.35f
        when (direction) {
            EyeTrackingManager.ScrollDirection.UP ->
                gestures.swipe(cx, cy - delta, cx, cy + delta)
            EyeTrackingManager.ScrollDirection.DOWN ->
                gestures.swipe(cx, cy + delta, cx, cy - delta)
        }
    }

    companion object {
        @Volatile
        var instance: EyeAccessibilityService? = null
    }
}
