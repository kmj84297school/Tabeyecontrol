package com.example.eyetab

class MovingAverageFilter(private val windowSize: Int) {
    private val buffer = ArrayDeque<Float>(windowSize)

    fun add(value: Float): Float {
        if (buffer.size >= windowSize) buffer.removeFirst()
        buffer.addLast(value)
        return buffer.average().toFloat()
    }

    fun reset() = buffer.clear()
}
