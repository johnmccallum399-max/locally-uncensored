package com.lcdr.assistant.data.local.dao

import androidx.room.*
import com.lcdr.assistant.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    suspend fun getAllSync(): List<MemoryEntity>

    @Query("SELECT * FROM memory_entries WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<MemoryEntity>)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memory_entries")
    suspend fun clearAll()
}
