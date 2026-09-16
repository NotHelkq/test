package com.pulsebridge

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Custom live heart rate chart view with gradient fill,
 * smooth lines, grid lines, dynamic clutch coloring, and glowing cursor.
 */
class HrChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val points = ArrayList<Int>()
    private val maxPoints = 80 // ~80 readings rolling window

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FF2D55")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.parseColor("#1B2333")
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val clutchLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.2f)
        color = Color.argb(130, 255, 0, 85)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(5f), dpToPx(3f)), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(9.5f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.parseColor("#64748B")
    }

    private val clutchTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(9f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.parseColor("#FF0055")
    }

    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val linePath = Path()
    private val fillPath = Path()

    fun addPoint(bpm: Int) {
        if (bpm <= 0) return
        if (points.size >= maxPoints) {
            points.removeAt(0)
        }
        points.add(bpm)
        postInvalidate()
    }

    fun clear() {
        points.clear()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padL = dpToPx(32f)
        val padR = dpToPx(14f)
        val padT = dpToPx(12f)
        val padB = dpToPx(16f)

        val chartW = w - padL - padR
        val chartH = h - padT - padB
        if (chartW <= 0 || chartH <= 0) return

        // Dynamic Y scale
        val minBpm = if (points.isEmpty()) 60 else maxOf(40, points.minOrNull()!! - 10)
        val maxBpm = if (points.isEmpty()) 165 else maxOf(165, points.maxOrNull()!! + 10)
        val range = maxOf(30, maxBpm - minBpm)

        // Draw horizontal grid lines: 80, 120, 160
        val gridLevels = intArrayOf(80, 120, 160)
        for (level in gridLevels) {
            if (level in minBpm..maxBpm) {
                val ratio = (level - minBpm).toFloat() / range
                val y = padT + chartH - (ratio * chartH)

                if (level == 160) {
                    canvas.drawLine(padL, y, padL + chartW, y, clutchLinePaint)
                    canvas.drawText("160 CLUTCH", dpToPx(2f), y + dpToPx(3.5f), clutchTextPaint)
                } else {
                    canvas.drawLine(padL, y, padL + chartW, y, gridPaint)
                    canvas.drawText(level.toString(), dpToPx(8f), y + dpToPx(3.5f), textPaint)
                }
            }
        }

        if (points.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = dpToPx(11f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("ОЖИДАНИЕ ДАННЫХ ПУЛЬСА...", w / 2f, h / 2f + dpToPx(4f), emptyPaint)
            return
        }

        val count = points.size
        linePath.reset()
        fillPath.reset()

        val stepX = if (count > 1) chartW / (count - 1).toFloat() else chartW
        var lastX = padL
        var lastY = padT + chartH

        for (i in 0 until count) {
            val bpm = points[i]
            val x = padL + i * stepX
            val ratio = ((bpm - minBpm).toFloat() / range).coerceIn(0f, 1f)
            val y = padT + chartH - (ratio * chartH)

            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, padT + chartH)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }

        fillPath.lineTo(lastX, padT + chartH)
        fillPath.close()

        val latestBpm = points.last()
        val isClutch = latestBpm >= 160
        val lineColor = if (isClutch) Color.parseColor("#FF0055") else Color.parseColor("#FF2D55")

        // Draw gradient fill
        fillPaint.shader = LinearGradient(
            0f, padT, 0f, padT + chartH,
            if (isClutch) Color.argb(90, 255, 0, 85) else Color.argb(70, 255, 45, 85),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        // Draw line
        linePaint.color = lineColor
        canvas.drawPath(linePath, linePaint)

        // Draw glowing point at the end
        dotGlowPaint.color = if (isClutch) Color.argb(130, 255, 0, 85) else Color.argb(100, 255, 45, 85)
        canvas.drawCircle(lastX, lastY, dpToPx(6f), dotGlowPaint)
        canvas.drawCircle(lastX, lastY, dpToPx(3f), dotPaint)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvHeartIcon: TextView
    private lateinit var tvBpm: TextView
    private lateinit var tvBpmLabel: TextView
    private lateinit var tvMinVal: TextView
    private lateinit var tvAvgVal: TextView
    private lateinit var tvMaxVal: TextView
    private lateinit var tvBatteryVal: TextView
    private lateinit var hrChartView: HrChartView
    private lateinit var tvLogs: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnToggle: Button
    private lateinit var btnCopy: Button
    private lateinit var btnClear: Button

    private var heartAnimator: ObjectAnimator? = null
    private var isRunning = false

    // Session Statistics
    private var minBpm: Int = 0
    private var maxBpm: Int = 0
    private var bpmSum: Long = 0L
    private var bpmCount: Int = 0

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

                        // Color zones and Clutch >= 160
                        when {
                            bpm >= 160 -> {
                                tvBpm.setTextColor(Color.parseColor("#FF0055"))
                                tvBpmLabel.text = "🔥 КЛАТЧ (BPM)"
                                tvBpmLabel.setTextColor(Color.parseColor("#FF0055"))
                            }
                            bpm >= 130 -> {
                                tvBpm.setTextColor(Color.parseColor("#EF4444"))
                                tvBpmLabel.text = "УДАРОВ В МИНУТУ (BPM)"
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                            }
                            bpm >= 100 -> {
                                tvBpm.setTextColor(Color.parseColor("#F59E0B"))
                                tvBpmLabel.text = "УДАРОВ В МИНУТУ (BPM)"
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                            }
                            else -> {
                                tvBpm.setTextColor(Color.parseColor("#10B981"))
                                tvBpmLabel.text = "УДАРОВ В МИНУТУ (BPM)"
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                            }
                        }
                        startHeartPulseAnimation(bpm)

                        // Update Session Stats
                        if (minBpm == 0 || bpm < minBpm) minBpm = bpm
                        if (bpm > maxBpm) maxBpm = bpm
                        bpmCount++
                        bpmSum += bpm
                        val avgBpm = (bpmSum / bpmCount).toInt()

                        tvMinVal.text = minBpm.toString()
                        tvAvgVal.text = avgBpm.toString()
                        tvMaxVal.text = maxBpm.toString()

                        // Update Graph
                        hrChartView.addPoint(bpm)
                    } else {
                        tvBpm.text = "--"
                        tvBpm.setTextColor(Color.parseColor("#6B7280"))
                        tvBpmLabel.text = "УДАРОВ В МИНУТУ (BPM)"
                        tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                        stopHeartPulseAnimation()
                    }
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
            setPadding(dp(16), dp(20), dp(16), dp(14))
        }

        // ==========================================
        // 1. HEADER (Title + Status Pill)
        // ==========================================
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
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

        // ==========================================
        // 2. HERO CARD (Heart Rate & Mini Stats: MIN, AVG, MAX, BATTERY)
        // ==========================================
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = makeDrawable(
                bgColor = Color.parseColor("#121622"),
                radius = dp(20).toFloat(),
                strokeColor = Color.parseColor("#1E2538"),
                strokeWidth = dp(1)
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        tvHeartIcon = TextView(this).apply {
            text = "❤️"
            textSize = 30f
            gravity = Gravity.CENTER
        }

        tvBpm = TextView(this).apply {
            text = "--"
            textSize = 50f
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

        // Mini metrics row: МИН | СРЕДНИЙ | МАКС | ЗАРЯД
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }

        val minBadge = createMiniMetric("МИН", "--", Color.parseColor("#38BDF8")).also { tvMinVal = it.second }
        val avgBadge = createMiniMetric("СРЕДНИЙ", "--", Color.parseColor("#FCD34D")).also { tvAvgVal = it.second }
        val maxBadge = createMiniMetric("МАКС", "--", Color.parseColor("#F43F5E")).also { tvMaxVal = it.second }
        val batBadge = createMiniMetric("ЗАРЯД", "--%", Color.parseColor("#10B981")).also { tvBatteryVal = it.second }

        metricsRow.addView(minBadge.first)
        metricsRow.addView(avgBadge.first)
        metricsRow.addView(maxBadge.first)
        metricsRow.addView(batBadge.first)

        heroCard.addView(tvHeartIcon)
        heroCard.addView(tvBpm)
        heroCard.addView(tvBpmLabel)
        heroCard.addView(metricsRow)
        root.addView(heroCard)

        // ==========================================
        // 3. GRAPH CARD (Real-time Pulse Chart)
        // ==========================================
        val graphCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeDrawable(
                bgColor = Color.parseColor("#121622"),
                radius = dp(18).toFloat(),
                strokeColor = Color.parseColor("#1E2538"),
                strokeWidth = dp(1)
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val graphHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }

        val tvGraphTitle = TextView(this).apply {
            text = "📈 ГРАФИК ПУЛЬСА (LIVE)"
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvGraphSub = TextView(this).apply {
            text = "80 ТОЧЕК"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
        }

        graphHeader.addView(tvGraphTitle)
        graphHeader.addView(tvGraphSub)
        graphCard.addView(graphHeader)

        hrChartView = HrChartView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(120)
            )
        }
        graphCard.addView(hrChartView)
        root.addView(graphCard)

        // ==========================================
        // 4. ACTION BUTTONS
        // ==========================================
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
                dp(50)
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
            ).apply { bottomMargin = dp(12) }
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
                val logsText = tvLogs.text.toString()
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("PulseBridgeLogs", logsText)
                clipboard.setPrimaryClip(clip)

                // Фоновая отправка на сервер
                Thread {
                    try {
                        val url = java.net.URL("https://y.shit.vc:68/api/log")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.outputStream.use { os ->
                            os.write(logsText.toByteArray(Charsets.UTF_8))
                        }
                        conn.responseCode
                        conn.disconnect()
                    } catch (_: Exception) {}
                }.start()

                Toast.makeText(this@MainActivity, "✅ Логи скопированы и загружены на сервер!", Toast.LENGTH_SHORT).show()
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
                resetStats()
                Toast.makeText(this@MainActivity, "🗑 Статистика и логи очищены", Toast.LENGTH_SHORT).show()
            }
        }

        btnRow.addView(btnCopy)
        btnRow.addView(btnClear)
        root.addView(btnRow)

        // ==========================================
        // 5. LIVE CONSOLE / LOGS CARD
        // ==========================================
        val consoleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(6))
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
            setPadding(dp(12), dp(10), dp(12), dp(10))
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

    private fun resetStats() {
        minBpm = 0
        maxBpm = 0
        bpmSum = 0L
        bpmCount = 0
        tvMinVal.text = "--"
        tvAvgVal.text = "--"
        tvMaxVal.text = "--"
        hrChartView.clear()
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

    private fun startHeartPulseAnimation(bpm: Int) {
        val durationMs = (Math.max(0.28, Math.min(2.0, 60.0 / bpm)) * 1000).toLong()
        if (heartAnimator != null && heartAnimator?.duration == durationMs) return
        heartAnimator?.cancel()
        heartAnimator = ObjectAnimator.ofPropertyValuesHolder(
            tvHeartIcon,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.26f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.26f, 1f)
        ).apply {
            duration = durationMs
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

    private fun createMiniMetric(label: String, initialVal: String, valColor: Int = Color.parseColor("#F1F5F9")): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvVal = TextView(this).apply {
            text = initialVal
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(valColor)
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
        tvBpmLabel.text = "УДАРОВ В МИНУТУ (BPM)"
        tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
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
