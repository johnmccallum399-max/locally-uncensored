package com.lcdr.assistant.di

import android.content.Context
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule
// Remaining singleton bindings live in NetworkModule and DatabaseModule.
// Add @Provides here for any cross-cutting singletons not covered by those.
