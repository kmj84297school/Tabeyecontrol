package com.example.eyetab

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

class OverlayCursorManager(private val context: Context) {

    private val tag = "EyeTab"
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var cursorView: View? = null
    private val cursorSizePx = (Constants.CURSOR_SIZE_DP * context.resources.displayMetrics.density).toInt()

    private val baseParams = WindowManager.LayoutParams(
        cursorSizePx, cursorSizePx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    fun show() {
        try {
            if (cursorView != null) return
            val v = View(context).apply {
                background = ContextCompat.getDrawable(context, R.drawable.cursor_circle)
            }
            cursorView = v
            wm.addView(v, WindowManager.LayoutParams().apply { copyFrom(baseParams) })
        } catch (e: Exception) {
            Log.e(tag, "show overlay", e)
            cursorView = null
        }
    }

    fun updatePosition(cx: Float, cy: Float) {
        try {
            val v = cursorView ?: return
            val params = (v.layoutParams as WindowManager.LayoutParams).apply {
                x = (cx - cursorSizePx / 2).toInt()
                y = (cy - cursorSizePx / 2).toInt()
            }
            wm.updateViewLayout(v, params)
        } catch (e: Exception) {
            Log.e(tag, "updatePosition", e)
        }
    }

    fun hide() {
        try {
            cursorView?.let {
                wm.removeView(it)
                cursorView = null
            }
        } catch (e: Exception) {
            Log.e(tag, "hide overlay", e)
            cursorView = null
        }
    }
}
