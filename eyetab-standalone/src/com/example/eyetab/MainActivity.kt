package com.example.eyetab

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private var tvCameraStatus: TextView? = null
    private var tvOverlayStatus: TextView? = null
    private var tvAccessStatus: TextView? = null
    private var tvTrackingStatus: TextView? = null
    private var tvLastError: TextView? = null
    private var btnStart: Button? = null
    private var btnStop: Button? = null
    private var uiReady = false
    private var tracking = false

    private val permCheckRunnable = object : Runnable {
        override fun run() {
            if (uiReady) updatePermissionStatus()
            window?.decorView?.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashHandler()
        try {
            setupUI()
            uiReady = true
            loadLastCrash()
            updatePermissionStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal in onCreate", e)
        }
    }

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, ex ->
            try {
                val msg = buildString {
                    append(ex.javaClass.name)
                    append(": ")
                    append(ex.message ?: "")
                    append("\n")
                    ex.stackTrace.take(8).forEach { append("  at $it\n") }
                }
                getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_CRASH, msg).apply()
            } catch (_: Exception) {}
            prev?.uncaughtException(t, ex)
        }
    }

    private fun loadLastCrash() {
        val crash = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CRASH, null) ?: return
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CRASH).apply()
        showError("이전 크래시:\n$crash")
    }

    private fun setupUI() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "EyeTab v1.1"
            textSize = 30f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 8)
        })

        root.addView(makeDivider())

        tvCameraStatus = addStatusRow(root, "카메라 권한")
        tvOverlayStatus = addStatusRow(root, "오버레이 권한")
        tvAccessStatus = addStatusRow(root, "접근성 서비스")
        tvTrackingStatus = addStatusRow(root, "추적 상태")

        root.addView(makeDivider())

        addButton(root, "1. 카메라 권한 요청", Color.rgb(33, 150, 243)) {
            try {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
            } catch (e: Exception) {
                Log.e(TAG, "requestPermissions", e)
                showError("카메라 권한 요청 실패: ${e.message}")
            }
        }
        addButton(root, "2. 오버레이 권한 설정", Color.rgb(33, 150, 243)) {
            try {
                startActivity(PermissionUtils.overlaySettingsIntent(this))
            } catch (e: Exception) {
                Log.e(TAG, "overlaySettings", e)
                showError("오버레이 설정 열기 실패: ${e.message}")
            }
        }
        addButton(root, "3. 접근성 서비스 설정", Color.rgb(33, 150, 243)) {
            try {
                startActivity(PermissionUtils.accessibilitySettingsIntent())
            } catch (e: Exception) {
                Log.e(TAG, "accessibilitySettings", e)
                showError("접근성 설정 열기 실패: ${e.message}")
            }
        }
        btnStart = addButton(root, "▶ 추적 시작", Color.rgb(46, 125, 50)) { onStartTracking() }
        btnStop = addButton(root, "■ 추적 중지", Color.rgb(183, 28, 28)) { onStopTracking() }

        root.addView(makeDivider())

        root.addView(TextView(this).apply {
            text = "사용법:\n• 짧은 눈 감기 (120~600ms) → 탭\n• 긴 눈 감기 (800ms+) → 스크롤\n• 커서 위 25% 구역 → 위 스크롤\n• 커서 아래 25% 구역 → 아래 스크롤"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 8)
        })

        root.addView(makeDivider())

        tvLastError = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(200, 0, 0))
            setPadding(0, 8, 0, 8)
            visibility = View.GONE
        }
        root.addView(tvLastError)
    }

    private fun onStartTracking() {
        try {
            val missing = mutableListOf<String>()
            if (!PermissionUtils.hasCameraPermission(this)) missing += "카메라 권한"
            if (!PermissionUtils.hasOverlayPermission(this)) missing += "오버레이 권한"
            if (!PermissionUtils.isAccessibilityServiceEnabled(this)) missing += "접근성 서비스"
            if (missing.isNotEmpty()) {
                showError("권한 부족: ${missing.joinToString(", ")}")
                return
            }
            startForegroundService(
                Intent(this, OverlayCursorService::class.java)
                    .apply { action = OverlayCursorService.ACTION_START }
            )
            tracking = true
            tvLastError?.visibility = View.GONE
            updatePermissionStatus()
        } catch (e: Exception) {
            Log.e(TAG, "onStartTracking", e)
            showError("추적 시작 실패: ${e.message}")
        }
    }

    private fun onStopTracking() {
        try {
            startService(
                Intent(this, OverlayCursorService::class.java)
                    .apply { action = OverlayCursorService.ACTION_STOP }
            )
        } catch (e: Exception) {
            Log.e(TAG, "onStopTracking", e)
        }
        tracking = false
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        if (uiReady) {
            updatePermissionStatus()
            window?.decorView?.post(permCheckRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        window?.decorView?.removeCallbacks(permCheckRunnable)
    }

    private fun updatePermissionStatus() {
        if (!uiReady) return
        try {
            val cam = PermissionUtils.hasCameraPermission(this)
            val ov = PermissionUtils.hasOverlayPermission(this)
            val acc = PermissionUtils.isAccessibilityServiceEnabled(this)
            setStatus(tvCameraStatus, cam, "✓ 허용됨", "✗ 필요")
            setStatus(tvOverlayStatus, ov, "✓ 허용됨", "✗ 필요")
            setStatus(tvAccessStatus, acc, "✓ 활성화됨", "✗ 비활성화")
            tvTrackingStatus?.text = if (tracking) "추적 중" else "중지됨"
            tvTrackingStatus?.setTextColor(if (tracking) Color.rgb(46, 125, 50) else Color.DKGRAY)
            btnStart?.isEnabled = cam && ov && acc && !tracking
            btnStop?.isEnabled = tracking
        } catch (e: Exception) {
            Log.e(TAG, "updatePermissionStatus", e)
        }
    }

    private fun setStatus(tv: TextView?, ok: Boolean, okText: String, failText: String) {
        tv?.text = if (ok) okText else failText
        tv?.setTextColor(if (ok) Color.rgb(46, 125, 50) else Color.rgb(200, 0, 0))
    }

    private fun showError(msg: String) {
        tvLastError?.text = msg
        tvLastError?.visibility = View.VISIBLE
    }

    private fun addStatusRow(parent: LinearLayout, label: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8; bottomMargin = 8 }
        }
        val lbl = TextView(this).apply {
            text = label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val status = TextView(this).apply {
            text = "확인 중"
            textSize = 16f
        }
        row.addView(lbl)
        row.addView(status)
        parent.addView(row)
        return status
    }

    private fun addButton(
        parent: LinearLayout,
        text: String,
        color: Int,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        this.text = text
        textSize = 16f
        setBackgroundColor(color)
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12 }
        setOnClickListener { onClick() }
        parent.addView(this)
    }

    private fun makeDivider(): View = View(this).apply {
        setBackgroundColor(Color.LTGRAY)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 2
        ).apply { topMargin = 12; bottomMargin = 12 }
    }

    companion object {
        private const val TAG = "EyeTab"
        private const val PREFS = "eyetab_prefs"
        private const val KEY_CRASH = "last_crash"
        private const val REQ_CAMERA = 100
    }
}
