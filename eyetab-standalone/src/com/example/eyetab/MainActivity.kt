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
        root.setPadding(48, 48, 48, 48)

        val title = TextView(this)
        title.text = "EyeTab CRASH TEST BUILD 2026-05-21"
        title.textSize = 26f
        title.setPadding(0, 0, 0, 32)

        val status = TextView(this)
        status.text = "앱 실행 성공. 현재는 기능을 모두 제거한 테스트 화면입니다."
        status.textSize = 18f
        status.setPadding(0, 0, 0, 32)

        val cameraButton = Button(this)
        cameraButton.text = "카메라 권한 버튼 테스트"
        cameraButton.setOnClickListener {
            Toast.makeText(this, "카메라 버튼 테스트", Toast.LENGTH_SHORT).show()
        }

        val overlayButton = Button(this)
        overlayButton.text = "오버레이 버튼 테스트"
        overlayButton.setOnClickListener {
            Toast.makeText(this, "오버레이 버튼 테스트", Toast.LENGTH_SHORT).show()
        }

        val accessibilityButton = Button(this)
        accessibilityButton.text = "접근성 버튼 테스트"
        accessibilityButton.setOnClickListener {
            Toast.makeText(this, "접근성 버튼 테스트", Toast.LENGTH_SHORT).show()
        }

        val startButton = Button(this)
        startButton.text = "추적 시작 버튼 테스트"
        startButton.setOnClickListener {
            Toast.makeText(this, "추적 시작 버튼 테스트", Toast.LENGTH_SHORT).show()
        }

        root.addView(title)
        root.addView(status)
        root.addView(cameraButton)
        root.addView(overlayButton)
        root.addView(accessibilityButton)
        root.addView(startButton)

        val scrollView = ScrollView(this)
        scrollView.addView(root)

        setContentView(scrollView)
    }
}
