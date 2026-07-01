package com.example.photoframe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class KeepAliveService : Service() {

    private val tag = "KeepAliveService"
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "photoframe_keepalive"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()
        Log.i(tag, "KeepAliveService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system kills the service, restart it automatically
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        Log.i(tag, "KeepAliveService destroyed")
    }

    // ── Notification ────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Photo Frame",
            NotificationManager.IMPORTANCE_MIN   // silent, no sound, no pop-up
        ).apply {
            description = "Keeps the photo frame slideshow running"
            setShowBadge(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Photo Frame")
            .setContentText("Slideshow is running")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setOngoing(true)          // cannot be swiped away
            .setShowWhen(false)
            .build()
    }

    // ── WakeLock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "photoframe:keepalive"
        ).apply {
            acquire()   // held indefinitely; released in onDestroy
        }
        Log.d(tag, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(tag, "WakeLock released")
            }
        }
        wakeLock = null
    }
}
