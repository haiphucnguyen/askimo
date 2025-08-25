/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ModelProvider.OPEN_AI
import io.askimo.core.providers.ModelProvider.X_AI
import io.askimo.core.providers.gemini.GeminiModelFactory
import io.askimo.core.providers.ollama.OllamaModelFactory
import io.askimo.core.providers.openai.OpenAiModelFactory
import io.askimo.core.providers.xai.XAiModelFactory

/**
 * Central registry for managing chat model factories across different AI providers.
 *
 * This singleton object maintains a registry of model factories for different providers
 * (like OpenAI, Ollama) and provides methods to:
 * - Register new model factories
 * - Retrieve factories for specific providers
 * - Get available models for a provider
 * - Create chat model instances with appropriate configuration
 *
 * The registry is initialized with default factories for known providers.
 */
object ProviderRegistry {
    /**
     * Internal registry mapping model providers to their respective factory implementations.
     */
    private val factories =
        mapOf(
            OPEN_AI to OpenAiModelFactory(),
            X_AI to XAiModelFactory(),
            GEMINI to GeminiModelFactory(),
            OLLAMA to OllamaModelFactory(),
        )

    /**
     * Retrieves the chat model factory for the specified provider.
     *
     * @param provider The model provider to get the factory for
     * @return The chat model factory for the provider, or null if no factory is registered
     */
    fun getFactory(provider: ModelProvider): ChatModelFactory? = factories[provider]

    /**
     * Returns the set of model providers for which a factory is currently registered.
     *
     * This reflects the providers available at runtime, including those registered during
     * initialization and any added later via [register]. The returned set is a live view
     * backed by the internal registry; it will reflect subsequent registrations or removals.
     * If you need a stable snapshot, make a defensive copy using `toSet()`.
     *
     * @return set of [ModelProvider]s that this registry can build models for
     */
    fun getSupportedProviders(): Set<ModelProvider> = factories.keys
}
