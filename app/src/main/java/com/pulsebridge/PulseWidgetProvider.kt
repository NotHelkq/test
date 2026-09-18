package com.pulsebridge

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.widget.RemoteViews
import androidx.core.graphics.PathParser

class PulseCompactWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (widgetId in appWidgetIds) {
            PulseWidgetHelper.updateCompactWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == PulseWidgetHelper.ACTION_UPDATE_WIDGETS) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PulseCompactWidgetProvider::class.java))
            for (id in ids) {
                PulseWidgetHelper.updateCompactWidget(context, appWidgetManager, id)
            }
        }
    }
}

class PulseExpandedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (widgetId in appWidgetIds) {
            PulseWidgetHelper.updateExpandedWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            PulseWidgetHelper.ACTION_WIDGET_TOGGLE -> {
                if (PulseBleService.isRunning) {
                    val stopIntent = Intent(context, PulseBleService::class.java).apply {
                        action = "ACTION_STOP_SERVICE"
                    }
                    context.startService(stopIntent)
                } else {
                    val startIntent = Intent(context, PulseBleService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startIntent)
                    } else {
                        context.startService(startIntent)
                    }
                }
                // Refresh widgets immediately
                PulseWidgetHelper.updateAllWidgets(context)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            PulseWidgetHelper.ACTION_UPDATE_WIDGETS -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PulseExpandedWidgetProvider::class.java))
                for (id in ids) {
                    PulseWidgetHelper.updateExpandedWidget(context, appWidgetManager, id)
                }
            }
        }
    }
}

object PulseWidgetHelper {
    const val ACTION_UPDATE_WIDGETS = "com.pulsebridge.ACTION_UPDATE_WIDGETS"
    const val ACTION_WIDGET_TOGGLE = "com.pulsebridge.ACTION_WIDGET_TOGGLE"

    // Dynamic telemetry state cache
    var currentBpm: Int = 0
    var currentBattery: Int = 0
    var currentSteps: Int = 0
    var currentCalories: Int = 0
    var currentDistanceKm: Float = 0f
    var currentStatus: String = "● OFF"
    var isStreaming: Boolean = false
    var minBpm: Int = 0
    var avgBpm: Int = 0
    var maxBpm: Int = 0

    private val HEART_SVG_PATH = "M12,21.35L10.55,20.03C5.4,15.36 2,12.28 2,8.5C2,5.42 4.42,3 7.5,3C9.24,3 10.91,3.81 12,5.09C13.09,3.81 14.76,3 16.5,3C19.58,3 22,5.42 22,8.5C22,12.28 18.6,15.36 13.45,20.04L12,21.35Z"

    fun getZoneColor(bpm: Int): Int {
        return when {
            bpm <= 0 -> Color.parseColor("#64748B")
            bpm < 110 -> Color.parseColor("#00E676")   // Green (Calm)
            bpm < 130 -> Color.parseColor("#FF9100")   // Orange (Normal)
            bpm < 150 -> Color.parseColor("#FF3D00")   // Red (High)
            else -> Color.parseColor("#FF2A85")        // Neon Pink (Clutch)
        }
    }

    fun getZoneTitle(bpm: Int): String {
        return when {
            bpm <= 0 -> "PULSE BRIDGE"
            bpm < 110 -> "CALM (<110)"
            bpm < 130 -> "NORMAL (110-129)"
            bpm < 150 -> "HIGH (130-149)"
            else -> "CLUTCH (150+)"
        }
    }

