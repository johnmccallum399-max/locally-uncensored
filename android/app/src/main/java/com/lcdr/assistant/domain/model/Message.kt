package com.lcdr.assistant.domain.model

import com.lcdr.assistant.data.local.entity.MessageEntity

data class Message(
    val id: Long = 0,
    val conversationId: String,
    val role: Role,
    val content: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolResults: List<ToolResultItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class Role { USER, ASSISTANT, SYSTEM, TOOL }

data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: Map<String, Any> = emptyMap()
)

data class ToolResultItem(
    val toolId: String,
    val content: String,
    val isError: Boolean = false
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name.lowercase(),
    content = content,
    timestamp = timestamp
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    conversationId = conversationId,
    role = Role.valueOf(role.uppercase()),
    content = content,
    timestamp = timestamp
)
