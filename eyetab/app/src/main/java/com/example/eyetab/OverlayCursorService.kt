package com.example.eyetab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class OverlayCursorService : LifecycleService() {

    private lateinit var overlay: OverlayCursorManager

    private val trackingListener = object : EyeTrackingManager.Listener {
        override fun onCursorMoved(x: Float, y: Float) = overlay.updatePosition(x, y)
        override fun onTapRequest(x: Float, y: Float) =
            EyeAccessibilityService.instance?.performTap(x, y) ?: Unit
        override fun onScrollRequest(
            direction: EyeTrackingManager.ScrollDirection, x: Float, y: Float
        ) = EyeAccessibilityService.instance?.performScroll(direction, x, y) ?: Unit
        override fun onFaceLost() = Unit
    }

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayCursorManager(this)
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EyeTab 추적 중")
            .setContentText("눈 추적이 활성화됩니다")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(NOTIF_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                overlay.show()
                EyeTrackingManager.instance.start(this, this, trackingListener)
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

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "EyeTab 추적", NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.eyetab.START"
        const val ACTION_STOP = "com.example.eyetab.STOP"
        private const val CHANNEL_ID = "eyetab_tracking"
        private const val NOTIF_ID = 1
    }
}
