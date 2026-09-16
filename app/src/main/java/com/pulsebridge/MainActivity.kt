package com.pulsebridge

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvHeartIcon: TextView
    private lateinit var tvBpm: TextView
    private lateinit var tvBpmLabel: TextView
    private lateinit var tvStepsVal: TextView
    private lateinit var tvBatteryVal: TextView
    private lateinit var tvLogs: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnToggle: Button
    private lateinit var btnCopy: Button
    private lateinit var btnClear: Button

    private var heartAnimator: ObjectAnimator? = null
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
                    updateStatusBadge(status)
                }
                "com.pulsebridge.BPM" -> {
                    val bpm = intent.getIntExtra("bpm", 0)
                    if (bpm > 0) {
                        tvBpm.text = bpm.toString()
                        tvBpm.setTextColor(Color.parseColor("#FF2D55"))
                        startHeartPulseAnimation()
                    } else {
                        tvBpm.text = "--"
                        tvBpm.setTextColor(Color.parseColor("#6B7280"))
                        stopHeartPulseAnimation()
                    }
                }
                "com.pulsebridge.STATS" -> {
                    val steps = intent.getIntExtra("steps", 0)
                    tvStepsVal.text = if (steps > 0) "$steps" else "--"
                }
                "com.pulsebridge.BATTERY" -> {
                    val level = intent.getIntExtra("level", 0)
                    if (level > 0) {
                        tvBatteryVal.text = "$level%"
                        tvBatteryVal.setTextColor(
                            if (level > 20) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
                        )
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0D14"))
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }

        // 1. HEADER (Title + Status Pill)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvAppTitle = TextView(this).apply {
            text = "⚡ PulseBridge"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFFFFF"))
        }

        val tvAppSub = TextView(this).apply {
            text = "Xiaomi Smart Band 9 Active"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        }

        titleBox.addView(tvAppTitle)
        titleBox.addView(tvAppSub)

        tvStatusBadge = TextView(this).apply {
            text = "● Остановлено"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            background = makeDrawable(Color.parseColor("#1E2433"), radius = dp(14).toFloat(), strokeColor = Color.parseColor("#2E384D"), strokeWidth = dp(1))
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        headerRow.addView(titleBox)
        headerRow.addView(tvStatusBadge)
        root.addView(headerRow)

        // 2. HERO CARD (Heart Rate & Mini Stats)
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = makeDrawable(
                bgColor = Color.parseColor("#121622"),
                radius = dp(20).toFloat(),
                strokeColor = Color.parseColor("#1E2538"),
                strokeWidth = dp(1)
            )
            setPadding(dp(20), dp(20), dp(20), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        tvHeartIcon = TextView(this).apply {
            text = "❤️"
            textSize = 34f
            gravity = Gravity.CENTER
        }

        tvBpm = TextView(this).apply {
            text = "--"
            textSize = 56f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.CENTER
        }

        tvBpmLabel = TextView(this).apply {
            text = "УДАРОВ В МИНУТУ (BPM)"
            textSize = 11f
            letterSpacing = 0.15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
        }

        // Mini metrics: ШАГИ | ЗАРЯД | УСТРОЙСТВО
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }

        val stepBadge = createMiniMetric("ШАГИ", "--").also { tvStepsVal = it.second }
        val batBadge = createMiniMetric("ЗАРЯД", "--%").also { tvBatteryVal = it.second }
        val macBadge = createMiniMetric("УСТРОЙСТВО", "Band 9 Active")

        metricsRow.addView(stepBadge.first)
        metricsRow.addView(batBadge.first)
        metricsRow.addView(macBadge.first)

        heroCard.addView(tvHeartIcon)
        heroCard.addView(tvBpm)
        heroCard.addView(tvBpmLabel)
        heroCard.addView(metricsRow)
        root.addView(heroCard)

        // 3. ACTION BUTTONS
        btnToggle = Button(this).apply {
            text = "▶  СТАРТ МОНИТОРИНГА"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeButtonDrawable(
                normalColor = Color.parseColor("#10B981"),
                pressedColor = Color.parseColor("#059669"),
                radius = dp(16).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply { bottomMargin = dp(10) }

            setOnClickListener {
                if (!isRunning) {
                    checkPermsAndStart()
                } else {
                    stopBridgeService()
                }
            }
        }
        root.addView(btnToggle)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        }

        btnCopy = Button(this).apply {
            text = "📋 СКОПИРОВАТЬ ЛОГ"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeButtonDrawable(
                normalColor = Color.parseColor("#3B82F6"),
                pressedColor = Color.parseColor("#2563EB"),
                radius = dp(14).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PulseBridgeLogs", tvLogs.text.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@MainActivity, "✅ Логи скопированы в буфер обмена!", Toast.LENGTH_SHORT).show()
            }
        }

        btnClear = Button(this).apply {
            text = "🗑 ОЧИСТИТЬ"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            background = makeButtonDrawable(
                normalColor = Color.parseColor("#1E2433"),
                pressedColor = Color.parseColor("#2A3347"),
                radius = dp(14).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(44))
            setOnClickListener {
                tvLogs.text = ""
            }
        }

        btnRow.addView(btnCopy)
        btnRow.addView(btnClear)
        root.addView(btnRow)

        // 4. LIVE CONSOLE / LOGS CARD
        val consoleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }

        val tvConsoleTitle = TextView(this).apply {
            text = "ЖУРНАЛ СОБЫТИЙ (BLE LOGS)"
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvLiveDot = TextView(this).apply {
            text = "● LIVE"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
        }

        consoleHeader.addView(tvConsoleTitle)
        consoleHeader.addView(tvLiveDot)
        root.addView(consoleHeader)

        val consoleCard = FrameLayout(this).apply {
            background = makeDrawable(
                bgColor = Color.parseColor("#06080D"),
                radius = dp(16).toFloat(),
                strokeColor = Color.parseColor("#181E2E"),
                strokeWidth = dp(1)
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        tvLogs = TextView(this).apply {
            setTextColor(Color.parseColor("#34D399"))
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setLineSpacing(dp(2).toFloat(), 1f)
            text = "[PulseBridge] Готов к работе. Нажмите «Старт» для подключения.\n"
        }

        scrollView.addView(tvLogs)
        consoleCard.addView(scrollView)
        root.addView(consoleCard)

        setContentView(root)

        val filter = IntentFilter().apply {
            addAction("com.pulsebridge.LOG")
            addAction("com.pulsebridge.STATUS")
            addAction("com.pulsebridge.BPM")
            addAction("com.pulsebridge.STATS")
            addAction("com.pulsebridge.BATTERY")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHeartPulseAnimation()
        unregisterReceiver(receiver)
    }

    private fun updateStatusBadge(status: String) {
        tvStatusBadge.text = "● $status"
        when {
            status.contains("Трансляция", true) || status.contains("Авторизовано", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#063321"), radius = dp(14).toFloat(), strokeColor = Color.parseColor("#10B981"), strokeWidth = dp(1))
            }
            status.contains("Подключение", true) || status.contains("Опрос", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#3B2904"), radius = dp(14).toFloat(), strokeColor = Color.parseColor("#F59E0B"), strokeWidth = dp(1))
            }
            status.contains("Ошибка", true) || status.contains("выключен", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#381111"), radius = dp(14).toFloat(), strokeColor = Color.parseColor("#EF4444"), strokeWidth = dp(1))
            }
            else -> {
                tvStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#1E2433"), radius = dp(14).toFloat(), strokeColor = Color.parseColor("#2E384D"), strokeWidth = dp(1))
            }
        }
    }

    private fun startHeartPulseAnimation() {
        if (heartAnimator != null) return
        heartAnimator = ObjectAnimator.ofPropertyValuesHolder(
            tvHeartIcon,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.28f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.28f, 1f)
        ).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopHeartPulseAnimation() {
        heartAnimator?.cancel()
        heartAnimator = null
        tvHeartIcon.scaleX = 1f
        tvHeartIcon.scaleY = 1f
    }

    private fun createMiniMetric(label: String, initialVal: String): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvVal = TextView(this).apply {
            text = initialVal
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F1F5F9"))
        }
        val tvLbl = TextView(this).apply {
            text = label
            textSize = 9.5f
            setTextColor(Color.parseColor("#64748B"))
        }
        box.addView(tvVal)
        box.addView(tvLbl)
        return Pair(box, tvVal)
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
        btnToggle.text = "⏹  ОСТАНОВИТЬ"
        btnToggle.background = makeButtonDrawable(
            normalColor = Color.parseColor("#EF4444"),
            pressedColor = Color.parseColor("#DC2626"),
            radius = dp(16).toFloat()
        )
        val intent = Intent(this, PulseBleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBridgeService() {
        isRunning = false
        btnToggle.text = "▶  СТАРТ МОНИТОРИНГА"
        btnToggle.background = makeButtonDrawable(
            normalColor = Color.parseColor("#10B981"),
            pressedColor = Color.parseColor("#059669"),
            radius = dp(16).toFloat()
        )
        stopService(Intent(this, PulseBleService::class.java))
        updateStatusBadge("Остановлено")
        tvBpm.text = "--"
        tvBpm.setTextColor(Color.parseColor("#6B7280"))
        stopHeartPulseAnimation()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun makeDrawable(bgColor: Int, radius: Float = 0f, strokeColor: Int = 0, strokeWidth: Int = 2): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(bgColor)
            if (strokeColor != 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun makeButtonDrawable(normalColor: Int, pressedColor: Int, radius: Float): StateListDrawable {
        val normal = makeDrawable(normalColor, radius)
        val pressed = makeDrawable(pressedColor, radius)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }
}
