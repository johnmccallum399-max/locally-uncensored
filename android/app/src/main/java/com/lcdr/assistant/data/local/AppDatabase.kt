package com.lcdr.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lcdr.assistant.data.local.dao.ActionLogDao
import com.lcdr.assistant.data.local.dao.MemoryDao
import com.lcdr.assistant.data.local.dao.MessageDao
import com.lcdr.assistant.data.local.entity.ActionLogEntity
import com.lcdr.assistant.data.local.entity.MemoryEntity
import com.lcdr.assistant.data.local.entity.MessageEntity

@Database(
    entities = [MessageEntity::class, MemoryEntity::class, ActionLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun actionLogDao(): ActionLogDao
}
