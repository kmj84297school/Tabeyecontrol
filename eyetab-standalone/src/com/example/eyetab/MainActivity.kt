package com.example.eyetab

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 80, 48, 48)

        root.addView(text("EyeTab UI BUILD 2026-05-21", 20f))
        root.addView(text("카메라 권한 상태: 테스트용 표시", 16f))
        root.addView(text("오버레이 권한 상태: 테스트용 표시", 16f))
        root.addView(text("접근성 서비스 상태: 테스트용 표시", 16f))
        root.addView(text("추적 상태: 테스트용 표시", 16f))
        root.addView(btn("카메라 권한 요청"))
        root.addView(btn("오버레이 설정 열기"))
        root.addView(btn("접근성 설정 열기"))
        root.addView(btn("추적 시작"))
        root.addView(btn("추적 중지"))

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun text(s: String, size: Float): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = size
        tv.setPadding(0, 12, 0, 12)
        return tv
    }

    private fun btn(label: String): Button {
        val b = Button(this)
        b.text = label
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 8
        b.layoutParams = lp
        b.setOnClickListener {
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
        }
        return b
    }
}
