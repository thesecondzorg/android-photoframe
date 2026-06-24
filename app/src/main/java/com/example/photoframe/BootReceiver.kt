package com.example.photoframe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.i("BootReceiver", "Boot completed broadcast received. Launching MainActivity...")
            try {
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(startIntent)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start MainActivity on boot", e)
            }
        }
    }
}
