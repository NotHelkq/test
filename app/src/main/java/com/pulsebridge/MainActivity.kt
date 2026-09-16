package com.pulsebridge

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.view.*
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

enum class TimeRange(val seconds: Int, val enKey: String, val ruKey: String) {
    SEC_60(60, "60s", "60с"),
    MIN_5(300, "5m", "5м"),
    MIN_10(600, "10m", "10м"),
    MIN_30(1800, "30m", "30м"),
    HOUR_1(3600, "1h", "1ч"),
    ALL(-1, "All", "Всё");

    fun getLabel(lang: Lang): String = if (lang == Lang.EN) enKey else ruKey
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
            "status_live" -> "Streaming"
            "bpm_label" -> "BEATS PER MINUTE (BPM)"
            "clutch_label" -> "🔥 CLUTCH MODE (BPM)"
            "metric_min" -> "MIN"
            "metric_avg" -> "AVG"
            "metric_max" -> "MAX"
            "metric_bat" -> "BATTERY"
            "graph_title" -> "HEART RATE GRAPH"
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
            "viewing_history" -> "Viewing Archive:"
            "back_to_live" -> "Back to Live"
            "zoom_reset" -> "↺ Reset Zoom"
            "toast_copied" -> "✅ Logs copied & uploaded to server!"
            "toast_cleared" -> "🗑 Session stats and logs cleared"
            "toast_history_cleared" -> "🗑 Session history cleared"
            else -> key
        }
        Lang.RU -> when (key) {
            "app_title" -> "PulseBridge"
            "device_sub" -> "Xiaomi Smart Band 9 Active"
            "status_stopped" -> "Остановлено"
            "status_live" -> "Трансляция"
            "bpm_label" -> "УДАРОВ В МИНУТУ (BPM)"
            "clutch_label" -> "🔥 КЛАТЧ (BPM)"
            "metric_min" -> "МИН"
            "metric_avg" -> "СРЕДНИЙ"
            "metric_max" -> "МАКС"
            "metric_bat" -> "ЗАРЯД"
            "graph_title" -> "ГРАФИК ПУЛЬСА"
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
            "viewing_history" -> "Архивная сессия:"
            "back_to_live" -> "Вернуться к Live"
            "zoom_reset" -> "↺ Сброс зума"
            "toast_copied" -> "✅ Логи скопированы и загружены на сервер!"
            "toast_cleared" -> "🗑 Статистика и логи очищены"
            "toast_history_cleared" -> "🗑 История сессий очищена"
            else -> key
        }
    }

    fun formatStatus(raw: String, lang: Lang): String {
        if (lang == Lang.RU) {
            return when {
                raw.contains("Workout", ignoreCase = true) || raw.contains("Трансляция", ignoreCase = true) -> "Трансляция"
                raw.contains("Авторизовано", ignoreCase = true) -> "Авторизовано"
                raw.contains("Подключение", ignoreCase = true) -> "Подключение..."
                raw.contains("Опрос", ignoreCase = true) -> "Опрос датчиков..."
                raw.contains("выключен", ignoreCase = true) -> "Bluetooth выключен"
                raw.contains("Ошибка", ignoreCase = true) -> "Ошибка подключения"
                raw.contains("Остановлено", ignoreCase = true) -> "Остановлено"
                raw.contains("Отключено", ignoreCase = true) -> "Отключено"
                else -> raw
            }
        }
        return when {
            raw.contains("Трансляция", ignoreCase = true) || raw.contains("Workout", ignoreCase = true) -> "Streaming"
            raw.contains("Авторизовано", ignoreCase = true) -> "Authorized"
            raw.contains("Подключение", ignoreCase = true) -> "Connecting..."
            raw.contains("Опрос", ignoreCase = true) -> "Polling sensor..."
            raw.contains("выключен", ignoreCase = true) -> "Bluetooth is OFF"
            raw.contains("Ошибка", ignoreCase = true) -> "Connection Error"
            raw.contains("Остановлено", ignoreCase = true) -> "Stopped"
            raw.contains("Отключено", ignoreCase = true) -> "Disconnected"
            else -> raw
        }
    }
}

