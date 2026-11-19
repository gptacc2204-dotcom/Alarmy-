package com.scanwake.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AlarmActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var isVisible = false
    }

    private lateinit var previewView: PreviewView
    private lateinit var tvTargetTitle: TextView
    private lateinit var tvDetected: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var targetObject: String = "Sink"
    private var isStopped = false
    private var emergencyTapCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen & keep screen awake
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContentView(R.layout.activity_alarm)

        targetObject = intent.getStringExtra("TARGET_OBJECT") ?: "Sink"
        previewView = findViewById(R.id.previewView)
        tvTargetTitle = findViewById(R.id.tvTargetTitle)
        tvDetected = findViewById(R.id.tvDetected)

        tvTargetTitle.text = "SCAN: ${targetObject.uppercase()}"

        // Hidden Emergency Exit: Tap title 25 times in case of total darkness
        tvTargetTitle.setOnClickListener {
            emergencyTapCount++
            if (emergencyTapCount >= 25) {
                Toast.makeText(this, "Emergency Exit Triggered", Toast.LENGTH_SHORT).show()
                stopAlarmAndFinish()
            }
        }

        // Block Back Button completely
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@AlarmActivity, "Scan $targetObject to stop!", Toast.LENGTH_SHORT).show()
            }
        })

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
    }

    override fun onPause() {
        super.onPause()
        isVisible = false
    }

    // Block Volume Up / Down hardware keys
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.65f)
                    .build()
            )

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy, labeler)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(
        imageProxy: ImageProxy,
        labeler: com.google.mlkit.vision.label.ImageLabeler
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && !isStopped) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    if (isStopped) return@addOnSuccessListener

                    val topLabels = labels.take(3).joinToString(" | ") {
                        "${it.text} (${(it.confidence * 100).toInt()}%)"
                    }
                    runOnUiThread {
                        tvDetected.text = if (topLabels.isEmpty()) "Scanning..." else topLabels
                    }

                    val matched = labels.any {
                        it.text.equals(targetObject, ignoreCase = true) ||
                        it.text.contains(targetObject, ignoreCase = true)
                    }
                    if (matched) {
                        stopAlarmAndFinish()
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun stopAlarmAndFinish() {
        if (isStopped) return
        isStopped = true
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        runOnUiThread {
            Toast.makeText(this, "$targetObject Verified! Alarm Stopped.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
