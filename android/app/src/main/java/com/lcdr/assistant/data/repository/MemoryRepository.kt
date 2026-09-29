package com.lcdr.assistant.data.repository

import com.lcdr.assistant.data.local.dao.MemoryDao
import com.lcdr.assistant.data.prefs.SecurePrefs
import com.lcdr.assistant.data.remote.ApiService
import com.lcdr.assistant.data.remote.dto.RememberRequest
import com.lcdr.assistant.domain.model.MemoryEntry
import com.lcdr.assistant.domain.model.toDomain
import com.lcdr.assistant.domain.model.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val apiService: ApiService,
    private val memoryDao: MemoryDao,
    private val securePrefs: SecurePrefs
) {

    fun observeMemories(): Flow<List<MemoryEntry>> =
        memoryDao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun refreshFromRemote(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = securePrefs.getToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val response = apiService.getMemories("Bearer $token")
            if (response.isSuccessful) {
                val memories = response.body()?.memories?.map { dto ->
                    com.lcdr.assistant.data.local.entity.MemoryEntity(
                        id = dto.id,
                        key = dto.key,
                        value = dto.value,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt
                    )
                } ?: emptyList()
                memoryDao.clearAll()
                memoryDao.insertAll(memories)
                Result.success(Unit)
            } else {
                Result.failure(Exception("${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun remember(key: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = securePrefs.getToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val response = apiService.remember("Bearer $token", RememberRequest(key, value))
            if (response.isSuccessful) {
                response.body()?.let { dto ->
                    memoryDao.insert(
                        com.lcdr.assistant.data.local.entity.MemoryEntity(
                            id = dto.id, key = dto.key, value = dto.value,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                Result.success(Unit)
            } else Result.failure(Exception("${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = securePrefs.getToken() ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            apiService.deleteMemory("Bearer $token", id)
            memoryDao.delete(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMemoriesAsContext(): String = withContext(Dispatchers.IO) {
        val memories = memoryDao.getAllSync()
        if (memories.isEmpty()) return@withContext ""
        memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
    }
}
