/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.providers

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.rag.RetrievalAugmentor
import io.askimo.core.context.ExecutionMode
import io.askimo.core.memory.ConversationSummary

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
     * @param retrievalAugmentor Optional retrieval-augmented generation (RAG) component.
     * If provided, the created chat service will use it to fetch and inject relevant context
     * into prompts during generation. Pass null to disable retrieval augmentation.
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
        retrievalAugmentor: RetrievalAugmentor? = null,
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
     * Creates a summarizer function for AI-powered conversation summarization.
     * Returns null if AI summarization is not supported or not enabled for this provider.
     *
     * @param settings Provider-specific settings
     * @return A function that takes conversation text and returns a ConversationSummary, or null
     */
    fun createSummarizer(settings: T): ((String) -> ConversationSummary)? = null
}
