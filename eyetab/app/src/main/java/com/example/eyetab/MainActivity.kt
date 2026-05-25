package com.example.eyetab

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.eyetab.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val tag = "EyeTab"
    private lateinit var binding: ActivityMainBinding
    private var tracking = false

    private val cameraPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermissions()
        }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshPermissions()
            binding.root.postDelayed(this, 800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            wireUp()
        } catch (e: Exception) {
            Log.e(tag, "onCreate failed", e)
            // Fallback: show plain TextView so we can still see something
            val tv = TextView(this)
            tv.text = "EyeTab 초기화 실패: ${e.javaClass.simpleName}\n${e.message}"
            tv.setPadding(48, 96, 48, 48)
            setContentView(tv)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun wireUp() {
        binding.btnRequestCamera.setOnClickListener {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
        binding.btnOpenOverlay.setOnClickListener {
            safeStartActivity { PermissionUtils.overlaySettingsIntent(this) }
        }
        binding.btnOpenAccessibility.setOnClickListener {
            safeStartActivity { PermissionUtils.accessibilitySettingsIntent() }
        }
        binding.btnStart.setOnClickListener { onStart() }
        binding.btnStop.setOnClickListener { onStop() }
    }

    private fun safeStartActivity(intent: () -> Intent) {
        try {
            startActivity(intent())
        } catch (e: Exception) {
            Log.e(tag, "startActivity failed", e)
        }
    }

    private fun onStart() {
        if (!PermissionUtils.hasCameraPermission(this)
            || !PermissionUtils.hasOverlayPermission(this)
            || !PermissionUtils.isAccessibilityServiceEnabled(this)
        ) return
        try {
            val intent = Intent(this, EyeTrackingService::class.java)
                .apply { action = EyeTrackingService.ACTION_START }
            ContextCompat.startForegroundService(this, intent)
            tracking = true
            refreshPermissions()
        } catch (e: Exception) {
            Log.e(tag, "start tracking failed", e)
        }
    }

    private fun onStop() {
        try {
            val intent = Intent(this, EyeTrackingService::class.java)
                .apply { action = EyeTrackingService.ACTION_STOP }
            startService(intent)
            tracking = false
            refreshPermissions()
        } catch (e: Exception) {
            Log.e(tag, "stop tracking failed", e)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        binding.root.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        binding.root.removeCallbacks(pollRunnable)
    }

    private fun refreshPermissions() {
        try {
            val cam = PermissionUtils.hasCameraPermission(this)
            val ov  = PermissionUtils.hasOverlayPermission(this)
            val acc = PermissionUtils.isAccessibilityServiceEnabled(this)

            setStatus(binding.tvCameraStatus, cam)
            setStatus(binding.tvOverlayStatus, ov)
            setStatus(binding.tvAccessStatus, acc)

            binding.btnRequestCamera.isEnabled = !cam
            binding.btnOpenOverlay.isEnabled   = !ov
            binding.btnOpenAccessibility.isEnabled = !acc

            val allOk = cam && ov && acc
            binding.btnStart.isEnabled = allOk && !tracking
            binding.btnStop.isEnabled  = tracking
        } catch (e: Exception) {
            Log.e(tag, "refreshPermissions failed", e)
        }
    }

    private fun setStatus(tv: TextView, ok: Boolean) {
        tv.text = getString(if (ok) R.string.status_granted else R.string.status_needed)
        tv.setTextColor(ContextCompat.getColor(
            this, if (ok) R.color.status_ok else R.color.status_fail
        ))
    }
}
