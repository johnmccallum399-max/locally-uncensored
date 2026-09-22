package com.lcdr.assistant.tools.impl

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Geocoder
import android.location.Location
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.core.app.NotificationCompat
import com.lcdr.assistant.tools.ToolCallSpec
import com.lcdr.assistant.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SystemTools @Inject constructor(@ApplicationContext private val context: Context) {

    fun getBattery(spec: ToolCallSpec): ToolResult {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ToolResult.Failure("Could not read battery status")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (level != -1 && scale != -1) (level * 100 / scale) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return ToolResult.Success("Battery: $pct% — ${if (charging) "charging" else "discharging"}")
    }

    fun getClipboard(spec: ToolCallSpec): ToolResult {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
        return if (text != null) ToolResult.Success(text)
        else ToolResult.Success("Clipboard is empty.")
    }

    fun setClipboard(spec: ToolCallSpec): ToolResult {
        val text = spec.arguments["text"] as? String
            ?: return ToolResult.Failure("'text' required")
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("LCDR", text))
        return ToolResult.Success("Clipboard updated.")
    }

    suspend fun setAlarm(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.Main) {
        val time = spec.arguments["time"] as? String
            ?: return@withContext ToolResult.Failure("'time' required (HH:mm)")
        val label = spec.arguments["label"] as? String ?: "LCDR alarm"
        val parts = time.split(":")
        if (parts.size != 2) return@withContext ToolResult.Failure("Invalid time format, use HH:mm")
        val hour = parts[0].toIntOrNull() ?: return@withContext ToolResult.Failure("Invalid hour")
        val minute = parts[1].toIntOrNull() ?: return@withContext ToolResult.Failure("Invalid minute")

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        ToolResult.Success("Alarm set for $time — $label")
    }

    suspend fun setTimer(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.Main) {
        val seconds = (spec.arguments["seconds"] as? Double)?.toInt()
            ?: return@withContext ToolResult.Failure("'seconds' required")
        val label = spec.arguments["label"] as? String ?: "LCDR timer"

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        ToolResult.Success("Timer set for ${seconds}s — $label")
    }

    fun sendNotification(spec: ToolCallSpec): ToolResult {
        val title = spec.arguments["title"] as? String ?: "LCDR"
        val body = spec.arguments["body"] as? String ?: ""

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "lcdr_tool_notifications"
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "LCDR Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notification)
        return ToolResult.Success("Notification sent.")
    }

    suspend fun getRunningApps(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3_600_000, now)
            if (stats.isNullOrEmpty()) return@withContext ToolResult.Success("No usage data (requires PACKAGE_USAGE_STATS permission)")
            val sorted = stats.sortedByDescending { it.lastTimeUsed }.take(10)
            val result = sorted.joinToString("\n") { it.packageName }
            ToolResult.Success(result)
        } catch (e: Exception) {
            ToolResult.Failure("Usage stats failed: ${e.message}")
        }
    }
}