/**
 * Custom live & session heart rate chart view with:
 * - Time range filtering (60s, 5m, 10m, 30m, 1h, All)
 * - Pinch-to-zoom (up to 20x) and horizontal panning gestures
 * - Interactive scrubber / touch inspection
 * - Vibrant dual-layer neon glow stroke & high-contrast gradient fill
 */
class HrChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var lang: Lang = Lang.EN
    var activeRange: TimeRange = TimeRange.SEC_60
    var onZoomChangedListener: ((Boolean, Float) -> Unit)? = null

    private val allSessionPoints = ArrayList<Int>()
    private var historicalPoints: List<Int>? = null

    // Zoom & Pan state
    var zoomScale: Float = 1.0f
        private set
    private var panRatio: Float = 1.0f // 1.0f means rightmost (latest)

    // Touch inspection / Scrubber
    private var touchedX: Float? = null
    private var inspectedBpm: Int? = null

    // Paints
    private val glowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.parseColor("#172033")
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(9.5f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.parseColor("#5A6E8C")
    }

    private val scrubberLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
        color = Color.parseColor("#38BDF8")
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(3f), dpToPx(3f)), 0f)
    }

    private val scrubberBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0C192E")
    }

    private val scrubberBadgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.parseColor("#38BDF8")
    }

    private val scrubberTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(11f)
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }

    private val linePath = Path()
    private val fillPath = Path()

    // Gestures
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (zoomScale * detector.scaleFactor).coerceIn(1.0f, 25.0f)
                if (newScale != zoomScale) {
                    zoomScale = newScale
                    onZoomChangedListener?.invoke(zoomScale > 1.05f, zoomScale)
                    postInvalidate()
                }
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (zoomScale > 1.0f) {
                    val w = width - dpToPx(42f)
                    if (w > 0) {
                        val delta = distanceX / (w * (zoomScale - 1f))
                        panRatio = (panRatio + delta).coerceIn(0f, 1f)
                        postInvalidate()
                    }
                }
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                handleScrubber(e.x)
                return true
            }
        })
    }

    fun addPoint(bpm: Int) {
        if (bpm <= 0) return
        allSessionPoints.add(bpm)
        postInvalidate()
    }

    fun setTimeRange(range: TimeRange) {
        activeRange = range
        historicalPoints = null
        resetZoom()
        postInvalidate()
    }

    fun resetZoom() {
        zoomScale = 1.0f
        panRatio = 1.0f
        touchedX = null
        inspectedBpm = null
        onZoomChangedListener?.invoke(false, 1.0f)
        postInvalidate()
    }

    fun showHistorical(points: List<Int>) {
        historicalPoints = points
        resetZoom()
        postInvalidate()
    }

    fun returnToLive() {
        historicalPoints = null
        resetZoom()
        postInvalidate()
    }

    fun isHistorical(): Boolean = historicalPoints != null

    fun clear() {
        allSessionPoints.clear()
        historicalPoints = null
        resetZoom()
        postInvalidate()
    }

    fun getSessionPoints(): List<Int> = allSessionPoints.toList()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress) {
            handleScrubber(event.x)
        } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            // Keep scrubber visible for 2 seconds or fade
            postDelayed({
                touchedX = null
                inspectedBpm = null
                postInvalidate()
            }, 2500)
        }
        return true
    }

    private fun handleScrubber(touchX: Float) {
        val padL = dpToPx(28f)
        val padR = dpToPx(14f)
        val chartW = width - padL - padR
        if (chartW <= 0) return

        val clampedX = touchX.coerceIn(padL, padL + chartW)
        touchedX = clampedX

        val visibleList = getVisibleData()
        if (visibleList.isNotEmpty()) {
            val ratio = (clampedX - padL) / chartW
            val index = (ratio * (visibleList.size - 1)).toInt().coerceIn(0, visibleList.size - 1)
            inspectedBpm = visibleList[index]
        }
        postInvalidate()
    }

    /**
     * Extracts the slice of points according to TimeRange, zoomScale, and panRatio.
     */
    private fun getVisibleData(): List<Int> {
        val baseList = historicalPoints ?: allSessionPoints
        if (baseList.isEmpty()) return emptyList()

        // 1. Time range filter
        val rangeCount = when (activeRange) {
            TimeRange.SEC_60 -> 60
            TimeRange.MIN_5 -> 300
            TimeRange.MIN_10 -> 600
            TimeRange.MIN_30 -> 1800
            TimeRange.HOUR_1 -> 3600
            TimeRange.ALL -> baseList.size
        }

        val rangeFiltered = if (activeRange == TimeRange.ALL || historicalPoints != null || baseList.size <= rangeCount) {
            baseList
        } else {
            baseList.takeLast(rangeCount)
        }

        // 2. Zoom & Pan window
        if (zoomScale <= 1.05f || rangeFiltered.size < 10) {
            return rangeFiltered
        }

        val visibleSize = maxOf(6, (rangeFiltered.size / zoomScale).toInt())
        val maxStart = rangeFiltered.size - visibleSize
        val start = (panRatio * maxStart).toInt().coerceIn(0, maxStart)
        return rangeFiltered.subList(start, start + visibleSize)
    }

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

        val visibleList = getVisibleData()

        // Dynamic Y scale
        val minBpm = if (visibleList.isEmpty()) 50 else maxOf(40, visibleList.minOrNull()!! - 10)
        val maxBpm = if (visibleList.isEmpty()) 150 else maxOf(130, visibleList.maxOrNull()!! + 10)
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

        if (visibleList.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = dpToPx(11f)
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(AppStrings.get("waiting_data", lang), w / 2f, h / 2f + dpToPx(4f), emptyPaint)
            return
        }

        val count = visibleList.size
        linePath.reset()
        fillPath.reset()

        // If in 60s live mode and points count < 60, spread as it arrives
        val stepX = if (activeRange == TimeRange.SEC_60 && historicalPoints == null && zoomScale <= 1.05f) {
            if (count < 60) chartW / 59f else chartW / (count - 1).toFloat()
        } else {
            if (count > 1) chartW / (count - 1).toFloat() else chartW
        }

        var lastX = padL
        var lastY = padT + chartH

        for (i in 0 until count) {
            val bpm = visibleList[i]
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

        val latestBpm = visibleList.last()
        val isClutch = latestBpm >= 160
        val lineColor = if (isClutch) Color.parseColor("#FF0055") else Color.parseColor("#FF2D55")

        // 1. High-Contrast Gradient Fill under curve
        fillPaint.shader = LinearGradient(
            0f, padT, 0f, padT + chartH,
            if (isClutch) Color.argb(120, 255, 0, 85) else Color.argb(95, 255, 45, 85),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        // 2. Neon Glow Bloom Layer (Thick semi-transparent line underneath)
        glowLinePaint.color = if (isClutch) Color.argb(90, 255, 0, 85) else Color.argb(75, 255, 45, 85)
        glowLinePaint.strokeWidth = dpToPx(6.5f)
        canvas.drawPath(linePath, glowLinePaint)

        // 3. Crisp Foreground Neon Stroke
        linePaint.color = lineColor
        linePaint.strokeWidth = dpToPx(2.6f)
        canvas.drawPath(linePath, linePaint)

        // 4. Glowing Live Cursor at the end (if not inspecting history)
        if (historicalPoints == null && zoomScale <= 1.05f) {
            dotGlowPaint.color = if (isClutch) Color.argb(160, 255, 0, 85) else Color.argb(130, 255, 45, 85)
            canvas.drawCircle(lastX, lastY, dpToPx(7f), dotGlowPaint)
            dotGlowPaint.color = if (isClutch) Color.argb(230, 255, 0, 85) else Color.argb(200, 255, 45, 85)
            canvas.drawCircle(lastX, lastY, dpToPx(4.5f), dotGlowPaint)
            canvas.drawCircle(lastX, lastY, dpToPx(2.5f), dotPaint)
        }

        // 5. Scrubber / Inspection Marker (when touched)
        if (touchedX != null && inspectedBpm != null) {
            val sx = touchedX!!
            canvas.drawLine(sx, padT, sx, padT + chartH, scrubberLinePaint)

            // Draw floating HUD tooltip badge
            val badgeText = "$inspectedBpm BPM"
            val badgeW = dpToPx(70f)
            val badgeH = dpToPx(24f)
            val badgeX = (sx - badgeW / 2f).coerceIn(padL, padL + chartW - badgeW)
            val badgeY = padT + dpToPx(2f)

            val badgeRect = RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
            canvas.drawRoundRect(badgeRect, dpToPx(6f), dpToPx(6f), scrubberBadgePaint)
            canvas.drawRoundRect(badgeRect, dpToPx(6f), dpToPx(6f), scrubberBadgeStroke)
            canvas.drawText(badgeText, badgeRect.centerX(), badgeRect.centerY() + dpToPx(4f), scrubberTextPaint)
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

    private lateinit var heroCard: FrameLayout
    private lateinit var heroGlowAura: View
    private lateinit var heroCardContent: LinearLayout
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
    private lateinit var hrChartView: HrChartView
    private lateinit var rangeButtonsContainer: LinearLayout
    private val rangeButtons = mutableMapOf<TimeRange, TextView>()

    private lateinit var btnResetZoom: TextView

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
    private var lastRawStatus: String = "Остановлено"

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
                    lastRawStatus = status
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
                                updateHeroGlow(isClutch = true, bpm = bpm)
                            }
                            bpm >= 130 -> {
                                tvBpm.setTextColor(Color.parseColor("#EF4444"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                updateHeroGlow(isClutch = false, bpm = bpm)
                            }
                            bpm >= 100 -> {
                                tvBpm.setTextColor(Color.parseColor("#F59E0B"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                updateHeroGlow(isClutch = false, bpm = bpm)
                            }
                            else -> {
                                tvBpm.setTextColor(Color.parseColor("#10B981"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                updateHeroGlow(isClutch = false, bpm = bpm)
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
                        updateHeroGlow(isClutch = false, bpm = 0)
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
            setBackgroundColor(Color.parseColor("#060910"))
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
            text = "● " + AppStrings.formatStatus(lastRawStatus, currentLang)
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
        // 2. HERO CARD (Heart Rate, Authentic Cyber Glow Aura & Mini Stats)
        // ==========================================
        heroCard = FrameLayout(this).apply {
            background = makeHeroCardDrawable(isClutch = false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        // Dedicated luminous neon glow halo behind the BPM display
        heroGlowAura = View(this).apply {
            background = makeGlowAuraDrawable(isClutch = false, hasBpm = false)
            layoutParams = FrameLayout.LayoutParams(dp(220), dp(130), Gravity.CENTER)
        }
        heroCard.addView(heroGlowAura)

        heroCardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvHeartIcon = TextView(this).apply {
            text = "❤️"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        tvBpm = TextView(this).apply {
            text = "--"
            textSize = 52f
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

        heroCardContent.addView(tvHeartIcon)
        heroCardContent.addView(tvBpm)
        heroCardContent.addView(tvBpmLabel)
        heroCardContent.addView(metricsRow)
        heroCard.addView(heroCardContent)
        root.addView(heroCard)

        // ==========================================
        // 3. GRAPH CARD (Time Range Pills + Zoom Reset + Canvas Chart)
        // ==========================================
        val graphCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeDrawable(
                bgColor = Color.parseColor("#0C101A"),
                radius = dp(18).toFloat(),
                strokeColor = Color.parseColor("#1A2438"),
                strokeWidth = dp(1)
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val graphTopHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        tvGraphTitle = TextView(this).apply {
            text = "📈 " + AppStrings.get("graph_title", currentLang)
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnResetZoom = TextView(this).apply {
            text = AppStrings.get("zoom_reset", currentLang)
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#38BDF8"))
            background = makeDrawable(Color.parseColor("#0F223D"), radius = dp(8).toFloat(), strokeColor = Color.parseColor("#2563EB"), strokeWidth = dp(1))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
            setOnClickListener {
                hrChartView.resetZoom()
            }
        }

        graphTopHeader.addView(tvGraphTitle)
        graphTopHeader.addView(btnResetZoom)
        graphCard.addView(graphTopHeader)

        // Time Range Pills Bar: [ 60s ] [ 5m ] [ 10m ] [ 30m ] [ 1h ] [ All ]
        val hScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        rangeButtonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (range in TimeRange.values()) {
            val btn = TextView(this).apply {
                text = range.getLabel(currentLang)
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(11), dp(5), dp(11), dp(5))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(6) }

                setOnClickListener {
                    selectTimeRange(range)
                }
            }
            rangeButtons[range] = btn
            rangeButtonsContainer.addView(btn)
        }
        updateRangePillsUI()
        hScrollView.addView(rangeButtonsContainer)
        graphCard.addView(hScrollView)

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
                dp(135)
            )
            onZoomChangedListener = { isZoomed, scale ->
                btnResetZoom.visibility = if (isZoomed) View.VISIBLE else View.GONE
                if (isZoomed) {
                    btnResetZoom.text = "↺ ${(scale * 10).toInt() / 10f}x"
                }
            }
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
        btnResetZoom.text = AppStrings.get("zoom_reset", currentLang)

        btnToggle.text = if (isRunning) AppStrings.get("btn_stop", currentLang) else AppStrings.get("btn_start", currentLang)
        btnHideLogs.text = if (isLogsVisible) AppStrings.get("btn_hide_logs", currentLang) else AppStrings.get("btn_show_logs", currentLang)
        btnHistory.text = AppStrings.get("btn_history", currentLang)
        btnCopy.text = AppStrings.get("btn_copy", currentLang)
        btnClear.text = AppStrings.get("btn_clear", currentLang)
        tvConsoleTitle.text = AppStrings.get("logs_title", currentLang)
        btnHistoricalClose.text = "✕ " + AppStrings.get("back_to_live", currentLang)

        updateStatusBadge(lastRawStatus)
        updateRangePillsUI()

        hrChartView.lang = currentLang
        hrChartView.invalidate()
    }

    // ==========================================
    // TIME RANGE PILLS
    // ==========================================
    private fun selectTimeRange(range: TimeRange) {
        if (hrChartView.isHistorical()) {
            closeHistoricalView()
        }
        hrChartView.setTimeRange(range)
        updateRangePillsUI()
    }

    private fun updateRangePillsUI() {
        for ((range, btn) in rangeButtons) {
            btn.text = range.getLabel(currentLang)
            if (range == hrChartView.activeRange && !hrChartView.isHistorical()) {
                btn.setTextColor(Color.WHITE)
                btn.background = makeDrawable(
                    Color.parseColor("#2563EB"),
                    radius = dp(10).toFloat(),
                    strokeColor = Color.parseColor("#60A5FA"),
                    strokeWidth = dp(1)
                )
            } else {
                btn.setTextColor(Color.parseColor("#64748B"))
                btn.background = makeDrawable(
                    Color.parseColor("#0F1422"),
                    radius = dp(10).toFloat(),
                    strokeColor = Color.parseColor("#1C263B"),
                    strokeWidth = dp(1)
                )
            }
        }
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

    private fun closeHistoricalView() {
        historicalBanner.visibility = View.GONE
        hrChartView.returnToLive()
        tvMinVal.text = if (minBpm > 0) minBpm.toString() else "--"
        tvAvgVal.text = if (bpmCount > 0) (bpmSum / bpmCount).toString() else "--"
        tvMaxVal.text = if (maxBpm > 0) maxBpm.toString() else "--"
        updateRangePillsUI()
    }

    // ==========================================
    // SESSION HISTORY PERSISTENCE & VIEWER
    // ==========================================
    private fun saveCurrentSession() {
        val points = hrChartView.getSessionPoints()
        if (points.size < 5) return

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

                val ptsArray = JSONArray()
                val step = maxOf(1, points.size / 500)
                for (i in 0 until points.size step step) {
                    ptsArray.put(points[i])
                }
                put("points", ptsArray)
            }

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
                bgColor = Color.parseColor("#0A0E18"),
                radius = dp(20).toFloat(),
                strokeColor = Color.parseColor("#1E2A42"),
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
                        bgColor = Color.parseColor("#111726"),
                        radius = dp(12).toFloat(),
                        strokeColor = Color.parseColor("#1B253D"),
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
                        hrChartView.showHistorical(s.points)
                        historicalBanner.visibility = View.VISIBLE
                        tvHistoricalInfo.text = "${AppStrings.get("viewing_history", currentLang)} ${s.dateStr} (${s.durationStr})"
                        tvMinVal.text = s.min.toString()
                        tvAvgVal.text = s.avg.toString()
                        tvMaxVal.text = s.max.toString()
                        updateRangePillsUI()
                    }
                }

                row2.addView(tvStats)
                row2.addView(btnInspect)
                card.addView(row2)

                listLayout.addView(card)
            }

            sv.addView(listLayout)
            container.addView(sv)

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
        updateRangePillsUI()
    }

    private fun updateStatusBadge(status: String) {
        lastRawStatus = status
        val localized = AppStrings.formatStatus(status, currentLang)
        tvStatusBadge.text = "● $localized"

        when {
            localized.contains("Streaming", true) || localized.contains("Трансляция", true) || localized.contains("Authorized", true) || localized.contains("Авторизовано", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#063321"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#10B981"), strokeWidth = dp(1))
            }
            localized.contains("Connecting", true) || localized.contains("Подключение", true) || localized.contains("Polling", true) || localized.contains("Опрос", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#3B2904"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#F59E0B"), strokeWidth = dp(1))
            }
            localized.contains("Error", true) || localized.contains("Ошибка", true) || localized.contains("OFF", true) || localized.contains("выключен", true) -> {
                tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#381111"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#EF4444"), strokeWidth = dp(1))
            }
            else -> {
                tvStatusBadge.setTextColor(Color.parseColor("#94A3B8"))
                tvStatusBadge.background = makeDrawable(Color.parseColor("#1E2433"), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#2E384D"), strokeWidth = dp(1))
            }
        }
    }

    private fun updateHeroGlow(isClutch: Boolean, bpm: Int) {
        heroCard.background = makeHeroCardDrawable(isClutch)
        heroGlowAura.background = makeGlowAuraDrawable(isClutch, bpm > 0)
    }

    private fun startHeartPulseAnimation(bpm: Int) {
        val durationMs = (Math.max(0.25, Math.min(2.0, 60.0 / bpm)) * 1000).toLong()
        if (heartAnimator != null && heartAnimator?.duration == durationMs) return
        heartAnimator?.cancel()
        heartAnimator = ObjectAnimator.ofPropertyValuesHolder(
            tvHeartIcon,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.28f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.28f, 1f)
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
        updateStatusBadge("Остановлено")
        tvBpm.text = "--"
        tvBpm.setTextColor(Color.parseColor("#6B7280"))
        tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
        tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
        updateHeroGlow(isClutch = false, bpm = 0)
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

    /**
     * Card background with dark glass & glowing neon borders.
     */
    private fun makeHeroCardDrawable(isClutch: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            if (isClutch) {
                setColor(Color.parseColor("#180B15"))
                setStroke(dp(2), Color.parseColor("#FF0055"))
            } else {
                setColor(Color.parseColor("#0C111E"))
                setStroke(dp(1), Color.parseColor("#1E2B44"))
            }
        }
    }

    /**
     * Radial neon bloom aura behind the heart and BPM.
     */
    private fun makeGlowAuraDrawable(isClutch: Boolean, hasBpm: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dp(140).toFloat()
            if (!hasBpm) {
                setColors(intArrayOf(
                    Color.argb(35, 56, 189, 248),
                    Color.argb(10, 56, 189, 248),
                    Color.TRANSPARENT
                ))
            } else if (isClutch) {
                setColors(intArrayOf(
                    Color.argb(140, 255, 0, 85),
                    Color.argb(50, 255, 0, 85),
                    Color.TRANSPARENT
                ))
            } else {
                setColors(intArrayOf(
                    Color.argb(90, 255, 45, 85),
                    Color.argb(30, 255, 45, 85),
                    Color.TRANSPARENT
                ))
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
