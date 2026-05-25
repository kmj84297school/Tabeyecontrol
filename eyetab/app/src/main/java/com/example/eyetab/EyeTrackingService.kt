package com.example.eyetab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class EyeTrackingService : LifecycleService() {

    private val tag = "EyeTab"
    private var overlay: OverlayCursorManager? = null
    private var started = false

    private val listener = object : EyeTrackingManager.Listener {
        override fun onCursorMoved(x: Float, y: Float) {
            try { overlay?.updatePosition(x, y) } catch (e: Exception) { Log.e(tag, "onCursorMoved", e) }
        }
        override fun onTapRequest(x: Float, y: Float) {
            try { EyeAccessibilityService.instance?.performTap(x, y) } catch (e: Exception) { Log.e(tag, "onTapRequest", e) }
        }
        override fun onScrollRequest(direction: EyeTrackingManager.ScrollDirection, x: Float, y: Float) {
            try { EyeAccessibilityService.instance?.performScroll(direction, x, y) } catch (e: Exception) { Log.e(tag, "onScrollRequest", e) }
        }
        override fun onFaceLost() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_stat_eyetab)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            Log.e(tag, "onCreate failed", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> doStart()
            ACTION_STOP -> doStop()
        }
        return START_NOT_STICKY
    }

    private fun doStart() {
        if (started) return
        try {
            if (!PermissionUtils.hasOverlayPermission(this) ||
                !PermissionUtils.hasCameraPermission(this)) {
                Log.e(tag, "missing required permissions")
                stopSelf(); return
            }
            val o = OverlayCursorManager(this)
            overlay = o
            o.show()
            EyeTrackingManager.instance.start(this, this, listener)
            started = true
        } catch (e: Exception) {
            Log.e(tag, "doStart failed", e)
            stopSelf()
        }
    }

    private fun doStop() {
        started = false
        try { EyeTrackingManager.instance.stop() } catch (e: Exception) { Log.e(tag, "stop tracker", e) }
        try { overlay?.hide() } catch (e: Exception) { Log.e(tag, "hide overlay", e) }
        overlay = null
        stopSelf()
    }

    override fun onDestroy() {
        started = false
        try { EyeTrackingManager.instance.stop() } catch (e: Exception) { Log.e(tag, "onDestroy stop", e) }
        try { overlay?.hide() } catch (e: Exception) { Log.e(tag, "onDestroy hide", e) }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    companion object {
        const val ACTION_START = "com.example.eyetab.START"
        const val ACTION_STOP = "com.example.eyetab.STOP"
        private const val CHANNEL_ID = "eyetab_tracking"
        private const val NOTIF_ID = 1
    }
}
