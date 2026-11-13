package com.chronie.homemoney.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/** DI for the AI chat feature's dedicated streaming HTTP client. */
@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    /**
     * A separate client is required because SSE responses are long-lived:
     * the app's default 5s read timeout would kill a generation mid-answer.
     * No auth/server-url interceptors here — custom providers are third-party
     * endpoints reached directly, never the household sync server.
     */
    @Provides
    @Singleton
    @Named("AiStreamClient")
    fun provideAiStreamClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
}
