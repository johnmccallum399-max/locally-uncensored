package com.lcdr.assistant.tools

import com.lcdr.assistant.data.local.dao.ActionLogDao
import com.lcdr.assistant.data.local.entity.ActionLogEntity
import com.lcdr.assistant.data.prefs.SecurePrefs
import com.lcdr.assistant.data.remote.ApiService
import com.lcdr.assistant.data.remote.dto.RememberRequest
import com.lcdr.assistant.domain.model.MemoryEntry
import com.lcdr.assistant.tools.impl.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolDispatcher @Inject constructor(
    private val smsTools: SmsTools,
    private val contactTools: ContactTools,
    private val calendarTools: CalendarTools,
    private val fileTools: FileTools,
    private val systemTools: SystemTools,
    private val actionLogDao: ActionLogDao,
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs,
    private val gson: Gson
) {

    suspend fun dispatch(spec: ToolCallSpec): ToolResult {
        val isEnabled = securePrefs.isToolEnabled(spec.name)
        if (!isEnabled) return ToolResult.Failure("Tool '${spec.name}' is disabled in settings.")

        val result = when (spec.name) {
            "read_sms" -> smsTools.readSms(spec)
            "send_sms" -> smsTools.sendSms(spec)
            "read_contacts" -> contactTools.readContacts(spec)
            "write_contact" -> contactTools.writeContact(spec)
            "list_events" -> calendarTools.listEvents(spec)
            "create_event" -> calendarTools.createEvent(spec)
            "delete_event" -> calendarTools.deleteEvent(spec)
            "list_files" -> fileTools.listFiles(spec)
            "read_file" -> fileTools.readFile(spec)
            "write_file" -> fileTools.writeFile(spec)
            "open_file" -> fileTools.openFile(spec)
            "get_battery" -> systemTools.getBattery(spec)
            "get_location" -> systemTools.getLocation(spec)
            "take_photo" -> systemTools.takePhoto(spec)
            "get_clipboard" -> systemTools.getClipboard(spec)
            "set_clipboard" -> systemTools.setClipboard(spec)
            "set_alarm" -> systemTools.setAlarm(spec)
            "set_timer" -> systemTools.setTimer(spec)
            "send_notification" -> systemTools.sendNotification(spec)
            "get_running_apps" -> systemTools.getRunningApps(spec)
            "remember" -> rememberTool(spec)
            "recall" -> recallTool(spec)
            else -> ToolResult.Failure("Unknown tool: ${spec.name}")
        }

        logAction(spec, result)
        return result
    }

    private suspend fun rememberTool(spec: ToolCallSpec): ToolResult {
        val key = spec.arguments["key"] as? String ?: return ToolResult.Failure("'key' required")
        val value = spec.arguments["value"] as? String ?: return ToolResult.Failure("'value' required")
        val token = securePrefs.getToken() ?: return ToolResult.Failure("Not authenticated")
        return try {
            apiService.remember("Bearer $token", RememberRequest(key, value))
            ToolResult.Success("Remembered: $key = $value")
        } catch (e: Exception) {
            ToolResult.Failure("Remember failed: ${e.message}")
        }
    }

    private suspend fun recallTool(spec: ToolCallSpec): ToolResult {
        val key = spec.arguments["key"] as? String
        val token = securePrefs.getToken() ?: return ToolResult.Failure("Not authenticated")
        return try {
            val response = apiService.getMemories("Bearer $token")
            if (!response.isSuccessful) return ToolResult.Failure("Recall failed: ${response.code()}")
            val memories = response.body()?.memories ?: emptyList()
            val filtered = if (key != null) memories.filter { it.key == key } else memories
            if (filtered.isEmpty()) ToolResult.Success("No memories found.")
            else ToolResult.Success(filtered.joinToString("\n") { "${it.key}: ${it.value}" })
        } catch (e: Exception) {
            ToolResult.Failure("Recall failed: ${e.message}")
        }
    }

    private suspend fun logAction(spec: ToolCallSpec, result: ToolResult) {
        withContext(Dispatchers.IO) {
            try {
                actionLogDao.insert(ActionLogEntity(
                    toolName = spec.name,
                    arguments = gson.toJson(spec.arguments),
                    result = result.content,
                    isError = result.isError
                ))
                actionLogDao.pruneOld()
            } catch (_: Exception) {}
        }
    }
}
