package com.example.eyetab

import com.example.eyetab.Constants.MOVEMENT_GAIN
import kotlin.math.max
import kotlin.math.min

class CalibrationMapper(private val screenWidth: Int, private val screenHeight: Int) {
    private var refNx = 0.5f
    private var refNy = 0.5f

    fun setReference(nx: Float, ny: Float) { refNx = nx; refNy = ny }

    fun map(nx: Float, ny: Float): Pair<Float, Float> {
        val dx = (nx - refNx) * MOVEMENT_GAIN
        val dy = (ny - refNy) * MOVEMENT_GAIN
        val cx = (0.5f + dx) * screenWidth
        val cy = (0.5f + dy) * screenHeight
        return Pair(
            min(max(cx, 0f), screenWidth.toFloat()),
            min(max(cy, 0f), screenHeight.toFloat())
        )
    }
}
