package com.example.photoframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.i("BootReceiver", "Boot completed — launching Photo Frame")
            try {
                // Start the keep-alive foreground service first so the WakeLock
                // is held before any user interaction is required.
                val serviceIntent = Intent(context, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // Then bring up the Activity / UI
                val activityIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(activityIntent)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start Photo Frame on boot", e)
            }
        }
    }
}
