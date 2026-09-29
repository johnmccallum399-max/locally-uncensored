package com.lcdr.assistant.di

import android.content.Context
import androidx.room.Room
import com.lcdr.assistant.data.local.AppDatabase
import com.lcdr.assistant.data.local.dao.ActionLogDao
import com.lcdr.assistant.data.local.dao.MemoryDao
import com.lcdr.assistant.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lcdr.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun provideMemoryDao(db: AppDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideActionLogDao(db: AppDatabase): ActionLogDao = db.actionLogDao()
}
