/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents supported AI model providers in the chat application.
 *
 * This enum defines the different AI service providers that can be used
 * as sources for chat models in the application.
 */
@Serializable
enum class ModelProvider {
    /**
     * Represents OpenAI's models like GPT-3.5, GPT-4, etc.
     */
    @SerialName("OPENAI")
    OPENAI,

    /**
     * Represents XAI's models like grok-3, grok3-mini, etc.
     */
    @SerialName("XAI")
    XAI,

    /**
     * Represents Gemini's models like gemini-2.5-flash, gemini-2.5-pro, etc.
     */
    @SerialName("GEMINI")
    GEMINI,

    /**
     * Represents Ollama's locally-hosted models.
     */
    @SerialName("OLLAMA")
    OLLAMA,

    /**
     * Represents Docker AI's locally-hosted models.
     */
    @SerialName("DOCKER")
    DOCKER,

    /**
     * Represents Anthropic's models
     */
    @SerialName("ANTHROPIC")
    ANTHROPIC,

    /**
     * Represents LocalAI's locally-hosted models.
     */
    @SerialName("LOCALAI")
    LOCALAI,

    /**
     * Represents LMStudio's locally-hosted models (OpenAI-compatible API).
     */
    @SerialName("LMSTUDIO")
    LMSTUDIO,

    /**
     * Represents an unidentified or unsupported model provider.
     */
    @SerialName("UNKNOWN")
    UNKNOWN,
    ;

    /**
     * Returns the lowercase name of this provider for use in cache keys and logging.
     */
    fun providerKey(): String = when (this) {
        OPENAI -> "openai"
        XAI -> "xai"
        GEMINI -> "gemini"
        OLLAMA -> "ollama"
        DOCKER -> "docker-ai"
        ANTHROPIC -> "anthropic"
        LOCALAI -> "localai"
        LMSTUDIO -> "lmstudio"
        UNKNOWN -> "unknown"
    }
}
