package com.pulsebridge

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvBpm: TextView
    private lateinit var tvLogs: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnToggle: Button

    private var isRunning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.pulsebridge.LOG" -> {
                    val msg = intent.getStringExtra("msg") ?: return
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    tvLogs.append("[$time] $msg\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
                "com.pulsebridge.STATUS" -> {
                    val status = intent.getStringExtra("status") ?: return
                    tvStatus.text = status
                }
                "com.pulsebridge.BPM" -> {
                    val bpm = intent.getIntExtra("bpm", 0)
                    tvBpm.text = if (bpm > 0) "$bpm BPM" else "-- BPM"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Верстка интерфейса программно
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xFF121218.toInt())
        }

        tvStatus = TextView(this).apply {
            text = "Статус: Остановлено"
            setTextColor(0xFFFFA502.toInt())
            textSize = 16f
        }

        tvBpm = TextView(this).apply {
            text = "-- BPM"
            setTextColor(0xFFFF4757.toInt())
            textSize = 44f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        btnToggle = Button(this).apply {
            text = "СТАРТ ТРАНСЛЯЦИИ"
            setBackgroundColor(0xFF2ED573.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setOnClickListener {
                if (!isRunning) {
                    checkPermsAndStart()
                } else {
                    stopBridgeService()
                }
            }
        }

        val logHeader = TextView(this).apply {
            text = "\nЖурнал событий (Логи):"
            setTextColor(0xFF888888.toInt())
            textSize = 13f
        }

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            setBackgroundColor(0xFF0A0A0E.toInt())
            setPadding(16, 16, 16, 16)
        }

        tvLogs = TextView(this).apply {
            setTextColor(0xFF00FF66.toInt())
            textSize = 12f
            setTypeface(android.graphics.Typeface.MONOSPACE)
        }
        scrollView.addView(tvLogs)

        root.addView(tvStatus)
        root.addView(tvBpm)
        root.addView(btnToggle)
        root.addView(logHeader)
        root.addView(scrollView)

        setContentView(root)

        val filter = IntentFilter().apply {
            addAction("com.pulsebridge.LOG")
            addAction("com.pulsebridge.STATUS")
            addAction("com.pulsebridge.BPM")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun checkPermsAndStart() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        } else {
            startBridgeService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startBridgeService()
    }

    private fun startBridgeService() {
        isRunning = true
        btnToggle.text = "ОСТАНОВИТЬ"
        btnToggle.setBackgroundColor(0xFFFF4757.toInt())
        val intent = Intent(this, PulseBleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBridgeService() {
        isRunning = false
        btnToggle.text = "СТАРТ ТРАНСЛЯЦИИ"
        btnToggle.setBackgroundColor(0xFF2ED573.toInt())
        stopService(Intent(this, PulseBleService::class.java))
        tvStatus.text = "Статус: Остановлено"
        tvBpm.text = "-- BPM"
    }
}
