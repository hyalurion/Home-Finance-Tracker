package com.chronie.homemoney.data.chat

import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.LlmRequest
import com.chronie.homemoney.domain.model.chat.ProviderFormat
import com.chronie.homemoney.domain.model.chat.StreamEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A model source capable of streaming a chat turn. Implementations are
 * deliberately provider-neutral at the repository level so the UI never
 * needs to know whether tokens came from MNN or a remote endpoint.
 */
interface LlmClient {
    fun stream(
        request: LlmRequest,
        config: AiProviderConfig,
        apiKey: String
    ): Flow<StreamEvent>

    /** Lightweight non-streaming connectivity/credential check for settings UI. */
    suspend fun probe(config: AiProviderConfig, apiKey: String): Result<String>
}

/** Selects the client matching the user's active provider. */
@Singleton
class LlmClientFactory @Inject constructor(
    private val builtIn: BuiltInLlmClient,
    private val openAi: OpenAiLlmClient,
    private val anthropic: AnthropicLlmClient
) {
    fun create(config: AiProviderConfig): LlmClient = when (config.format) {
        ProviderFormat.BUILT_IN -> builtIn
        ProviderFormat.OPENAI -> openAi
        ProviderFormat.ANTHROPIC -> anthropic
    }
}
