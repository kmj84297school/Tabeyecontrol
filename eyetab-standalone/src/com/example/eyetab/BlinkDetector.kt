package com.example.eyetab

import com.example.eyetab.Constants.LONG_BLINK_MIN_MS
import com.example.eyetab.Constants.SHORT_BLINK_MAX_MS
import com.example.eyetab.Constants.SHORT_BLINK_MIN_MS

class BlinkDetector(
    private val onShortBlink: () -> Unit,
    private val onLongBlink: () -> Unit
) {
    private var noFaceStart: Long = 0L
    private var faceAbsent = false

    // Called each frame: facePresent=true when face/eyes detected, false when not
    fun process(facePresent: Boolean) {
        val now = System.currentTimeMillis()
        if (!facePresent && !faceAbsent) {
            noFaceStart = now
            faceAbsent = true
        } else if (facePresent && faceAbsent) {
            val duration = now - noFaceStart
            faceAbsent = false
            when {
                duration in SHORT_BLINK_MIN_MS..SHORT_BLINK_MAX_MS -> onShortBlink()
                duration >= LONG_BLINK_MIN_MS -> onLongBlink()
            }
        }
    }

    fun reset() { faceAbsent = false; noFaceStart = 0L }
}
