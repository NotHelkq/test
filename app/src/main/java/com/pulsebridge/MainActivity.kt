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
            "app_title" -> "PULSE"
            "app_title_accent" -> "BRIDGE"
            "device_sub" -> "Smart Band 9 Active • BLE v2"
            "status_stopped" -> "Stopped"
            "status_live" -> "Streaming"
            "bpm_label" -> "BEATS PER MINUTE"
            "clutch_label" -> "🔥 CLUTCH MODE (160+)"
            "metric_min" -> "MIN"
            "metric_avg" -> "AVG"
            "metric_max" -> "MAX"
            "metric_bat" -> "BATTERY"
            "graph_title" -> "REAL-TIME TELEMETRY"
            "btn_start" -> "▶  START MONITORING"
            "btn_stop" -> "⏹  STOP MONITORING"
            "btn_history" -> "📜 HISTORY"
            "btn_copy" -> "📋 SYNC LOGS"
            "btn_clear" -> "🗑 CLEAR"
            "btn_hide_logs" -> "👁 HIDE LOGS"
            "btn_show_logs" -> "👁 SHOW LOGS"
            "logs_title" -> "BLE EVENT STREAM"
            "waiting_data" -> "AWAITING HEART RATE SIGNAL..."
            "history_title" -> "SESSION ARCHIVE"
            "history_empty" -> "No recorded sessions yet.\nComplete a monitoring session to save history!"
            "history_clear" -> "Clear All History"
            "close" -> "Close"
            "view_graph" -> "View on Graph"
            "viewing_history" -> "Archive:"
            "back_to_live" -> "Back to Live"
            "zoom_reset" -> "↺ Reset Zoom"
            "toast_copied" -> "✅ Logs copied & uploaded to server!"
            "toast_cleared" -> "🗑 Session stats and logs cleared"
            "toast_history_cleared" -> "🗑 Session history cleared"
            else -> key
        }
        Lang.RU -> when (key) {
            "app_title" -> "PULSE"
            "app_title_accent" -> "BRIDGE"
            "device_sub" -> "Smart Band 9 Active • BLE v2"
            "status_stopped" -> "Остановлено"
            "status_live" -> "В эфире"
            "bpm_label" -> "УДАРОВ В МИНУТУ"
            "clutch_label" -> "🔥 КЛАТЧ РЕЖИМ (160+)"
            "metric_min" -> "МИН"
            "metric_avg" -> "СРЕДНИЙ"
            "metric_max" -> "МАКС"
            "metric_bat" -> "ЗАРЯД"
            "graph_title" -> "ТЕЛЕМЕТРИЯ ПУЛЬСА"
            "btn_start" -> "▶  СТАРТ МОНИТОРИНГА"
            "btn_stop" -> "⏹  ОСТАНОВИТЬ"
            "btn_history" -> "📜 ИСТОРИЯ"
            "btn_copy" -> "📋 СИНХРОНИЗАЦИЯ"
            "btn_clear" -> "🗑 ОЧИСТИТЬ"
            "btn_hide_logs" -> "👁 СКРЫТЬ ЛОГИ"
            "btn_show_logs" -> "👁 ПОКАЗАТЬ ЛОГИ"
            "logs_title" -> "ПОТОК СОБЫТИЙ BLE"
            "waiting_data" -> "ОЖИДАНИЕ СИГНАЛА ПУЛЬСА..."
            "history_title" -> "АРХИВ СЕССИЙ"
            "history_empty" -> "История пуста.\nЗавершите сессию мониторинга для сохранения!"
            "history_clear" -> "Очистить историю"
            "close" -> "Закрыть"
            "view_graph" -> "Открыть график"
            "viewing_history" -> "Архив:"
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
                raw.contains("Workout", ignoreCase = true) || raw.contains("Трансляция", ignoreCase = true) -> "В эфире"
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
 * Custom vector cyberpunk electric pulse wave glyph with dynamic neon glow bloom
 * and real-time beat pulse animation (zero hearts, zero emoji).
 */
class PulseGlyphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var isClutch = false
    private var activeColor = Color.parseColor("#00F0FF")
    private val glyphPath = Path()

    private val strokeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    fun setStatus(clutch: Boolean, bpm: Int) {
        isClutch = clutch
        activeColor = when {
            bpm <= 0 -> Color.parseColor("#4A5C78")
            clutch -> Color.parseColor("#FF0055")
            bpm >= 130 -> Color.parseColor("#EF4444")
            bpm >= 100 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#00F0FF")
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val s = minOf(w, h) / 48f
        val cx = w / 2f
        val cy = h / 2f

        glyphPath.reset()
        // Stylized electric lightning bolt + pulse waveform
        glyphPath.moveTo(cx - 20f * s, cy)
        glyphPath.lineTo(cx - 13f * s, cy)
        glyphPath.lineTo(cx - 8f * s, cy - 6f * s)
        glyphPath.lineTo(cx - 3f * s, cy + 8f * s)
        glyphPath.lineTo(cx + 3f * s, cy - 18f * s) // High electric spike
        glyphPath.lineTo(cx + 8f * s, cy + 16f * s) // Deep spike
        glyphPath.lineTo(cx + 13f * s, cy - 6f * s)
        glyphPath.lineTo(cx + 16f * s, cy)
        glyphPath.lineTo(cx + 20f * s, cy)

        val glowAlpha = if (isClutch) 140 else 90
        val r = Color.red(activeColor)
        val g = Color.green(activeColor)
        val b = Color.blue(activeColor)
        val glowColor = Color.argb(glowAlpha, r, g, b)

        // 1. Neon Glow Stroke Underlay
        strokeGlowPaint.color = glowColor
        strokeGlowPaint.strokeWidth = s * 5.5f
        canvas.drawPath(glyphPath, strokeGlowPaint)

        // 2. Crisp Foreground Neon Beam Line
        strokePaint.color = activeColor
        strokePaint.strokeWidth = s * 2.5f
        canvas.drawPath(glyphPath, strokePaint)

        // 3. Electric spark node at highest peak
        val peakX = cx + 3f * s
        val peakY = cy - 18f * s
        canvas.drawCircle(peakX, peakY, s * 2.2f, sparkPaint)
    }
}

/**
 * Custom live & session heart rate chart view with:
 * - Time range filtering (60s, 5m, 10m, 30m, 1h, All)
 * - Pinch-to-zoom (up to 25x) and horizontal panning gestures
 * - Interactive scrubber / touch inspection
 * - Vibrant dual-layer neon beam stroke & high-contrast gradient fill
 */

/**
 * Smooth ambient radial glow underlay for the heart rate value.
 * Dynamically computes safe radius to ensure the gradient reaches 100% transparency (0 alpha)
 * well within the card boundaries, completely preventing rectangular bounding-box clipping.
 */
class SmoothAmbientGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }

    private var activeColor: Int = Color.parseColor("#9333EA")
    private var isClutch: Boolean = false
    private var hasBpm: Boolean = false

    fun updateGlow(clutch: Boolean, bpm: Int) {
        isClutch = clutch
        hasBpm = bpm > 0
        activeColor = when {
            bpm <= 0 -> Color.parseColor("#4A2574")
            clutch -> Color.parseColor("#FF0055")
            bpm >= 130 -> Color.parseColor("#EF4444")
            bpm >= 100 -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#9333EA")
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h * 0.40f

        // Safe radius strictly inside bounds, never touching card edges
        val safeRadius = minOf(w * 0.38f, cy * 0.80f, (h - cy) * 0.80f).coerceAtLeast(dpToPx(35f))

        val r = Color.red(activeColor)
        val g = Color.green(activeColor)
        val b = Color.blue(activeColor)

        val centerAlpha = when {
            isClutch -> 150
            hasBpm -> 110
            else -> 40
        }

        val colors = intArrayOf(
            Color.argb(centerAlpha, r, g, b),
            Color.argb((centerAlpha * 0.52f).toInt(), r, g, b),
            Color.argb((centerAlpha * 0.16f).toInt(), r, g, b),
            Color.argb(0, r, g, b)
        )
        val stops = floatArrayOf(0f, 0.38f, 0.72f, 1f)

        glowPaint.shader = RadialGradient(
            cx, cy, safeRadius,
            colors, stops,
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(cx, cy, safeRadius, glowPaint)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}

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
    private var panRatio: Float = 1.0f

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
        color = Color.parseColor("#231438")
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpToPx(9.5f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.parseColor("#7A5FA0")
    }

    private val scrubberLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
        color = Color.parseColor("#A855F7")
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(3f), dpToPx(3f)), 0f)
    }

    private val scrubberBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#180D2B")
    }

    private val scrubberBadgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.parseColor("#A855F7")
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

    private fun getVisibleData(): List<Int> {
        val baseList = historicalPoints ?: allSessionPoints
        if (baseList.isEmpty()) return emptyList()

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

        val minBpm = if (visibleList.isEmpty()) 50 else maxOf(40, visibleList.minOrNull()!! - 10)
        val maxBpm = if (visibleList.isEmpty()) 150 else maxOf(130, visibleList.maxOrNull()!! + 10)
        val range = maxOf(30, maxBpm - minBpm)

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
                color = Color.parseColor("#4B5B75")
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
        val lineColor = if (isClutch) Color.parseColor("#FF0055") else Color.parseColor("#A855F7")

        // 1. High-Contrast Gradient Fill
        fillPaint.shader = LinearGradient(
            0f, padT, 0f, padT + chartH,
            if (isClutch) Color.argb(130, 255, 0, 85) else Color.argb(120, 168, 85, 247),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        // 2. Neon Beam Bloom Underlay
        glowLinePaint.color = if (isClutch) Color.argb(100, 255, 0, 85) else Color.argb(90, 168, 85, 247)
        glowLinePaint.strokeWidth = dpToPx(7f)
        canvas.drawPath(linePath, glowLinePaint)

        // 3. Crisp Foreground Neon Line
        linePaint.color = lineColor
        linePaint.strokeWidth = dpToPx(2.8f)
        canvas.drawPath(linePath, linePaint)

        // 4. Glowing Live Cursor at the end
        if (historicalPoints == null && zoomScale <= 1.05f) {
            dotGlowPaint.color = if (isClutch) Color.argb(160, 255, 0, 85) else Color.argb(140, 168, 85, 247)
            canvas.drawCircle(lastX, lastY, dpToPx(8f), dotGlowPaint)
            dotGlowPaint.color = if (isClutch) Color.argb(240, 255, 0, 85) else Color.argb(240, 168, 85, 247)
            canvas.drawCircle(lastX, lastY, dpToPx(5f), dotGlowPaint)
            canvas.drawCircle(lastX, lastY, dpToPx(2.8f), dotPaint)
        }

        // 5. Scrubber Tooltip when touched
        if (touchedX != null && inspectedBpm != null) {
            val sx = touchedX!!
            canvas.drawLine(sx, padT, sx, padT + chartH, scrubberLinePaint)

            val badgeText = "$inspectedBpm BPM"
            val badgeW = dpToPx(74f)
            val badgeH = dpToPx(26f)
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
    private lateinit var tvAppTitleAccent: TextView
    private lateinit var tvAppSub: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var btnLangToggle: TextView

    private lateinit var heroCard: FrameLayout
    private lateinit var heroGlowAura: SmoothAmbientGlowView
    private lateinit var pulseGlyph: PulseGlyphView
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

    private var glyphAnimator: ObjectAnimator? = null
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
                                pulseGlyph.setStatus(true, bpm)
                                updateHeroGlow(isClutch = true, bpm = bpm)
                            }
                            bpm >= 130 -> {
                                tvBpm.setTextColor(Color.parseColor("#EF4444"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                pulseGlyph.setStatus(false, bpm)
                                updateHeroGlow(isClutch = false, bpm = bpm)
                            }
                            bpm >= 100 -> {
                                tvBpm.setTextColor(Color.parseColor("#F59E0B"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                pulseGlyph.setStatus(false, bpm)
                                updateHeroGlow(isClutch = false, bpm = bpm)
                            }
                            else -> {
                                tvBpm.setTextColor(Color.parseColor("#10B981"))
                                tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                                tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                                pulseGlyph.setStatus(false, bpm)
                                updateHeroGlow(isClutch = false, bpm = bpm)
                            }
                        }
                        startPulseGlyphAnimation(bpm)

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

                        hrChartView.addPoint(bpm)
                    } else {
                        tvBpm.text = "--"
                        tvBpm.setTextColor(Color.parseColor("#4A5C78"))
                        tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
                        tvBpmLabel.setTextColor(Color.parseColor("#64748B"))
                        pulseGlyph.setStatus(false, 0)
                        updateHeroGlow(isClutch = false, bpm = 0)
                        stopPulseGlyphAnimation()
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
            setBackgroundColor(Color.parseColor("#050811"))
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        // ==========================================
        // 1. HEADER (Title with Neon Accent + Subtitle + Lang + Status Pill)
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

        val titleLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        tvAppTitle = TextView(this).apply {
            text = AppStrings.get("app_title", currentLang)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }

        tvAppTitleAccent = TextView(this).apply {
            text = " " + AppStrings.get("app_title_accent", currentLang)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#A855F7"))
        }

        titleLine.addView(tvAppTitle)
        titleLine.addView(tvAppTitleAccent)

        tvAppSub = TextView(this).apply {
            text = AppStrings.get("device_sub", currentLang)
            textSize = 11f
            setTextColor(Color.parseColor("#5A6E8C"))
        }

        titleBox.addView(titleLine)
        titleBox.addView(tvAppSub)

        btnLangToggle = TextView(this).apply {
            text = if (currentLang == Lang.EN) "🇺🇸 EN" else "🇷🇺 RU"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            background = makeGradientDrawable(intArrayOf(Color.parseColor("#25133E"), Color.parseColor("#130922")), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#4A2574"), strokeWidth = dp(1))
            setTextColor(Color.parseColor("#C084FC"))
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
            background = makeGradientDrawable(intArrayOf(Color.parseColor("#1D1030"), Color.parseColor("#0E071A")), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#361D54"), strokeWidth = dp(1))
            setTextColor(Color.parseColor("#C4B5FD"))
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }

        headerRow.addView(titleBox)
        headerRow.addView(btnLangToggle)
        headerRow.addView(tvStatusBadge)
        root.addView(headerRow)

        // ==========================================
        // 2. HERO CARD (Electric Pulse Glyph & Unified Telemetry Bar)
        // ==========================================
        heroCard = FrameLayout(this).apply {
            background = makeHeroCardDrawable(isClutch = false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        heroGlowAura = SmoothAmbientGlowView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        heroCard.addView(heroGlowAura)

        val heroCardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        pulseGlyph = PulseGlyphView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                bottomMargin = dp(4)
            }
        }

        tvBpm = TextView(this).apply {
            text = "--"
            textSize = 56f
            letterSpacing = -0.02f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#4A5C78"))
            gravity = Gravity.CENTER
        }

        tvBpmLabel = TextView(this).apply {
            text = AppStrings.get("bpm_label", currentLang)
            textSize = 10f
            letterSpacing = 0.2f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#5A6E8C"))
            gravity = Gravity.CENTER
        }

        // Unified Symmetrical Telemetry Bar: MIN | AVG | MAX | BATTERY
        val telemetryBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = makeGradientDrawable(intArrayOf(Color.parseColor("#170B28"), Color.parseColor("#0C0517")), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#2C1547"), strokeWidth = dp(1))
            setPadding(dp(6), dp(8), dp(6), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }

        val minBadge = createMetricSegment("metric_min", "--", Color.parseColor("#38BDF8")).also {
            tvMinVal = it.first
            tvMinLbl = it.second
        }
        val avgBadge = createMetricSegment("metric_avg", "--", Color.parseColor("#FBBF24")).also {
            tvAvgVal = it.first
            tvAvgLbl = it.second
        }
        val maxBadge = createMetricSegment("metric_max", "--", Color.parseColor("#F43F5E")).also {
            tvMaxVal = it.first
            tvMaxLbl = it.second
        }
        val batBadge = createMetricSegment("metric_bat", "--%", Color.parseColor("#10B981")).also {
            tvBatteryVal = it.first
            tvBatteryLbl = it.second
        }

        telemetryBar.addView(minBadge.third)
        telemetryBar.addView(createVerticalDivider())
        telemetryBar.addView(avgBadge.third)
        telemetryBar.addView(createVerticalDivider())
        telemetryBar.addView(maxBadge.third)
        telemetryBar.addView(createVerticalDivider())
        telemetryBar.addView(batBadge.third)

        heroCardContent.addView(pulseGlyph)
        heroCardContent.addView(tvBpm)
        heroCardContent.addView(tvBpmLabel)
        heroCardContent.addView(telemetryBar)
        heroCard.addView(heroCardContent)
        root.addView(heroCard)

        // ==========================================
        // 3. GRAPH CARD (Time Range Pills + Zoom Reset + Oscilloscope Chart)
        // ==========================================
        val graphCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeGradientDrawable(
                intArrayOf(Color.parseColor("#160B26"), Color.parseColor("#0A0413")),
                radius = dp(18).toFloat(),
                strokeColor = Color.parseColor("#2F174B"),
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
            textSize = 10.5f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#7A8FA8"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnResetZoom = TextView(this).apply {
            text = AppStrings.get("zoom_reset", currentLang)
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            background = makeGradientDrawable(intArrayOf(Color.parseColor("#2E184C"), Color.parseColor("#180B2B")), radius = dp(8).toFloat(), strokeColor = Color.parseColor("#A855F7"), strokeWidth = dp(1))
            setTextColor(Color.parseColor("#C084FC"))
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
                setPadding(dp(12), dp(5), dp(12), dp(5))
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
        updateRangePillsUI()
        root.addView(graphCard)

        // ==========================================
        // 4. ACTION BUTTONS & CONTROLS
        // ==========================================
        btnToggle = Button(this).apply {
            text = AppStrings.get("btn_start", currentLang)
            textSize = 14.5f
            letterSpacing = 0.05f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeGradientButtonDrawable(
                colors = intArrayOf(Color.parseColor("#9333EA"), Color.parseColor("#6366F1")),
                pressedColors = intArrayOf(Color.parseColor("#7E22CE"), Color.parseColor("#4F46E5")),
                radius = dp(14).toFloat(),
                strokeColor = Color.parseColor("#C084FC"),
                strokeWidth = dp(1)
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

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }

        btnHideLogs = Button(this).apply {
            text = AppStrings.get("btn_hide_logs", currentLang)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            background = makeToolbarButtonDrawable()
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(5) }
            setOnClickListener {
                toggleLogsVisibility()
            }
        }

        btnHistory = Button(this).apply {
            text = AppStrings.get("btn_history", currentLang)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#C084FC"))
            background = makeToolbarButtonDrawable()
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(5) }
            setOnClickListener {
                showHistoryDialog()
            }
        }

        btnCopy = Button(this).apply {
            text = AppStrings.get("btn_copy", currentLang)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            background = makeToolbarButtonDrawable()
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(5) }
            setOnClickListener {
                copyAndUploadLogs()
            }
        }

        btnClear = Button(this).apply {
            text = AppStrings.get("btn_clear", currentLang)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            background = makeToolbarButtonDrawable()
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
            textSize = 10f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#5A6E8C"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvLiveDot = TextView(this).apply {
            text = "● LIVE"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
        }

        consoleHeader.addView(tvConsoleTitle)
        consoleHeader.addView(tvLiveDot)
        root.addView(consoleHeader)

        consoleCard = FrameLayout(this).apply {
            background = makeDrawable(
                bgColor = Color.parseColor("#04070D"),
                radius = dp(14).toFloat(),
                strokeColor = Color.parseColor("#121926"),
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
            textSize = 10f
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
        stopPulseGlyphAnimation()
        unregisterReceiver(receiver)
    }

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
        tvAppTitle.text = AppStrings.get("app_title", currentLang)
        tvAppTitleAccent.text = " " + AppStrings.get("app_title_accent", currentLang)
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
                btn.background = makeGradientDrawable(
                    intArrayOf(Color.parseColor("#9333EA"), Color.parseColor("#7C3AED")),
                    radius = dp(10).toFloat(),
                    strokeColor = Color.parseColor("#C084FC"),
                    strokeWidth = dp(1)
                )
            } else {
                btn.setTextColor(Color.parseColor("#9C8EB5"))
                btn.background = makeGradientDrawable(
                    intArrayOf(Color.parseColor("#1B0F2E"), Color.parseColor("#0E0619")),
                    radius = dp(10).toFloat(),
                    strokeColor = Color.parseColor("#2C174A"),
                    strokeWidth = dp(1)
                )
            }
        }
    }

    private fun toggleLogsVisibility() {
        isLogsVisible = !isLogsVisible
        if (isLogsVisible) {
            consoleHeader.visibility = View.VISIBLE
            consoleCard.visibility = View.VISIBLE
            btnHideLogs.text = AppStrings.get("btn_hide_logs", currentLang)
            btnHideLogs.setTextColor(Color.parseColor("#94A3B8"))
        } else {
            consoleHeader.visibility = View.GONE
            consoleCard.visibility = View.GONE
            btnHideLogs.text = AppStrings.get("btn_show_logs", currentLang)
            btnHideLogs.setTextColor(Color.parseColor("#A855F7"))
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
                bgColor = Color.parseColor("#080D18"),
                radius = dp(20).toFloat(),
                strokeColor = Color.parseColor("#1B273F"),
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
                setTextColor(Color.parseColor("#5A6E8C"))
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
                        bgColor = Color.parseColor("#0E1422"),
                        radius = dp(12).toFloat(),
                        strokeColor = Color.parseColor("#182236"),
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
            localized.contains("Streaming", true) || localized.contains("эфире", true) || localized.contains("Authorized", true) || localized.contains("Авторизовано", true) -> {
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
                tvStatusBadge.background = makeGradientDrawable(intArrayOf(Color.parseColor("#1D1030"), Color.parseColor("#0E071A")), radius = dp(12).toFloat(), strokeColor = Color.parseColor("#361D54"), strokeWidth = dp(1))
            }
        }
    }

    private fun updateHeroGlow(isClutch: Boolean, bpm: Int) {
        heroCard.background = makeHeroCardDrawable(isClutch)
        heroGlowAura.updateGlow(isClutch, bpm)
    }

    private fun startPulseGlyphAnimation(bpm: Int) {
        val durationMs = (Math.max(0.25, Math.min(2.0, 60.0 / bpm)) * 1000).toLong()
        if (glyphAnimator != null && glyphAnimator?.duration == durationMs) return
        glyphAnimator?.cancel()
        glyphAnimator = ObjectAnimator.ofPropertyValuesHolder(
            pulseGlyph,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.22f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.22f, 1f)
        ).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopPulseGlyphAnimation() {
        glyphAnimator?.cancel()
        glyphAnimator = null
        pulseGlyph.scaleX = 1f
        pulseGlyph.scaleY = 1f
    }

    private fun createMetricSegment(labelKey: String, initialVal: String, valColor: Int): Triple<TextView, TextView, LinearLayout> {
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
            textSize = 9f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#5A6E8C"))
        }
        box.addView(tvVal)
        box.addView(tvLbl)
        return Triple(tvVal, tvLbl, box)
    }

    private fun createVerticalDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#152033"))
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(22))
        }
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
        btnToggle.background = makeGradientButtonDrawable(
            colors = intArrayOf(Color.parseColor("#FF0055"), Color.parseColor("#DC2626")),
            pressedColors = intArrayOf(Color.parseColor("#CC0044"), Color.parseColor("#991B1B")),
            radius = dp(14).toFloat(),
            strokeColor = Color.parseColor("#FF4D79"),
            strokeWidth = dp(1)
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
        btnToggle.background = makeGradientButtonDrawable(
            colors = intArrayOf(Color.parseColor("#9333EA"), Color.parseColor("#6366F1")),
            pressedColors = intArrayOf(Color.parseColor("#7E22CE"), Color.parseColor("#4F46E5")),
            radius = dp(14).toFloat(),
            strokeColor = Color.parseColor("#C084FC"),
            strokeWidth = dp(1)
        )
        stopService(Intent(this, PulseBleService::class.java))
        updateStatusBadge("Остановлено")
        tvBpm.text = "--"
        tvBpm.setTextColor(Color.parseColor("#4A5C78"))
        tvBpmLabel.text = AppStrings.get("bpm_label", currentLang)
        tvBpmLabel.setTextColor(Color.parseColor("#5A6E8C"))
        pulseGlyph.setStatus(false, 0)
        updateHeroGlow(isClutch = false, bpm = 0)
        stopPulseGlyphAnimation()
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

    private fun makeHeroCardDrawable(isClutch: Boolean): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (isClutch) {
                intArrayOf(Color.parseColor("#2A0A18"), Color.parseColor("#15050D"))
            } else {
                intArrayOf(Color.parseColor("#1A0F2B"), Color.parseColor("#0E0719"))
            }
        ).apply {
            cornerRadius = dp(20).toFloat()
            if (isClutch) {
                setStroke(dp(2), Color.parseColor("#FF0055"))
            } else {
                setStroke(dp(1), Color.parseColor("#381F54"))
            }
        }
    }

    private fun makeGradientDrawable(
        colors: IntArray,
        radius: Float = 0f,
        strokeColor: Int = 0,
        strokeWidth: Int = 0,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    ): GradientDrawable {
        return GradientDrawable(orientation, colors).apply {
            cornerRadius = radius
            if (strokeWidth > 0 && strokeColor != 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun makeGradientButtonDrawable(
        colors: IntArray,
        pressedColors: IntArray,
        radius: Float,
        strokeColor: Int = 0,
        strokeWidth: Int = 0,
        orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT
    ): StateListDrawable {
        val normal = GradientDrawable(orientation, colors).apply {
            cornerRadius = radius
            if (strokeWidth > 0 && strokeColor != 0) setStroke(strokeWidth, strokeColor)
        }
        val pressed = GradientDrawable(orientation, pressedColors).apply {
            cornerRadius = radius
            if (strokeWidth > 0 && strokeColor != 0) setStroke(strokeWidth, strokeColor)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun makeToolbarButtonDrawable(
        normalColors: IntArray = intArrayOf(Color.parseColor("#221339"), Color.parseColor("#120721")),
        pressedColors: IntArray = intArrayOf(Color.parseColor("#371D5C"), Color.parseColor("#1F0D37")),
        strokeColor: Int = Color.parseColor("#472575")
    ): StateListDrawable {
        return makeGradientButtonDrawable(
            colors = normalColors,
            pressedColors = pressedColors,
            radius = dp(12).toFloat(),
            strokeColor = strokeColor,
            strokeWidth = dp(1)
        )
    }
}
