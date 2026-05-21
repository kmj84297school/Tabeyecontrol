package com.example.eyetab

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val tag = "EyeTab"

    private var tvCamera: TextView? = null
    private var tvOverlay: TextView? = null
    private var tvAccess: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUI()
        } catch (e: Exception) {
            Log.e(tag, "onCreate error: ${e.javaClass.simpleName} ${e.message}")
            val tv = TextView(this)
            tv.text = "오류: ${e.javaClass.simpleName}\n${e.message}"
            tv.setPadding(48, 48, 48, 48)
            setContentView(tv)
        }
    }

    private fun buildUI() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 80, 48, 48)

        val title = TextView(this)
        title.text = "EyeTab — 권한 확인"
        title.textSize = 24f
        title.setTextColor(Color.BLACK)
        title.setPadding(0, 0, 0, 32)
        root.addView(title)

        tvCamera = addStatusRow(root, "카메라 권한")
        tvOverlay = addStatusRow(root, "오버레이 권한")
        tvAccess  = addStatusRow(root, "접근성 서비스")

        val space = TextView(this)
        space.setPadding(0, 24, 0, 0)
        root.addView(space)

        addButton(root, "카메라 권한 요청") {
            try {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            } catch (e: Exception) {
                Log.e(tag, "requestPermissions: ${e.javaClass.simpleName} ${e.message}")
            }
        }

        addButton(root, "오버레이 권한 설정 열기") {
            try {
                startActivity(PermissionUtils.overlaySettingsIntent(this))
            } catch (e: Exception) {
                Log.e(tag, "overlay settings: ${e.javaClass.simpleName} ${e.message}")
            }
        }

        addButton(root, "접근성 서비스 설정 열기") {
            try {
                startActivity(PermissionUtils.accessibilitySettingsIntent())
            } catch (e: Exception) {
                Log.e(tag, "accessibility settings: ${e.javaClass.simpleName} ${e.message}")
            }
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        try {
            val cam = PermissionUtils.hasCameraPermission(this)
            val ov  = PermissionUtils.hasOverlayPermission(this)
            val acc = PermissionUtils.isAccessibilityServiceEnabled(this)
            setText(tvCamera, cam, "✓ 허용됨", "✗ 필요")
            setText(tvOverlay, ov,  "✓ 허용됨", "✗ 필요")
            setText(tvAccess,  acc, "✓ 활성화됨", "✗ 비활성화")
        } catch (e: Exception) {
            Log.e(tag, "refreshStatus: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun setText(tv: TextView?, ok: Boolean, yes: String, no: String) {
        tv?.text = if (ok) yes else no
        tv?.setTextColor(if (ok) Color.rgb(46, 125, 50) else Color.rgb(200, 0, 0))
    }

    private fun addStatusRow(parent: LinearLayout, label: String): TextView {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, 8, 0, 8)

        val lbl = TextView(this)
        lbl.text = label
        lbl.textSize = 16f
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lbl.layoutParams = lp
        row.addView(lbl)

        val status = TextView(this)
        status.text = "확인 중"
        status.textSize = 16f
        row.addView(status)

        parent.addView(row)
        return status
    }

    private fun addButton(parent: LinearLayout, label: String, onClick: () -> Unit) {
        val btn = Button(this)
        btn.text = label
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 12
        btn.layoutParams = lp
        btn.setOnClickListener { onClick() }
        parent.addView(btn)
    }
}
