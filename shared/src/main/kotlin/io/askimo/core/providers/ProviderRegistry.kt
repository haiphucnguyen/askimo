/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import io.askimo.core.providers.ModelProvider.ANTHROPIC
import io.askimo.core.providers.ModelProvider.DOCKER
import io.askimo.core.providers.ModelProvider.GEMINI
import io.askimo.core.providers.ModelProvider.LMSTUDIO
import io.askimo.core.providers.ModelProvider.LOCALAI
import io.askimo.core.providers.ModelProvider.OLLAMA
import io.askimo.core.providers.ModelProvider.OPENAI
import io.askimo.core.providers.ModelProvider.OPENAI_COMPATIBLE
import io.askimo.core.providers.ModelProvider.XAI
import io.askimo.core.providers.anthropic.AnthropicModelFactory
import io.askimo.core.providers.docker.DockerAiModelFactory
import io.askimo.core.providers.gemini.GeminiModelFactory
import io.askimo.core.providers.lmstudio.LmStudioModelFactory
import io.askimo.core.providers.localai.LocalAiModelFactory
import io.askimo.core.providers.ollama.OllamaModelFactory
import io.askimo.core.providers.openai.OpenAiModelFactory
import io.askimo.core.providers.openaicompatible.OpenAiCompatibleModelFactory
import io.askimo.core.providers.xai.XAiModelFactory

/**
 * Central registry for managing chat model factories across different AI providers.
 * Mutable to allow downstream apps (e.g. Askimo Pro) to register additional factories
 * via [register] without modifying this shared module.
 */
object ProviderRegistry {

    private val factories: MutableMap<ModelProvider, ChatModelFactory<*>> = mutableMapOf(
        OPENAI to OpenAiModelFactory(),
        XAI to XAiModelFactory(),
        GEMINI to GeminiModelFactory(),
        OLLAMA to OllamaModelFactory(),
        DOCKER to DockerAiModelFactory(),
        ANTHROPIC to AnthropicModelFactory(),
        LOCALAI to LocalAiModelFactory(),
        LMSTUDIO to LmStudioModelFactory(),
        OPENAI_COMPATIBLE to OpenAiCompatibleModelFactory(),
    )

    /**
     * Registers a custom factory for the given provider.
     * Intended for downstream apps that extend the shared provider set.
     */
    fun register(provider: ModelProvider, factory: ChatModelFactory<*>) {
        factories[provider] = factory
    }

    /** Returns the factory for [provider], or null if none is registered. */
    fun getFactory(provider: ModelProvider): ChatModelFactory<*>? = factories[provider]

    /** Returns the set of providers that currently have a registered factory. */
    fun getSupportedProviders(): Set<ModelProvider> = factories.keys
}
