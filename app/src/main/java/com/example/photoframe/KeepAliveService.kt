package com.example.photoframe

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import java.io.File

class KeepAliveService : Service() {

    private val tag = "KeepAliveService"
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Intent.ACTION_SCREEN_OFF == intent.action) {
                handleScreenOff(context)
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "photoframe_keepalive"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireWakeLock()

        // Register receiver to detect when the screen is turned off
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        Log.i(tag, "KeepAliveService started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system kills the service, restart it automatically
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            Log.e(tag, "Failed to unregister screenOffReceiver", e)
        }
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

    // ── Auto-Wake Recovery ───────────────────────────────────────────────────

    private fun handleScreenOff(context: Context) {
        Log.i(tag, "Screen turned off. Checking if we should auto-wake...")

        // Wait 3 seconds to let screen state settle and avoid visual jitter
        handler.postDelayed({
            val settings = getSettingsJson()
            val inDim = isInDimWindow(settings)
            Log.d(tag, "Screen off check: inDimWindow = $inDim")

            if (!inDim) {
                Log.i(tag, "Screen turned off outside night hours. Auto-waking screen now...")
                try {
                    val activityIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    context.startActivity(activityIntent)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to auto-wake screen", e)
                }
            } else {
                Log.i(tag, "Screen turned off during night hours. Leaving screen off.")
            }
        }, 3000)
    }

    private fun getSettingsJson(): String? {
        val thumbnailsDir = getExternalFilesDir("thumbnails") ?: File(cacheDir, "thumbnails")
        val settingsFile = File(thumbnailsDir, "settings.json")
        return if (settingsFile.exists()) settingsFile.readText() else null
    }

    private fun isInDimWindow(settingsJson: String?): Boolean {
        if (settingsJson == null) return false
        try {
            val settings = org.json.JSONObject(settingsJson)
            if (!settings.optBoolean("dimEnabled", false)) return false

            val now = java.util.Calendar.getInstance()
            val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

            val parseTime = { timeStr: String ->
                val parts = timeStr.split(":")
                if (parts.size == 2) {
                    parts[0].toInt() * 60 + parts[1].toInt()
                } else {
                    0
                }
            }

            val fromMinutes = parseTime(settings.optString("dimFrom", "22:00"))
            val toMinutes = parseTime(settings.optString("dimTo", "07:00"))

            return if (fromMinutes <= toMinutes) {
                currentMinutes in fromMinutes until toMinutes
            } else {
                currentMinutes >= fromMinutes || currentMinutes < toMinutes
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing settings for dim window check", e)
            return false
        }
    }
}
