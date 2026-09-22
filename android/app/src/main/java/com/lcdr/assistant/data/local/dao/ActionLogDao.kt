package com.lcdr.assistant.data.local.dao

import androidx.room.*
import com.lcdr.assistant.data.local.entity.ActionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<ActionLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ActionLogEntity): Long

    @Query("DELETE FROM action_log WHERE id NOT IN (SELECT id FROM action_log ORDER BY timestamp DESC LIMIT 500)")
    suspend fun pruneOld()

    @Query("DELETE FROM action_log")
    suspend fun clearAll()
}
