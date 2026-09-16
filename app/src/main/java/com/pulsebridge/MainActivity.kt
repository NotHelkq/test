package com.pulsebridge

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class Lang(val code: String) {
    EN("en"),
    RU("ru")
}

enum class GraphMode {
    LIVE_60S,
    FULL_SESSION
}

data class SessionRecord(
    val id: Long,
    val dateStr: String,
    val durationStr: String,
    val min: Int,
    val avg: Int,
    val max: Int,
    val points: List<Int>
)

object AppStrings {
    fun get(key: String, lang: Lang): String = when (lang) {
        Lang.EN -> when (key) {
            "app_title" -> "PulseBridge"
            "device_sub" -> "Xiaomi Smart Band 9 Active"
            "status_stopped" -> "Stopped"
            "status_live" -> "Live"
            "bpm_label" -> "BEATS PER MINUTE (BPM)"
            "clutch_label" -> "CLUTCH (BPM)"
            "metric_min" -> "MIN"
            "metric_avg" -> "AVG"
            "metric_max" -> "MAX"
            "metric_bat" -> "BATTERY"
            "graph_title" -> "PULSE GRAPH"
            "tab_live" -> "60s Live"
            "tab_session" -> "Full Session"
            "btn_start" -> "▶  START MONITORING"
            "btn_stop" -> "⏹  STOP"
            "btn_history" -> "📜 HISTORY"
            "btn_copy" -> "📋 COPY LOGS"
            "btn_clear" -> "🗑 CLEAR"
            "btn_hide_logs" -> "👁 HIDE LOGS"
            "btn_show_logs" -> "👁 SHOW LOGS"
            "logs_title" -> "EVENT CONSOLE (BLE LOGS)"
            "waiting_data" -> "AWAITING HEART RATE DATA..."
            "history_title" -> "SESSION HISTORY"
            "history_empty" -> "No recorded sessions yet.\nComplete a session to review history!"
            "history_clear" -> "Clear All History"
            "close" -> "Close"
            "view_graph" -> "View on Graph"
            "viewing_history" -> "Viewing History Session:"
            "back_to_live" -> "Back to Live"
            "toast_copied" -> "✅ Logs copied & uploaded to server!"
            "toast_cleared" -> "🗑 Session stats and logs cleared"
            "toast_history_cleared" -> "🗑 Session history cleared"
            else -> key
        }
        Lang.RU -> when (key) {
            "app_title" -> "PulseBridge"
            "device_sub" -> "Xiaomi Smart Band 9 Active"
            "status_stopped" -> "Остановлено"
            "status_live" -> "В эфире"
            "bpm_label" -> "УДАРОВ В МИНУТУ (BPM)"
            "clutch_label" -> "🔥 КЛАТЧ (BPM)"
            "metric_min" -> "МИН"
            "metric_avg" -> "СРЕДНИЙ"
            "metric_max" -> "МАКС"
            "metric_bat" -> "ЗАРЯД"
            "graph_title" -> "ГРАФИК ПУЛЬСА"
            "tab_live" -> "60 сек (Live)"
            "tab_session" -> "Вся сессия"
            "btn_start" -> "▶  СТАРТ МОНИТОРИНГА"
            "btn_stop" -> "⏹  ОСТАНОВИТЬ"
            "btn_history" -> "📜 ИСТОРИЯ"
            "btn_copy" -> "📋 СКОПИРОВАТЬ ЛОГ"
            "btn_clear" -> "🗑 ОЧИСТИТЬ"
            "btn_hide_logs" -> "👁 СКРЫТЬ ЛОГИ"
            "btn_show_logs" -> "👁 ПОКАЗАТЬ ЛОГИ"
            "logs_title" -> "ЖУРНАЛ СОБЫТИЙ (BLE LOGS)"
            "waiting_data" -> "ОЖИДАНИЕ ДАННЫХ ПУЛЬСА..."
            "history_title" -> "ИСТОРИЯ СЕССИЙ"
            "history_empty" -> "История пуста.\nЗавершите сессию мониторинга для сохранения!"
            "history_clear" -> "Очистить историю"
            "close" -> "Закрыть"
            "view_graph" -> "Открыть график"
            "viewing_history" -> "Просмотр архивной сессии:"
            "back_to_live" -> "Вернуться к Live"
            "toast_copied" -> "✅ Логи скопированы и загружены на сервер!"
            "toast_cleared" -> "🗑 Статистика и логи очищены"
            "toast_history_cleared" -> "🗑 История сессий очищена"
            else -> key
        }
    }
}

