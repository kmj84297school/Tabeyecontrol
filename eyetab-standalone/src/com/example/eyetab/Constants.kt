package com.example.eyetab

object Constants {
    const val BLINK_THRESHOLD_SCORE = 40  // Camera face score below this = likely blinking
    const val SHORT_BLINK_MIN_MS = 120L
    const val SHORT_BLINK_MAX_MS = 600L
    const val LONG_BLINK_MIN_MS = 800L
    const val TAP_COOLDOWN_MS = 800L
    const val SCROLL_COOLDOWN_MS = 1000L
    const val SMOOTHING_WINDOW = 8
    const val CURSOR_SIZE_DP = 32
    const val SCROLL_EDGE_RATIO = 0.25f
    const val MOVEMENT_GAIN = 2.2f
    const val FACE_LOST_TIMEOUT_MS = 500L
}
