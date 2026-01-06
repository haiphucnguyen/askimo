/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.rag.content.retriever.ContentRetriever
import io.askimo.core.context.ExecutionMode

/**
 * Factory interface for creating chat model instances for a specific AI provider.
 * Each implementation corresponds to a different model provider (e.g., OpenAI, Ollama).
 *
 * @param T The specific ProviderSettings type for this factory
 */
interface ChatModelFactory<T : ProviderSettings> {
    /**
     * Returns a list of available model names for this provider.
     *
     * @param settings Provider-specific settings that may be needed to retrieve available models
     * @return List of model identifiers that can be used with this provider
     */
    fun availableModels(settings: T): List<String>

    /**
     * Returns the default settings for this provider.
     *
     * @return Default provider-specific settings that can be used to initialize models
     */
    fun defaultSettings(): T

    /**
     * Creates a chat service instance with the specified parameters.
     *
     * @param model The identifier of the model to create
     * @param settings Provider-specific settings to configure the model
     * @param presets Global presets controlling style and verbosity, independent of provider
     * @param retriever Optional content retriever for RAG (Retrieval-Augmented Generation).
     * If provided, the factory will create a RetrievalAugmentor internally with appropriate
     * configuration. Pass null to disable retrieval augmentation.
     * @param executionMode The execution mode indicating how the user is running the application.
     * Tools are disabled for DESKTOP mode.
     * @param chatMemory Optional chat memory for conversation context. If provided, memory will be
     * integrated into the LangChain4j AI service.
     * @return A configured ChatModel instance
     */
    fun create(
        sessionId: String? = null,
        model: String,
        settings: T,
        presets: Presets,
        retriever: ContentRetriever? = null,
        executionMode: ExecutionMode,
        chatMemory: ChatMemory? = null,
    ): ChatClient

    /**
     * Returns helpful guidance text to display when no models are available for this provider.
     * Each factory can override this to provide provider-specific instructions.
     *
     * @return Help text explaining how to set up or configure the provider
     */
    fun getNoModelsHelpText(): String = "Please check your provider configuration."

    /**
     * Creates an intent classification client for RAG decisions.
     * Returns a cheap, fast model suitable for YES/NO classification.
     *
     * For cloud providers: returns a cheap model (e.g., GPT-3.5-turbo for OpenAI)
     * For local providers: returns the current model (no extra overhead)
     *
     * @param settings Provider-specific settings
     * @return ChatClient configured with a classification model
     */
    fun createUtilityClient(
        settings: T,
    ): ChatClient
}