    fun createHeartBitmap(color: Int, sizePx: Int = 96): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        try {
            val path = PathParser.createPathFromPathData(HEART_SVG_PATH)
            val matrix = Matrix()
            // SVG viewbox is 24x24
            matrix.setScale(sizePx / 24f, sizePx / 24f)
            path.transform(matrix)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            canvas.drawPath(path, paint)
        } catch (e: Exception) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f * 0.8f, paint)
        }
        return bitmap
    }

    fun updateAllWidgets(
        context: Context,
        bpm: Int = currentBpm,
        status: String = currentStatus,
        battery: Int = currentBattery,
        steps: Int = currentSteps,
        calories: Int = currentCalories,
        distanceKm: Float = currentDistanceKm,
        running: Boolean = isStreaming,
        min: Int = minBpm,
        avg: Int = avgBpm,
        max: Int = maxBpm
    ) {
        currentBpm = bpm
        currentStatus = status
        currentBattery = battery
        currentSteps = steps
        currentCalories = calories
        currentDistanceKm = distanceKm
        isStreaming = running
        minBpm = min
        avgBpm = avg
        maxBpm = max

        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Update Compact Widgets
        val compactIds = appWidgetManager.getAppWidgetIds(ComponentName(context, PulseCompactWidgetProvider::class.java))
        for (id in compactIds) {
            updateCompactWidget(context, appWidgetManager, id)
        }

        // Update Expanded Widgets
        val expandedIds = appWidgetManager.getAppWidgetIds(ComponentName(context, PulseExpandedWidgetProvider::class.java))
        for (id in expandedIds) {
            updateExpandedWidget(context, appWidgetManager, id)
        }
    }

    fun updateCompactWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_pulse_compact)

        // PendingIntent to launch MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetCompactRoot, openPendingIntent)

        // Pulse and zone
        val zoneColor = getZoneColor(currentBpm)
        val heartBitmap = createHeartBitmap(zoneColor, 80)
        views.setImageViewBitmap(R.id.ivWidgetHeart, heartBitmap)

        if (currentBpm > 0 && isStreaming) {
            views.setTextViewText(R.id.tvWidgetBpm, currentBpm.toString())
            views.setTextColor(R.id.tvWidgetBpm, Color.WHITE)
            views.setTextViewText(R.id.tvWidgetZone, getZoneTitle(currentBpm))
            views.setTextColor(R.id.tvWidgetZone, zoneColor)
            views.setTextViewText(R.id.tvWidgetStatus, "● LIVE")
            views.setTextColor(R.id.tvWidgetStatus, Color.parseColor("#10B981"))
        } else {
            views.setTextViewText(R.id.tvWidgetBpm, "--")
            views.setTextColor(R.id.tvWidgetBpm, Color.parseColor("#64748B"))
            views.setTextViewText(R.id.tvWidgetZone, "PULSE BRIDGE")
            views.setTextColor(R.id.tvWidgetZone, Color.parseColor("#64748B"))
            views.setTextViewText(R.id.tvWidgetStatus, if (isStreaming) "● CONNECTING" else "● OFF")
            views.setTextColor(R.id.tvWidgetStatus, if (isStreaming) Color.parseColor("#F59E0B") else Color.parseColor("#64748B"))
        }

        // Battery
        if (currentBattery > 0) {
            views.setTextViewText(R.id.tvWidgetBattery, "$currentBattery%")
            views.setTextColor(R.id.tvWidgetBattery, if (currentBattery <= 20) Color.parseColor("#EF4444") else Color.parseColor("#10B981"))
        } else {
            views.setTextViewText(R.id.tvWidgetBattery, "--%")
            views.setTextColor(R.id.tvWidgetBattery, Color.parseColor("#64748B"))
        }

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    fun updateExpandedWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_pulse_expanded)

        // PendingIntent to launch MainActivity on background click
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.layoutLeft, openPendingIntent)
        views.setOnClickPendingIntent(R.id.layoutMiddle, openPendingIntent)
        views.setOnClickPendingIntent(R.id.btnWidgetOpen, openPendingIntent)

        // PendingIntent for Start/Stop Button
        val toggleIntent = Intent(context, PulseExpandedWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            context, 2, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btnWidgetToggle, togglePendingIntent)

        // Pulse and zone
        val zoneColor = getZoneColor(currentBpm)
        val heartBitmap = createHeartBitmap(zoneColor, 80)
        views.setImageViewBitmap(R.id.ivExpandedHeart, heartBitmap)

        if (currentBpm > 0 && isStreaming) {
            views.setTextViewText(R.id.tvExpandedBpm, currentBpm.toString())
            views.setTextColor(R.id.tvExpandedBpm, Color.WHITE)
            views.setTextViewText(R.id.tvExpandedZone, getZoneTitle(currentBpm))
            views.setTextColor(R.id.tvExpandedZone, zoneColor)
            views.setTextViewText(R.id.tvExpandedStatus, "● ТРАНСЛЯЦИЯ")
            views.setTextColor(R.id.tvExpandedStatus, Color.parseColor("#10B981"))
        } else {
            views.setTextViewText(R.id.tvExpandedBpm, "--")
            views.setTextColor(R.id.tvExpandedBpm, Color.parseColor("#64748B"))
            views.setTextViewText(R.id.tvExpandedZone, "BEATS PER MINUTE")
            views.setTextColor(R.id.tvExpandedZone, Color.parseColor("#64748B"))
            views.setTextViewText(R.id.tvExpandedStatus, if (isStreaming) "● ПОДКЛЮЧЕНИЕ..." else "● ОСТАНОВЛЕНО")
            views.setTextColor(R.id.tvExpandedStatus, if (isStreaming) Color.parseColor("#F59E0B") else Color.parseColor("#64748B"))
        }

        // Stats: Min, Avg, Max
        if (minBpm > 0 && maxBpm > 0) {
            views.setTextViewText(R.id.tvExpandedStats, "МИН: $minBpm  СРЕД: $avgBpm  МАКС: $maxBpm")
        } else {
            views.setTextViewText(R.id.tvExpandedStats, "МИН: --  СРЕД: --  МАКС: --")
        }

        // Activity: Steps & Calories
        val stepsStr = if (currentSteps > 0) "$currentSteps" else "--"
        val calStr = if (currentCalories > 0) "$currentCalories" else "--"
        views.setTextViewText(R.id.tvExpandedActivity, "👟 $stepsStr шагов  •  🔥 $calStr ккал")

        // Device & Battery
        val batStr = if (currentBattery > 0) "$currentBattery%" else "--%"
        views.setTextViewText(R.id.tvExpandedDevice, "🔋 $batStr  •  Band 9 Active")

        // Button Start / Stop state
        if (isStreaming) {
            views.setTextViewText(R.id.btnWidgetToggle, "⏹ СТОП")
            views.setInt(R.id.btnWidgetToggle, "setBackgroundResource", R.drawable.widget_btn_stop_bg)
        } else {
            views.setTextViewText(R.id.btnWidgetToggle, "▶ СТАРТ")
            views.setInt(R.id.btnWidgetToggle, "setBackgroundResource", R.drawable.widget_btn_bg)
        }

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
