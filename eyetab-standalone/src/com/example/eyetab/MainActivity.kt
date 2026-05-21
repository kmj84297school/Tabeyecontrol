package com.example.eyetab

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "EyeTab Safe Mode 실행됨"
        tv.textSize = 24f
        tv.setPadding(48, 48, 48, 48)
        setContentView(tv)
    }
}
