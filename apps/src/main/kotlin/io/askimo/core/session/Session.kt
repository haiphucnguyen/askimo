/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.session

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import io.askimo.core.project.PgVectorContentRetriever
import io.askimo.core.project.PgVectorIndexer
import io.askimo.core.project.ProjectEntry
import io.askimo.core.project.buildRetrievalAugmentor
import io.askimo.core.providers.ChatModelFactory
import io.askimo.core.providers.ChatService
import io.askimo.core.providers.ModelProvider
import io.askimo.core.providers.NoopProviderSettings
import io.askimo.core.providers.ProviderRegistry
import io.askimo.core.providers.ProviderSettings
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Controls what happens to the *chat memory* when the active [ChatService] is re-created
 * (e.g., after `:setparam`, switching provider/model, or any programmatic rebuild).
 *
 * The “combo” refers to the pair **(provider, modelName)** used as the memory bucket key.
 *
 * - [KEEP_PER_PROVIDER_MODEL] – Reuse the existing memory bucket for the current combo.
 *   If the provider or model changes, a different bucket is selected automatically.
 *   Choose this to preserve conversation context across minor setting changes.
 *
 * - [RESET_FOR_THIS_COMBO] – Drop and recreate the memory bucket for the current combo
 *   before rebuilding. Choose this when you want a clean slate (benchmarks, prompt iteration,
 *   avoiding context carryover).
 */
enum class MemoryPolicy {
    /**
     * Reuse the existing memory for the current **(provider, modelName)**.
     *
     * If the provider/model changes, the session naturally switches to a different memory bucket
     * keyed by that new combo. Best for normal chat UX where continuity is expected.
     */
    KEEP_PER_PROVIDER_MODEL,

    /**
     * Clear and recreate the memory for the current **(provider, modelName)** before rebuilding.
     *
     * Useful when prior context could bias results or when you want reproducible, clean runs
     * after each parameter change (e.g., style/verbosity tweaks, API key changes, etc.).
     */
    RESET_FOR_THIS_COMBO,
}

data class Scope(
    val projectName: String,
    val projectDir: Path,
)

/**
 * Manages a chat session with language models, handling model creation, provider settings, and conversation memory.
 *
 * The Session class serves as the central coordinator for interactions between the CLI and language model providers.
 * It maintains:
 * - The active chat model instance
 * - Provider-specific settings
 * - Conversation memory for each provider/model combination
 * - Session parameters that control behavior
 *
 * Session instances are responsible for creating and configuring chat models based on the current
 * session parameters, managing conversation history through memory buckets, and providing access
 * to the active model for sending prompts and receiving responses.
 *
 * @property params The parameters that configure this session, including the current provider, model name,
 *                  and provider-specific settings
 */
