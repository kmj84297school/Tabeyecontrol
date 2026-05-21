package com.example.eyetab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayCursorManager(private val context: Context) {

    private val TAG = "EyeTab"
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sizePx = (Constants.CURSOR_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private var cursorView: CursorView? = null

    private inner class CursorView(ctx: Context) : View(ctx) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80FF3D3D.toInt(); style = Paint.Style.FILL
        }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
        }
        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val r = minOf(cx, cy) - 2f
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, stroke)
        }
    }

    fun show() {
        if (cursorView != null) return
        try {
            val params = WindowManager.LayoutParams(
                sizePx, sizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
            val v = CursorView(context)
            cursorView = v
            wm.addView(v, params)
        } catch (e: Exception) {
            Log.e(TAG, "show overlay failed", e)
            cursorView = null
        }
    }

    fun updatePosition(cx: Float, cy: Float) {
        val v = cursorView ?: return
        try {
            val params = (v.layoutParams as WindowManager.LayoutParams).apply {
                x = (cx - sizePx / 2).toInt()
                y = (cy - sizePx / 2).toInt()
            }
            wm.updateViewLayout(v, params)
        } catch (e: Exception) {
            Log.e(TAG, "updatePosition failed", e)
        }
    }

    fun hide() {
        try {
            cursorView?.let { wm.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "hide overlay failed", e)
        }
        cursorView = null
    }
}
