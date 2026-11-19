package com.scanwake.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    companion object {
        const val ACTION_STOP_ALARM = "com.scanwake.alarm.STOP_ALARM"
        private const val CHANNEL_ID = "scanwake_alarm_channel"
        @Volatile
        var isAlarmRunning = false
    }

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var currentTarget = "Sink"

    private val lockdownRunnable = object : Runnable {
        override fun run() {
            if (!isAlarmRunning) return

            // 1. Force Alarm Volume to 100% (Anti-Volume-Down)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVol < maxVol) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
            }

            // 2. Keep AlarmActivity on top if user tries to escape via Home/Recents
            if (!AlarmActivity.isVisible) {
                launchAlarmScreen()
            }

            handler.postDelayed(this, 600)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarmAndService()
            return START_NOT_STICKY
        }

        currentTarget = intent?.getStringExtra("TARGET_OBJECT") ?: "Sink"
        isAlarmRunning = true

        startForeground(1, buildNotification())
        startLoudAlarmSound()
        launchAlarmScreen()

        handler.removeCallbacks(lockdownRunnable)
        handler.post(lockdownRunnable)

        return START_STICKY
    }

    private fun startLoudAlarmSound() {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun launchAlarmScreen() {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("TARGET_OBJECT", currentTarget)
        }
        startActivity(fullScreenIntent)
    }

    private fun buildNotification(): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("TARGET_OBJECT", currentTarget)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ScanWake Alarm Active!")
            .setContentText("Scan '$currentTarget' to turn off alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(pendingIntent, true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ScanWake Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Strict object-scanning alarm channel"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopAlarmAndService() {
        isAlarmRunning = false
        handler.removeCallbacks(lockdownRunnable)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(lockdownRunnable)
        mediaPlayer?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
