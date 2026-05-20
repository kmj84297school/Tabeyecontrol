package com.example.eyetab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class OverlayCursorService : Service() {

    private lateinit var overlay: OverlayCursorManager

    private val trackingListener = object : EyeTrackingManager.Listener {
        override fun onCursorMoved(x: Float, y: Float) = overlay.updatePosition(x, y)
        override fun onTapRequest(x: Float, y: Float) =
            EyeAccessibilityService.instance?.performTap(x, y) ?: Unit
        override fun onScrollRequest(dir: EyeTrackingManager.ScrollDirection, x: Float, y: Float) =
            EyeAccessibilityService.instance?.performScroll(dir, x, y) ?: Unit
        override fun onFaceLost() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayCursorManager(this)
        createChannel()
        val notif = android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("EyeTab 추적 중")
            .setContentText("눈 추적 활성화됨")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(1, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                overlay.show()
                EyeTrackingManager.instance.start(this, trackingListener)
            }
            ACTION_STOP -> {
                EyeTrackingManager.instance.stop()
                overlay.hide()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        EyeTrackingManager.instance.stop()
        overlay.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "EyeTab 추적", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    companion object {
        const val ACTION_START = "com.example.eyetab.START"
        const val ACTION_STOP = "com.example.eyetab.STOP"
        const val CHANNEL_ID = "eyetab_tracking"
    }
}
