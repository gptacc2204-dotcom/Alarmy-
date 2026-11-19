package com.scanwake.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "Sink"
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("TARGET_OBJECT", targetObject)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