/**
 * Custom live & session heart rate chart view with gradient fill,
 * smooth lines, grid lines, dynamic clutch coloring, and glowing cursor.
 */
class HrChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var lang: Lang = Lang.EN
    var currentMode: GraphMode = GraphMode.LIVE_60S

    private val livePoints = ArrayList<Int>()
    private val sessionPoints = ArrayList<Int>()
    private var historicalPoints: List<Int>? = null

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
        color = Color.parseColor("#171F2F")
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(9.5f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.parseColor("#475569")
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
        if (livePoints.size >= 60) {
            livePoints.removeAt(0)
        }
        livePoints.add(bpm)
        sessionPoints.add(bpm)
        postInvalidate()
    }

    fun setMode(mode: GraphMode) {
        historicalPoints = null
        currentMode = mode
        postInvalidate()
    }

    fun showHistorical(points: List<Int>) {
        historicalPoints = points
        postInvalidate()
    }

    fun returnToLive() {
        historicalPoints = null
        postInvalidate()
    }

    fun isHistorical(): Boolean = historicalPoints != null

    fun clear() {
        livePoints.clear()
        sessionPoints.clear()
        historicalPoints = null
        postInvalidate()
    }

    fun getSessionPoints(): List<Int> = sessionPoints.toList()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padL = dpToPx(28f)
        val padR = dpToPx(14f)
        val padT = dpToPx(12f)
        val padB = dpToPx(16f)

        val chartW = w - padL - padR
        val chartH = h - padT - padB
        if (chartW <= 0 || chartH <= 0) return

        val activeList = historicalPoints ?: if (currentMode == GraphMode.LIVE_60S) livePoints else sessionPoints

        // Dynamic Y scale
        val minBpm = if (activeList.isEmpty()) 50 else maxOf(40, activeList.minOrNull()!! - 10)
        val maxBpm = if (activeList.isEmpty()) 150 else maxOf(130, activeList.maxOrNull()!! + 10)
        val range = maxOf(30, maxBpm - minBpm)

        // Subtle horizontal grid lines: 60, 90, 120, 150, 180
        val gridLevels = intArrayOf(60, 90, 120, 150, 180)
        for (level in gridLevels) {
            if (level in minBpm..maxBpm) {
                val ratio = (level - minBpm).toFloat() / range
                val y = padT + chartH - (ratio * chartH)
                canvas.drawLine(padL, y, padL + chartW, y, gridPaint)
                canvas.drawText(level.toString(), dpToPx(6f), y + dpToPx(3.5f), textPaint)
            }
        }

        if (activeList.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = dpToPx(11f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(AppStrings.get("waiting_data", lang), w / 2f, h / 2f + dpToPx(4f), emptyPaint)
            return
        }

        val count = activeList.size
        linePath.reset()
        fillPath.reset()

        val stepX = if (currentMode == GraphMode.LIVE_60S && historicalPoints == null) {
            if (count < 60) chartW / 59f else chartW / (count - 1).toFloat()
        } else {
            if (count > 1) chartW / (count - 1).toFloat() else chartW
        }

        var lastX = padL
        var lastY = padT + chartH

        for (i in 0 until count) {
            val bpm = activeList[i]
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

        val latestBpm = activeList.last()
        val isClutch = latestBpm >= 160
        val lineColor = if (isClutch) Color.parseColor("#FF0055") else Color.parseColor("#FF2D55")

        // Draw gradient fill
        fillPaint.shader = LinearGradient(
            0f, padT, 0f, padT + chartH,
            if (isClutch) Color.argb(80, 255, 0, 85) else Color.argb(60, 255, 45, 85),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        // Draw line
        linePaint.color = lineColor
        canvas.drawPath(linePath, linePaint)

        // Draw glowing point at the end (only if live mode)
        if (historicalPoints == null) {
            dotGlowPaint.color = if (isClutch) Color.argb(140, 255, 0, 85) else Color.argb(110, 255, 45, 85)
            canvas.drawCircle(lastX, lastY, dpToPx(6f), dotGlowPaint)
            canvas.drawCircle(lastX, lastY, dpToPx(3f), dotPaint)
        }
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

    // Localization & Preferences
    private var currentLang: Lang = Lang.EN
    private val PREFS_NAME = "pulsebridge_prefs"
    private val KEY_LANG = "key_lang"

    // Views
    private lateinit var tvAppTitle: TextView
    private lateinit var tvAppSub: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var btnLangToggle: TextView

    private lateinit var heroCard: LinearLayout
    private lateinit var tvHeartIcon: TextView
    private lateinit var tvBpm: TextView
    private lateinit var tvBpmLabel: TextView

    private lateinit var tvMinVal: TextView
    private lateinit var tvMinLbl: TextView
    private lateinit var tvAvgVal: TextView
    private lateinit var tvAvgLbl: TextView
    private lateinit var tvMaxVal: TextView
    private lateinit var tvMaxLbl: TextView
    private lateinit var tvBatteryVal: TextView
    private lateinit var tvBatteryLbl: TextView

    private lateinit var tvGraphTitle: TextView
    private lateinit var btnTabLive: TextView
    private lateinit var btnTabSession: TextView
    private lateinit var hrChartView: HrChartView

    private lateinit var historicalBanner: LinearLayout
    private lateinit var tvHistoricalInfo: TextView
    private lateinit var btnHistoricalClose: TextView

    private lateinit var btnToggle: Button
    private lateinit var btnHideLogs: Button
    private lateinit var btnHistory: Button
    private lateinit var btnCopy: Button
    private lateinit var btnClear: Button

    private lateinit var consoleHeader: LinearLayout
    private lateinit var tvConsoleTitle: TextView
    private lateinit var consoleCard: FrameLayout
    private lateinit var tvLogs: TextView
    private lateinit var scrollView: ScrollView

    private var heartAnimator: ObjectAnimator? = null
    private var isRunning = false
    private var isLogsVisible = true

    // Session Statistics
    private var sessionStartTimeMs: Long = 0L
    private var minBpm: Int = 0
    private var maxBpm: Int = 0
    private var bpmSum: Long = 0L
    private var bpmCount: Int = 0
    private var currentBattery: Int = 0

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.pulsebridge.LOG" -> {
                    val msg = intent.getStringExtra("msg") ?: return
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    tvLogs.append("[$time] $msg\n")
                    if (isLogsVisible) {
                        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
                "com.pulsebridge.STATUS" -> {
                    val status = intent.getStringExtra("status") ?: return
                    updateStatusBadge(status)
                }
                "com.pulsebridge.BPM" -> {
                    val bpm = intent.getIntExtra("bpm", 0)
                    if (bpm > 0) {
                        tvBpm.text = bpm.toString()

                        // Color zones and Clutch >= 160 with cyber glow
                        when {
                            bpm >= 160 -> {
                                tvBpm.setTextColor(Color.parseColor("#FF0055"))
                                tvBpmLabel.text = AppStrings.get("clutch_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#FF0055"))
                                updateHeroGlow(isClutch = true)
                            }
                            bpm >= 130 -> {
                                tvBpm.setTextColor(Color.parseColor("#EF4444"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                updateHeroGlow(isClutch = false)
                            }
                            bpm >= 100 -> {
                                tvBpm.setTextColor(Color.parseColor("#F59E0B"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                updateHeroGlow(isClutch = false)
                            }
                            else -> {
                                tvBpm.setTextColor(Color.parseColor("#10B981"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                updateHeroGlow(isClutch = false)
                            }
                        }
                        startHeartPulseAnimation(bpm)

                        // If not currently viewing a historical session, update live stats
                        if (!hrChartView.isHistorical()) {
                            if (minBpm == 0 || bpm < minBpm) minBpm = bpm
                            if (bpm > maxBpm) maxBpm = bpm
                            bpmCount++
                            bpmSum += bpm
                            val avgBpm = (bpmSum / bpmCount).toInt()

                            tvMinVal.text = minBpm.toString()
                            tvAvgVal.text = avgBpm.toString()
                            tvMaxVal.text = maxBpm.toString()
                        }

                        // Add point to chart
                        hrChartView.addPoint(bpm)
                    } else {
                        tvBpm.text = "--"
                        tvBpm.setTextColor(Color.parseColor("#6B7280"))
                        tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                        tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                        updateHeroGlow(isClutch = false)
                        stopHeartPulseAnimation()
                    }
                }
                "com.pulsebridge.BATTERY" -> {
                    val level = intent.getIntExtra("level", 0)
                    if (level > 0) {
                        currentBattery = level
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

        // Load saved language (default EN)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLangCode = prefs.getString(KEY_LANG, Lang.EN.code) ?: Lang.EN.code
        currentLang = if (savedLangCode == Lang.RU.code) Lang.RU else Lang.EN

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070A10"))
            setPadding(dp(16), dp(18), dp(16), dp(12))
        }

        // ==========================================
        // 1. HEADER (Title + Status Pill + Lang Switcher)
        // ==========================================
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        tvAppTitle = TextView(this).apply {
            text = "⚡ " + AppStrings.get("app_title", currentLang)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#FFFFFF"))
        }

        tvAppSub = TextView(this).apply {
            text = AppStrings.get("device_sub", currentLang)
            textSize = 11.5f
            setTextColor(Color.parseColor("#64748B"))
        }

        titleBox.addView(tvAppTitle)
        titleBox.addView(tvAppSub)

        btnLangToggle = TextView(this).apply {
            text = if (currentLang == Lang.EN) "🇺🇸 EN" else "🇷🇺 RU"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            background = makeDrawable(Color.parseColor("#111F38"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#1E3A8A"), strokeWidth = dp(1))
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8) }

            setOnClickListener {
                toggleLanguage()
            }
        }

        tvStatusBadge = TextView(this).apply {
            text = "● " + AppStrings.get("status_stopped", currentLang)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            background = makeDrawable(Color.parseColor("#1E2433"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#2E384D"), strokeWidth = dp(1))
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }

        headerRow.addView(titleBox)
        headerRow.addView(btnLangToggle)
        headerRow.addView(tvStatusBadge)
        root.addView(headerRow)

        // ==========================================
        // 2. HERO CARD (Heart Rate, Cyber Glow & Mini Stats)
        // ==========================================
        heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = makeHeroDrawable(isClutch = false)
            setPadding(dp(16), dp(14), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        tvHeartIcon = TextView(this).apply {
            text = "❤️"
            textSize = 28f
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
            text = AppStrings.get("bpm_label", currentLang)
            textSize = 10.5f
            letterSpacing = 0.15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
        }

        // Mini metrics row: MIN | AVG | MAX | BATTERY
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }

        val minBadge = createMiniMetric("metric_min", "--", Color.parseColor("#38BDF8")).also {
            tvMinVal = it.second
            tvMinLbl = it.third
        }
        val avgBadge = createMiniMetric("metric_avg", "--", Color.parseColor("#FCD34D")).also {
            tvAvgVal = it.second
            tvAvgLbl = it.third
        }
        val maxBadge = createMiniMetric("metric_max", "--", Color.parseColor("#F43F5E")).also {
            tvMaxVal = it.second
            tvMaxLbl = it.third
        }
        val batBadge = createMiniMetric("metric_bat", "--%", Color.parseColor("#10B981")).also {
            tvBatteryVal = it.second
            tvBatteryLbl = it.third
        }

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
        // 3. GRAPH CARD (Tabs: 60s Live / Full Session + Chart)
        // ==========================================
        val graphCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeDrawable(
                bgColor = Color.parseColor("#0E131F"),
                radius = dp(18).toFloat(),
                strokeColor = Color.parseColor("#1C263B"),
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

        tvGraphTitle = TextView(this).apply {
            text = "📈 " + AppStrings.get("graph_title", currentLang)
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Tabs: [ 60s Live ] [ Full Session ]
        val tabsBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = makeDrawable(Color.parseColor("#080B12"), radius = dp(10).toFloat(), strokeColor = Color.parseColor("#182030"), strokeWidth = dp(1))
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        btnTabLive = TextView(this).apply {
            text = AppStrings.get("tab_live", currentLang)
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeDrawable(Color.parseColor("#2563EB"), radius = dp(8).toFloat())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                setGraphTab(GraphMode.LIVE_60S)
            }
        }

        btnTabSession = TextView(this).apply {
            text = AppStrings.get("tab_session", currentLang)
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            background = null
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                setGraphTab(GraphMode.FULL_SESSION)
            }
        }

        tabsBox.addView(btnTabLive)
        tabsBox.addView(btnTabSession)

        graphHeader.addView(tvGraphTitle)
        graphHeader.addView(tabsBox)
        graphCard.addView(graphHeader)

        // Banner when inspecting a historical session
        historicalBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = makeDrawable(Color.parseColor("#291F09"), radius = dp(8).toFloat(), strokeColor = Color.parseColor("#D97706"), strokeWidth = dp(1))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }

        tvHistoricalInfo = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#FBBF24"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnHistoricalClose = TextView(this).apply {
            text = "✕ " + AppStrings.get("back_to_live", currentLang)
            textSize = 10f
            setTextColor(Color.parseColor("#FDE68A"))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            setOnClickListener {
                closeHistoricalView()
            }
        }

        historicalBanner.addView(tvHistoricalInfo)
        historicalBanner.addView(btnHistoricalClose)
        graphCard.addView(historicalBanner)

        hrChartView = HrChartView(this).apply {
            lang = currentLang
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(125)
            )
        }
        graphCard.addView(hrChartView)
        root.addView(graphCard)

        // ==========================================
        // 4. ACTION BUTTONS & CONTROLS
        // ==========================================
        btnToggle = Button(this).apply {
            text = AppStrings.get("btn_start", currentLang)
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
                dp(48)
            ).apply { bottomMargin = dp(8) }

            setOnClickListener {
                if (!isRunning) {
                    checkPermsAndStart()
                } else {
                    stopBridgeService()
                }
            }
        }
        root.addView(btnToggle)

        // Sub-buttons Row: [ 👁 HIDE/SHOW LOGS ] [ 📜 HISTORY ] [ 📋 COPY ] [ 🗑 CLEAR ]
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        btnHideLogs = Button(this).apply {
            text = AppStrings.get("btn_hide_logs", currentLang)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#CBD5E1"))
            background = makeButtonDrawable(
                normalColor = Color.parseColor("#1E293B"),
                pressedColor = Color.parseColor("#334155"),
                radius = dp(12).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(5) }
            setOnClickListener {
                toggleLogsVisibility()
            }
        }

        btnHistory = Button(this).apply {
            text = AppStrings.get("btn_history", currentLang)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#A78BFA"))
            background = makeButtonDrawable(
                normalColor = Color.parseColor("#20163B"),
                pressedColor = Color.parseColor("#2E1F54"),
                radius = dp(12).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(5) }
            setOnClickListener {
                showHistoryDialog()
            }
        }

        btnCopy = Button(this).apply {
            text = AppStrings.get("btn_copy", currentLang)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeButtonDrawable(
                normalColor = Color.parseColor("#2563EB"),
                pressedColor = Color.parseColor("#1D4ED8"),
                radius = dp(12).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(5) }
            setOnClickListener {
                copyAndUploadLogs()
            }
        }

        btnClear = Button(this).apply {
            text = AppStrings.get("btn_clear", currentLang)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            background = makeButtonDrawable(
                normalColor = Color.parseColor("#141A29"),
                pressedColor = Color.parseColor("#1E273D"),
                radius = dp(12).toFloat()
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 0.9f)
            setOnClickListener {
                tvLogs.text = ""
                resetCurrentStats()
                Toast.makeText(this@MainActivity, AppStrings.get("toast_cleared", currentLang), Toast.LENGTH_SHORT).show()
            }
        }

        actionRow.addView(btnHideLogs)
        actionRow.addView(btnHistory)
        actionRow.addView(btnCopy)
        actionRow.addView(btnClear)
        root.addView(actionRow)

        // ==========================================
        // 5. LIVE CONSOLE / LOGS CARD (Collapsible)
        // ==========================================
        consoleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(4))
        }

        tvConsoleTitle = TextView(this).apply {
            text = AppStrings.get("logs_title", currentLang)
            textSize = 10.5f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvLiveDot = TextView(this).apply {
            text = "● LIVE"
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
        }

        consoleHeader.addView(tvConsoleTitle)
        consoleHeader.addView(tvLiveDot)
        root.addView(consoleHeader)

        consoleCard = FrameLayout(this).apply {
            background = makeDrawable(
                bgColor = Color.parseColor("#05070C"),
                radius = dp(14).toFloat(),
                strokeColor = Color.parseColor("#141926"),
                strokeWidth = dp(1)
            )
            setPadding(dp(10), dp(8), dp(10), dp(8))
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
            textSize = 10.5f
            setTypeface(Typeface.MONOSPACE)
            setLineSpacing(dp(2).toFloat(), 1f)
            text = "[PulseBridge] Ready. Press Start to connect.\n"
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

    // ==========================================
    // LANGUAGE & LOCALIZATION
    // ==========================================
    private fun toggleLanguage() {
        currentLang = if (currentLang == Lang.EN) Lang.RU else Lang.EN
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, currentLang.code)
            .apply()
        applyTranslations()
    }

    private fun applyTranslations() {
        btnLangToggle.text = if (currentLang == Lang.EN) "🇺🇸 EN" else "🇷🇺 RU"
        tvAppTitle.text = "⚡ " + AppStrings.get("app_title", currentLang)
        tvAppSub.text = AppStrings.get("device_sub", currentLang)
        tvBpmLabel.text = if (tvBpmLabel.text.toString().contains("КЛАТЧ") || tvBpmLabel.text.toString().contains("CLUTCH")) {
            AppStrings.get("clutch_label", currentLang)
        } else {
            AppStrings.get("bpm_label", currentLang)
        }
        tvMinLbl.text = AppStrings.get("metric_min", currentLang)
        tvAvgLbl.text = AppStrings.get("metric_avg", currentLang)
        tvMaxLbl.text = AppStrings.get("metric_max", currentLang)
        tvBatteryLbl.text = AppStrings.get("metric_bat", currentLang)

        tvGraphTitle.text = "📈 " + AppStrings.get("graph_title", currentLang)
        btnTabLive.text = AppStrings.get("tab_live", currentLang)
        btnTabSession.text = AppStrings.get("tab_session", currentLang)

        btnToggle.text = if (isRunning) AppStrings.get("btn_stop", currentLang) else AppStrings.get("btn_start", currentLang)
        btnHideLogs.text = if (isLogsVisible) AppStrings.get("btn_hide_logs", currentLang) else AppStrings.get("btn_show_logs", currentLang)
        btnHistory.text = AppStrings.get("btn_history", currentLang)
        btnCopy.text = AppStrings.get("btn_copy", currentLang)
        btnClear.text = AppStrings.get("btn_clear", currentLang)
        tvConsoleTitle.text = AppStrings.get("logs_title", currentLang)
        btnHistoricalClose.text = "✕ " + AppStrings.get("back_to_live", currentLang)

        hrChartView.lang = currentLang
        hrChartView.invalidate()
    }

    // ==========================================
    // LOGS VISIBILITY TOGGLE
    // ==========================================
    private fun toggleLogsVisibility() {
        isLogsVisible = !isLogsVisible
        if (isLogsVisible) {
            consoleHeader.visibility = View.VISIBLE
            consoleCard.visibility = View.VISIBLE
            btnHideLogs.text = AppStrings.get("btn_hide_logs", currentLang)
            btnHideLogs.setTextColor(Color.parseColor("#CBD5E1"))
        } else {
            consoleHeader.visibility = View.GONE
            consoleCard.visibility = View.GONE
            btnHideLogs.text = AppStrings.get("btn_show_logs", currentLang)
            btnHideLogs.setTextColor(Color.parseColor("#38BDF8"))
        }
    }

    // ==========================================
    // GRAPH TABS & MODES
    // ==========================================
    private fun setGraphTab(mode: GraphMode) {
        if (hrChartView.isHistorical()) {
            closeHistoricalView()
        }
        hrChartView.setMode(mode)
        if (mode == GraphMode.LIVE_60S) {
            btnTabLive.setTextColor(Color.WHITE)
            btnTabLive.background = makeDrawable(Color.parseColor("#2563EB"), radius = dp(8).toFloat())
            btnTabSession.setTextColor(Color.parseColor("#64748B"))
            btnTabSession.background = null
        } else {
            btnTabSession.setTextColor(Color.WHITE)
            btnTabSession.background = makeDrawable(Color.parseColor("#2563EB"), radius = dp(8).toFloat())
            btnTabLive.setTextColor(Color.parseColor("#64748B"))
            btnTabLive.background = null
        }
    }

    private fun closeHistoricalView() {
        historicalBanner.visibility = View.GONE
        hrChartView.returnToLive()
        // Restore live metrics
        tvMinVal.text = if (minBpm > 0) minBpm.toString() else "--"
        tvAvgVal.text = if (bpmCount > 0) (bpmSum / bpmCount).toString() else "--"
        tvMaxVal.text = if (maxBpm > 0) maxBpm.toString() else "--"
        setGraphTab(hrChartView.currentMode)
    }

    // ==========================================
    // SESSION HISTORY PERSISTENCE & VIEWER
    // ==========================================
    private fun saveCurrentSession() {
        val points = hrChartView.getSessionPoints()
        if (points.size < 5) return // Ignore trivial sessions

        val durationSec = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000).toInt()
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        val durationStr = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
        val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(sessionStartTimeMs))

        try {
            val file = File(filesDir, "sessions.json")
            val arr = if (file.exists()) JSONArray(file.readText()) else JSONArray()

            val obj = JSONObject().apply {
                put("id", sessionStartTimeMs)
                put("date", dateStr)
                put("duration", durationStr)
                put("min", minBpm)
                put("avg", if (bpmCount > 0) (bpmSum / bpmCount).toInt() else minBpm)
                put("max", maxBpm)

                // Downsample points if list is very large (> 500)
                val ptsArray = JSONArray()
                val step = maxOf(1, points.size / 400)
                for (i in 0 until points.size step step) {
                    ptsArray.put(points[i])
                }
                put("points", ptsArray)
            }

            // Prepend new session
            val newArr = JSONArray()
            newArr.put(obj)
            for (i in 0 until minOf(arr.length(), 40)) {
                newArr.put(arr.getJSONObject(i))
            }

            file.writeText(newArr.toString())
        } catch (_: Exception) {}
    }

    private fun loadSavedSessions(): List<SessionRecord> {
        val list = mutableListOf<SessionRecord>()
        try {
            val file = File(filesDir, "sessions.json")
            if (!file.exists()) return list
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ptsArray = obj.getJSONArray("points")
                val pts = ArrayList<Int>()
                for (j in 0 until ptsArray.length()) {
                    pts.add(ptsArray.getInt(j))
                }
                list.add(
                    SessionRecord(
                        id = obj.optLong("id", 0L),
                        dateStr = obj.optString("date", ""),
                        durationStr = obj.optString("duration", ""),
                        min = obj.optInt("min", 0),
                        avg = obj.optInt("avg", 0),
                        max = obj.optInt("max", 0),
                        points = pts
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun showHistoryDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeDrawable(
                bgColor = Color.parseColor("#0C111C"),
                radius = dp(20).toFloat(),
                strokeColor = Color.parseColor("#1F2C47"),
                strokeWidth = dp(1)
            )
            setPadding(dp(18), dp(18), dp(18), dp(16))
        }

        val dHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val tvDTitle = TextView(this).apply {
            text = "📜 " + AppStrings.get("history_title", currentLang)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnDClose = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener { dialog.dismiss() }
        }

        dHeader.addView(tvDTitle)
        dHeader.addView(btnDClose)
        container.addView(dHeader)

        val sessions = loadSavedSessions()
        if (sessions.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = AppStrings.get("history_empty", currentLang)
                textSize = 12.5f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            }
            container.addView(tvEmpty)
        } else {
            val sv = ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(320)
                )
            }
            val listLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            for (s in sessions) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = makeDrawable(
                        bgColor = Color.parseColor("#131929"),
                        radius = dp(12).toFloat(),
                        strokeColor = Color.parseColor("#1E283D"),
                        strokeWidth = dp(1)
                    )
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }

                val row1 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val tvDate = TextView(this).apply {
                    text = s.dateStr
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvDur = TextView(this).apply {
                    text = "⏱ ${s.durationStr}"
                    textSize = 11f
                    setTextColor(Color.parseColor("#94A3B8"))
                }

                row1.addView(tvDate)
                row1.addView(tvDur)
                card.addView(row1)

                val row2 = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, 0)
                }

                val tvStats = TextView(this).apply {
                    text = "${AppStrings.get("metric_min", currentLang)}: ${s.min}   ${AppStrings.get("metric_avg", currentLang)}: ${s.avg}   ${AppStrings.get("metric_max", currentLang)}: ${s.max}"
                    textSize = 11.5f
                    setTypeface(Typeface.MONOSPACE)
                    setTextColor(Color.parseColor("#38BDF8"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val btnInspect = TextView(this).apply {
                    text = AppStrings.get("view_graph", currentLang)
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#34D399"))
                    background = makeDrawable(Color.parseColor("#063321"), radius = dp(8).toFloat(), strokeColor = Color.parseColor("#10B981"), strokeWidth = dp(1))
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        dialog.dismiss()
                        // Load into main graph
                        hrChartView.showHistorical(s.points)
                        historicalBanner.visibility = View.VISIBLE
                        tvHistoricalInfo.text = "${AppStrings.get("viewing_history", currentLang)} ${s.dateStr} (${s.durationStr})"
                        tvMinVal.text = s.min.toString()
                        tvAvgVal.text = s.avg.toString()
                        tvMaxVal.text = s.max.toString()
                    }
                }

                row2.addView(tvStats)
                row2.addView(btnInspect)
                card.addView(row2)

                listLayout.addView(card)
            }

            sv.addView(listLayout)
            container.addView(sv)

            // Button to clear history
            val btnClearHistory = TextView(this).apply {
                text = "🗑 " + AppStrings.get("history_clear", currentLang)
                textSize = 11.5f
                setTextColor(Color.parseColor("#EF4444"))
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), 0)
                setOnClickListener {
                    File(filesDir, "sessions.json").delete()
                    Toast.makeText(this@MainActivity, AppStrings.get("toast_history_cleared", currentLang), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            container.addView(btnClearHistory)
        }

        dialog.setContentView(container)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    // ==========================================
    // ACTIONS & CONTROLS
    // ==========================================
    private fun copyAndUploadLogs() {
        val logsText = tvLogs.text.toString()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("PulseBridgeLogs", logsText)
        clipboard.setPrimaryClip(clip)

        // Background server upload
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

        Toast.makeText(this, AppStrings.get("toast_copied", currentLang), Toast.LENGTH_SHORT).show()
    }

    private fun resetCurrentStats() {
        minBpm = 0
        maxBpm = 0
        bpmSum = 0L
        bpmCount = 0
        tvMinVal.text = "--"
        tvAvgVal.text = "--"
        tvMaxVal.text = "--"
        historicalBanner.visibility = View.GONE
        hrChartView.clear()
    }

    private fun updateStatusBadge(status: String) {
        tvStatusBadge.text = "● $status"
        when {
            status.contains("Трансляция", true) || status.contains("Streaming", true) || status.contains("Авторизовано", true) || status.contains("Authorized", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#063321"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#10B981"), strokeWidth = dp(1))
            }
            status.contains("Подключение", true) || status.contains("Connecting", true) || status.contains("Опрос", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#3B2904"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#F59E0B"), strokeWidth = dp(1))
            }
            status.contains("Ошибка", true) || status.contains("Error", true) || status.contains("выключен", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#381111"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#EF4444"), strokeWidth = dp(1))
            }
            else -> {
                tvStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#1E2433"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#2E384D"), strokeWidth = dp(1))
            }
        }
    }

    private fun updateHeroGlow(isClutch: Boolean) {
        heroCard.background = makeHeroDrawable(isClutch)
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

    private fun createMiniMetric(labelKey: String, initialVal: String, valColor: Int): Triple<LinearLayout, TextView, TextView> {
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
            text = AppStrings.get(labelKey, currentLang)
            textSize = 9.5f
            setTextColor(Color.parseColor("#64748B"))
        }
        box.addView(tvVal)
        box.addView(tvLbl)
        return Triple(box, tvVal, tvLbl)
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
        sessionStartTimeMs = System.currentTimeMillis()
        btnToggle.text = AppStrings.get("btn_stop", currentLang)
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
        saveCurrentSession()
        btnToggle.text = AppStrings.get("btn_start", currentLang)
        btnToggle.background = makeButtonDrawable(
            normalColor = Color.parseColor("#10B981"),
            pressedColor = Color.parseColor("#059669"),
            radius = dp(16).toFloat()
        )
        stopService(Intent(this, PulseBleService::class.java))
        updateStatusBadge(AppStrings.get("status_stopped", currentLang))
        tvBpm.text = "--"
        tvBpm.setTextColor(Color.parseColor("#6B7280"))
        tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
        tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
        updateHeroGlow(isClutch = false)
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

    private fun makeHeroDrawable(isClutch: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            if (isClutch) {
                colors = intArrayOf(Color.parseColor("#260814"), Color.parseColor("#111520"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dp(1.5f.toInt()), Color.parseColor("#FF0055"))
            } else {
                colors = intArrayOf(Color.parseColor("#121726"), Color.parseColor("#0D111A"))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke(dp(1), Color.parseColor("#1E273A"))
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
