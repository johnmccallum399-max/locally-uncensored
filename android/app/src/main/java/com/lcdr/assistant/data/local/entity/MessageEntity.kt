package com.lcdr.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val toolCallsJson: String? = null,
    val toolResultsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