class Session(
    val params: SessionParams,
) {
    private val memoryMap = mutableMapOf<String, ChatMemory>()

    var lastResponse: String? = null

    /**
     * The active chat model for this session.
     * This property is initialized lazily and can only be set through setChatModel().
     */
    lateinit var chatService: ChatService
        private set

    /**
     * Sets the chat model for this session.
     *
     * @param chatService The chat model to use for this session
     */
    fun setChatService(chatService: ChatService) {
        this.chatService = chatService
    }

    /**
     * Checks if a chat service has been initialized for this session.
     *
     * @return true if chat service has been set, false otherwise
     */
    fun hasChatService(): Boolean = ::chatService.isInitialized

    /**
     * Gets the currently active model provider for this session.
     */
    fun getActiveProvider(): ModelProvider = params.currentProvider

    /**
     * Current project scope for this session, if any.
     *
     * When set via setScope(ProjectEntry), it records the human-readable project name
     * and the normalized absolute path to the project's root directory. A null value
     * means the session is not bound to any project (and project-specific RAG features
     * may be disabled).
     *
     * This property is read-only to callers; use setScope(...) to activate a project
     * and clearScope() to remove the association.
     */
    var scope: Scope? = null
        private set

    fun setScope(project: ProjectEntry) {
        scope = Scope(project.name, Paths.get(project.dir).toAbsolutePath().normalize())
    }

    fun clearScope() {
        scope = null
    }

    /**
     * Gets the current provider's settings.
     */
    fun getCurrentProviderSettings(): ProviderSettings =
        params.providerSettings[params.currentProvider]
            ?: getModelFactory()?.defaultSettings()
            ?: NoopProviderSettings

    /**
     * Sets the provider-specific settings into the map.
     */
    fun setProviderSetting(
        provider: ModelProvider,
        settings: ProviderSettings,
    ) {
        params.providerSettings[provider] = settings
    }

    /**
     * Returns the registered factory for the current provider.
     */
    fun getModelFactory(): ChatModelFactory? = ProviderRegistry.getFactory(params.currentProvider)

    /**
     * Returns the registered factory for the given provider.
     */
    fun getModelFactory(provider: ModelProvider): ChatModelFactory? = ProviderRegistry.getFactory(provider)

    /**
     * Gets the provider-specific settings map, or creates defaults if missing.
     */
    fun getOrCreateProviderSettings(provider: ModelProvider): ProviderSettings =
        params.providerSettings.getOrPut(provider) {
            getModelFactory(provider)?.defaultSettings() ?: NoopProviderSettings
        }

    /**
     * Retrieves an existing chat memory for the given provider and model combination,
     * or creates a new one if none exists.
     *
     * @param provider The model provider to get or create memory for
     * @param model The model name to get or create memory for
     * @param settings Provider-specific settings that may influence memory creation
     * @return A [ChatMemory] instance that maintains conversation history for the specified
     *         provider/model combination
     */
    fun getOrCreateMemory(
        provider: ModelProvider,
        model: String,
        settings: ProviderSettings,
    ): ChatMemory {
        val key = "${provider.name}/$model"
        return memoryMap.getOrPut(key) {
            ProviderRegistry.getFactory(provider)?.createMemory(model, settings)
                ?: MessageWindowChatMemory.withMaxMessages(200)
        }
    }

    fun removeMemory(
        provider: ModelProvider,
        modelName: String,
    ) {
        memoryMap.remove("${provider.name}/$modelName")
    }

    /**
     * Rebuilds and returns a new instance of the active chat model based on current session parameters.
     *
     * @param memoryPolicy Controls whether to keep or reset the chat memory for the current
     *                     provider/model combination. Default is [MemoryPolicy.KEEP_PER_PROVIDER_MODEL].
     * @return A newly created [ChatService] instance that becomes the active model for this session.
     * @throws IllegalStateException if no model factory is registered for the current provider.
     */
    fun rebuildActiveChatService(memoryPolicy: MemoryPolicy = MemoryPolicy.KEEP_PER_PROVIDER_MODEL): ChatService {
        val provider = params.currentProvider
        val factory =
            getModelFactory(provider)
                ?: error("No model factory registered for $provider")

        val settings = getOrCreateProviderSettings(provider)
        val modelName = params.model

        if (memoryPolicy == MemoryPolicy.RESET_FOR_THIS_COMBO) {
            val key = "${provider.name}/$modelName"
            memoryMap.remove(key)
        }

        val memory = getOrCreateMemory(provider, modelName, settings)
        val newModel = factory.create(modelName, settings, memory)
        setChatService(newModel)
        return newModel
    }

    /**
     * Returns the active [ChatService]. If a model has not been created yet for the
     * current (provider, model) and settings, it will be built now.
     *
     * @param memoryPolicy Controls whether the existing memory bucket for this
     * (provider, model) is reused or reset when building for the first time.
     */
    fun getChatService(memoryPolicy: MemoryPolicy = MemoryPolicy.KEEP_PER_PROVIDER_MODEL): ChatService =
        if (hasChatService()) chatService else rebuildActiveChatService(memoryPolicy)

    /**
     * Enables Retrieval-Augmented Generation (RAG) for the current session using
     * the provided PgVectorIndexer.
     *
     * This method wires the indexer into a PgVectorContentRetriever and builds a
     * retrieval augmentor, then recreates the active ChatService with the same
     * provider, model, settings, and memory bucket, but augmented with retrieval.
     *
     * Notes:
     * - Memory is preserved; the conversation context for the current (provider, model)
     *   is reused.
     * - Requires that a model factory is registered for the current provider; otherwise
     *   an IllegalStateException is thrown.
     * - Typically called after setScope(...) when switching to a project that has
     *   indexed content.
     *
     * @param indexer The PgVector-backed indexer to use for retrieving relevant context.
     */
    fun enableRagWith(indexer: PgVectorIndexer) {
        val retriever = PgVectorContentRetriever(indexer)
        val rag = buildRetrievalAugmentor(retriever)

        val provider = params.currentProvider
        val model = params.model
        val settings = getCurrentProviderSettings()
        val memory = getOrCreateMemory(provider, model, settings)

        val factory =
            getModelFactory(provider)
                ?: error("No model factory registered for $provider")

        val upgraded =
            factory.create(
                model = model,
                settings = settings,
                memory = memory,
                retrievalAugmentor = rag,
            )
        setChatService(upgraded)
    }
}
