package com.scanwake.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val targetObjects = listOf(
        "Sink",
        "Bottle",
        "Chair",
        "Cup",
        "Shoe",
        "Desk",
        "Computer",
        "Door",
        "Plant",
        "Book"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        val spinner = findViewById<Spinner>(R.id.objectSpinner)
        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        val btnSetAlarm = findViewById<Button>(R.id.btnSetAlarm)
        val btnTestAlarm = findViewById<Button>(R.id.btnTestAlarm)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, targetObjects)
        spinner.adapter = adapter

        btnPermissions.setOnClickListener {
            requestAllPermissions()
        }

        btnSetAlarm.setOnClickListener {
            if (!hasRequiredPermissions()) {
                Toast.makeText(this, "Pehle permissions grant karein!", Toast.LENGTH_LONG).show()
                requestAllPermissions()
                return@setOnClickListener
            }

            val selectedObject = spinner.selectedItem.toString()
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, timePicker.hour)
                set(Calendar.MINUTE, timePicker.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            scheduleExactAlarm(calendar.timeInMillis, selectedObject)
            val timeText = String.format("%02d:%02d", timePicker.hour, timePicker.minute)
            tvStatus.text = "Alarm set for $timeText | Target: $selectedObject"
            Toast.makeText(this, "Strict Alarm set for $timeText!", Toast.LENGTH_SHORT).show()
        }

        btnTestAlarm.setOnClickListener {
            if (!hasRequiredPermissions()) {
                Toast.makeText(this, "Pehle permissions grant karein!", Toast.LENGTH_LONG).show()
                requestAllPermissions()
                return@setOnClickListener
            }
            val selectedObject = spinner.selectedItem.toString()
            val intent = Intent(this, AlarmService::class.java).apply {
                putExtra("TARGET_OBJECT", selectedObject)
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private fun scheduleExactAlarm(triggerAtMillis: Long, targetObject: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("TARGET_OBJECT", targetObject)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
        alarmManager.setAlarmClock(info, pendingIntent)
    }

    private fun hasRequiredPermissions(): Boolean {
        val camOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val overlayOk = Settings.canDrawOverlays(this)
        return camOk && overlayOk
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 200)

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps' for anti-cheat lock", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}
