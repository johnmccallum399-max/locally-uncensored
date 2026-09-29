package com.lcdr.assistant.domain.model

import com.lcdr.assistant.data.local.entity.MemoryEntity
import com.lcdr.assistant.data.remote.dto.MemoryDto

data class MemoryEntry(
    val id: String,
    val key: String,
    val value: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

fun MemoryDto.toDomain() = MemoryEntry(
    id = id, key = key, value = value, createdAt = createdAt, updatedAt = updatedAt
)

fun MemoryEntity.toDomain() = MemoryEntry(
    id = id, key = key, value = value, createdAt = createdAt, updatedAt = updatedAt
)

fun MemoryEntry.toEntity() = MemoryEntity(
    id = id, key = key, value = value, createdAt = createdAt, updatedAt = updatedAt
)
