/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory

/**
 * Factory interface for creating chat model instances for a specific AI provider.
 * Each implementation corresponds to a different model provider (e.g., OpenAI, Ollama).
 */
interface ChatModelFactory {
    /**
     * Returns a list of available model names for this provider.
     *
     * @param settings Provider-specific settings that may be needed to retrieve available models
     * @return List of model identifiers that can be used with this provider
     */
    fun availableModels(settings: ProviderSettings): List<String>

    /**
     * Returns the default settings for this provider.
     *
     * @return Default provider-specific settings that can be used to initialize models
     */
    fun defaultSettings(): ProviderSettings

    /**
     * Creates a chat service instance with the specified parameters.
     *
     * @param model The identifier of the model to create
     * @param settings Provider-specific settings to configure the model
     * @param memory Chat memory to use for conversation history
     * @return A configured ChatModel instance
     */
    fun create(
        model: String,
        settings: ProviderSettings,
        memory: ChatMemory,
    ): ChatService

    /**
     * Creates a chat memory instance for storing conversation history.
     *
     * @param model The identifier of the model to create memory for
     * @param settings Provider-specific settings that may influence memory configuration
     * @return A ChatMemory instance configured with a default window size of 200 messages
     */
    fun createMemory(
        model: String,
        settings: ProviderSettings,
    ): ChatMemory = MessageWindowChatMemory.withMaxMessages(200)
}
