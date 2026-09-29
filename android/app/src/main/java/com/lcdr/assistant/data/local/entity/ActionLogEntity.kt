package com.lcdr.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolName: String,
    val arguments: String,
    val result: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
