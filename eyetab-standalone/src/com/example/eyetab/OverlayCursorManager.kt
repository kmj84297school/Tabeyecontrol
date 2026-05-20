package com.example.eyetab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class OverlayCursorManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val cursorSizePx = (Constants.CURSOR_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private var cursorView: CursorView? = null

    private inner class CursorView(ctx: Context) : View(ctx) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80FF3D3D.toInt()
            style = Paint.Style.FILL
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(cx, cy) - 2f
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, strokePaint)
        }
    }

    fun show() {
        if (cursorView != null) return
        val params = WindowManager.LayoutParams(
            cursorSizePx, cursorSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val v = CursorView(context)
        cursorView = v
        wm.addView(v, params)
    }

    fun updatePosition(cx: Float, cy: Float) {
        val v = cursorView ?: return
        val params = (v.layoutParams as WindowManager.LayoutParams).apply {
            x = (cx - cursorSizePx / 2).toInt()
            y = (cy - cursorSizePx / 2).toInt()
        }
        wm.updateViewLayout(v, params)
    }

    fun hide() {
        cursorView?.let { wm.removeView(it); cursorView = null }
    }
}
