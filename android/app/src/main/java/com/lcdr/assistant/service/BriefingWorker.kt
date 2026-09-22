package com.lcdr.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.lcdr.assistant.MainActivity
import com.lcdr.assistant.data.prefs.SecurePrefs
import com.lcdr.assistant.data.repository.ChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import java.util.concurrent.TimeUnit

@HiltWorker
class BriefingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatRepository: ChatRepository,
    private val securePrefs: SecurePrefs
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "lcdr_daily_briefing"
        const val CHANNEL_ID = "lcdr_briefing"

        fun schedule(context: Context, hourOfDay: Int, minute: Int) {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
            }
            if (target.before(now)) target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val delayMs = target.timeInMillis - now.timeInMillis

            val request = OneTimeWorkRequestBuilder<BriefingWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        if (securePrefs.getToken() == null) return Result.success()

        val briefingText = StringBuilder()
        val events = mutableListOf<com.lcdr.assistant.data.repository.ChatEvent>()

        chatRepository.sendMessage(
            "Give me a morning briefing: today's calendar, any pending tasks in memory, and a one-line status summary. Keep it under 200 words.",
            conversationId = "briefing"
        ).collect { event ->
            if (event is com.lcdr.assistant.data.repository.ChatEvent.TextDelta) {
                briefingText.append(event.text)
            }
        }

        if (briefingText.isNotEmpty()) {
            postNotification(briefingText.toString())
        }

        // Reschedule for next day at same time
        val briefingTime = securePrefs.getBriefingTime()
        val parts = briefingTime.split(":")
        if (parts.size == 2) {
            schedule(applicationContext, parts[0].toIntOrNull() ?: 7, parts[1].toIntOrNull() ?: 0)
        }

        return Result.success()
    }

    private fun postNotification(briefing: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "LCDR Daily Briefing", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        val tapIntent = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                putExtra("briefing", briefing)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("LCDR Morning Briefing")
            .setContentText(briefing.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(briefing))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(2001, notification)
    }
}
