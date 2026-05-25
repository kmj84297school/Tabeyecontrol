package com.example.eyetab

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

class GestureController(private val service: AccessibilityService) {

    private val tag = "EyeTab"

    fun tap(x: Float, y: Float) {
        try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(tag, "tap", e)
        }
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 350) {
        try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(tag, "swipe", e)
        }
    }
}
