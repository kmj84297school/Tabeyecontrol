package com.example.eyetab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

class OverlayCursorService : Service() {

    private var overlay: OverlayCursorManager? = null
    private var started = false

    private val trackingListener = object : EyeTrackingManager.Listener {
        override fun onCursorMoved(x: Float, y: Float) {
            try { overlay?.updatePosition(x, y) } catch (e: Exception) { Log.e(TAG, "onCursorMoved", e) }
        }
        override fun onTapRequest(x: Float, y: Float) {
            try { EyeAccessibilityService.instance?.performTap(x, y) } catch (e: Exception) { Log.e(TAG, "onTapRequest", e) }
        }
        override fun onScrollRequest(dir: EyeTrackingManager.ScrollDirection, x: Float, y: Float) {
            try { EyeAccessibilityService.instance?.performScroll(dir, x, y) } catch (e: Exception) { Log.e(TAG, "onScrollRequest", e) }
        }
        override fun onFaceLost() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
            val notif = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate failed", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> doStart()
            ACTION_STOP -> doStop()
        }
        return START_NOT_STICKY
    }

    private fun doStart() {
        if (started) return
        try {
            if (!PermissionUtils.hasOverlayPermission(this)) {
                Log.e(TAG, "No overlay permission")
                stopSelf(); return
            }
            if (!PermissionUtils.hasCameraPermission(this)) {
                Log.e(TAG, "No camera permission")
                stopSelf(); return
            }
            val mgr = OverlayCursorManager(this)
            overlay = mgr
            mgr.show()
            EyeTrackingManager.instance.start(this, trackingListener)
            started = true
        } catch (e: Exception) {
            Log.e(TAG, "doStart failed", e)
            stopSelf()
        }
    }

    private fun doStop() {
        started = false
        try { EyeTrackingManager.instance.stop() } catch (e: Exception) { Log.e(TAG, "stop tracker", e) }
        try { overlay?.hide() } catch (e: Exception) { Log.e(TAG, "hide overlay", e) }
        overlay = null
        stopSelf()
    }

    override fun onDestroy() {
        started = false
        try { EyeTrackingManager.instance.stop() } catch (e: Exception) { Log.e(TAG, "onDestroy stop", e) }
        try { overlay?.hide() } catch (e: Exception) { Log.e(TAG, "onDestroy hide", e) }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "EyeTab", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("EyeTab 추적 중")
            .setContentText("눈 추적 활성화됨")
            .setSmallIcon(R.drawable.ic_stat_eyetab)
            .build()

    companion object {
        private const val TAG = "EyeTab"
        private const val NOTIF_ID = 1
        const val CHANNEL_ID = "eyetab_tracking"
        const val ACTION_START = "com.example.eyetab.START"
        const val ACTION_STOP = "com.example.eyetab.STOP"
    }
}
