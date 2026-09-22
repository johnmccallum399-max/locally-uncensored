package com.lcdr.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val messages: List<MessageDto>,
    @SerializedName("system_prompt") val systemPrompt: String,
    val tools: List<ToolDefinition> = emptyList(),
    val stream: Boolean = true,
    @SerializedName("conversation_id") val conversationId: String? = null
)

data class MessageDto(
    val role: String,
    val content: String
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>
)

data class SseChunk(
    val type: String,
    val delta: String? = null,
    val text: String? = null,
    val error: String? = null,
    @SerializedName("tool_name") val toolName: String? = null,
    @SerializedName("tool_id") val toolId: String? = null,
    val arguments: String? = null,
    @SerializedName("call_id") val callId: String? = null
)

data class ToolResultRequest(
    @SerializedName("conversation_id") val conversationId: String,
    val messages: List<MessageDto>,
    @SerializedName("tool_results") val toolResults: List<ToolResultDto>,
    @SerializedName("system_prompt") val systemPrompt: String,
    val tools: List<ToolDefinition> = emptyList(),
    val stream: Boolean = true
)

data class ToolResultDto(
    @SerializedName("tool_id") val toolId: String,
    val content: String,
    @SerializedName("is_error") val isError: Boolean = false
)
