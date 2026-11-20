package com.scanwake.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "Sink"
        val ringtoneUri = intent.getStringExtra("RINGTONE_URI")
        val vibrate = intent.getBooleanExtra("VIBRATE", true)

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("TARGET_OBJECT", targetObject)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", vibrate)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
