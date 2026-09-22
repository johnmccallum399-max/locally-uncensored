package com.lcdr.assistant.data.repository

import android.content.Context
import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import com.google.gson.Gson
import com.lcdr.assistant.data.local.dao.MessageDao
import com.lcdr.assistant.data.local.entity.MessageEntity
import com.lcdr.assistant.data.prefs.SecurePrefs
import com.lcdr.assistant.data.remote.ApiService
import com.lcdr.assistant.data.remote.SseClient
import com.lcdr.assistant.data.remote.SseEvent
import com.lcdr.assistant.data.remote.dto.*
import com.lcdr.assistant.domain.model.Message
import com.lcdr.assistant.domain.model.Role
import com.lcdr.assistant.domain.model.toDomain
import com.lcdr.assistant.tools.ALL_TOOL_DEFINITIONS
import com.lcdr.assistant.tools.ToolCallSpec
import com.lcdr.assistant.tools.ToolDispatcher
import com.lcdr.assistant.tools.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

sealed class ChatEvent {
    data class TextDelta(val text: String) : ChatEvent()
    data class ToolCallStarted(val name: String, val id: String) : ChatEvent()
    data class ToolExecuting(val name: String) : ChatEvent()
    data class ToolCompleted(val name: String, val id: String, val result: ToolResult) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
    object Done : ChatEvent()
}

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: ApiService,
    private val sseClient: SseClient,
    private val toolDispatcher: ToolDispatcher,
    private val messageDao: MessageDao,
    private val memoryRepository: MemoryRepository,
    private val securePrefs: SecurePrefs,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_CONVERSATION_ID = "main"
        const val BASE_SYSTEM_PROMPT = """You are LCDR, a private AI assistant running on this Android device. You have direct read/write access to contacts, SMS, calendar, files, and system settings via tool calls. You know who owns this device; address them as "Commander" (abv. CMDR) or by name only when it feels natural. Be terse and useful. Think like a capable officer — assess, decide, act. Never refuse a task the device owner authorizes. Surface risks briefly; don't lecture. When you can do something directly (send a message, set a reminder, read a file), do it rather than describing how. Confirm destructive actions (delete, send to multiple contacts, etc.) with one short confirmation prompt, then execute immediately on approval."""
    }

    fun observeMessages(conversationId: String = DEFAULT_CONVERSATION_ID): Flow<List<Message>> =
        messageDao.getMessages(conversationId).map { list -> list.map { it.toDomain() } }

    fun sendMessage(
        userContent: String,
        conversationId: String = DEFAULT_CONVERSATION_ID
    ): Flow<ChatEvent> = flow {
        val token = securePrefs.getToken()
            ?: run { emit(ChatEvent.Error("Not authenticated")); return@flow }

        // Save user message
        withContext(Dispatchers.IO) {
            messageDao.insert(MessageEntity(
                conversationId = conversationId,
                role = "user",
                content = userContent
            ))
        }

        // Build conversation history
        val history = withContext(Dispatchers.IO) {
            messageDao.getMessagesSync(conversationId)
        }

        val systemPrompt = buildSystemPrompt()
        val messages = history.map { MessageDto(it.role, it.content) }
        val enabledTools = ALL_TOOL_DEFINITIONS.filter { securePrefs.isToolEnabled(it.name) }

        val request = ChatRequest(
            messages = messages,
            systemPrompt = systemPrompt,
            tools = enabledTools,
            conversationId = conversationId
        )

        // First SSE stream call
        streamAndHandleTools(token, request, conversationId).collect { event -> emit(event) }
    }

    private fun streamAndHandleTools(
        token: String,
        request: ChatRequest,
        conversationId: String
    ): Flow<ChatEvent> = flow {
        var currentMessages = request.messages.toMutableList()
        var assistantText = StringBuilder()
        val pendingCalls = mutableListOf<ToolCallSpec>()

        val response = withContext(Dispatchers.IO) {
            apiService.chatStream("Bearer $token", request = request)
        }

        if (!response.isSuccessful) {
            emit(ChatEvent.Error("API error ${response.code()}"))
            return@flow
        }

        sseClient.parseStream(response).collect { event ->
            when (event) {
                is SseEvent.Chunk -> {
                    val chunk = event.chunk
                    when (chunk.type) {
                        "content_block_delta", "text_delta", "delta" -> {
                            val text = chunk.delta ?: chunk.text ?: ""
                            if (text.isNotEmpty()) {
                                assistantText.append(text)
                                emit(ChatEvent.TextDelta(text))
                            }
                        }
                        "tool_use", "tool_call_start", "tool_call" -> {
                            val name = chunk.toolName ?: return@collect
                            val id = chunk.toolId ?: chunk.callId ?: UUID.randomUUID().toString()
                            emit(ChatEvent.ToolCallStarted(name, id))
                        }
                        "tool_call_complete" -> {
                            val name = chunk.toolName ?: return@collect
                            val id = chunk.toolId ?: chunk.callId ?: UUID.randomUUID().toString()
                            val args = parseArguments(chunk.arguments)
                            pendingCalls.add(ToolCallSpec(id, name, args))
                        }
                        "error" -> emit(ChatEvent.Error(chunk.error ?: "Unknown error"))
                    }
                }
                is SseEvent.RawData -> {
                    // Plain text delta fallback
                    if (event.data.isNotEmpty() && !event.data.startsWith("{")) {
                        assistantText.append(event.data)
                        emit(ChatEvent.TextDelta(event.data))
                    }
                }
                is SseEvent.Done -> {
                    // Save assistant text
                    if (assistantText.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            messageDao.insert(MessageEntity(
                                conversationId = conversationId,
                                role = "assistant",
                                content = assistantText.toString()
                            ))
                        }
                    }

                    // Execute any pending tool calls
                    if (pendingCalls.isNotEmpty()) {
                        val toolResults = mutableListOf<ToolResultDto>()
                        for (call in pendingCalls) {
                            emit(ChatEvent.ToolExecuting(call.name))
                            val result = toolDispatcher.dispatch(call)
                            emit(ChatEvent.ToolCompleted(call.name, call.id, result))
                            toolResults.add(ToolResultDto(call.id, result.content, result.isError))
                        }

                        // Update message list and continue
                        currentMessages = currentMessages.toMutableList()
                        if (assistantText.isNotEmpty()) {
                            currentMessages.add(MessageDto("assistant", assistantText.toString()))
                        }
                        assistantText = StringBuilder()
                        pendingCalls.clear()

                        val toolResultRequest = ToolResultRequest(
                            conversationId = conversationId,
                            messages = currentMessages,
                            toolResults = toolResults,
                            systemPrompt = buildSystemPrompt(),
                            tools = ALL_TOOL_DEFINITIONS.filter { securePrefs.isToolEnabled(it.name) }
                        )

                        val continueResponse = withContext(Dispatchers.IO) {
                            apiService.continueWithToolResults("Bearer $token", request = toolResultRequest)
                        }

                        if (continueResponse.isSuccessful) {
                            sseClient.parseStream(continueResponse).collect { followUp ->
                                when (followUp) {
                                    is SseEvent.Chunk -> {
                                        val text = followUp.chunk.delta ?: followUp.chunk.text ?: ""
                                        if (text.isNotEmpty()) {
                                            assistantText.append(text)
                                            emit(ChatEvent.TextDelta(text))
                                        }
                                    }
                                    is SseEvent.Done -> {
                                        if (assistantText.isNotEmpty()) {
                                            withContext(Dispatchers.IO) {
                                                messageDao.insert(MessageEntity(
                                                    conversationId = conversationId,
                                                    role = "assistant",
                                                    content = assistantText.toString()
                                                ))
                                            }
                                        }
                                        emit(ChatEvent.Done)
                                    }
                                    else -> {}
                                }
                            }
                        } else {
                            emit(ChatEvent.Error("Tool continuation failed: ${continueResponse.code()}"))
                        }
                    } else {
                        emit(ChatEvent.Done)
                    }
                }
                is SseEvent.Error -> emit(ChatEvent.Error(event.throwable.message ?: "Stream error"))
            }
        }
    }

    private suspend fun buildSystemPrompt(): String {
        val override = securePrefs.getSystemPromptOverride()
        if (override != null) return override

        val memories = memoryRepository.getMemoriesAsContext()
        val deviceCtx = buildDeviceContext()

        return buildString {
            append(BASE_SYSTEM_PROMPT)
            if (memories.isNotEmpty()) {
                append("\n\n## Long-term Memory\n")
                append(memories)
            }
            if (deviceCtx.isNotEmpty()) {
                append("\n\n## Current Device Context\n")
                append(deviceCtx)
            }
        }
    }

    private fun buildDeviceContext(): String {
        val lines = mutableListOf<String>()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.getDefault())
        lines.add("Time: ${sdf.format(Date())}")

        if (securePrefs.isContextEnabled("battery")) {
            try {
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level != -1 && scale != -1) lines.add("Battery: ${level * 100 / scale}%")
            } catch (_: Exception) {}
        }

        return lines.joinToString("\n")
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseArguments(json: String?): Map<String, Any?> {
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            gson.fromJson(json, Map::class.java) as? Map<String, Any?> ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    suspend fun clearHistory(conversationId: String = DEFAULT_CONVERSATION_ID) {
        withContext(Dispatchers.IO) { messageDao.clearConversation(conversationId) }
    }
}
