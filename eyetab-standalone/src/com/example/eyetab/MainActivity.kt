package com.example.eyetab

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var tvCameraStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private var tracking = false

    private val permCheckRunnable = object : Runnable {
        override fun run() {
            updatePermissionStatus()
            window.decorView.postDelayed(this, 800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }
        scroll.addView(root)
        setContentView(scroll)

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 28f
            setTextColor(Color.BLACK)
            setPadding(0, 16, 0, 8)
        }

        fun statusRow(name: String): Pair<TextView, TextView> {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val lbl = TextView(this).apply { text = name; textSize = 16f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val status = TextView(this).apply { text = "확인 중"; textSize = 16f }
            row.addView(lbl)
            row.addView(status)
            root.addView(row)
            return Pair(lbl, status)
        }

        root.addView(label("EyeTab"))

        val divider1 = View(this).apply { setBackgroundColor(Color.LTGRAY); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 8; bottomMargin = 8 } }
        root.addView(divider1)

        val (_, camStatus) = statusRow("카메라 권한")
        tvCameraStatus = camStatus
        val (_, overlayStatus) = statusRow("오버레이 권한")
        tvOverlayStatus = overlayStatus
        val (_, accessStatus) = statusRow("접근성 서비스")
        tvAccessStatus = accessStatus

        val divider2 = View(this).apply { setBackgroundColor(Color.LTGRAY); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 8; bottomMargin = 8 } }
        root.addView(divider2)

        fun btn(text: String, color: Int, action: () -> Unit): Button {
            val b = Button(this).apply {
                this.text = text
                textSize = 16f
                setBackgroundColor(color)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 }
                setOnClickListener { action() }
            }
            root.addView(b)
            return b
        }

        btn("1. 카메라 권한 요청", Color.rgb(33, 150, 243)) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }
        btn("2. 오버레이 권한 설정", Color.rgb(33, 150, 243)) {
            startActivity(PermissionUtils.overlaySettingsIntent(this))
        }
        btn("3. 접근성 서비스 설정", Color.rgb(33, 150, 243)) {
            startActivity(PermissionUtils.accessibilitySettingsIntent())
        }

        btnStart = btn("▶ 추적 시작", Color.rgb(46, 125, 50)) {
            if (PermissionUtils.hasCameraPermission(this) &&
                PermissionUtils.hasOverlayPermission(this) &&
                PermissionUtils.isAccessibilityServiceEnabled(this)) {
                val intent = Intent(this, OverlayCursorService::class.java)
                    .apply { action = OverlayCursorService.ACTION_START }
                startForegroundService(intent)
                tracking = true
                updatePermissionStatus()
            }
        }

        btnStop = btn("■ 추적 중지", Color.rgb(183, 28, 28)) {
            val intent = Intent(this, OverlayCursorService::class.java)
                .apply { action = OverlayCursorService.ACTION_STOP }
            startService(intent)
            tracking = false
            updatePermissionStatus()
        }

        val hint = TextView(this).apply {
            text = """

                사용법:
                • 짧은 눈 감기(120~600ms) → 탭
                • 긴 눈 감기(800ms+) → 스크롤
                • 커서 위쪽 25% → 위로 스크롤
                • 커서 아래쪽 25% → 아래로 스크롤

                튜닝(Constants.kt):
                • MOVEMENT_GAIN = 2.2
                • SHORT_BLINK_MIN/MAX_MS
                • SMOOTHING_WINDOW = 8
            """.trimIndent()
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }
        root.addView(hint)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        window.decorView.post(permCheckRunnable)
    }

    override fun onPause() {
        super.onPause()
        window.decorView.removeCallbacks(permCheckRunnable)
    }

    private fun updatePermissionStatus() {
        val cam = PermissionUtils.hasCameraPermission(this)
        val ov = PermissionUtils.hasOverlayPermission(this)
        val acc = PermissionUtils.isAccessibilityServiceEnabled(this)
        tvCameraStatus.text = if (cam) "✓ OK" else "✗ 필요"
        tvCameraStatus.setTextColor(if (cam) Color.rgb(46, 125, 50) else Color.rgb(183, 28, 28))
        tvOverlayStatus.text = if (ov) "✓ OK" else "✗ 필요"
        tvOverlayStatus.setTextColor(if (ov) Color.rgb(46, 125, 50) else Color.rgb(183, 28, 28))
        tvAccessStatus.text = if (acc) "✓ OK" else "✗ 필요"
        tvAccessStatus.setTextColor(if (acc) Color.rgb(46, 125, 50) else Color.rgb(183, 28, 28))
        btnStart.isEnabled = cam && ov && acc && !tracking
        btnStop.isEnabled = tracking
    }
}
