package com.lcdr.assistant.tools.impl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.lcdr.assistant.tools.ToolCallSpec
import com.lcdr.assistant.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarTools @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun listEvents(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startStr = spec.arguments["start_date"] as? String ?: "today"
            val endStr = spec.arguments["end_date"] as? String ?: startStr

            val start = if (startStr == "today") startOfDay(System.currentTimeMillis())
            else sdf.parse(startStr)?.time ?: startOfDay(System.currentTimeMillis())

            val end = if (endStr == "today") endOfDay(System.currentTimeMillis())
            else sdf.parse(endStr)?.time?.let { it + 86_400_000L - 1 } ?: endOfDay(System.currentTimeMillis())

            val uri = CalendarContract.Events.CONTENT_URI
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION
                ),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(start.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )

            val dt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val result = StringBuilder()
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val title = it.getString(1) ?: "Untitled"
                    val dtStart = it.getLong(2)
                    val dtEnd = it.getLong(3)
                    val location = it.getString(4)
                    result.append("[$id] ${dt.format(Date(dtStart))}–${dt.format(Date(dtEnd))} $title")
                    if (!location.isNullOrBlank()) result.append(" @ $location")
                    result.appendLine()
                }
            }

            if (result.isEmpty()) ToolResult.Success("No events in range.")
            else ToolResult.Success(result.toString().trim())
        } catch (e: Exception) {
            ToolResult.Failure("Calendar read failed: ${e.message}")
        }
    }

    suspend fun createEvent(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        val title = spec.arguments["title"] as? String
            ?: return@withContext ToolResult.Failure("'title' required")
        val start = spec.arguments["start"] as? String
            ?: return@withContext ToolResult.Failure("'start' required")
        val end = spec.arguments["end"] as? String
            ?: return@withContext ToolResult.Failure("'end' required")
        val location = spec.arguments["location"] as? String
        val notes = spec.arguments["notes"] as? String

        try {
            val calendarId = getDefaultCalendarId() ?: return@withContext ToolResult.Failure("No calendar available")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val startMs = sdf.parse(start)?.time ?: return@withContext ToolResult.Failure("Invalid start date")
            val endMs = sdf.parse(end)?.time ?: return@withContext ToolResult.Failure("Invalid end date")

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMs)
                put(CalendarContract.Events.DTEND, endMs)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (location != null) put(CalendarContract.Events.EVENT_LOCATION, location)
                if (notes != null) put(CalendarContract.Events.DESCRIPTION, notes)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val id = uri?.lastPathSegment
            ToolResult.Success("Event created: '$title' (id=$id)")
        } catch (e: Exception) {
            ToolResult.Failure("Create event failed: ${e.message}")
        }
    }

    suspend fun deleteEvent(spec: ToolCallSpec): ToolResult = withContext(Dispatchers.IO) {
        val id = spec.arguments["id"] as? String
            ?: return@withContext ToolResult.Failure("'id' required")
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id.toLong())
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) ToolResult.Success("Event $id deleted.")
            else ToolResult.Failure("Event $id not found.")
        } catch (e: Exception) {
            ToolResult.Failure("Delete event failed: ${e.message}")
        }
    }

    private fun getDefaultCalendarId(): Long? {
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.IS_PRIMARY} = 1",
            null, null
        )
        return cursor?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    private fun startOfDay(ms: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return cal.timeInMillis
    }

    private fun endOfDay(ms: Long): Long = startOfDay(ms) + 86_400_000L - 1
}
