package com.scanwake.alarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val targetObjects = listOf(
        "Sink",
        "Bottle",
        "Cup",
        "Chair",
        "Shoe",
        "Door",
        "Desk",
        "Computer",
        "Book",
        "Plant",
        "Toothbrush",
        "Refrigerator"
    )

    private lateinit var tvBadge: TextView
    private lateinit var cardActiveAlarm: MaterialCardView
    private lateinit var tvActiveTime: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvSoundName: TextView
    private lateinit var btnPermissions: MaterialButton
    private lateinit var switchVibrate: SwitchMaterial
    private lateinit var spinner: Spinner

    private var selectedRingtoneUri: String? = null
    private var selectedRingtoneTitle: String = "Default Alarm Sound"

    private val handler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateActiveAlarmUI()
            handler.postDelayed(this, 1000)
        }
    }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                selectedRingtoneUri = uri.toString()
                val ringtone = RingtoneManager.getRingtone(this, uri)
                selectedRingtoneTitle = ringtone?.getTitle(this) ?: "Custom Alarm Sound"
            } else {
                selectedRingtoneUri = null
                selectedRingtoneTitle = "Default Alarm Sound"
            }
            saveSoundPrefs()
            tvSoundName.text = selectedRingtoneTitle
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        spinner = findViewById(R.id.objectSpinner)
        tvBadge = findViewById(R.id.tvBadge)
        cardActiveAlarm = findViewById(R.id.cardActiveAlarm)
        tvActiveTime = findViewById(R.id.tvActiveTime)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvSoundName = findViewById(R.id.tvSoundName)
        switchVibrate = findViewById(R.id.switchVibrate)
        btnPermissions = findViewById(R.id.btnPermissions)
        val btnPickSound = findViewById<MaterialButton>(R.id.btnPickSound)
        val btnSetAlarm = findViewById<MaterialButton>(R.id.btnSetAlarm)
        val btnCancelAlarm = findViewById<MaterialButton>(R.id.btnCancelAlarm)
        val btnTestAlarm = findViewById<MaterialButton>(R.id.btnTestAlarm)

        val adapter = ArrayAdapter(this, R.layout.spinner_item, targetObjects)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = adapter

        loadSavedPrefs()

        btnPickSound.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                val currentUri = selectedRingtoneUri?.let { Uri.parse(it) }
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            }
            ringtonePickerLauncher.launch(intent)
        }

        switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("scanwake_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("vibrate_enabled", isChecked).apply()
        }

        btnPermissions.setOnClickListener {
            requestAllPermissions()
        }

        btnSetAlarm.setOnClickListener {
            if (!hasRequiredPermissions()) {
                Toast.makeText(this, "Pehle Camera aur Overlay permissions allow karein!", Toast.LENGTH_LONG).show()
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
            updateActiveAlarmUI()
            Toast.makeText(this, "Strict Alarm set!", Toast.LENGTH_SHORT).show()
        }

        btnCancelAlarm.setOnClickListener {
            cancelScheduledAlarm()
            updateActiveAlarmUI()
            Toast.makeText(this, "Scheduled Alarm Cancelled", Toast.LENGTH_SHORT).show()
        }

        btnTestAlarm.setOnClickListener {
            if (!hasRequiredPermissions()) {
                Toast.makeText(this, "Pehle permissions allow karein!", Toast.LENGTH_LONG).show()
                requestAllPermissions()
                return@setOnClickListener
            }
            val selectedObject = spinner.selectedItem.toString()
            val intent = Intent(this, AlarmService::class.java).apply {
                putExtra("TARGET_OBJECT", selectedObject)
                putExtra("RINGTONE_URI", selectedRingtoneUri)
                putExtra("VIBRATE", switchVibrate.isChecked)
            }
            ContextCompat.startForegroundService(this, intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButton()
        updateActiveAlarmUI()
        handler.removeCallbacks(countdownRunnable)
        handler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(countdownRunnable)
    }

    private fun loadSavedPrefs() {
        val prefs = getSharedPreferences("scanwake_prefs", Context.MODE_PRIVATE)
        selectedRingtoneUri = prefs.getString("ringtone_uri", null)
        selectedRingtoneTitle = prefs.getString("ringtone_title", "Default Alarm Sound") ?: "Default Alarm Sound"
        tvSoundName.text = selectedRingtoneTitle
        switchVibrate.isChecked = prefs.getBoolean("vibrate_enabled", true)

        val savedTarget = prefs.getString("target_object", "Sink")
        val idx = targetObjects.indexOf(savedTarget)
        if (idx >= 0) spinner.setSelection(idx)
    }

    private fun saveSoundPrefs() {
        getSharedPreferences("scanwake_prefs", Context.MODE_PRIVATE).edit()
            .putString("ringtone_uri", selectedRingtoneUri)
            .putString("ringtone_title", selectedRingtoneTitle)
            .apply()
    }

    private fun scheduleExactAlarm(triggerAtMillis: Long, targetObject: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("TARGET_OBJECT", targetObject)
            putExtra("RINGTONE_URI", selectedRingtoneUri)
            putExtra("VIBRATE", switchVibrate.isChecked)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val info = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
        alarmManager.setAlarmClock(info, pendingIntent)

        getSharedPreferences("scanwake_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("alarm_active", true)
            .putLong("alarm_time_millis", triggerAtMillis)
            .putString("target_object", targetObject)
            .apply()
    }

    private fun cancelScheduledAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        getSharedPreferences("scanwake_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("alarm_active", false)
            .apply()
    }

    private fun updateActiveAlarmUI() {
        val prefs = getSharedPreferences("scanwake_prefs", Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean("alarm_active", false)
        val triggerMillis = prefs.getLong("alarm_time_millis", 0L)
        val target = prefs.getString("target_object", "Sink") ?: "Sink"
        val remaining = triggerMillis - System.currentTimeMillis()

        if (isActive && remaining > 0) {
            cardActiveAlarm.visibility = View.VISIBLE
            tvBadge.text = "ACTIVE"
            tvBadge.setTextColor(0xFF4CAF50.toInt())

            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(triggerMillis))
            tvActiveTime.text = "Alarm: $timeStr  •  Target: $target"

            val totalSecs = remaining / 1000
            val hours = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            val secs = totalSecs % 60
            tvCountdown.text = String.format(Locale.getDefault(), "Rings in %02dh %02dm %02ds", hours, mins, secs)
        } else {
            cardActiveAlarm.visibility = View.GONE
            tvBadge.text = "OFF"
            tvBadge.setTextColor(0xFF9AA0A6.toInt())
        }
    }

    private fun updatePermissionButton() {
        btnPermissions.text = if (hasRequiredPermissions()) "Permissions OK" else "Permissions Needed"
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
