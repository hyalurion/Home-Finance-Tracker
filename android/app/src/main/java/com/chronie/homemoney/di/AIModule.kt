package com.chronie.homemoney.di

import android.content.Context
import com.chronie.homemoney.data.ocr.DocumentScanProcessor
import com.chronie.homemoney.data.repository.AIRecordRepositoryImpl
import com.chronie.homemoney.data.repository.ChatRepositoryImpl
import com.chronie.homemoney.data.vlm.MnnVlmEngine
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import com.chronie.homemoney.domain.repository.AIRecordRepository
import com.chronie.homemoney.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AI Module Dependency Injection
 *
 * Two AI paths coexist: the original on-device recognition stack (OpenCV +
 * MNN Qwen3-VL, still used for writes) and the new chat repository, which can
 * drive the built-in engine or user-supplied OpenAI/Anthropic-compatible APIs.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {

    /**
     * Bind AIRecordRepository
     */
    @Binds
    @Singleton
    abstract fun bindAIRecordRepository(
        impl: AIRecordRepositoryImpl
    ): AIRecordRepository

    /** Conversations, streaming generation and tool execution. */
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository

    companion object {
        /**
         * Provides the OpenCV document scan processor (stateless besides
         * the OpenCV loader flag, safe as a singleton).
         */
        @Provides
        @Singleton
        fun provideDocumentScanProcessor(
            @ApplicationContext context: Context
        ): DocumentScanProcessor {
            return DocumentScanProcessor(context)
        }

        /**
         * Provides the on-device multimodal LLM engine wrapper.
         */
        @Provides
        @Singleton
        fun provideMnnVlmEngine(): MnnVlmEngine {
            return MnnVlmEngine()
        }

        /**
         * Provides the model download / lifecycle manager.
         */
        @Provides
        @Singleton
        fun provideOnDeviceModelManager(
            @ApplicationContext context: Context
        ): OnDeviceModelManager {
            return OnDeviceModelManager(context)
        }
    }
}
