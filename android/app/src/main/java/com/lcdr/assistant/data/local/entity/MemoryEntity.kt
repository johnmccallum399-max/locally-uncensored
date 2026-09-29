package com.lcdr.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_entries")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
