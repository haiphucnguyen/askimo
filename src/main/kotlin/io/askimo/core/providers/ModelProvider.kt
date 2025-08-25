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
    @SerialName("OPEN_AI")
    OPEN_AI,

    /**
     * Represents XAI's models like grok-3, grok3-mini, etc.
     */
    @SerialName("X_AI")
    X_AI,

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
     * Represents an unidentified or unsupported model provider.
     */
    @SerialName("UNKNOWN")
    UNKNOWN,
}
