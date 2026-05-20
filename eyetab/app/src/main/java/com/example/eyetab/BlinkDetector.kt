package com.example.eyetab

import com.example.eyetab.Constants.BLINK_THRESHOLD
import com.example.eyetab.Constants.LONG_BLINK_MIN_MS
import com.example.eyetab.Constants.SHORT_BLINK_MAX_MS
import com.example.eyetab.Constants.SHORT_BLINK_MIN_MS

class BlinkDetector(
    private val onShortBlink: () -> Unit,
    private val onLongBlink: () -> Unit
) {
    private var eyesClosedStart: Long = 0L
    private var eyesClosed = false

    fun process(leftOpenProb: Float, rightOpenProb: Float) {
        val avgOpen = (leftOpenProb + rightOpenProb) / 2f
        val now = System.currentTimeMillis()
        val closed = avgOpen < BLINK_THRESHOLD

        if (closed && !eyesClosed) {
            eyesClosedStart = now
            eyesClosed = true
        } else if (!closed && eyesClosed) {
            val duration = now - eyesClosedStart
            eyesClosed = false
            when {
                duration in SHORT_BLINK_MIN_MS..SHORT_BLINK_MAX_MS -> onShortBlink()
                duration >= LONG_BLINK_MIN_MS -> onLongBlink()
            }
        }
    }

    fun reset() {
        eyesClosed = false
        eyesClosedStart = 0L
    }
}
