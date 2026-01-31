package com.voice2.app.di

import com.voice2.app.data.api.Voice2ApiService
import com.voice2.app.data.repository.Voice2Repository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideVoice2Repository(apiService: Voice2ApiService): Voice2Repository {
        return Voice2Repository(apiService)
    }
}
